package com.secure.otpforwarder

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages app configuration using encrypted SharedPreferences.
 * 
 * Security: All sensitive data is encrypted at rest using Android Security Crypto.
 */
class ConfigManager(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_otp_config",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    // Backend Configuration
    var backendUrl: String
        get() = sharedPreferences.getString(KEY_BACKEND_URL, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_BACKEND_URL, value).apply()
    
    // Encryption Keys (must match backend)
    var aesKey: String
        get() = sharedPreferences.getString(KEY_AES_KEY, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_AES_KEY, value).apply()
    
    var hmacKey: String
        get() = sharedPreferences.getString(KEY_HMAC_KEY, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_HMAC_KEY, value).apply()
    
    // Sender Allowlist (comma-separated)
    var allowedSenders: Set<String>
        get() {
            val sendersString = sharedPreferences.getString(KEY_ALLOWED_SENDERS, "") ?: ""
            return if (sendersString.isEmpty()) {
                emptySet()
            } else {
                sendersString.split(",").map { it.trim().uppercase() }.toSet()
            }
        }
        set(value) {
            val sendersString = value.joinToString(",")
            sharedPreferences.edit().putString(KEY_ALLOWED_SENDERS, sendersString).apply()
        }
    
    // Office Hours Configuration
    var officeHoursEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_OFFICE_HOURS_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_OFFICE_HOURS_ENABLED, value).apply()
    
    var officeStartHour: Int
        get() = sharedPreferences.getInt(KEY_OFFICE_START_HOUR, 9)
        set(value) = sharedPreferences.edit().putInt(KEY_OFFICE_START_HOUR, value).apply()
    
    var officeEndHour: Int
        get() = sharedPreferences.getInt(KEY_OFFICE_END_HOUR, 18)
        set(value) = sharedPreferences.edit().putInt(KEY_OFFICE_END_HOUR, value).apply()
    
    // Manual Override
    var manualOverrideEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_MANUAL_OVERRIDE, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_MANUAL_OVERRIDE, value).apply()
    
    // Last forwarded OTP timestamp (for UI display)
    var lastForwardedTimestamp: Long
        get() = sharedPreferences.getLong(KEY_LAST_FORWARDED, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_FORWARDED, value).apply()
    
    /**
     * Check if current time is within office hours.
     */
    fun isWithinOfficeHours(): Boolean {
        if (!officeHoursEnabled || manualOverrideEnabled) {
            return true
        }
        
        val calendar = java.util.Calendar.getInstance()
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        
        return currentHour in officeStartHour until officeEndHour
    }
    
    /**
     * Check if sender is in allowlist.
     */
    fun isSenderAllowed(sender: String): Boolean {
        val senders = allowedSenders
        if (senders.isEmpty()) {
            return true  // If no allowlist configured, allow all
        }
        return senders.contains(sender.uppercase())
    }
    
    /**
     * Validate that all required configuration is present.
     */
    fun isConfigured(): Boolean {
        return backendUrl.isNotEmpty() &&
               aesKey.isNotEmpty() &&
               hmacKey.isNotEmpty()
    }
    
    companion object {
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_AES_KEY = "aes_key"
        private const val KEY_HMAC_KEY = "hmac_key"
        private const val KEY_ALLOWED_SENDERS = "allowed_senders"
        private const val KEY_OFFICE_HOURS_ENABLED = "office_hours_enabled"
        private const val KEY_OFFICE_START_HOUR = "office_start_hour"
        private const val KEY_OFFICE_END_HOUR = "office_end_hour"
        private const val KEY_MANUAL_OVERRIDE = "manual_override"
        private const val KEY_LAST_FORWARDED = "last_forwarded"
    }
}
