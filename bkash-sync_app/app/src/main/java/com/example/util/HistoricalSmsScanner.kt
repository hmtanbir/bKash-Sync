package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HistoricalSmsScanner {
    private const val TAG = "HistoricalSmsScanner"

    /**
     * Scans the system inbox and imports historical bKash receipt messages.
     * Integrates with Local Room database through repository to guarantee uniqueness.
     */
    suspend fun scanHistoricalSms(context: Context, repository: TransactionRepository, limit: Int = 0): ScanResult = withContext(Dispatchers.IO) {
        var processedCount = 0
        var addedCount = 0
        var duplicateCount = 0
        var parseFailureCount = 0

        try {
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("address", "body", "date")
            
            // Query inbox with latest entries first
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC"
            )

            cursor?.use { c ->
                val addressColumn = c.getColumnIndexOrThrow("address")
                val bodyColumn = c.getColumnIndexOrThrow("body")
                val dateColumn = c.getColumnIndexOrThrow("date")

                var examinedCount = 0
                while (c.moveToNext()) {
                    if (limit > 0 && examinedCount >= limit) {
                        break
                    }
                    examinedCount++

                    val address = c.getString(addressColumn) ?: ""
                    val body = c.getString(bodyColumn) ?: ""
                    val timestamp = c.getLong(dateColumn)

                    // Step 1 & 2: Match valid bKash sender and contains money received indicator
                    if (SmsParser.isValidSender(address)) {
                        if (body.contains("You have received Tk", ignoreCase = true)) {
                            processedCount++
                            
                            // Step 3: Match with Regex & extract
                            val parsed = SmsParser.parse(body)
                            if (parsed != null) {
                                val isInserted = repository.addTransaction(
                                    trxId = parsed.trxId,
                                    amount = parsed.amount,
                                    senderNumber = parsed.senderNumber,
                                    balance = parsed.balance,
                                    datetime = parsed.datetime,
                                    rawSms = body,
                                    simSlot = "Inbox"
                                )
                                if (isInserted) {
                                    addedCount++
                                } else {
                                    duplicateCount++
                                }

                                // Update metadata tracked stamps
                                val oldState = repository.getSyncState()
                                repository.saveSyncState(
                                    lastSmsTimestamp = java.lang.Math.max(oldState?.lastSmsTimestamp ?: 0L, timestamp),
                                    lastSyncTime = oldState?.lastSyncTime ?: 0L
                                )
                            } else {
                                parseFailureCount++
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing historical sync query cursor check", e)
            return@withContext ScanResult(false, 0, 0, 0, 0, e.localizedMessage ?: "Unknown query error")
        }

        return@withContext ScanResult(
            isSuccess = true,
            totalProcessed = processedCount,
            totalNewInserted = addedCount,
            totalDuplicates = duplicateCount,
            totalParsedFailures = parseFailureCount,
            error = ""
        )
    }
}

data class ScanResult(
    val isSuccess: Boolean,
    val totalProcessed: Int,
    val totalNewInserted: Int,
    val totalDuplicates: Int,
    val totalParsedFailures: Int,
    val error: String
)
