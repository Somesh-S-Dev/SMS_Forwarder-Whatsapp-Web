package com.secure.otpforwarder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.secure.otpforwarder.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main activity for OTP Forwarder configuration.
 * 
 * Features:
 * - Permission management
 * - Configuration UI
 * - Manual override toggle
 * - Status display
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val SMS_PERMISSION_REQUEST = 100
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var config: ConfigManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        config = ConfigManager(this)
        
        setupUI()
        checkPermissions()
        loadConfiguration()
    }
    
    private fun setupUI() {
        // Save configuration button
        binding.btnSaveConfig.setOnClickListener {
            saveConfiguration()
        }
        
        // Manual override toggle
        binding.switchManualOverride.setOnCheckedChangeListener { _, isChecked ->
            config.manualOverrideEnabled = isChecked
            updateStatus()
        }
        
        // Request permissions button
        binding.btnRequestPermissions.setOnClickListener {
            requestSmsPermission()
        }
    }
    
    private fun loadConfiguration() {
        // Load existing configuration
        binding.etBackendUrl.setText(config.backendUrl)
        binding.etAesKey.setText(config.aesKey)
        binding.etHmacKey.setText(config.hmacKey)
        binding.etAllowedSenders.setText(config.allowedSenders.joinToString(", "))
        binding.switchOfficeHours.isChecked = config.officeHoursEnabled
        binding.etStartHour.setText(config.officeStartHour.toString())
        binding.etEndHour.setText(config.officeEndHour.toString())
        binding.switchManualOverride.isChecked = config.manualOverrideEnabled
        
        updateStatus()
    }
    
    private fun saveConfiguration() {
        // Validate inputs
        val backendUrl = binding.etBackendUrl.text.toString().trim()
        val aesKey = binding.etAesKey.text.toString().trim()
        val hmacKey = binding.etHmacKey.text.toString().trim()
        
        if (backendUrl.isEmpty() || aesKey.isEmpty() || hmacKey.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!backendUrl.startsWith("https://")) {
            Toast.makeText(this, "Backend URL must use HTTPS", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (aesKey.length != 64 || hmacKey.length != 64) {
            Toast.makeText(this, "Keys must be 64 hex characters", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save configuration
        config.backendUrl = backendUrl
        config.aesKey = aesKey
        config.hmacKey = hmacKey
        
        val sendersText = binding.etAllowedSenders.text.toString()
        config.allowedSenders = if (sendersText.isNotEmpty()) {
            sendersText.split(",").map { it.trim() }.toSet()
        } else {
            emptySet()
        }
        
        config.officeHoursEnabled = binding.switchOfficeHours.isChecked
        config.officeStartHour = binding.etStartHour.text.toString().toIntOrNull() ?: 9
        config.officeEndHour = binding.etEndHour.text.toString().toIntOrNull() ?: 18
        
        Toast.makeText(this, "✅ Configuration saved", Toast.LENGTH_SHORT).show()
        updateStatus()
    }
    
    private fun updateStatus() {
        // Update status display
        val isConfigured = config.isConfigured()
        val hasPermission = checkSmsPermission()
        
        binding.tvConfigStatus.text = if (isConfigured) {
            "✅ Configured"
        } else {
            "❌ Not configured"
        }
        
        binding.tvPermissionStatus.text = if (hasPermission) {
            "✅ SMS permission granted"
        } else {
            "❌ SMS permission required"
        }
        
        binding.tvOfficeHoursStatus.text = if (config.isWithinOfficeHours()) {
            "✅ Within office hours"
        } else {
            "⏰ Outside office hours"
        }
        
        if (config.manualOverrideEnabled) {
            binding.tvOfficeHoursStatus.text = "🔓 Manual override enabled"
        }
        
        // Last forwarded timestamp
        val lastForwarded = config.lastForwardedTimestamp
        if (lastForwarded > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
            binding.tvLastForwarded.text = "Last forwarded: ${dateFormat.format(Date(lastForwarded))}"
        } else {
            binding.tvLastForwarded.text = "No OTPs forwarded yet"
        }
    }
    
    private fun checkPermissions() {
        if (!checkSmsPermission()) {
            binding.btnRequestPermissions.isEnabled = true
        } else {
            binding.btnRequestPermissions.isEnabled = false
        }
    }
    
    private fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECEIVE_SMS),
            SMS_PERMISSION_REQUEST
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == SMS_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ SMS permission granted", Toast.LENGTH_SHORT).show()
                checkPermissions()
                updateStatus()
            } else {
                Toast.makeText(this, "❌ SMS permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
