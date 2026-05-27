package com.example.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["trxId"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trxId: String,
    val amount: String,
    val senderNumber: String,
    val balance: String,
    val datetime: String,
    val rawSms: String,
    val simSlot: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "outbox",
    indices = [Index(value = ["trxId"], unique = true)]
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trxId: String,
    val payloadJson: String,
    val status: String, // "PENDING", "SENT", "FAILED"
    val retryCount: Int = 0,
    val lastAttemptTime: Long = 0
)

@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey val id: Int = 1,
    val lastSmsTimestamp: Long,
    val lastSyncTime: Long
)
