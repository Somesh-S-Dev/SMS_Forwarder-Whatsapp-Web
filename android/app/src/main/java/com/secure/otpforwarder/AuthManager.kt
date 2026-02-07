package com.secure.otpforwarder

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages user authentication state and PIN security.
 */
class AuthManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    enum class AuthState {
        NOT_REGISTERED,      // First launch
        REGISTERED_NO_PIN,   // Signed up, no PIN set
        REGISTERED_WITH_PIN, // Fully configured
        SERVICE_DISABLED     // User disabled service
    }

    /**
     * Get the current authentication state.
     */
    fun getAuthState(): AuthState {
        if (!isUserRegistered()) return AuthState.NOT_REGISTERED
        if (getPinHash().isEmpty()) return AuthState.REGISTERED_NO_PIN
        if (!isServiceEnabled()) return AuthState.SERVICE_DISABLED
        return AuthState.REGISTERED_WITH_PIN
    }

    fun isUserRegistered(): Boolean {
        return sharedPreferences.getString(KEY_WHATSAPP_NUMBER, "").isNullOrEmpty().not()
    }

    fun saveUserProfile(name: String, whatsappNumber: String) {
        sharedPreferences.edit()
            .putString(KEY_USER_NAME, name)
            .putString(KEY_WHATSAPP_NUMBER, whatsappNumber)
            .apply()
    }

    fun getUserName(): String = sharedPreferences.getString(KEY_USER_NAME, "") ?: ""
    fun getWhatsappNumber(): String = sharedPreferences.getString(KEY_WHATSAPP_NUMBER, "") ?: ""

    /**
     * Store PIN hash. In a real app, use Argon2 or PBKDF2.
     * Here we store it in EncryptedSharedPreferences which is already secure at rest.
     */
    fun setPin(pin: String) {
        sharedPreferences.edit().putString(KEY_PIN_HASH, pin).apply()
    }

    fun getPinHash(): String = sharedPreferences.getString(KEY_PIN_HASH, "") ?: ""

    fun verifyPin(pin: String): Boolean {
        return getPinHash() == pin
    }

    fun isServiceEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, true)
    }

    fun setServiceEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun logout() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_WHATSAPP_NUMBER = "whatsapp_number"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }
}
