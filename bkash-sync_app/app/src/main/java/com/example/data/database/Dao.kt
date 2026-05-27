package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    // --- Transactions ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE trxId = :trxId LIMIT 1")
    suspend fun getTransactionByTrxId(trxId: String): TransactionEntity?

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsList(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    fun getTransactionsCount(): Flow<Int>

    // --- Outbox ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(outbox: OutboxEntity): Long

    @Query("SELECT * FROM outbox WHERE status = 'PENDING' OR status = 'FAILED' ORDER BY id ASC")
    suspend fun getPendingOutbox(): List<OutboxEntity>

    @Query("SELECT * FROM outbox ORDER BY id DESC")
    fun getAllOutbox(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE trxId = :trxId LIMIT 1")
    suspend fun getOutboxByTrxId(trxId: String): OutboxEntity?

    @Query("UPDATE outbox SET status = :status, retryCount = :retryCount, lastAttemptTime = :lastAttemptTime WHERE trxId = :trxId")
    suspend fun updateOutboxStatus(trxId: String, status: String, retryCount: Int, lastAttemptTime: Long)

    @Query("DELETE FROM outbox WHERE trxId = :trxId")
    suspend fun deleteOutboxByTrxId(trxId: String)

    @Query("SELECT COUNT(*) FROM outbox WHERE status = :status")
    fun getOutboxCountByStatus(status: String): Flow<Int>

    // --- Sync State ---

    @Query("SELECT * FROM sync_state WHERE id = 1 LIMIT 1")
    fun getSyncStateFlow(): Flow<SyncState?>

    @Query("SELECT * FROM sync_state WHERE id = 1 LIMIT 1")
    suspend fun getSyncState(): SyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSyncState(state: SyncState)
}
