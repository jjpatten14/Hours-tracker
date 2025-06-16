package com.dayforcetracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check security setup status and route accordingly
        routeUser()
    }

    private fun routeUser() {
        when {
            !PinSetupActivity.SecurityManager.isSecuritySetupComplete(this) -> {
                // First time setup - go to PIN setup
                startActivity(Intent(this, PinSetupActivity::class.java))
            }
            else -> {
                // Security is set up - go to login
                startActivity(Intent(this, PinLoginActivity::class.java))
            }
        }
        
        // Close launcher activity
        finish()
    }
}