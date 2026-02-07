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
        private val classifier = MessageClassifier()
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        
        // Load configuration
        val config = ConfigManager(context)
        val authManager = AuthManager(context)
        
        // Check if service is enabled and user is registered
        if (!authManager.isServiceEnabled() || !authManager.isUserRegistered()) {
            Log.d(TAG, "Service disabled or user not registered, ignoring SMS")
            return
        }

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
        
        // Handle potential multi-part SMS reassembly within this intent
        processConsolidatedMessages(context, messages, config, authManager)
    }
    
    private fun processConsolidatedMessages(
        context: Context,
        messages: Array<SmsMessage>,
        config: ConfigManager,
        authManager: AuthManager
    ) {
        val sender = messages[0].displayOriginatingAddress ?: "UNKNOWN"
        val fullBody = StringBuilder()
        for (msg in messages) {
            msg.messageBody?.let { fullBody.append(it) }
        }
        
        processSmsMessage(context, sender, fullBody.toString(), config, authManager)
    }
    
    private fun processSmsMessage(
        context: Context,
        sender: String,
        messageBody: String,
        config: ConfigManager,
        authManager: AuthManager
    ) {
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
        
        // Local Classification
        val classification = classifier.classify(messageBody)
        Log.d(TAG, "Classified message as ${classification.type} (Confidence: ${classification.confidence})")

        // Check if this type is enabled in settings
        if (!isTypeEnabled(classification.type, config)) {
            Log.d(TAG, "Message type ${classification.type} is disabled in settings, ignoring")
            return
        }

        // Forward message (async)
        Log.i(TAG, "Message detected from $sender, forwarding...")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // In Phase 7/8, we use a more generic MessageForwardingService
                // and determine message type
                val forwardingService = MessageForwardingService(context)
                forwardingService.forwardMessage(
                    content = messageBody,
                    sender = sender,
                    messageType = classification.type.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to forward message: ${e.message}")
            }
        }
    }
    
    private fun isTypeEnabled(type: MessageType, config: ConfigManager): Boolean {
        return when (type) {
            MessageType.OTP -> config.forwardOtp
            MessageType.TRANSACTION -> config.forwardTransaction
            MessageType.BILL -> config.forwardBill
            MessageType.SECURITY_ALERT -> config.forwardSecurity
            MessageType.UNKNOWN -> false // Don't forward unknown/unclassified to save resources
        }
    }
}
