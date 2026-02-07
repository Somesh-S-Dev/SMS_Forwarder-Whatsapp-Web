package com.secure.otpforwarder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.secure.otpforwarder.databinding.ActivityDashboardBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main dashboard for the app. Shown after PIN entry.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var authManager: AuthManager
    private lateinit var config: ConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        config = ConfigManager(this)

        setupUI()
        updateDashboard()
        checkPermissions()
    }

    private fun setupUI() {
        binding.tvUserName.text = "Hi, ${authManager.getUserName()}"
        binding.tvUserPhone.text = maskPhoneNumber(authManager.getWhatsappNumber())

        binding.tvClassifierStatus.text = "• Smart Classification: Active"
        binding.tvClassifierStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green))
        
        binding.switchService.isChecked = authManager.isServiceEnabled()
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            authManager.setServiceEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Service Enabled" else "Service Disabled", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnTestSms.setOnClickListener {
            Toast.makeText(this, "Simulating test SMS...", Toast.LENGTH_SHORT).show()
            // In a real app, send a sample message to the backend
        }
    }

    private fun updateDashboard() {
        val lastForwarded = config.lastForwardedTimestamp
        if (lastForwarded > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
            binding.tvLastForwarded.text = "Last forwarded: ${dateFormat.format(Date(lastForwarded))}"
        } else {
            binding.tvLastForwarded.text = "No messages forwarded yet"
        }

        // Example stats - in a real app, track these in ConfigManager or a database
        binding.tvTotalMessages.text = "0"
        binding.tvSuccessRate.text = "0%"
    }

    private fun maskPhoneNumber(phone: String): String {
        if (phone.length < 4) return phone
        return phone.substring(0, 3) + " ****" + phone.substring(phone.length - 4)
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS), 101)
        }
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
    }
}
