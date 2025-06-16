package com.dayforcetracker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        const val PREFS_NAME = "DayforceTrackerPrefs"
        const val PREF_BASE_PAY_RATE = "base_pay_rate"
        const val PREF_PAY_PERIOD_TYPE = "pay_period_type"
        const val DEFAULT_PAY_RATE = 20.0
        const val PAY_PERIOD_WEEKLY = "weekly"
        const val PAY_PERIOD_BIWEEKLY = "biweekly"
    }
    
    private lateinit var payRateEditText: EditText
    private lateinit var weeklyRadio: RadioButton
    private lateinit var biweeklyRadio: RadioButton
    private lateinit var payPeriodGroup: RadioGroup
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var prefs: SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Initialize SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Initialize views
        initializeViews()
        
        // Load current settings
        loadCurrentSettings()
        
        // Set up button listeners
        setupButtonListeners()
        
        // Set up validation
        setupValidation()
    }
    
    private fun initializeViews() {
        payRateEditText = findViewById(R.id.payRateEditText)
        weeklyRadio = findViewById(R.id.weeklyRadio)
        biweeklyRadio = findViewById(R.id.biweeklyRadio)
        payPeriodGroup = findViewById(R.id.payPeriodGroup)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        
        // Set title
        supportActionBar?.title = "Pay Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun loadCurrentSettings() {
        // Load pay rate
        val currentPayRate = prefs.getFloat(PREF_BASE_PAY_RATE, DEFAULT_PAY_RATE.toFloat()).toDouble()
        payRateEditText.setText(String.format("%.2f", currentPayRate))
        
        // Load pay period type
        val payPeriodType = prefs.getString(PREF_PAY_PERIOD_TYPE, PAY_PERIOD_WEEKLY)
        when (payPeriodType) {
            PAY_PERIOD_WEEKLY -> weeklyRadio.isChecked = true
            PAY_PERIOD_BIWEEKLY -> biweeklyRadio.isChecked = true
        }
    }
    
    private fun setupButtonListeners() {
        saveButton.setOnClickListener {
            if (validateAndSaveSettings()) {
                setResult(RESULT_OK)
                finish()
            }
        }
        
        cancelButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }
    
    private fun setupValidation() {
        payRateEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                validatePayRate()
            }
        }
    }
    
    private fun validatePayRate(): Boolean {
        val payRateText = payRateEditText.text.toString().trim()
        
        if (payRateText.isEmpty()) {
            showError("Pay rate cannot be empty")
            return false
        }
        
        try {
            val payRate = payRateText.toDouble()
            if (payRate <= 0) {
                showError("Pay rate must be greater than $0.00")
                return false
            }
            if (payRate > 1000) {
                showError("Pay rate seems unreasonably high. Please verify.")
                return false
            }
            return true
        } catch (e: NumberFormatException) {
            showError("Please enter a valid dollar amount")
            return false
        }
    }
    
    private fun validateAndSaveSettings(): Boolean {
        if (!validatePayRate()) {
            return false
        }
        
        try {
            val payRate = payRateEditText.text.toString().toDouble()
            val payPeriodType = when {
                weeklyRadio.isChecked -> PAY_PERIOD_WEEKLY
                biweeklyRadio.isChecked -> PAY_PERIOD_BIWEEKLY
                else -> PAY_PERIOD_WEEKLY
            }
            
            // Save to SharedPreferences
            prefs.edit().apply {
                putFloat(PREF_BASE_PAY_RATE, payRate.toFloat())
                putString(PREF_PAY_PERIOD_TYPE, payPeriodType)
                apply()
            }
            
            Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
            return true
            
        } catch (e: Exception) {
            showError("Error saving settings: ${e.message}")
            return false
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        setResult(RESULT_CANCELED)
        finish()
        return true
    }
    
    // Static methods for accessing settings from other activities
    object Settings {
        fun getBasePayRate(context: Context): Double {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(PREF_BASE_PAY_RATE, DEFAULT_PAY_RATE.toFloat()).toDouble()
        }
        
        fun getPayPeriodType(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_PAY_PERIOD_TYPE, PAY_PERIOD_WEEKLY) ?: PAY_PERIOD_WEEKLY
        }
        
        fun isSettingsConfigured(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.contains(PREF_BASE_PAY_RATE)
        }
        
        fun formatPayRate(payRate: Double): String {
            return "$${String.format("%.2f", payRate)}/hr"
        }
        
        fun getOvertimeRate(baseRate: Double): Double {
            return baseRate * PayCalculator.OVERTIME_MULTIPLIER
        }
        
        fun getSundayPremiumRate(baseRate: Double): Double {
            return baseRate * PayCalculator.SUNDAY_PREMIUM_MULTIPLIER
        }
    }
}