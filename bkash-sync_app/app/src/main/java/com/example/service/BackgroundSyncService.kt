package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.pref.PreferencesManager
import com.example.data.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackgroundSyncService : Service() {

    companion object {
        private const val TAG = "BackgroundSyncService"
        private const val CHANNEL_ID = "bkash_sync_service_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            try {
                val intent = Intent(context, BackgroundSyncService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "BackgroundSyncService start command invoked.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BackgroundSyncService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, BackgroundSyncService::class.java)
                context.stopService(intent)
                Log.d(TAG, "BackgroundSyncService stop command invoked.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop BackgroundSyncService", e)
            }
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var isSubscribed = false
    private var isScannerStarted = false

    data class Metrics(
        val total: Int = 0,
        val pending: Int = 0,
        val sent: Int = 0,
        val failed: Int = 0,
        val lastSyncTime: Long = 0L
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BackgroundSyncService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BackgroundSyncService starting foreground")
        createNotificationChannel()
        
        // Start foreground with an initial fallback/loading notification layout
        val initialNotification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed startForeground call", e)
        }

        // Start observing database flows to update status in real-time
        startMetricsSubscription()

        // Start periodic verification loops comparing system SMS inbox with live database
        startInboxScannerLoop()

        // Return START_STICKY so Android recreates the Service if it's ever killed under extreme pressure
        return START_STICKY
    }

    private fun startInboxScannerLoop() {
        if (isScannerStarted) return
        isScannerStarted = true

        val repository = TransactionRepository(this)
        serviceScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val prefs = PreferencesManager(this@BackgroundSyncService)
                    if (prefs.isConfigured) {
                        Log.d(TAG, "BackgroundSyncService: Running periodic background sweep comparing inbox with DB...")
                        val result = com.example.util.HistoricalSmsScanner.scanHistoricalSms(
                            context = this@BackgroundSyncService,
                            repository = repository,
                            limit = 50 // process top 50 recent SMS to guarantee speed & avoid heavy battery/CPU load
                        )
                        if (result.isSuccess && result.totalNewInserted > 0) {
                            Log.d(TAG, "BackgroundSyncService sweep discovered ${result.totalNewInserted} sync-missing transactions in inbox. Triggering instant uploads.")
                            repository.triggerSync()
                        }

                        // If autoReconcileOnSweep is enabled, run the smart delta sync to synchronize differences bi-directionally
                        if (prefs.autoReconcileOnSweep) {
                            Log.d(TAG, "BackgroundSyncService: Auto-reconcile with Smart Delta-Sync active.")
                            repository.performSmartDeltaSync()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in background continuous inbox comparison loop", e)
                }
                // Sweep check occurs periodically according to configured sync intervals
                val prefs = PreferencesManager(this@BackgroundSyncService)
                val intervalMs = prefs.syncIntervalSeconds.coerceAtLeast(1).toLong() * 1000L
                kotlinx.coroutines.delay(intervalMs)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
         return null
    }

    private fun startMetricsSubscription() {
        if (isSubscribed) return
        isSubscribed = true

        val repository = TransactionRepository(this)
        serviceScope.launch {
            try {
                combine(
                    repository.transactionsCount,
                    repository.pendingCount,
                    repository.sentCount,
                    repository.failedCount,
                    repository.syncStateFlow
                ) { total, pending, sent, failed, state ->
                    Metrics(
                        total = total,
                        pending = pending,
                        sent = sent,
                        failed = failed,
                        lastSyncTime = state?.lastSyncTime ?: 0L
                    )
                }.collect { metrics ->
                    updateNotificationWithMetrics(metrics)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in metrics flow subscription", e)
            }
        }
    }

    private fun updateNotificationWithMetrics(metrics: Metrics) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notificationManager != null) {
            val updatedNotification = buildMetricsNotification(metrics)
            notificationManager.notify(NOTIFICATION_ID, updatedNotification)
        }
    }

    private fun buildNotification(): Notification {
        val prefs = PreferencesManager(this)
        val phone = prefs.userPhoneNumber.ifEmpty { "Not Configured" }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("bKash SMS Gateway: Active")
            .setContentText("Monitoring live incoming bKash SMS streams...")
            .setSubText("User: $phone")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildMetricsNotification(metrics: Metrics): Notification {
        val prefs = PreferencesManager(this)
        val phone = prefs.userPhoneNumber.ifEmpty { "Not Configured" }

        val syncStatusStr = "Synced: ${metrics.sent} | Pending: ${metrics.pending} | Failed: ${metrics.failed}"
        
        val lastSyncStr = if (metrics.lastSyncTime > 0L) {
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            "Last Sync: " + sdf.format(Date(metrics.lastSyncTime))
        } else {
            "Last Sync: Never"
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (metrics.pending > 0) {
            "bKash Sync Service: Uploading... 🔄"
        } else {
            "bKash Sync Service: Active 🟢"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(syncStatusStr)
            .setSubText(lastSyncStr)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "bKash SMS Background Sync Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors and instantly synchronizes bKash transactions securely in the background."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "BackgroundSyncService destroyed and scope canceled")
    }
}
