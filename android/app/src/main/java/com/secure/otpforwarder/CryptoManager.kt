package com.secure.otpforwarder

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Cryptographic operations for OTP encryption and HMAC signing.
 * 
 * Security features:
 * - AES-256-GCM for authenticated encryption
 * - Random IV per message (12 bytes for GCM)
 * - HMAC-SHA256 for message authentication
 * - No OTP values stored or logged
 */
class CryptoManager(
    private val aesKeyHex: String,
    private val hmacKeyHex: String
) {
    
    private val aesKey: SecretKeySpec
    private val hmacKey: SecretKeySpec
    private val secureRandom = SecureRandom()
    
    init {
        // Convert hex keys to byte arrays
        val aesKeyBytes = hexToBytes(aesKeyHex)
        val hmacKeyBytes = hexToBytes(hmacKeyHex)
        
        // Create key specs
        aesKey = SecretKeySpec(aesKeyBytes, "AES")
        hmacKey = SecretKeySpec(hmacKeyBytes, "HmacSHA256")
    }
    
    /**
     * Encrypt OTP using AES-256-GCM.
     * 
     * @param plaintext The OTP to encrypt
     * @return Base64 encoded (IV + ciphertext + auth_tag)
     * 
     * Security: Each encryption uses a unique random IV
     */
    fun encryptOTP(plaintext: String): String {
        // Generate random IV (12 bytes for GCM)
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        
        // Initialize cipher
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)  // 128-bit auth tag
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
        
        // Encrypt
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val ciphertextWithTag = cipher.doFinal(plaintextBytes)
        
        // Combine IV + ciphertext + tag
        val combined = iv + ciphertextWithTag
        
        // Return base64 encoded
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
    
    /**
     * Generate HMAC-SHA256 signature for encrypted payload.
     * 
     * @param payload The base64 encoded encrypted data
     * @return Hex encoded HMAC signature
     */
    fun generateHMAC(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val signature = mac.doFinal(payloadBytes)
        
        return bytesToHex(signature)
    }
    
    /**
     * Encrypt OTP and generate HMAC in one operation.
     * 
     * @param otp The OTP to encrypt
     * @return Pair of (encrypted_payload, hmac_signature)
     */
    fun encryptAndSign(otp: String): Pair<String, String> {
        val encryptedPayload = encryptOTP(otp)
        val hmacSignature = generateHMAC(encryptedPayload)
        return Pair(encryptedPayload, hmacSignature)
    }
    
    // Helper functions
    
    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                          Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
    
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789abcdef"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789abcdef"[v and 0x0F]
        }
        return String(hexChars)
    }
}
