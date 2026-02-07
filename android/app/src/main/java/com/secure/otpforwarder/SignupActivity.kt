package com.secure.otpforwarder

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.secure.otpforwarder.databinding.ActivitySignupBinding

/**
 * User registration screen (Name and WhatsApp number).
 */
class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSignup.setOnClickListener {
            handleSignup()
        }
    }

    private fun handleSignup() {
        val name = binding.etName.text.toString().trim()
        val whatsappNumber = binding.etWhatsappNumber.text.toString().trim()

        if (name.isEmpty() || whatsappNumber.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!binding.cbPrivacy.isChecked) {
            Toast.makeText(this, "Please accept the Privacy Policy", Toast.LENGTH_SHORT).show()
            return
        }

        // Navigate to OTP verification
        val intent = Intent(this, OtpVerificationActivity::class.java).apply {
            putExtra("name", name)
            putExtra("whatsappNumber", whatsappNumber)
        }
        startActivity(intent)
    }
}
