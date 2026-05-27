package com.example.util

import android.util.Log
import java.util.regex.Pattern

data class ParsedTransaction(
    val amount: String,
    val senderNumber: String,
    val balance: String,
    val trxId: String,
    val datetime: String
)

object SmsParser {
    private const val TAG = "SmsParser"
    
    // Regex specified in requirements:
    // You have received Tk\s([\d,]+\.\d{2})\sfrom\s(\d+).*?Balance Tk\s([\d,]+\.\d{2}).*?TrxID\s([A-Z0-9]+)\sat\s([\d/: ]+)
    // We compile with CASE_INSENSITIVE and DOTALL flags to support multi-line SMS.
    private val regexPattern = Pattern.compile(
        "You have received Tk\\s([\\d,]+\\.\\d{2})\\sfrom\\s(\\d+).*?Balance Tk\\s([\\d,]+\\.\\d{2}).*?TrxID\\s([A-Z0-9]+)\\sat\\s([\\d/: ]+)",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    /**
     * Parses an incoming bKash SMS and extracts the transactions details.
     * Returns null if does not match bKash format.
     */
    fun parse(smsBody: String): ParsedTransaction? {
        try {
            val matcher = regexPattern.matcher(smsBody)
            if (matcher.find()) {
                val rawAmount = matcher.group(1) ?: ""
                val sender = matcher.group(2) ?: ""
                val rawBalance = matcher.group(3) ?: ""
                val trxId = matcher.group(4) ?: ""
                val datetime = matcher.group(5) ?: ""

                // Ensure essential fields are not empty
                if (trxId.isNotEmpty() && rawAmount.isNotEmpty() && sender.isNotEmpty()) {
                    // Normalize money amounts (remove commas: e.g., "10,000.00" -> "10000.00")
                    val amount = rawAmount.replace(",", "").trim()
                    val balance = rawBalance.replace(",", "").trim()
                    
                    return ParsedTransaction(
                        amount = amount,
                        senderNumber = sender.trim(),
                        balance = balance,
                        trxId = trxId.trim(),
                        datetime = datetime.trim()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing failed for SMS: $smsBody", e)
        }
        return null
    }

    /**
     * Checks if the sender ID is a valid bKash sender ID.
     */
    fun isValidSender(sender: String?): Boolean {
        if (sender == null) return false
        val cleanSender = sender.trim().lowercase()
        return cleanSender == "bkash" || cleanSender.contains("bkash")
    }
}
