package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.example.data.database.AppDatabase
import com.example.data.database.OutboxEntity
import com.example.data.database.SyncState
import com.example.data.database.TransactionEntity
import com.example.data.pref.PreferencesManager
import com.example.data.worker.SyncWorker
import com.example.data.api.RetrofitClient
import com.example.data.api.DeltaSyncPayload
import com.example.data.api.DeltaSyncResponse
import com.example.data.api.TransactionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class TransactionRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.transactionDao()

    val allTransactions: Flow<List<TransactionEntity>> = dao.getAllTransactions()
    val allOutbox: Flow<List<OutboxEntity>> = dao.getAllOutbox()
    
    val transactionsCount: Flow<Int> = dao.getTransactionsCount()
    val pendingCount: Flow<Int> = dao.getOutboxCountByStatus("PENDING")
    val sentCount: Flow<Int> = dao.getOutboxCountByStatus("SENT")
    val failedCount: Flow<Int> = dao.getOutboxCountByStatus("FAILED")
    val syncStateFlow: Flow<SyncState?> = dao.getSyncStateFlow()

    companion object {
        private const val TAG = "TransactionRepository"
    }

    /**
     * Tries to add a new transaction and queue it for background synchronization.
     * Prevents duplicate parsing through database unique constraints.
     */
    suspend fun addTransaction(
        trxId: String,
        amount: String,
        senderNumber: String,
        balance: String,
        datetime: String,
        rawSms: String,
        simSlot: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check for duplicate trxId in database before writing
            val existing = dao.getTransactionByTrxId(trxId)
            if (existing != null) {
                Log.d(TAG, "Duplicate transaction found with trxID: $trxId. Skipping.")
                return@withContext false
            }

            val prefs = PreferencesManager(context)
            val userPhone = prefs.userPhoneNumber

            // Create JSON format matching API Sync batch expectation
            val payloadJson = """
                {
                  "trxId": "$trxId",
                  "amount": "$amount",
                  "senderNumber": "$senderNumber",
                  "balance": "$balance",
                  "datetime": "$datetime",
                  "userPhoneNumber": "$userPhone",
                  "simSlot": "${simSlot ?: ""}"
                }
            """.trimIndent()

            val transaction = TransactionEntity(
                trxId = trxId,
                amount = amount,
                senderNumber = senderNumber,
                balance = balance,
                datetime = datetime,
                rawSms = rawSms,
                simSlot = simSlot,
                createdAt = System.currentTimeMillis()
            )

            val outbox = OutboxEntity(
                trxId = trxId,
                payloadJson = payloadJson,
                status = "PENDING"
            )

            // Database transitions
            val insertedId = dao.insertTransaction(transaction)
            if (insertedId > 0) {
                dao.insertOutbox(outbox)
                Log.d(TAG, "Successfully saved transaction & outbox for ID: $trxId")
                
                // Immediately trigger background synchronization
                triggerSync()
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting transaction: $trxId", e)
        }
        return@withContext false
    }

    /**
     * Triggers active sync via WorkManager
     */
    fun triggerSync() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag("bKashSyncWorkerTag")
                .build()

            WorkManager.getInstance(context).enqueue(syncRequest)
            Log.d(TAG, "WorkManager sync request enqueued successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger WorkManager", e)
        }
    }

    suspend fun saveSyncState(lastSmsTimestamp: Long, lastSyncTime: Long) = withContext(Dispatchers.IO) {
        val state = SyncState(id = 1, lastSmsTimestamp = lastSmsTimestamp, lastSyncTime = lastSyncTime)
        dao.insertOrUpdateSyncState(state)
    }

    suspend fun getSyncState(): SyncState? = withContext(Dispatchers.IO) {
        dao.getSyncState()
    }

    /**
     * Resets and places ALL local transaction records back into the outbox as PENDING.
     * Use this to recover when rows are deleted or missing from the server database.
     */
    suspend fun forceResyncAll() = withContext(Dispatchers.IO) {
        try {
            val transactionsList = dao.getAllTransactionsList()
            val prefs = PreferencesManager(context)
            val userPhone = prefs.userPhoneNumber

            for (tx in transactionsList) {
                val payloadJson = """
                    {
                      "trxId": "${tx.trxId}",
                      "amount": "${tx.amount}",
                      "senderNumber": "${tx.senderNumber}",
                      "balance": "${tx.balance}",
                      "datetime": "${tx.datetime}",
                      "userPhoneNumber": "$userPhone",
                      "simSlot": "${tx.simSlot ?: ""}"
                    }
                """.trimIndent()

                val outbox = OutboxEntity(
                    trxId = tx.trxId,
                    payloadJson = payloadJson,
                    status = "PENDING",
                    retryCount = 0,
                    lastAttemptTime = 0
                )
                dao.insertOutbox(outbox)
            }
            Log.d(TAG, "forceResyncAll: Logically requeued ${transactionsList.size} items to PENDING outbox")
            triggerSync()
        } catch (e: Exception) {
            Log.e(TAG, "Error performing forceResyncAll", e)
        }
    }

    /**
     * Executes the Smart Delta-Sync protocol.
     * Compares local trxIds with the server-side database.
     * 1. Re-queues any local transactions missing on the server to PENDING in the outbox.
     * 2. Imports any server transactions missing on the client into local SQLite.
     */
    suspend fun performSmartDeltaSync(): Boolean = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(context)
        if (!prefs.isConfigured) {
            Log.d(TAG, "Smart Delta-Sync: Skipped because system is not configured.")
            return@withContext false
        }

        val deltaSyncUrl = prefs.getDeltaSyncUrl()
        val apiKey = prefs.apiKey
        val userPhone = prefs.userPhoneNumber

        if (deltaSyncUrl.isEmpty() || apiKey.isEmpty() || userPhone.isEmpty()) {
            Log.e(TAG, "Smart Delta-Sync: Skipped due to missing configurations.")
            return@withContext false
        }

        try {
            // 1. Collect all local transaction IDs
            val localTxList = dao.getAllTransactionsList()
            val localTrxIds = localTxList.map { it.trxId }

            // 2. Perform network POST call to /synctransaction
            val payload = com.example.data.api.DeltaSyncPayload(
                userPhoneNumber = userPhone,
                localTrxIds = localTrxIds
            )
            val authHeader = "Bearer $apiKey"

            Log.d(TAG, "Smart Delta-Sync: Calling $deltaSyncUrl with ${localTrxIds.size} local IDs.")
            val response = RetrofitClient.apiService.deltaSync(
                url = deltaSyncUrl,
                authHeader = authHeader,
                payload = payload
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                val missingOnServer = body.missingOnServer ?: emptyList()
                val missingOnClient = body.missingOnClient ?: emptyList()

                Log.d(TAG, "Smart Delta-Sync Success! Missing on Server: ${missingOnServer.size}, Missing on Client: ${missingOnClient.size}")

                // A. Recover Missing on Server: Re-queue local records back into outbox as PENDING
                if (missingOnServer.isNotEmpty()) {
                    var reQueuedCount = 0
                    val localMap = localTxList.associateBy { it.trxId }
                    for (trxId in missingOnServer) {
                        val localTx = localMap[trxId] ?: continue
                        val payloadJson = """
                            {
                              "trxId": "${localTx.trxId}",
                              "amount": "${localTx.amount}",
                              "senderNumber": "${localTx.senderNumber}",
                              "balance": "${localTx.balance}",
                              "datetime": "${localTx.datetime}",
                              "userPhoneNumber": "$userPhone",
                              "simSlot": "${localTx.simSlot ?: ""}"
                            }
                        """.trimIndent()

                        val outbox = OutboxEntity(
                            trxId = localTx.trxId,
                            payloadJson = payloadJson,
                            status = "PENDING",
                            retryCount = 0,
                            lastAttemptTime = 0
                        )
                        dao.insertOutbox(outbox)
                        reQueuedCount++
                    }
                    Log.d(TAG, "Smart Delta-Sync: Re-queued $reQueuedCount items missing on Server to PENDING outbox")
                    // Trigger SyncWorker to upload them immediately
                    triggerSync()
                }

                // B. Recover Missing on Client: Download server-side records and populate local DB
                if (missingOnClient.isNotEmpty()) {
                    var restoredLocalCount = 0
                    for (serverItem in missingOnClient) {
                        // Check if it really doesn't exist to avoid constraints
                        if (dao.getTransactionByTrxId(serverItem.trxId) == null) {
                            val transaction = TransactionEntity(
                                trxId = serverItem.trxId,
                                amount = serverItem.amount,
                                senderNumber = serverItem.senderNumber,
                                balance = serverItem.balance,
                                datetime = serverItem.datetime,
                                rawSms = "Restored via Smart Delta-Sync from server",
                                simSlot = serverItem.simSlot,
                                createdAt = System.currentTimeMillis()
                            )
                            dao.insertTransaction(transaction)

                            // Add a SENT record in outbox so it shows up in history as synchronized
                            val payloadJson = """
                                {
                                  "trxId": "${serverItem.trxId}",
                                  "amount": "${serverItem.amount}",
                                  "senderNumber": "${serverItem.senderNumber}",
                                  "balance": "${serverItem.balance}",
                                  "datetime": "${serverItem.datetime}",
                                  "userPhoneNumber": "${serverItem.userPhoneNumber ?: userPhone}",
                                  "simSlot": "${serverItem.simSlot ?: ""}"
                                }
                            """.trimIndent()
                            
                            val outbox = OutboxEntity(
                                trxId = serverItem.trxId,
                                payloadJson = payloadJson,
                                status = "SENT",
                                retryCount = 0,
                                lastAttemptTime = System.currentTimeMillis()
                            )
                            dao.insertOutbox(outbox)
                            restoredLocalCount++
                        }
                    }
                    Log.d(TAG, "Smart Delta-Sync: Restored $restoredLocalCount missing records locally.")
                }

                // Update sync check execution time
                dao.insertOrUpdateSyncState(
                    com.example.data.database.SyncState(
                        id = 1,
                        lastSmsTimestamp = dao.getSyncState()?.lastSmsTimestamp ?: 0L,
                        lastSyncTime = System.currentTimeMillis()
                    )
                )

                return@withContext true
            } else {
                Log.e(TAG, "Smart Delta-Sync response failed: ${response.code()} / ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Smart Delta-Sync", e)
        }
        return@withContext false
    }

    /**
     * Purges database content safely (mainly useful for troubleshooting and demo validation)
     */
    suspend fun clearDatabase() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        Log.d(TAG, "Database purged entirely.")
    }
}
