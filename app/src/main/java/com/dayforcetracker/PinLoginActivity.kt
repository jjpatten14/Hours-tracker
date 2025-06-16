package com.dayforcetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

class PinLoginActivity : AppCompatActivity() {

    private lateinit var loginInstructionText: TextView
    private lateinit var errorMessage: TextView
    private lateinit var biometricButton: ImageButton
    private lateinit var enableBiometricCheckbox: CheckBox

    // PIN dots
    private lateinit var pinDots: List<TextView>
    
    // PIN keypad buttons
    private lateinit var pinButtons: List<Button>
    private lateinit var pinClearButton: Button

    private var currentPin = ""
    private var failedAttempts = 0
    private var isLocked = false

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var executor: Executor

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30000L // 30 seconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_login)

        initializeViews()
        setupPinKeypad()
        setupBiometricComponents()
        setupBiometricCheckbox()
        checkBiometricAvailability()
        
        // Auto-trigger biometric prompt if enabled
        if (PinSetupActivity.SecurityManager.isBiometricEnabled(this)) {
            showBiometricPrompt()
        }
    }

    private fun initializeViews() {
        loginInstructionText = findViewById(R.id.loginInstructionText)
        errorMessage = findViewById(R.id.errorMessage)
        biometricButton = findViewById<ImageButton>(R.id.biometricButton)
        enableBiometricCheckbox = findViewById(R.id.enableBiometricCheckbox)

        // PIN dots
        pinDots = listOf(
            findViewById<TextView>(R.id.pinDot1),
            findViewById<TextView>(R.id.pinDot2),
            findViewById<TextView>(R.id.pinDot3),
            findViewById<TextView>(R.id.pinDot4)
        )

        // PIN keypad buttons
        pinButtons = listOf(
            findViewById(R.id.pin0),
            findViewById(R.id.pin1),
            findViewById(R.id.pin2),
            findViewById(R.id.pin3),
            findViewById(R.id.pin4),
            findViewById(R.id.pin5),
            findViewById(R.id.pin6),
            findViewById(R.id.pin7),
            findViewById(R.id.pin8),
            findViewById(R.id.pin9)
        )
        
        pinClearButton = findViewById(R.id.pinClear)
    }

    private fun setupPinKeypad() {
        // Number buttons
        pinButtons.forEachIndexed { index, button ->
            val digit = if (index == 0) "0" else index.toString()
            button.setOnClickListener {
                if (!isLocked) {
                    addPinDigit(digit)
                }
            }
        }

        // Clear button
        pinClearButton.setOnClickListener {
            if (!isLocked) {
                clearLastDigit()
            }
        }

        // Biometric button
        biometricButton.setOnClickListener {
            showBiometricPrompt()
        }
        
        // Checkbox change listener
        enableBiometricCheckbox.setOnCheckedChangeListener { _, isChecked ->
            handleBiometricCheckboxChange(isChecked)
        }
    }

    private fun setupBiometricComponents() {
        executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    showError("Biometric authentication failed: $errString")
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                authenticateSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                showError("Biometric authentication failed")
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle("Use your biometric to access the app")
            .setNegativeButtonText("Use PIN")
            .build()
    }

    private fun setupBiometricCheckbox() {
        android.util.Log.d("PinLogin", "setupBiometricCheckbox called")
        
        // Check if biometric authentication is available
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        android.util.Log.d("PinLogin", "Biometric availability: $canAuthenticate")
        
        // Set checkbox initial state
        val isBiometricEnabled = PinSetupActivity.SecurityManager.isBiometricEnabled(this)
        enableBiometricCheckbox.isChecked = isBiometricEnabled
        
        when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                android.util.Log.d("PinLogin", "Biometric available - showing checkbox")
                enableBiometricCheckbox.visibility = View.VISIBLE
                enableBiometricCheckbox.isEnabled = true
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                android.util.Log.d("PinLogin", "No biometric hardware - hiding checkbox")
                enableBiometricCheckbox.visibility = View.GONE
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                android.util.Log.d("PinLogin", "No biometrics enrolled - disabling checkbox")
                enableBiometricCheckbox.visibility = View.VISIBLE
                enableBiometricCheckbox.isEnabled = false
                enableBiometricCheckbox.text = "Set up biometrics in Settings first"
            }
            else -> {
                android.util.Log.d("PinLogin", "Biometric not available: $canAuthenticate - hiding checkbox")
                enableBiometricCheckbox.visibility = View.GONE
            }
        }
    }

    private fun handleBiometricCheckboxChange(isChecked: Boolean) {
        android.util.Log.d("PinLogin", "Biometric checkbox changed to: $isChecked")
        
        // Save the biometric preference
        val prefs = getSharedPreferences("DayforceSecurityPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("biometric_enabled", isChecked).apply()
        
        if (isChecked) {
            android.util.Log.d("PinLogin", "Enabling biometrics - showing prompt")
            showBiometricPrompt()
        } else {
            android.util.Log.d("PinLogin", "Disabling biometrics")
        }
    }

    private fun checkBiometricAvailability() {
        android.util.Log.d("PinLogin", "checkBiometricAvailability called")
        val isBiometricEnabled = PinSetupActivity.SecurityManager.isBiometricEnabled(this)
        android.util.Log.d("PinLogin", "isBiometricEnabled: $isBiometricEnabled")
        
        if (isBiometricEnabled) {
            val biometricManager = BiometricManager.from(this)
            val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            android.util.Log.d("PinLogin", "canAuthenticate: $canAuthenticate")
            
            when (canAuthenticate) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    android.util.Log.d("PinLogin", "Biometric available - showing button")
                    biometricButton.visibility = View.VISIBLE
                }
                else -> {
                    android.util.Log.d("PinLogin", "Biometric not available - hiding button")
                    biometricButton.visibility = View.GONE
                }
            }
        } else {
            android.util.Log.d("PinLogin", "Biometric not enabled in settings - hiding button")
            biometricButton.visibility = View.GONE
        }
    }

    private fun showBiometricPrompt() {
        if (PinSetupActivity.SecurityManager.isBiometricEnabled(this)) {
            val biometricManager = BiometricManager.from(this)
            when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    biometricPrompt.authenticate(promptInfo)
                }
                else -> {
                    showError("Biometric authentication not available")
                }
            }
        }
    }

    private fun addPinDigit(digit: String) {
        if (currentPin.length < 4) {
            currentPin += digit
            updatePinDots()
            
            if (currentPin.length == 4) {
                validatePin()
            }
        }
    }

    private fun clearLastDigit() {
        if (currentPin.isNotEmpty()) {
            currentPin = currentPin.dropLast(1)
            updatePinDots()
            hideError()
        }
    }

    private fun updatePinDots() {
        pinDots.forEachIndexed { index, dot ->
            if (index < currentPin.length) {
                // Filled dot - solid black circle
                dot.text = "●"
                dot.setTextColor(android.graphics.Color.parseColor("#1976D2")) // Blue color
            } else {
                // Empty dot - hollow circle
                dot.text = "○"
                dot.setTextColor(android.graphics.Color.parseColor("#9CA3AF")) // Gray color
            }
        }
    }

    private fun validatePin() {
        if (PinSetupActivity.SecurityManager.validatePin(this, currentPin)) {
            authenticateSuccess()
        } else {
            handleFailedAttempt()
        }
    }

    private fun handleFailedAttempt() {
        failedAttempts++
        currentPin = ""
        updatePinDots()
        
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockApp()
        } else {
            val remainingAttempts = MAX_FAILED_ATTEMPTS - failedAttempts
            showError("Incorrect PIN. $remainingAttempts attempts remaining.")
        }
    }

    private fun lockApp() {
        isLocked = true
        showError("Too many failed attempts. App locked for 30 seconds.")
        
        // Disable all buttons
        pinButtons.forEach { it.isEnabled = false }
        pinClearButton.isEnabled = false
        biometricButton.isEnabled = false
        
        // Unlock after timeout
        Handler(Looper.getMainLooper()).postDelayed({
            unlockApp()
        }, LOCKOUT_DURATION_MS)
    }

    private fun unlockApp() {
        isLocked = false
        failedAttempts = 0
        currentPin = ""
        updatePinDots()
        hideError()
        
        // Re-enable all buttons
        pinButtons.forEach { it.isEnabled = true }
        pinClearButton.isEnabled = true
        biometricButton.isEnabled = true
        
        loginInstructionText.text = "Enter your PIN to continue"
    }

    private fun authenticateSuccess() {
        Toast.makeText(this, "Authentication successful!", Toast.LENGTH_SHORT).show()
        
        // Navigate to main app
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showError(message: String) {
        errorMessage.text = message
        errorMessage.visibility = View.VISIBLE
        
        // Clear error after 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            hideError()
        }, 3000)
    }

    private fun hideError() {
        errorMessage.visibility = View.GONE
        errorMessage.text = ""
    }

    override fun onBackPressed() {
        // Prevent going back - user must authenticate
        // Optionally close the app completely
        finishAffinity()
    }
}