package com.secure.otpforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for incoming SMS messages.
 * 
 * Security features:
 * - OTP extraction using regex (4-8 digits)
 * - Sender allowlist validation
 * - Office hours enforcement
 * - No OTP logging
 */
class SmsReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "SmsReceiver"
        
        // OTP regex: 4-8 consecutive digits
        private val OTP_REGEX = Regex("\\b\\d{4,8}\\b")
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        
        // Load configuration
        val config = ConfigManager(context)
        
        // Check if app is configured
        if (!config.isConfigured()) {
            Log.w(TAG, "App not configured, ignoring SMS")
            return
        }
        
        // Extract SMS messages
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            return
        }
        
        for (smsMessage in messages) {
            processSmsMessage(context, smsMessage, config)
        }
    }
    
    private fun processSmsMessage(
        context: Context,
        smsMessage: SmsMessage,
        config: ConfigManager
    ) {
        val sender = smsMessage.displayOriginatingAddress ?: "UNKNOWN"
        val messageBody = smsMessage.messageBody ?: return
        
        // Security check 1: Sender allowlist
        if (!config.isSenderAllowed(sender)) {
            Log.d(TAG, "Sender not in allowlist: $sender")
            return
        }
        
        // Security check 2: Office hours
        if (!config.isWithinOfficeHours()) {
            Log.d(TAG, "Outside office hours, ignoring SMS")
            return
        }
        
        // Extract OTP
        val otp = extractOTP(messageBody)
        if (otp == null) {
            Log.d(TAG, "No OTP found in message")
            return
        }
        
        // Forward OTP (async)
        Log.i(TAG, "OTP detected from $sender, forwarding...")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val forwardingService = OtpForwardingService(context)
                forwardingService.forwardOTP(otp, sender)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to forward OTP: ${e.message}")
            }
        }
    }
    
    /**
     * Extract OTP from SMS message body.
     * 
     * @param messageBody The SMS text
     * @return OTP string if found, null otherwise
     * 
     * Security: Uses regex to find 4-8 digit sequences
     */
    private fun extractOTP(messageBody: String): String? {
        val match = OTP_REGEX.find(messageBody)
        return match?.value
    }
}
