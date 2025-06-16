package com.dayforcetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.concurrent.Executor

class PinSetupActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "DayforceSecurityPrefs"
        private const val PREF_PIN_HASH = "pin_hash"
        private const val PREF_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val PREF_SECURITY_SETUP_COMPLETE = "security_setup_complete"
    }

    private lateinit var setupInstructionText: TextView
    private lateinit var biometricSetupCard: CardView
    private lateinit var biometricButton: ImageButton
    private lateinit var skipBiometricButton: Button
    private lateinit var enableBiometricButton: Button
    private lateinit var enableBiometricCheckbox: CheckBox

    // PIN dots
    private lateinit var pinDots: List<TextView>
    
    // PIN keypad buttons
    private lateinit var pinButtons: List<Button>
    private lateinit var pinClearButton: Button

    private var currentPin = ""
    private var confirmationPin = ""
    private var isConfirmationMode = false
    private var isChangingPin = false

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var executor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_setup)

        // Check if this is a PIN change operation
        isChangingPin = intent.getBooleanExtra("isChangingPin", false)
        
        initializeViews()
        setupPinKeypad()
        setupBiometricComponents()
        updateUI()
        
        // Update UI for PIN change mode
        if (isChangingPin) {
            setupInstructionText.text = "Enter your new 4-digit PIN"
        }
    }

    private fun initializeViews() {
        android.util.Log.d("PinSetup", "initializeViews called")
        
        setupInstructionText = findViewById(R.id.setupInstructionText)
        biometricSetupCard = findViewById(R.id.biometricSetupCard)
        biometricButton = findViewById<ImageButton>(R.id.biometricButton)
        skipBiometricButton = findViewById(R.id.skipBiometricButton)
        enableBiometricButton = findViewById(R.id.enableBiometricButton)
        enableBiometricCheckbox = findViewById(R.id.enableBiometricCheckbox)

        android.util.Log.d("PinSetup", "Basic views initialized")

        // PIN dots (now TextViews)
        pinDots = listOf(
            findViewById<TextView>(R.id.pinDot1),
            findViewById<TextView>(R.id.pinDot2),
            findViewById<TextView>(R.id.pinDot3),
            findViewById<TextView>(R.id.pinDot4)
        )
        android.util.Log.d("PinSetup", "PIN dots initialized, count: ${pinDots.size}")

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
        android.util.Log.d("PinSetup", "PIN buttons initialized, count: ${pinButtons.size}")
        
        pinClearButton = findViewById(R.id.pinClear)
        android.util.Log.d("PinSetup", "Clear button initialized")
    }

    private fun setupPinKeypad() {
        android.util.Log.d("PinSetup", "setupPinKeypad called, pinButtons size: ${pinButtons.size}")
        
        // Number buttons
        pinButtons.forEachIndexed { index, button ->
            val digit = if (index == 0) "0" else index.toString()
            android.util.Log.d("PinSetup", "Setting up button $index for digit $digit")
            button.setOnClickListener {
                android.util.Log.d("PinSetup", "Button clicked for digit: $digit")
                addPinDigit(digit)
            }
        }

        // Clear button
        pinClearButton.setOnClickListener {
            clearLastDigit()
        }

        // Biometric setup buttons
        skipBiometricButton.setOnClickListener {
            completePinSetup(false)
        }

        enableBiometricButton.setOnClickListener {
            setupBiometricAuthentication()
        }
        
        // Setup checkbox behavior
        setupBiometricCheckbox()
    }

    private fun setupBiometricCheckbox() {
        android.util.Log.d("PinSetup", "setupBiometricCheckbox called")
        
        // Check if biometric authentication is available
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        android.util.Log.d("PinSetup", "Biometric availability: $canAuthenticate")
        
        when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                android.util.Log.d("PinSetup", "Biometric available - showing checkbox")
                enableBiometricCheckbox.visibility = View.VISIBLE
                enableBiometricCheckbox.isEnabled = true
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                android.util.Log.d("PinSetup", "No biometric hardware - hiding checkbox")
                enableBiometricCheckbox.visibility = View.GONE
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                android.util.Log.d("PinSetup", "No biometrics enrolled - disabling checkbox")
                enableBiometricCheckbox.visibility = View.VISIBLE
                enableBiometricCheckbox.isEnabled = false
                enableBiometricCheckbox.text = "Set up biometrics in Settings first"
            }
            else -> {
                android.util.Log.d("PinSetup", "Biometric not available: $canAuthenticate - hiding checkbox")
                enableBiometricCheckbox.visibility = View.GONE
            }
        }
    }

    private fun setupBiometricComponents() {
        executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(this@PinSetupActivity, "Biometric setup failed: $errString", Toast.LENGTH_SHORT).show()
                // Continue with PIN-only setup
                completePinSetup(false)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Toast.makeText(this@PinSetupActivity, "Biometric authentication enabled!", Toast.LENGTH_SHORT).show()
                completePinSetup(true)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(this@PinSetupActivity, "Biometric authentication failed", Toast.LENGTH_SHORT).show()
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Enable Biometric Authentication")
            .setSubtitle("Confirm your biometric to enable fast login")
            .setNegativeButtonText("Cancel")
            .build()
    }

    private fun addPinDigit(digit: String) {
        android.util.Log.d("PinSetup", "addPinDigit called with digit: $digit")
        val currentInput = if (isConfirmationMode) confirmationPin else currentPin
        android.util.Log.d("PinSetup", "Current input length: ${currentInput.length}, isConfirmationMode: $isConfirmationMode")
        
        if (currentInput.length < 4) {
            if (isConfirmationMode) {
                confirmationPin += digit
                android.util.Log.d("PinSetup", "Added to confirmation PIN, new length: ${confirmationPin.length}")
            } else {
                currentPin += digit
                android.util.Log.d("PinSetup", "Added to current PIN, new length: ${currentPin.length}")
            }
            
            updatePinDots()
            
            // Check if PIN is complete
            val pin = if (isConfirmationMode) confirmationPin else currentPin
            if (pin.length == 4) {
                android.util.Log.d("PinSetup", "PIN complete, calling handlePinComplete")
                handlePinComplete()
            }
        } else {
            android.util.Log.d("PinSetup", "PIN already complete, ignoring digit")
        }
    }

    private fun clearLastDigit() {
        if (isConfirmationMode) {
            if (confirmationPin.isNotEmpty()) {
                confirmationPin = confirmationPin.dropLast(1)
            }
        } else {
            if (currentPin.isNotEmpty()) {
                currentPin = currentPin.dropLast(1)
            }
        }
        updatePinDots()
    }

    private fun updatePinDots() {
        val currentInput = if (isConfirmationMode) confirmationPin else currentPin
        android.util.Log.d("PinSetup", "updatePinDots called, currentInput length: ${currentInput.length}")
        
        pinDots.forEachIndexed { index, dot ->
            android.util.Log.d("PinSetup", "Updating dot $index, filled: ${index < currentInput.length}")
            if (index < currentInput.length) {
                // Filled dot - solid black circle
                dot.text = "●"
                dot.setTextColor(android.graphics.Color.parseColor("#1976D2")) // Blue color
                android.util.Log.d("PinSetup", "Set dot $index to filled (●)")
            } else {
                // Empty dot - hollow circle
                dot.text = "○"
                dot.setTextColor(android.graphics.Color.parseColor("#9CA3AF")) // Gray color
                android.util.Log.d("PinSetup", "Set dot $index to empty (○)")
            }
        }
    }

    private fun handlePinComplete() {
        android.util.Log.d("PinSetup", "handlePinComplete called, isConfirmationMode: $isConfirmationMode")
        
        if (!isConfirmationMode) {
            // First PIN entry complete, ask for confirmation
            android.util.Log.d("PinSetup", "First PIN entered: ${currentPin.length} digits")
            isConfirmationMode = true
            confirmationPin = ""
            setupInstructionText.text = "Confirm your 4-digit PIN"
            updatePinDots()
        } else {
            // Confirmation PIN complete, validate
            android.util.Log.d("PinSetup", "Confirmation PIN entered: ${confirmationPin.length} digits")
            if (currentPin == confirmationPin) {
                android.util.Log.d("PinSetup", "PINs match, completing setup")
                // PINs match, complete setup with checkbox state
                val enableBiometric = enableBiometricCheckbox.isChecked && enableBiometricCheckbox.isEnabled
                android.util.Log.d("PinSetup", "Biometric checkbox checked: ${enableBiometricCheckbox.isChecked}, enabled: ${enableBiometricCheckbox.isEnabled}")
                completePinSetup(enableBiometric)
            } else {
                android.util.Log.d("PinSetup", "PINs don't match, resetting")
                // PINs don't match, reset
                Toast.makeText(this, "PINs don't match. Please try again.", Toast.LENGTH_SHORT).show()
                resetPinSetup()
            }
        }
    }

    private fun resetPinSetup() {
        currentPin = ""
        confirmationPin = ""
        isConfirmationMode = false
        setupInstructionText.text = "Create a 4-digit PIN to secure your app"
        updatePinDots()
    }

    private fun showBiometricSetup() {
        android.util.Log.d("PinSetup", "showBiometricSetup called")
        
        // Check if biometric authentication is available
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        android.util.Log.d("PinSetup", "Biometric status: $canAuthenticate")
        
        when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                android.util.Log.d("PinSetup", "Biometric available, showing setup card")
                // Show biometric setup option
                biometricSetupCard.visibility = View.VISIBLE
                setupInstructionText.text = "PIN created successfully!"
                
                // Auto-skip biometric setup after 3 seconds for testing
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (biometricSetupCard.visibility == View.VISIBLE) {
                        android.util.Log.d("PinSetup", "Auto-skipping biometric setup")
                        Toast.makeText(this, "Continuing with PIN only", Toast.LENGTH_SHORT).show()
                        completePinSetup(false)
                    }
                }, 3000) // 3 seconds timeout for testing
            }
            else -> {
                android.util.Log.d("PinSetup", "Biometric not available: $canAuthenticate")
                Toast.makeText(this, "Biometric authentication not available", Toast.LENGTH_SHORT).show()
                completePinSetup(false)
            }
        }
    }

    private fun setupBiometricAuthentication() {
        biometricPrompt.authenticate(promptInfo)
    }

    private fun completePinSetup(biometricEnabled: Boolean) {
        try {
            android.util.Log.d("PinSetup", "completePinSetup called with biometric: $biometricEnabled")
            
            // Use regular SharedPreferences for now (can be enhanced with encryption later)
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Hash the PIN for storage
            val pinHash = hashPin(currentPin)
            android.util.Log.d("PinSetup", "PIN hash created successfully")

            // Save settings
            prefs.edit().apply {
                putString(PREF_PIN_HASH, pinHash)
                putBoolean(PREF_BIOMETRIC_ENABLED, biometricEnabled)
                putBoolean(PREF_SECURITY_SETUP_COMPLETE, true)
                apply()
            }
            android.util.Log.d("PinSetup", "Settings saved successfully")

            val message = if (isChangingPin) "PIN changed successfully!" else "Security setup complete!"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            // Hide biometric card if visible
            biometricSetupCard.visibility = View.GONE

            // Navigate appropriately based on context
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isChangingPin) {
                    android.util.Log.d("PinSetup", "PIN change complete, returning to settings")
                    finish() // Return to settings
                } else {
                    android.util.Log.d("PinSetup", "Starting MainActivity")
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }, 1000) // 1 second delay

        } catch (e: Exception) {
            android.util.Log.e("PinSetup", "Error in completePinSetup", e)
            Toast.makeText(this, "Error setting up security: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hashPin(pin: String): String {
        val bytes = pin.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun updateUI() {
        updatePinDots()
    }

    // Static helper methods
    object SecurityManager {
        fun isSecuritySetupComplete(context: Context): Boolean {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getBoolean(PREF_SECURITY_SETUP_COMPLETE, false)
            } catch (e: Exception) {
                false
            }
        }

        fun isBiometricEnabled(context: Context): Boolean {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getBoolean(PREF_BIOMETRIC_ENABLED, false)
            } catch (e: Exception) {
                false
            }
        }

        fun validatePin(context: Context, inputPin: String): Boolean {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val storedHash = prefs.getString(PREF_PIN_HASH, null)
                val inputHash = hashPin(inputPin)
                
                storedHash == inputHash
            } catch (e: Exception) {
                false
            }
        }

        private fun hashPin(pin: String): String {
            val bytes = pin.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            return digest.fold("") { str, it -> str + "%02x".format(it) }
        }
    }
}