package com.secure.otpforwarder

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Service for forwarding OTPs to backend via HTTPS.
 * 
 * Security features:
 * - HTTPS only (enforced by network security config)
 * - AES-256-GCM encryption
 * - HMAC signature
 * - No OTP logging
 * - Timeout protection
 */
class OtpForwardingService(context: Context) {
    
    companion object {
        private const val TAG = "OtpForwardingService"
        private const val TIMEOUT_SECONDS = 10L
    }
    
    private val config = ConfigManager(context)
    private val cryptoManager = CryptoManager(config.aesKey, config.hmacKey)
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * Forward OTP to backend.
     * 
     * @param otp The OTP value
     * @param sender The SMS sender
     * @throws Exception if forwarding fails
     * 
     * Security: OTP is encrypted before transmission, never logged
     */
    suspend fun forwardOTP(otp: String, sender: String) {
        // Encrypt OTP and generate HMAC
        val (encryptedPayload, hmacSignature) = cryptoManager.encryptAndSign(otp)
        
        // Create request payload
        val timestamp = System.currentTimeMillis() / 1000
        val requestData = mapOf(
            "encrypted_payload" to encryptedPayload,
            "hmac_signature" to hmacSignature,
            "sender" to sender,
            "timestamp" to timestamp
        )
        
        val jsonPayload = gson.toJson(requestData)
        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
        
        // Build request
        val request = Request.Builder()
            .url("${config.backendUrl}/forward-otp")
            .post(requestBody)
            .build()
        
        // Execute request
        try {
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                Log.i(TAG, "✅ OTP forwarded successfully")
                config.lastForwardedTimestamp = System.currentTimeMillis()
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "❌ Backend error: ${response.code} - $errorBody")
                throw Exception("Backend returned ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Network error: ${e.message}")
            throw e
        }
    }
}
