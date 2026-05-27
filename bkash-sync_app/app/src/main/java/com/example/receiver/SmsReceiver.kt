package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.pref.PreferencesManager
import com.example.data.repository.TransactionRepository
import com.example.util.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = PreferencesManager(context)
        // 5. Config Requirement: The app MUST NOT start monitoring until API is configured.
        if (!prefs.isConfigured) {
            Log.d(TAG, "SMS ignored: configuration inactive.")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val firstMessage = messages[0]
        val sender = firstMessage.originatingAddress ?: ""

        if (!SmsParser.isValidSender(sender)) {
            Log.d(TAG, "SMS ignored: sender prefix not of bkash origin.")
            return
        }

        val fullBody = StringBuilder()
        for (msg in messages) {
            fullBody.append(msg.messageBody)
        }
        val smsText = fullBody.toString()

        // Filtering Rule: Must contain "You have received Tk"
        if (!smsText.contains("You have received Tk", ignoreCase = true)) {
            Log.d(TAG, "SMS ignored: message does not contain incoming token.")
            return
        }

        Log.d(TAG, "Valid bKash received transaction intercepted.")
        val subscriptionId = intent.getIntExtra("subscription", -1)
        val simSlotId = intent.getIntExtra("slot", -1)
        Log.d(TAG, "SIM Broadcast Details - Subscription ID: $subscriptionId, SIM Slot Index: $simSlotId")

        val simSlotString = if (simSlotId != -1) {
            "SIM ${simSlotId + 1}"
        } else if (subscriptionId != -1) {
            "SubId: $subscriptionId"
        } else {
            "SIM 1"
        }

        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val parsed = SmsParser.parse(smsText)
                if (parsed != null) {
                    val repository = TransactionRepository(context)
                    val success = repository.addTransaction(
                        trxId = parsed.trxId,
                        amount = parsed.amount,
                        senderNumber = parsed.senderNumber,
                        balance = parsed.balance,
                        datetime = parsed.datetime,
                        rawSms = smsText,
                        simSlot = simSlotString
                    )
                    
                    val smsTimestamp = firstMessage.timestampMillis
                    val oldState = repository.getSyncState()
                    repository.saveSyncState(
                        lastSmsTimestamp = java.lang.Math.max(oldState?.lastSmsTimestamp ?: 0L, smsTimestamp),
                        lastSyncTime = oldState?.lastSyncTime ?: 0L
                    )
                    Log.d(TAG, "Intercepted transaction parse outcome. Success: $success")
                } else {
                    Log.e(TAG, "Regular expression parsing failed on target-identified SMS text: $smsText")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Receiver background thread execution error", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
