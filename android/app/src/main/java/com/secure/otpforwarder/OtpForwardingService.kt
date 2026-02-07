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
 * Service for forwarding sensitive messages to backend via HTTPS.
 */
class MessageForwardingService(context: Context) {
    
    companion object {
        private const val TAG = "MessageForwardingService"
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
     * Forward message to backend.
     */
    suspend fun forwardMessage(
        content: String, 
        sender: String, 
        messageType: String = "UNKNOWN",
        category: String? = null,
        summary: String? = null,
        language: String? = "en"
    ) {
        // Encrypt content and generate HMAC
        val (encryptedPayload, hmacSignature) = cryptoManager.encryptAndSign(content)
        
        // Create request payload
        val timestamp = System.currentTimeMillis() / 1000
        val requestData = mutableMapOf<String, Any>(
            "encrypted_payload" to encryptedPayload,
            "hmac_signature" to hmacSignature,
            "sender" to sender,
            "message_type" to messageType,
            "timestamp" to timestamp
        )
        
        // Add optional AI metadata
        category?.let { requestData["category"] = it }
        summary?.let { requestData["summary"] = it }
        language?.let { requestData["language"] = it }
        
        val jsonPayload = gson.toJson(requestData)
        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
        
        // Build request - use the new forward-message endpoint
        val request = Request.Builder()
            .url("${config.backendUrl}/forward-message")
            .post(requestBody)
            .build()
        
        // Execute request
        try {
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                Log.i(TAG, "✅ Message forwarded successfully")
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
