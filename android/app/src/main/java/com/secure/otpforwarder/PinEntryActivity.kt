package com.secure.otpforwarder

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.secure.otpforwarder.databinding.ActivityPinBinding

/**
 * Activity to enter the PIN to unlock the app.
 */
class PinEntryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinBinding
    private lateinit var authManager: AuthManager
    private var failedAttempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)

        binding.btnUnlock.setOnClickListener {
            handlePinEntry()
        }
    }

    private fun handlePinEntry() {
        val pin = binding.etPin.text.toString().trim()
        
        if (authManager.verifyPin(pin)) {
            failedAttempts = 0
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            failedAttempts++
            val remaining = 5 - failedAttempts
            if (remaining > 0) {
                Toast.makeText(this, "Incorrect PIN. $remaining attempts left.", Toast.LENGTH_SHORT).show()
                binding.etPin.text?.clear()
            } else {
                Toast.makeText(this, "Too many failed attempts. Try again later.", Toast.LENGTH_LONG).show()
                binding.btnUnlock.isEnabled = false
                // In a real app, implement lockout timer
            }
        }
    }
}
