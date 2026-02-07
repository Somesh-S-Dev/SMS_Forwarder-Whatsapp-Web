package com.secure.otpforwarder

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.secure.otpforwarder.databinding.ActivityPinBinding

/**
 * Activity to set up the app PIN for the first time.
 */
class PinSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinBinding
    private lateinit var authManager: AuthManager
    private var firstPin = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)

        binding.tvPinHint.text = "Create a 4-6 digit PIN to secure your app"

        binding.btnUnlock.text = "Set PIN"
        binding.btnUnlock.setOnClickListener {
            handlePinSetup()
        }
    }

    private fun handlePinSetup() {
        val pin = binding.etPin.text.toString().trim()
        if (pin.length < 4) {
            Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
            return
        }

        if (firstPin.isEmpty()) {
            firstPin = pin
            binding.etPin.text?.clear()
            binding.tvPinHint.text = "Confirm your PIN"
            binding.btnUnlock.text = "Confirm"
        } else {
            if (pin == firstPin) {
                authManager.setPin(pin)
                Toast.makeText(this, "PIN set successfully", Toast.LENGTH_SHORT).show()
                
                // Navigate to Dashboard (In a real app, maybe ServiceConfig first)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show()
                firstPin = ""
                binding.etPin.text?.clear()
                binding.tvPinHint.text = "Create a 4-6 digit PIN"
                binding.btnUnlock.text = "Set PIN"
            }
        }
    }
}
