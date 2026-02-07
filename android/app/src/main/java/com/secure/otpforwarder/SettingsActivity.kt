package com.secure.otpforwarder

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.secure.otpforwarder.databinding.ActivitySettingsBinding

/**
 * Settings screen for app configuration.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var config: ConfigManager
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = ConfigManager(this)
        authManager = AuthManager(this)

        loadSettings()

        binding.btnLogout.setOnClickListener {
            handleLogout()
        }
    }

    private fun loadSettings() {
        binding.cbForwardOtp.isChecked = config.forwardOtp
        binding.cbForwardTransaction.isChecked = config.forwardTransaction
        binding.cbForwardBill.isChecked = config.forwardBill
        binding.cbForwardSecurity.isChecked = config.forwardSecurity

        binding.switchOfficeHours.isChecked = config.officeHoursEnabled
        binding.etBackendUrl.setText(config.backendUrl)

        // Save on change
        binding.cbForwardOtp.setOnCheckedChangeListener { _, b -> config.forwardOtp = b }
        binding.cbForwardTransaction.setOnCheckedChangeListener { _, b -> config.forwardTransaction = b }
        binding.cbForwardBill.setOnCheckedChangeListener { _, b -> config.forwardBill = b }
        binding.cbForwardSecurity.setOnCheckedChangeListener { _, b -> config.forwardSecurity = b }
        binding.switchOfficeHours.setOnCheckedChangeListener { _, b -> config.officeHoursEnabled = b }
    }

    private fun handleLogout() {
        authManager.logout()
        Toast.makeText(this, "Logged out and data cleared", Toast.LENGTH_SHORT).show()
        
        // Restart app to splash
        val intent = Intent(this, SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onPause() {
        super.onPause()
        // Save backend URL if changed
        val url = binding.etBackendUrl.text.toString().trim()
        if (url.startsWith("https://")) {
            config.backendUrl = url
        }
    }
}
