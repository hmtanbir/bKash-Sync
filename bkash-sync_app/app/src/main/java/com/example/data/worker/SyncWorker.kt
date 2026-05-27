package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.api.RetrofitClient
import com.example.data.api.TransactionItem
import com.example.data.api.TransactionPayload
import com.example.data.database.AppDatabase
import com.example.data.pref.PreferencesManager

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val BATCH_SIZE = 30
        private const val MAX_RETRY_LIMIT = 8
    }

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        if (!prefs.isConfigured) {
            Log.d(TAG, "SyncWorker skipped: API system not configured yet.")
            return Result.success()
        }

        val syncUrl = prefs.getSyncUrl()
        val key = prefs.apiKey
        if (syncUrl.isEmpty() || key.isEmpty()) {
            Log.d(TAG, "SyncWorker skipped: Normalized syncUrl or API key empty.")
            return Result.success()
        }

        val dao = AppDatabase.getDatabase(applicationContext).transactionDao()
        val pendingItems = dao.getPendingOutbox()
        if (pendingItems.isEmpty()) {
            Log.d(TAG, "SyncWorker completed: Outbox contains 0 pending items.")
            // Record last sync check time
            dao.insertOrUpdateSyncState(
                com.example.data.database.SyncState(
                    id = 1,
                    lastSmsTimestamp = dao.getSyncState()?.lastSmsTimestamp ?: 0L,
                    lastSyncTime = System.currentTimeMillis()
                )
            )
            return Result.success()
        }

        Log.d(TAG, "Starting sync for ${pendingItems.size} transactions.")
        var hasFailures = false

        // Process pending entries in logical batch chunks (up to 30 items)
        val chunks = pendingItems.chunked(BATCH_SIZE)
        for (chunk in chunks) {
            val transactionsList = mutableListOf<TransactionItem>()
            val processedOutbox = mutableListOf<com.example.data.database.OutboxEntity>()

            for (outbox in chunk) {
                val tx = dao.getTransactionByTrxId(outbox.trxId)
                if (tx != null) {
                    transactionsList.add(
                        TransactionItem(
                            trxId = tx.trxId,
                            amount = tx.amount,
                            senderNumber = tx.senderNumber,
                            balance = tx.balance,
                            datetime = tx.datetime,
                            userPhoneNumber = prefs.userPhoneNumber,
                            simSlot = tx.simSlot
                        )
                    )
                    processedOutbox.add(outbox)
                } else {
                    // Orphaned outbox reference (no sync matching transaction), purge securely
                    dao.deleteOutboxByTrxId(outbox.trxId)
                }
            }

            if (transactionsList.isEmpty()) continue

            try {
                val payload = TransactionPayload(transactions = transactionsList)
                val authHeader = "Bearer $key"
                
                Log.d(TAG, "Uploading batch of ${transactionsList.size} transactions to $syncUrl")
                val response = RetrofitClient.apiService.syncTransactions(
                    url = syncUrl,
                    authHeader = authHeader,
                    payload = payload
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d(TAG, "Batch synchronization success.")
                    // Mark outbox entries as SENT
                    for (outbox in processedOutbox) {
                        // Update status to SENT so they reside inside the outbox visual logger as synced.
                        dao.updateOutboxStatus(
                            trxId = outbox.trxId,
                            status = "SENT",
                            retryCount = outbox.retryCount,
                            lastAttemptTime = System.currentTimeMillis()
                        )
                    }
                } else {
                    Log.e(TAG, "Batch synchronization response failed: Code ${response.code()}")
                    hasFailures = true
                    handleBatchFailure(dao, processedOutbox)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network synchronizer exception for current batch: ${e.message}", e)
                hasFailures = true
                handleBatchFailure(dao, processedOutbox)
            }
        }

        // Update overall sync date
        dao.insertOrUpdateSyncState(
            com.example.data.database.SyncState(
                id = 1,
                lastSmsTimestamp = dao.getSyncState()?.lastSmsTimestamp ?: 0L,
                lastSyncTime = System.currentTimeMillis()
            )
        )

        return if (hasFailures) {
            Log.d(TAG, "SyncWorker finished with failure blocks. Rescheduling retry.")
            Result.retry()
        } else {
            Log.d(TAG, "SyncWorker finished successfully.")
            Result.success()
        }
    }

    private suspend fun handleBatchFailure(
        dao: com.example.data.database.TransactionDao,
        failedChunk: List<com.example.data.database.OutboxEntity>
    ) {
        for (outbox in failedChunk) {
            val count = outbox.retryCount + 1
            val nextStatus = if (count >= MAX_RETRY_LIMIT) "FAILED" else "PENDING"
            dao.updateOutboxStatus(
                trxId = outbox.trxId,
                status = nextStatus,
                retryCount = count,
                lastAttemptTime = System.currentTimeMillis()
            )
        }
    }
}
