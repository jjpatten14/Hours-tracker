package com.dayforcetracker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.widget.ImageView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        const val PREFS_NAME = "DayforceTrackerPrefs"
        const val PREF_BASE_PAY_RATE = "base_pay_rate"
        const val PREF_PAY_PERIOD_TYPE = "pay_period_type"
        const val DEFAULT_PAY_RATE = 20.0
        const val PAY_PERIOD_WEEKLY = "weekly"
        const val PAY_PERIOD_BIWEEKLY = "biweekly"
        
        // Goal preferences
        const val PREF_MONDAY_GOAL = "monday_goal_hours"
        const val PREF_TUESDAY_GOAL = "tuesday_goal_hours"
        const val PREF_WEDNESDAY_GOAL = "wednesday_goal_hours"
        const val PREF_THURSDAY_GOAL = "thursday_goal_hours"
        const val PREF_FRIDAY_GOAL = "friday_goal_hours"
        const val PREF_SATURDAY_GOAL = "saturday_goal_hours"
        const val PREF_SUNDAY_GOAL = "sunday_goal_hours"
        const val DEFAULT_WEEKDAY_GOAL = 8.0
        const val DEFAULT_WEEKEND_GOAL = 7.0
        
        // Dayforce credentials
        const val PREF_DAYFORCE_USERNAME = "dayforce_username"
        const val PREF_DAYFORCE_PASSWORD = "dayforce_password"
    }
    
    private lateinit var payRateEditText: EditText
    private lateinit var weeklyRadio: RadioButton
    private lateinit var biweeklyRadio: RadioButton
    private lateinit var payPeriodGroup: RadioGroup
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var prefs: SharedPreferences
    
    // Goal input fields
    private lateinit var mondayGoalInput: EditText
    private lateinit var tuesdayGoalInput: EditText
    private lateinit var wednesdayGoalInput: EditText
    private lateinit var thursdayGoalInput: EditText
    private lateinit var fridayGoalInput: EditText
    private lateinit var saturdayGoalInput: EditText
    private lateinit var sundayGoalInput: EditText
    
    // Rate preview fields
    private lateinit var regularRatePreview: TextView
    private lateinit var overtimeRatePreview: TextView
    private lateinit var sundayRatePreview: TextView
    
    // Pay rate visibility toggle
    private lateinit var togglePayRateButton: Button
    private lateinit var payRateContent: LinearLayout
    private var isPayRateVisible = false
    
    // Projected weekly pay
    private lateinit var projectedWeeklyPayText: TextView
    private lateinit var projectedHoursText: TextView
    
    // Security settings
    private lateinit var changePinButton: LinearLayout
    private lateinit var biometricToggleSwitch: Switch
    
    // Dayforce credentials
    private lateinit var dayforceUsernameInput: TextInputEditText
    private lateinit var dayforcePasswordInput: TextInputEditText
    private lateinit var testDayforceLoginButton: Button
    
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
        
        // Set up rate preview updates
        setupRatePreview()
        
        // Set up pay rate toggle
        setupPayRateToggle()
        
        // Set up projected weekly pay calculation
        setupProjectedWeeklyPay()
        
        // Set up security settings
        setupSecuritySettings()
        
        // Set up Dayforce credentials
        setupDayforceCredentials()
    }
    
    private fun initializeViews() {
        payRateEditText = findViewById(R.id.payRateEditText)
        weeklyRadio = findViewById(R.id.weeklyRadio)
        biweeklyRadio = findViewById(R.id.biweeklyRadio)
        payPeriodGroup = findViewById(R.id.payPeriodGroup)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        
        // Goal input fields
        try {
            mondayGoalInput = findViewById(R.id.mondayGoalInput)
            tuesdayGoalInput = findViewById(R.id.tuesdayGoalInput)
            wednesdayGoalInput = findViewById(R.id.wednesdayGoalInput)
            thursdayGoalInput = findViewById(R.id.thursdayGoalInput)
            fridayGoalInput = findViewById(R.id.fridayGoalInput)
            saturdayGoalInput = findViewById(R.id.saturdayGoalInput)
            sundayGoalInput = findViewById(R.id.sundayGoalInput)
        } catch (e: Exception) {
            // Goal fields not found in layout yet - will add them
        }
        
        // Rate preview fields
        try {
            regularRatePreview = findViewById(R.id.regularRatePreview)
            overtimeRatePreview = findViewById(R.id.overtimeRatePreview)
            sundayRatePreview = findViewById(R.id.sundayRatePreview)
        } catch (e: Exception) {
            // Rate preview fields not found
        }
        
        // Pay rate visibility toggle
        try {
            togglePayRateButton = findViewById(R.id.togglePayRateButton)
            payRateContent = findViewById(R.id.payRateContent)
        } catch (e: Exception) {
            // Toggle fields not found
        }
        
        // Projected weekly pay
        try {
            projectedWeeklyPayText = findViewById(R.id.projectedWeeklyPayText)
            projectedHoursText = findViewById(R.id.projectedHoursText)
        } catch (e: Exception) {
            // Projected pay fields not found
        }
        
        // Security settings
        try {
            changePinButton = findViewById<LinearLayout>(R.id.changePinButton)
            biometricToggleSwitch = findViewById(R.id.biometricToggleSwitch)
        } catch (e: Exception) {
            // Security fields not found
        }
        
        // Dayforce credentials
        try {
            dayforceUsernameInput = findViewById(R.id.dayforceUsernameInput)
            dayforcePasswordInput = findViewById(R.id.dayforcePasswordInput)
            testDayforceLoginButton = findViewById(R.id.testDayforceLoginButton)
        } catch (e: Exception) {
            // Dayforce fields not found
        }
        
        // Set title (removed ActionBar - now using card-based design)
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
        
        // Load goal settings
        try {
            mondayGoalInput.setText(String.format("%.1f", prefs.getFloat(PREF_MONDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat())))
            tuesdayGoalInput.setText(String.format("%.1f", prefs.getFloat(PREF_TUESDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat())))
            wednesdayGoalInput.setText(String.format("%.1f", prefs.getFloat(PREF_WEDNESDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat())))
            thursdayGoalInput.setText(String.format("%.1f", prefs.getFloat(PREF_THURSDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat())))
            fridayGoalInput.setText(String.format("%.1f", prefs.getFloat(PREF_FRIDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat())))
            saturdayGoalInput.setText(String.format("%.1f", prefs.getFloat(PREF_SATURDAY_GOAL, DEFAULT_WEEKEND_GOAL.toFloat())))
            sundayGoalInput.setText(String.format("%.1f", prefs.getFloat(PREF_SUNDAY_GOAL, DEFAULT_WEEKEND_GOAL.toFloat())))
        } catch (e: Exception) {
            // Goal fields not available yet
        }
        
        // Load Dayforce credentials
        try {
            dayforceUsernameInput.setText(prefs.getString(PREF_DAYFORCE_USERNAME, ""))
            dayforcePasswordInput.setText(prefs.getString(PREF_DAYFORCE_PASSWORD, ""))
        } catch (e: Exception) {
            // Dayforce fields not available yet
        }
        
        // Load biometric setting
        try {
            biometricToggleSwitch.isChecked = PinSetupActivity.SecurityManager.isBiometricEnabled(this)
        } catch (e: Exception) {
            // Biometric toggle not available yet
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
                
                // Save goal settings
                try {
                    putFloat(PREF_MONDAY_GOAL, mondayGoalInput.text.toString().toFloatOrNull() ?: DEFAULT_WEEKDAY_GOAL.toFloat())
                    putFloat(PREF_TUESDAY_GOAL, tuesdayGoalInput.text.toString().toFloatOrNull() ?: DEFAULT_WEEKDAY_GOAL.toFloat())
                    putFloat(PREF_WEDNESDAY_GOAL, wednesdayGoalInput.text.toString().toFloatOrNull() ?: DEFAULT_WEEKDAY_GOAL.toFloat())
                    putFloat(PREF_THURSDAY_GOAL, thursdayGoalInput.text.toString().toFloatOrNull() ?: DEFAULT_WEEKDAY_GOAL.toFloat())
                    putFloat(PREF_FRIDAY_GOAL, fridayGoalInput.text.toString().toFloatOrNull() ?: DEFAULT_WEEKDAY_GOAL.toFloat())
                    putFloat(PREF_SATURDAY_GOAL, saturdayGoalInput.text.toString().toFloatOrNull() ?: DEFAULT_WEEKEND_GOAL.toFloat())
                    putFloat(PREF_SUNDAY_GOAL, sundayGoalInput.text.toString().toFloatOrNull() ?: DEFAULT_WEEKEND_GOAL.toFloat())
                } catch (e: Exception) {
                    // Goal fields not available - use defaults
                }
                
                // Save Dayforce credentials
                try {
                    putString(PREF_DAYFORCE_USERNAME, dayforceUsernameInput.text.toString().trim())
                    putString(PREF_DAYFORCE_PASSWORD, dayforcePasswordInput.text.toString())
                } catch (e: Exception) {
                    // Dayforce fields not available
                }
                
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
    
    private fun setupRatePreview() {
        try {
            payRateEditText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    updateRatePreview()
                }
            })
            
            // Initial update
            updateRatePreview()
        } catch (e: Exception) {
            // Rate preview not available
        }
    }
    
    private fun updateRatePreview() {
        try {
            val rateText = payRateEditText.text.toString().trim()
            val rate = if (rateText.isNotEmpty()) {
                rateText.toDoubleOrNull() ?: 20.0
            } else {
                20.0
            }
            
            val overtimeRate = rate * 1.5
            val sundayRate = rate * 2.0
            
            regularRatePreview.text = "• Regular: $${String.format("%.2f", rate)}/hr"
            overtimeRatePreview.text = "• Overtime (1.5x): $${String.format("%.2f", overtimeRate)}/hr"
            sundayRatePreview.text = "• Sunday Premium (2x): $${String.format("%.2f", sundayRate)}/hr"
        } catch (e: Exception) {
            // Error updating preview
        }
    }
    
    private fun setupPayRateToggle() {
        try {
            togglePayRateButton.setOnClickListener {
                isPayRateVisible = !isPayRateVisible
                if (isPayRateVisible) {
                    payRateContent.visibility = android.view.View.VISIBLE
                    togglePayRateButton.text = "👁️‍🗨️ Hide"
                } else {
                    payRateContent.visibility = android.view.View.GONE
                    togglePayRateButton.text = "👁️ Show"
                }
            }
        } catch (e: Exception) {
            // Toggle not available
        }
    }
    
    private fun setupProjectedWeeklyPay() {
        try {
            // Add text watchers to all goal inputs to update projection
            val goalInputs = listOf(
                mondayGoalInput, tuesdayGoalInput, wednesdayGoalInput,
                thursdayGoalInput, fridayGoalInput, saturdayGoalInput, sundayGoalInput
            )
            
            val textWatcher = object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    updateProjectedWeeklyPay()
                }
            }
            
            goalInputs.forEach { input ->
                try {
                    input.addTextChangedListener(textWatcher)
                } catch (e: Exception) {
                    // Input not found
                }
            }
            
            // Also watch pay rate changes
            payRateEditText.addTextChangedListener(textWatcher)
            
            // Initial calculation
            updateProjectedWeeklyPay()
        } catch (e: Exception) {
            // Projected pay not available
        }
    }
    
    private fun updateProjectedWeeklyPay() {
        try {
            val baseRate = payRateEditText.text.toString().toDoubleOrNull() ?: 20.0
            
            // Get current earnings and hours from MainActivity (week to date)
            val currentEarnings = getCurrentWeekEarnings()
            val currentHours = getCurrentWeekHours()
            
            // Get all daily goal hours
            val mondayGoal = mondayGoalInput.text.toString().toDoubleOrNull() ?: 8.0
            val tuesdayGoal = tuesdayGoalInput.text.toString().toDoubleOrNull() ?: 8.0
            val wednesdayGoal = wednesdayGoalInput.text.toString().toDoubleOrNull() ?: 8.0
            val thursdayGoal = thursdayGoalInput.text.toString().toDoubleOrNull() ?: 8.0
            val fridayGoal = fridayGoalInput.text.toString().toDoubleOrNull() ?: 8.0
            val saturdayGoal = saturdayGoalInput.text.toString().toDoubleOrNull() ?: 7.0
            val sundayGoal = sundayGoalInput.text.toString().toDoubleOrNull() ?: 7.0
            
            // Calculate total goal hours
            val totalGoalHours = mondayGoal + tuesdayGoal + wednesdayGoal + thursdayGoal + fridayGoal + saturdayGoal + sundayGoal
            
            // Calculate REMAINING hours needed to hit goals
            val remainingHours = kotlin.math.max(0.0, totalGoalHours - currentHours)
            
            // Calculate what the total hours would be if we hit all goals
            val projectedTotalHours = currentHours + remainingHours
            
            // Calculate pay for the total projected hours (current + remaining)
            val nonSundayHours = kotlin.math.min(projectedTotalHours, projectedTotalHours - sundayGoal)
            val regularHours = kotlin.math.min(nonSundayHours, 40.0)
            val overtimeHours = kotlin.math.max(0.0, nonSundayHours - 40.0)
            
            val regularPay = regularHours * baseRate
            val overtimePay = overtimeHours * (baseRate * 1.5)
            
            // Calculate Sunday pay - 2x rate ONLY if total projected hours >= 40, otherwise regular rate
            val sundayPremiumPay = if (nonSundayHours >= 40.0) {
                sundayGoal * (baseRate * 2.0)  // 2x premium if already worked 40+
            } else {
                sundayGoal * baseRate  // Regular rate if under 40 hours
            }
            
            val totalProjectedPay = regularPay + overtimePay + sundayPremiumPay
            
            projectedWeeklyPayText.text = "$${String.format("%.2f", totalProjectedPay)}"
            projectedHoursText.text = "${String.format("%.1f", projectedTotalHours)}h projected • ${String.format("%.1f", remainingHours)}h remaining"
        } catch (e: Exception) {
            // Error calculating projection
        }
    }
    
    private fun getCurrentWeekEarnings(): Double {
        // Get current earnings from MainActivity's stored data
        val mainPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedPayString = mainPrefs.getString("stored_total_pay", "0.0")
        return storedPayString?.toDoubleOrNull() ?: 0.0
    }
    
    private fun getCurrentWeekHours(): Double {
        // Get current hours worked from MainActivity's stored data
        val mainPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedHoursString = mainPrefs.getString("stored_total_hours", "0.0")
        return storedHoursString?.toDoubleOrNull() ?: 0.0
    }
    
    private fun setupSecuritySettings() {
        try {
            // Setup change PIN button
            changePinButton.setOnClickListener {
                // Launch PIN change activity
                val intent = Intent(this, PinSetupActivity::class.java)
                intent.putExtra("isChangingPin", true)
                startActivity(intent)
            }
            
            // Setup biometric toggle
            biometricToggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                // Save biometric preference
                val securityPrefs = getSharedPreferences("DayforceSecurityPrefs", Context.MODE_PRIVATE)
                securityPrefs.edit().putBoolean("biometric_enabled", isChecked).apply()
                
                if (isChecked) {
                    Toast.makeText(this, "Biometric login enabled", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Biometric login disabled", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            // Security settings not available
        }
    }
    
    private fun setupDayforceCredentials() {
        try {
            // Setup test login button
            testDayforceLoginButton.setOnClickListener {
                val username = dayforceUsernameInput.text.toString().trim()
                val password = dayforcePasswordInput.text.toString()
                
                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                
                // Actually test the login credentials
                testDayforceLogin(username, password)
            }
        } catch (e: Exception) {
            // Dayforce settings not available
        }
    }

    private fun testDayforceLogin(username: String, password: String) {
        // Show progress
        testDayforceLoginButton.text = "Testing..."
        testDayforceLoginButton.isEnabled = false
        
        // Create a hidden WebView to test login
        val testWebView = android.webkit.WebView(this)
        testWebView.settings.javaScriptEnabled = true
        testWebView.settings.domStorageEnabled = true
        testWebView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Safari/537.36"
        
        var loginAttempted = false
        
        testWebView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                android.util.Log.d("SettingsActivity", "Page finished loading: $url")
                
                when {
                    url?.contains("mydayforce.aspx", ignoreCase = true) == true && !loginAttempted -> {
                        // On login page - try to login
                        loginAttempted = true
                        android.util.Log.d("SettingsActivity", "Attempting login...")
                        
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val loginScript = """
                                (function() {
                                    try {
                                        // Fill company field
                                        var companyField = document.querySelector('input[name*="CompanyName"]');
                                        if (companyField) {
                                            companyField.value = 'tgi';
                                            console.log('Company field filled');
                                        }
                                        
                                        // Fill username
                                        var usernameField = document.querySelector('input[name*="UserName"]');
                                        if (usernameField) {
                                            usernameField.value = '$username';
                                            console.log('Username field filled');
                                        }
                                        
                                        // Fill password
                                        var passwordField = document.querySelector('input[name*="UserPass"]') || 
                                                          document.querySelector('input[type="password"]');
                                        if (passwordField) {
                                            passwordField.value = '$password';
                                            console.log('Password field filled');
                                        }
                                        
                                        // Click login button
                                        var loginBtn = document.querySelector('input[name*="cmdLogin"]') ||
                                                      document.querySelector('input[value="Login"]') ||
                                                      document.querySelector('input[type="submit"]');
                                        if (loginBtn) {
                                            loginBtn.click();
                                            console.log('Login button clicked');
                                            return 'Login attempted';
                                        }
                                        
                                        return 'Login button not found';
                                        
                                    } catch (e) {
                                        console.log('Login script error: ' + e.message);
                                        return 'Error: ' + e.message;
                                    }
                                })();
                            """.trimIndent()
                            
                            testWebView.evaluateJavascript(loginScript) { result ->
                                android.util.Log.d("SettingsActivity", "Login script result: $result")
                                
                                // Wait longer for login result - some systems are slow
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    val currentUrl = testWebView.url ?: ""
                                    android.util.Log.d("SettingsActivity", "Final URL after login: $currentUrl")
                                    handleLoginTestResult(username, password, currentUrl)
                                }, 8000) // Increased to 8 seconds
                            }
                        }, 3000) // Wait 3 seconds for page to load
                    }
                    url?.contains("mydayforce.aspx", ignoreCase = true) == true && loginAttempted -> {
                        // Still on login page after attempt - likely failed
                        android.util.Log.d("SettingsActivity", "Still on login page after attempt - login failed")
                        handleLoginTestResult(username, password, url ?: "")
                    }
                    loginAttempted -> {
                        // Page changed after login attempt - check if it's success
                        android.util.Log.d("SettingsActivity", "Page changed after login to: $url")
                        handleLoginTestResult(username, password, url ?: "")
                    }
                }
            }
            
            override fun onReceivedError(view: android.webkit.WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                showLoginTestResult(false, "Network error: $description")
            }
        }
        
        // Start the test
        testWebView.loadUrl("https://us251.dayforcehcm.com/MyDayforce/Mydayforce.aspx")
    }
    
    private fun handleLoginTestResult(username: String, password: String, finalUrl: String) {
        // More robust success detection
        val isSuccess = when {
            // Definitely failed cases
            finalUrl.contains("mydayforce.aspx", ignoreCase = true) -> false // Still on login page
            finalUrl.contains("error", ignoreCase = true) -> false // Error page
            finalUrl.contains("invalid", ignoreCase = true) -> false // Invalid login
            finalUrl.contains("denied", ignoreCase = true) -> false // Access denied
            
            // Success cases - redirected to main Dayforce areas
            finalUrl.contains("home", ignoreCase = true) -> true // Home page
            finalUrl.contains("dashboard", ignoreCase = true) -> true // Dashboard
            finalUrl.contains("timesheet", ignoreCase = true) -> true // Timesheet page
            finalUrl.contains("employee", ignoreCase = true) -> true // Employee portal
            finalUrl.contains("portal", ignoreCase = true) -> true // Portal page
            
            // If URL changed from login page and contains dayforce, likely success
            finalUrl.contains("dayforce", ignoreCase = true) && 
            !finalUrl.contains("login", ignoreCase = true) && 
            !finalUrl.contains("mydayforce.aspx", ignoreCase = true) -> true
            
            else -> {
                // Log the URL for debugging and assume failure if unsure
                android.util.Log.d("SettingsActivity", "Unknown login result URL: $finalUrl")
                false
            }
        }
        
        android.util.Log.d("SettingsActivity", "Login test result - URL: $finalUrl, Success: $isSuccess")
        
        if (isSuccess) {
            // Save credentials since they work
            prefs.edit().apply {
                putString(PREF_DAYFORCE_USERNAME, username)
                putString(PREF_DAYFORCE_PASSWORD, password)
                apply()
            }
            showLoginTestResult(true, "Login successful! Credentials saved.")
        } else {
            showLoginTestResult(false, "Login failed. Check credentials or try again.")
        }
    }
    
    private fun showLoginTestResult(success: Boolean, message: String) {
        // Reset button
        testDayforceLoginButton.text = "🔗 Test Dayforce Login"
        testDayforceLoginButton.isEnabled = true
        
        // Show result
        if (success) {
            Toast.makeText(this, "✅ $message", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "❌ $message", Toast.LENGTH_LONG).show()
        }
    }

    // Removed onSupportNavigateUp since we no longer have ActionBar
    
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
        
        // Goal methods
        fun getDailyGoal(context: Context, dayOfWeek: java.time.DayOfWeek): Double {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return when (dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> prefs.getFloat(PREF_MONDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat()).toDouble()
                java.time.DayOfWeek.TUESDAY -> prefs.getFloat(PREF_TUESDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat()).toDouble()
                java.time.DayOfWeek.WEDNESDAY -> prefs.getFloat(PREF_WEDNESDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat()).toDouble()
                java.time.DayOfWeek.THURSDAY -> prefs.getFloat(PREF_THURSDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat()).toDouble()
                java.time.DayOfWeek.FRIDAY -> prefs.getFloat(PREF_FRIDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat()).toDouble()
                java.time.DayOfWeek.SATURDAY -> prefs.getFloat(PREF_SATURDAY_GOAL, DEFAULT_WEEKEND_GOAL.toFloat()).toDouble()
                java.time.DayOfWeek.SUNDAY -> prefs.getFloat(PREF_SUNDAY_GOAL, DEFAULT_WEEKEND_GOAL.toFloat()).toDouble()
            }
        }
        
        fun getWeeklyGoalTotal(context: Context): Double {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(PREF_MONDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat()).toDouble() +
                   prefs.getFloat(PREF_TUESDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat()).toDouble() +
                   prefs.getFloat(PREF_WEDNESDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat()).toDouble() +
                   prefs.getFloat(PREF_THURSDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat()).toDouble() +
                   prefs.getFloat(PREF_FRIDAY_GOAL, DEFAULT_WEEKDAY_GOAL.toFloat()).toDouble() +
                   prefs.getFloat(PREF_SATURDAY_GOAL, DEFAULT_WEEKEND_GOAL.toFloat()).toDouble() +
                   prefs.getFloat(PREF_SUNDAY_GOAL, DEFAULT_WEEKEND_GOAL.toFloat()).toDouble()
        }
        
        // Dayforce credential methods
        fun getDayforceUsername(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_DAYFORCE_USERNAME, "") ?: ""
        }
        
        fun getDayforcePassword(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_DAYFORCE_PASSWORD, "") ?: ""
        }
        
        fun areDayforceCredentialsSet(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val username = prefs.getString(PREF_DAYFORCE_USERNAME, "") ?: ""
            val password = prefs.getString(PREF_DAYFORCE_PASSWORD, "") ?: ""
            return username.isNotEmpty() && password.isNotEmpty()
        }
    }
}