package com.secure.otpforwarder

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * Entry point activity that routes the user based on authentication state.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        authManager = AuthManager(this)

        // Delay for splash screen feel
        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthState()
        }, 1500)
    }

    private fun checkAuthState() {
        val nextActivity = when (authManager.getAuthState()) {
            AuthManager.AuthState.NOT_REGISTERED -> WelcomeActivity::class.java
            AuthManager.AuthState.REGISTERED_NO_PIN -> PinSetupActivity::class.java
            AuthManager.AuthState.REGISTERED_WITH_PIN,
            AuthManager.AuthState.SERVICE_DISABLED -> PinEntryActivity::class.java
        }

        startActivity(Intent(this, nextActivity))
        finish()
    }
}
