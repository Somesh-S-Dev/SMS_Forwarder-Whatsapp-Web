package com.secure.otpforwarder

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.secure.otpforwarder.databinding.ActivityOtpVerifyBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Activity to verify the OTP sent to WhatsApp.
 */
class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerifyBinding
    private lateinit var authManager: AuthManager
    private val client = OkHttpClient()
    private var resendEnabled = true
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)

        val name = intent.getStringExtra("name") ?: ""
        val whatsappNumber = intent.getStringExtra("whatsappNumber") ?: ""

        // Initial OTP send
        sendOtpRequest(whatsappNumber)

        binding.btnVerify.setOnClickListener {
            val otp = binding.etOtp.text.toString().trim()
            if (otp.length == 6) {
                verifyOtp(name, whatsappNumber, otp)
            } else {
                Toast.makeText(this, "Enter 6-digit OTP", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnResend.setOnClickListener {
            if (resendEnabled) {
                sendOtpRequest(whatsappNumber)
                startResendCooldown()
            }
        }
    }

    private fun startResendCooldown() {
        resendEnabled = false
        binding.btnResend.isEnabled = false
        var remaining = 30
        
        val runnable = object : Runnable {
            override fun run() {
                if (remaining > 0) {
                    binding.btnResend.text = "Resend Code ($remaining)"
                    remaining--
                    handler.postDelayed(this, 1000)
                } else {
                    resendEnabled = true
                    binding.btnResend.isEnabled = true
                    binding.btnResend.text = "Resend Code"
                }
            }
        }
        handler.post(runnable)
    }

    private fun sendOtpRequest(whatsappNumber: String) {
        // In a real app, retrieve backend URL from config or use a default
        val backendUrl = ConfigManager(this).backendUrl 
        if (backendUrl.isEmpty()) {
            Toast.makeText(this, "Backend URL not configured", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("whatsapp_number", whatsappNumber)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$backendUrl/send-verification-otp")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@OtpVerificationActivity, "OTP sent to WhatsApp", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@OtpVerificationActivity, "Failed to send OTP", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OtpVerificationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun verifyOtp(name: String, whatsappNumber: String, otp: String) {
        val backendUrl = ConfigManager(this).backendUrl
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("whatsapp_number", whatsappNumber)
                    put("otp", otp)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$backendUrl/verify-otp")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            // Register user locally
                            authManager.saveUserProfile(name, whatsappNumber)
                            // Navigate to PIN setup
                            startActivity(Intent(this@OtpVerificationActivity, PinSetupActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this@OtpVerificationActivity, "Invalid OTP", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@OtpVerificationActivity, "Verification failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OtpVerificationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
