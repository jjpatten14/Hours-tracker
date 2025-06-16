package com.dayforcetracker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {

    // Existing UI components
    private lateinit var loginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var webView: WebView
    private lateinit var resultsScrollView: ScrollView
    private lateinit var resultsText: TextView
    private lateinit var totalHoursText: TextView

    // New pay calculator UI components
    private lateinit var payCalculator: PayCalculator
    private lateinit var payDisplayLayout: LinearLayout
    private lateinit var totalEarningsText: TextView
    private lateinit var liveCounterText: TextView
    private lateinit var currentRateText: TextView
    private lateinit var hoursInfoText: TextView
    private lateinit var settingsButton: Button
    private lateinit var refreshButton: Button
    private lateinit var detailsButton: Button
    private lateinit var hoursReportButton: Button

    // Goal and motivation UI elements
    private lateinit var dailyGoalText: TextView
    private lateinit var weeklyProgressText: TextView
    private lateinit var dailyProgressText: TextView
    private lateinit var weeklyGoalDiscrepancyText: TextView
    private lateinit var lossCalculatorText: TextView
    private lateinit var lossCalculatorLayout: LinearLayout
    private lateinit var projectedOvertimeText: TextView
    private lateinit var projectedTotalPayText: TextView

    // Live daily tracking UI elements
    private lateinit var liveDailyTrackingCard: androidx.cardview.widget.CardView
    private lateinit var liveDailyGoalText: TextView
    private lateinit var liveDailyEarningsText: TextView
    private lateinit var liveDailyDiscrepancyText: TextView

    // Data and state management
    private var extractedData = mutableListOf<PunchData>()
    private var isLoggedIn = false
    private var isRealTimeUpdating = false
    private var liveUpdateHandler: Handler? = null
    private var liveUpdateRunnable: Runnable? = null
    private var workStartTime: LocalDateTime? = null
    private var currentPayBreakdown: PayBreakdown? = null

    // Dashboard state
    private var isDashboardMode = false
    private lateinit var dashboardLayout: ScrollView

    companion object {
        private const val SETTINGS_REQUEST_CODE = 1001
        private const val LIVE_UPDATE_INTERVAL_MS = 1000L // Update every second

        // Storage keys
        private const val PREF_STORED_HOURS = "stored_total_hours"
        private const val PREF_STORED_PAY = "stored_total_pay"
        private const val PREF_LAST_UPDATE = "last_update_time"
        private const val PREF_DAILY_BREAKDOWN = "daily_breakdown_json"
    }

    data class PunchData(
        val day: String,
        val punchIn: String?,
        val punchOut: String?,
        val totalHours: String?
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize all views
        initializeViews()

        // Check if pay settings are configured
        checkPaySettings()

        setupWebView()
        setupClickListeners()

        // Auto-login to Dayforce since user is already authenticated
        autoLoginToDayforce()
    }

    private fun autoLoginToDayforce() {
        val credentials = getStoredCredentials()
        if (credentials != null) {
            // Get credentials from settings instead of hardcoded values
            val settingsUsername = SettingsActivity.Settings.getDayforceUsername(this)
            val settingsPassword = SettingsActivity.Settings.getDayforcePassword(this)
            
            if (settingsUsername.isNotEmpty() && settingsPassword.isNotEmpty()) {
                storeCredentials(settingsUsername, settingsPassword)
            } else {
                // No credentials configured in settings, use existing stored ones
                // or prompt user to configure them
                if (!SettingsActivity.Settings.areDayforceCredentialsSet(this)) {
                    showDayforceCredentialsNeeded()
                    return
                }
            }
            
            if (hasStoredData()) {
                // User has credentials and previous data - show dashboard
                showDashboard()
            } else {
                // User has credentials but no data - auto-fetch
                startDayforceFetch()
            }
        } else {
            // No credentials stored - get from settings and store
            val settingsUsername = SettingsActivity.Settings.getDayforceUsername(this)
            val settingsPassword = SettingsActivity.Settings.getDayforcePassword(this)
            
            if (settingsUsername.isNotEmpty() && settingsPassword.isNotEmpty()) {
                storeCredentials(settingsUsername, settingsPassword)
                showConnectToDayforce()
            } else {
                // No credentials configured - show proper UI to guide user
                showDayforceCredentialsNeeded()
                return
            }
        }
    }

    private fun showConnectToDayforce() {
        // Show the main screen with connect button
        resultsScrollView.visibility = View.VISIBLE
        loginButton.visibility = View.VISIBLE
        payDisplayLayout?.visibility = View.GONE
        
        // Show a message or initialize the dashboard UI
        totalHoursText.text = "Ready to connect to Dayforce"
    }

    private fun initializeViews() {
        // Existing views
        loginButton = findViewById(R.id.loginButton)
        progressBar = findViewById(R.id.progressBar)
        webView = findViewById(R.id.webView)
        resultsScrollView = findViewById(R.id.resultsScrollView)
        resultsText = findViewById(R.id.resultsText)
        totalHoursText = findViewById(R.id.totalHoursText)

        // Dashboard elements - initialize these outside try-catch
        dashboardLayout = resultsScrollView // Reuse results area for dashboard

        // New pay calculator UI elements (will be added to layout)
        try {
            payDisplayLayout = findViewById(R.id.payDisplayLayout)
            totalEarningsText = findViewById(R.id.totalEarningsText)
            liveCounterText = findViewById(R.id.liveCounterText)
            currentRateText = findViewById(R.id.currentRateText)
            hoursInfoText = findViewById(R.id.hoursInfoText)
            settingsButton = findViewById(R.id.settingsButton)
            refreshButton = findViewById(R.id.refreshButton)
            detailsButton = findViewById(R.id.detailsButton)
            hoursReportButton = findViewById(R.id.hoursReportButton)

            // Goal and motivation elements
            dailyGoalText = findViewById(R.id.dailyGoalText)
            weeklyProgressText = findViewById(R.id.weeklyProgressText)
            dailyProgressText = findViewById(R.id.dailyProgressText)
            weeklyGoalDiscrepancyText = findViewById(R.id.weeklyGoalDiscrepancyText)
            lossCalculatorText = findViewById(R.id.lossCalculatorText)
            lossCalculatorLayout = findViewById(R.id.lossCalculatorLayout)
            projectedOvertimeText = findViewById(R.id.projectedOvertimeText)
            projectedTotalPayText = findViewById(R.id.projectedTotalPayText)

            // Live daily tracking elements
            liveDailyTrackingCard = findViewById(R.id.liveDailyTrackingCard)
            liveDailyGoalText = findViewById(R.id.liveDailyGoalText)
            liveDailyEarningsText = findViewById(R.id.liveDailyEarningsText)
            liveDailyDiscrepancyText = findViewById(R.id.liveDailyDiscrepancyText)

            // Initially hide pay display until data is loaded
            payDisplayLayout.visibility = View.GONE
        } catch (e: Exception) {
            Log.d("DayforceTracker", "Pay calculator UI not found - continuing with basic functionality")
        }
    }

    private fun checkPaySettings() {
        try {
            if (!SettingsActivity.Settings.isSettingsConfigured(this)) {
                // First time - show settings
                showFirstTimeSetup()
            } else {
                // Settings exist - initialize calculator
                initializePayCalculator()
            }
        } catch (e: Exception) {
            Log.d("DayforceTracker", "Pay settings check failed - continuing with basic functionality")
        }
    }

    private fun showFirstTimeSetup() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Welcome to Dayforce Pay Tracker!")
            .setMessage("To calculate your earnings, please set up your pay rate first.")
            .setPositiveButton("Set Up Pay Rate") { _, _ ->
                openSettings()
            }
            .setNegativeButton("Skip for Now") { _, _ ->
                // Continue without pay calculation
                try {
                    payDisplayLayout.visibility = View.GONE
                } catch (e: Exception) {
                    // UI not available
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun showDayforceCredentialsNeeded() {
        // App is useless without Dayforce - go straight to settings
        openSettings()
    }

    private fun showCredentialsStillNeeded() {
        // User returned from Settings without configuring Dayforce credentials
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Dayforce Credentials Required")
            .setMessage("This app requires Dayforce login credentials to function. Would you like to:")
            .setPositiveButton("Configure Now") { _, _ ->
                openSettings()
            }
            .setNegativeButton("Exit App") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun initializePayCalculator() {
        try {
            val basePayRate = SettingsActivity.Settings.getBasePayRate(this)
            payCalculator = PayCalculator(basePayRate)
            payDisplayLayout.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.d("DayforceTracker", "Pay calculator initialization failed - continuing with basic functionality")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Safari/537.36"
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = false
        }

        webView.addJavascriptInterface(DayforceInterface(), "Android")
        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.d("DayforceTracker", "Page loaded: $url")

                when {
                    url.contains("mydayforce.aspx", ignoreCase = true) -> {
                        handleLoginPage()
                    }
                    url.contains("dayforcehcm.com") && !url.contains("login") && !isLoggedIn -> {
                        handlePostLogin()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        loginButton.setOnClickListener {
            // Connect to Dayforce (credentials are already stored)
            startDayforceFetch()
        }


        // New pay calculator button listeners
        try {
            settingsButton.setOnClickListener {
                openSettings()
            }

            refreshButton.setOnClickListener {
                // Start Dayforce data fetch
                startDayforceFetch()
            }

            detailsButton.setOnClickListener {
                showDetailedBreakdown()
            }

            hoursReportButton.setOnClickListener {
                showHoursReport()
            }
        } catch (e: Exception) {
            Log.d("DayforceTracker", "Pay calculator buttons not available")
        }
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivityForResult(intent, SETTINGS_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SETTINGS_REQUEST_CODE) {
            // Handle both Save (RESULT_OK) and Cancel (RESULT_CANCELED)
            // Always check if credentials are now available
            if (SettingsActivity.Settings.areDayforceCredentialsSet(this)) {
                // Credentials are now set - initialize and try auto-login
                initializePayCalculator()
                autoLoginToDayforce()
                
                // Recalculate if we have timesheet data
                currentPayBreakdown?.let { updatePayDisplay(it) }
            } else {
                // Still no credentials - give user options instead of going back to grey screen
                showCredentialsStillNeeded()
            }
        }
    }

    private fun startLogin() {
        showProgress(true)
        isLoggedIn = false
        extractedData.clear()

        webView.visibility = View.VISIBLE
        webView.loadUrl("https://us251.dayforcehcm.com/MyDayforce/Mydayforce.aspx")
    }

    private fun handleLoginPage() {
        lifecycleScope.launch {
            delay(3000) // Wait for page to fully load

            // Get credentials from settings
            val username = SettingsActivity.Settings.getDayforceUsername(this@MainActivity)
            val password = SettingsActivity.Settings.getDayforcePassword(this@MainActivity)

            val loginScript = """
                (function() {
                    try {
                        // Fill company field
                        var companyField = document.querySelector('input[name*="CompanyName"]');
                        if (companyField) {
                            companyField.value = 'tgi';
                        }
                        
                        // Fill username
                        var usernameField = document.querySelector('input[name*="UserName"]');
                        if (usernameField) {
                            usernameField.value = '$username';
                        }
                        
                        // Fill password
                        var passwordField = document.querySelector('input[name*="UserPass"]') || 
                                          document.querySelector('input[type="password"]');
                        if (passwordField) {
                            passwordField.value = '$password';
                        }
                        
                        // Click login button
                        var loginBtn = document.querySelector('input[name*="cmdLogin"]') ||
                                      document.querySelector('input[value="Login"]') ||
                                      document.querySelector('input[type="submit"]');
                        if (loginBtn) {
                            loginBtn.click();
                            return 'Login form submitted';
                        }
                        
                        return 'Login button not found';
                        
                    } catch (e) {
                        return 'Error: ' + e.message;
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(loginScript) { result ->
                Log.d("DayforceTracker", "Login script result: $result")
            }
        }
    }

    private fun handlePostLogin() {
        isLoggedIn = true
        lifecycleScope.launch {
            delay(2000)
            handleCookieBanner()
            delay(2000)
            navigateToTimesheet()
        }
    }

    private fun handleCookieBanner() {
        val cookieScript = """
            (function() {
                try {
                    var acceptBtn = document.querySelector('button[contains(text(), "Accept")]') ||
                                   document.querySelector('button[contains(text(), "Accept All")]') ||
                                   document.querySelector('.cookie-banner button') ||
                                   document.querySelector('.cookie-consent button');
                    
                    if (acceptBtn) {
                        acceptBtn.click();
                        return 'Cookie banner accepted';
                    }
                    return 'No cookie banner found';
                } catch (e) {
                    return 'Error: ' + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(cookieScript) { result ->
            Log.d("DayforceTracker", "Cookie banner result: $result")
        }
    }

    private fun navigateToTimesheet() {
        // First set wider viewport
        val viewportScript = """
            var viewport = document.querySelector('meta[name="viewport"]');
            if (viewport) {
                viewport.setAttribute('content', 'width=3200, initial-scale=0.15');
            } else {
                var meta = document.createElement('meta');
                meta.name = 'viewport';
                meta.content = 'width=3200, initial-scale=0.15';
                document.getElementsByTagName('head')[0].appendChild(meta);
            }
        """.trimIndent()

        webView.evaluateJavascript(viewportScript) {
            Log.d("DayforceTracker", "Viewport set to desktop width")
        }

        val timesheetScript = """
            (function() {
                try {
                    // Strategy 1: Find by the FeatureName div with "Employee Timesheet" text (like Python script)
                    var timesheetElement = null;
                    
                    // Look for FeatureName class with Employee Timesheet text
                    var featureElements = document.querySelectorAll('div[class*="FeatureName"]');
                    for (var i = 0; i < featureElements.length; i++) {
                        if (featureElements[i].textContent.indexOf('Employee Timesheet') !== -1) {
                            timesheetElement = featureElements[i];
                            console.log('Found by FeatureName class and text');
                            break;
                        }
                    }
                    
                    // Strategy 2: Find by the icon class (like Python script)
                    if (!timesheetElement) {
                        var iconElement = document.querySelector('.dfI_Nav_HTMLEmployeeTimeSheet');
                        if (iconElement) {
                            // Find the parent FeatureGroup container
                            var parent = iconElement;
                            while (parent && !parent.className.includes('FeatureGroup')) {
                                parent = parent.parentElement;
                            }
                            if (parent) {
                                timesheetElement = parent;
                                console.log('Found by icon class');
                            }
                        }
                    }
                    
                    // Strategy 3: Simple text search (like Python script)
                    if (!timesheetElement) {
                        var allElements = document.querySelectorAll('*');
                        for (var i = 0; i < allElements.length; i++) {
                            if (allElements[i].textContent && allElements[i].textContent.indexOf('Employee Timesheet') !== -1) {
                                timesheetElement = allElements[i];
                                console.log('Found by text content');
                                break;
                            }
                        }
                    }
                    
                    if (timesheetElement) {
                        timesheetElement.scrollIntoView(true);
                        setTimeout(function() {
                            timesheetElement.click();
                        }, 1000);
                        return 'Found and clicked Employee Timesheet element';
                    } else {
                        // Fallback: try direct URL navigation to Time.aspx (like Python script)
                        console.log('Could not find Employee Timesheet element, trying direct navigation');
                        window.location.href = 'https://us251.dayforcehcm.com/MyDayforce/Time.aspx';
                        return 'Navigated to timesheet URL directly';
                    }
                    
                } catch (e) {
                    console.error('Error in timesheet navigation: ' + e.message);
                    return 'Error: ' + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(timesheetScript) { result ->
            Log.d("DayforceTracker", "Timesheet navigation result: $result")

            lifecycleScope.launch {
                delay(8000) // Wait longer for timesheet page to load (like Python script)
                extractPunchTimes()
            }
        }
    }

    private fun scrollAndExtractPunchTimes() {
        val scrollScript = """
            // First extract current data
            window.allExtractedData = [];
            
            function extractCurrentData() {
                var punchTimes = [];
                
                var allPunchIns = [];
                var startIcons = document.querySelectorAll('.Icon_ScheduledShiftBlue');
                for (var i = 0; i < startIcons.length; i++) {
                    var row = startIcons[i].closest('div[class*="tableRow"]');
                    if (row) {
                        var actualTimeCell = row.querySelector('.actualTime .dijitOutput');
                        if (actualTimeCell && actualTimeCell.textContent.trim()) {
                            allPunchIns.push(actualTimeCell.textContent.trim());
                        }
                    }
                }
                
                var allPunchOuts = [];
                var endIcons = document.querySelectorAll('.Icon_ScheduledShiftEndBlue');
                for (var i = 0; i < endIcons.length; i++) {
                    var row = endIcons[i].closest('div[class*="tableRow"]');
                    if (row) {
                        var actualTimeCell = row.querySelector('.actualTime .dijitOutput');
                        if (actualTimeCell && actualTimeCell.textContent.trim()) {
                            allPunchOuts.push(actualTimeCell.textContent.trim());
                        }
                    }
                }
                
                var allTotalHours = [];
                var totalElements = document.querySelectorAll('.actualTotal .dijitOutput');
                for (var i = 0; i < totalElements.length; i++) {
                    var totalText = totalElements[i].textContent.trim();
                    if (totalText && totalText.indexOf('h') !== -1 && totalElements[i].offsetParent !== null) {
                        allTotalHours.push(totalText);
                    }
                }
                
                return {ins: allPunchIns, outs: allPunchOuts, hours: allTotalHours};
            }
            
            // Extract initial data
            var initialData = extractCurrentData();
            window.allExtractedData.push(initialData);
            console.log('Initial extraction - Ins: ' + initialData.ins.length + ', Outs: ' + initialData.outs.length + ', Hours: ' + initialData.hours.length);
            
            // Scroll right and extract more
            var timesheet = document.querySelector('.timeSheetContainer') || document.querySelector('.dijitContentPane') || document.body;
            if (timesheet) {
                timesheet.scrollLeft += 800;
                console.log('Scrolled right 800px');
                
                setTimeout(function() {
                    var scrolledData = extractCurrentData();
                    window.allExtractedData.push(scrolledData);
                    console.log('After scroll - Ins: ' + scrolledData.ins.length + ', Outs: ' + scrolledData.outs.length + ', Hours: ' + scrolledData.hours.length);
                    
                    // Scroll more if needed
                    timesheet.scrollLeft += 800;
                    console.log('Scrolled right another 800px');
                    
                    setTimeout(function() {
                        var finalData = extractCurrentData();
                        window.allExtractedData.push(finalData);
                        console.log('Final scroll - Ins: ' + finalData.ins.length + ', Outs: ' + finalData.outs.length + ', Hours: ' + finalData.hours.length);
                        
                        // Combine all unique data
                        var allIns = [], allOuts = [], allHours = [];
                        for (var d = 0; d < window.allExtractedData.length; d++) {
                            var data = window.allExtractedData[d];
                            for (var i = 0; i < data.ins.length; i++) {
                                if (allIns.indexOf(data.ins[i]) === -1) allIns.push(data.ins[i]);
                            }
                            for (var i = 0; i < data.outs.length; i++) {
                                if (allOuts.indexOf(data.outs[i]) === -1) allOuts.push(data.outs[i]);
                            }
                            for (var i = 0; i < data.hours.length; i++) {
                                if (allHours.indexOf(data.hours[i]) === -1) allHours.push(data.hours[i]);
                            }
                        }
                        
                        console.log('Combined totals - Ins: ' + allIns.length + ', Outs: ' + allOuts.length + ', Hours: ' + allHours.length);
                        
                        // Android.extractionComplete(JSON.stringify({ins: allIns, outs: allOuts, hours: allHours}));
                    }, 2000);
                }, 2000);
            }
        """.trimIndent()

        webView.evaluateJavascript(scrollScript) { result ->
            Log.d("DayforceTracker", "Scroll script result: $result")

            // Wait for scrolling and extraction to complete, then get final data
            lifecycleScope.launch {
                delay(6000) // Wait for all scrolling and extraction
                extractFinalData()
            }
        }
    }

    private fun extractFinalData() {
        val finalScript = """
            (function() {
                if (window.allExtractedData && window.allExtractedData.length > 0) {
                    var allIns = [], allOuts = [], allHours = [];
                    for (var d = 0; d < window.allExtractedData.length; d++) {
                        var data = window.allExtractedData[d];
                        for (var i = 0; i < data.ins.length; i++) {
                            if (allIns.indexOf(data.ins[i]) === -1) allIns.push(data.ins[i]);
                        }
                        for (var i = 0; i < data.outs.length; i++) {
                            if (allOuts.indexOf(data.outs[i]) === -1) allOuts.push(data.outs[i]);
                        }
                        for (var i = 0; i < data.hours.length; i++) {
                            if (allHours.indexOf(data.hours[i]) === -1) allHours.push(data.hours[i]);
                        }
                    }
                    
                    var punchTimes = [];
                    var dayNames = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
                    var maxDays = Math.max(allIns.length, allOuts.length, allHours.length);
                    
                    for (var i = 0; i < maxDays; i++) {
                        var dayName = i < dayNames.length ? dayNames[i] : 'Day ' + (i + 1);
                        
                        if (i < allIns.length) {
                            punchTimes.push(dayName + ' Punch In: ' + allIns[i]);
                        }
                        if (i < allOuts.length) {
                            punchTimes.push(dayName + ' Punch Out: ' + allOuts[i]);
                        }
                        if (i < allHours.length) {
                            punchTimes.push(dayName + ' Hours: ' + allHours[i]);
                        }
                    }
                    
                    return JSON.stringify(punchTimes);
                } else {
                    return JSON.stringify(['No scroll data found']);
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(finalScript) { result ->
            try {
                val cleanResult = result.replace("\\\\", "").removeSurrounding("\"")
                Log.d("DayforceTracker", "Final extraction result: $result")
                processExtractedData(cleanResult, result)
            } catch (e: Exception) {
                Log.e("DayforceTracker", "Error processing final data", e)
                runOnUiThread {
                    Toast.makeText(this, "Failed to extract data: ${e.message}", Toast.LENGTH_LONG).show()
                    showProgress(false)
                }
            }
        }
    }

    private fun extractPunchTimes() {
        val extractionScript = """
            (function() {
                try {
                    console.log('Starting simple icon extraction...');
                    var punchTimes = [];
                    
                    // Get punch in times by icons
                    var allPunchIns = [];
                    var startIcons = document.querySelectorAll('.Icon_ScheduledShiftBlue');
                    console.log('Found ' + startIcons.length + ' punch in icons');
                    
                    for (var i = 0; i < startIcons.length; i++) {
                        var row = startIcons[i].closest('div[class*="tableRow"]');
                        if (row) {
                            var actualTimeCell = row.querySelector('.actualTime .dijitOutput');
                            if (actualTimeCell && actualTimeCell.textContent.trim()) {
                                allPunchIns.push(actualTimeCell.textContent.trim());
                            }
                        }
                    }
                    
                    // Get punch out times by icons
                    var allPunchOuts = [];
                    var endIcons = document.querySelectorAll('.Icon_ScheduledShiftEndBlue');
                    console.log('Found ' + endIcons.length + ' punch out icons');
                    
                    for (var i = 0; i < endIcons.length; i++) {
                        var row = endIcons[i].closest('div[class*="tableRow"]');
                        if (row) {
                            var actualTimeCell = row.querySelector('.actualTime .dijitOutput');
                            if (actualTimeCell && actualTimeCell.textContent.trim()) {
                                allPunchOuts.push(actualTimeCell.textContent.trim());
                            }
                        }
                    }
                    
                    // Get total hours
                    var allTotalHours = [];
                    var totalElements = document.querySelectorAll('.actualTotal .dijitOutput');
                    console.log('Found ' + totalElements.length + ' total elements');
                    
                    for (var i = 0; i < totalElements.length; i++) {
                        var totalText = totalElements[i].textContent.trim();
                        if (totalText && totalText.indexOf('h') !== -1 && totalElements[i].offsetParent !== null) {
                            allTotalHours.push(totalText);
                        }
                    }
                    
                    console.log('Punch ins: ' + allPunchIns.length + ', Punch outs: ' + allPunchOuts.length + ', Hours: ' + allTotalHours.length);
                    
                    // If we don't find 3 of each, try scanning ALL icons on the page
                    if (allPunchIns.length < 3 || allPunchOuts.length < 3) {
                        console.log('Only found ' + allPunchIns.length + ' punch ins and ' + allPunchOuts.length + ' punch outs. Scanning for more icons...');
                        
                        // Find all elements with icon classes
                        var allIcons = document.querySelectorAll('[class*="Icon_"]');
                        console.log('Found ' + allIcons.length + ' total icon elements');
                        
                        for (var i = 0; i < allIcons.length; i++) {
                            console.log('Icon ' + i + ': ' + allIcons[i].className);
                        }
                    }
                    
                    // Combine data by day
                    var maxDays = Math.max(allPunchIns.length, allPunchOuts.length, allTotalHours.length);
                    var dayNames = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'];
                    
                    for (var i = 0; i < maxDays; i++) {
                        var dayName = i < dayNames.length ? dayNames[i] : 'Day ' + (i + 1);
                        
                        if (i < allPunchIns.length) {
                            punchTimes.push(dayName + ' Punch In: ' + allPunchIns[i]);
                        }
                        if (i < allPunchOuts.length) {
                            punchTimes.push(dayName + ' Punch Out: ' + allPunchOuts[i]);
                        }
                        if (i < allTotalHours.length) {
                            punchTimes.push(dayName + ' Hours: ' + allTotalHours[i]);
                        }
                    }
                    
                    console.log('Extraction complete. Found ' + punchTimes.length + ' items');
                    return JSON.stringify(punchTimes);
                    
                } catch (e) {
                    console.error('Error in extraction: ' + e.message);
                    return JSON.stringify(['Error: ' + e.message]);
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(extractionScript) { result ->
            try {
                // Clean up the result string
                val cleanResult = result.replace("\\", "").removeSurrounding("\"")
                Log.d("DayforceTracker", "RAW extraction result: $result")
                Log.d("DayforceTracker", "CLEAN extraction result: $cleanResult")

                // Parse and display results (pass raw data for debugging)
                processExtractedData(cleanResult, result)

            } catch (e: Exception) {
                Log.e("DayforceTracker", "Error processing extracted data", e)
                runOnUiThread {
                    Toast.makeText(this, "Failed to extract data: ${e.message}", Toast.LENGTH_LONG).show()
                    showProgress(false)
                }
            }
        }
    }

    private fun processExtractedData(jsonData: String, rawData: String = "") {
        try {
            extractedData.clear()
            var totalHours = 0.0

            // Parse the JSON data from multi-day extraction (array of strings)
            val cleanData = jsonData.trim()
            Log.d("DayforceTracker", "Processing data: $cleanData")

            if (cleanData.startsWith("[") && cleanData.endsWith("]")) {
                // Remove brackets and split by comma (simple string array)
                val dataContent = cleanData.substring(1, cleanData.length - 1)
                val items = dataContent.split("\",\"")

                Log.d("DayforceTracker", "Split into ${items.size} items:")
                for (i in items.indices) {
                    Log.d("DayforceTracker", "  Item $i: ${items[i]}")
                }

                // Group by day
                val dayData = mutableMapOf<String, MutableMap<String, String>>()

                for (item in items) {
                    val cleanItem = item.replace("\"", "").trim()
                    Log.d("DayforceTracker", "Processing item: $cleanItem")

                    when {
                        cleanItem.contains(" Punch In:") -> {
                            val parts = cleanItem.split(" Punch In:")
                            if (parts.size >= 2) {
                                val day = parts[0].trim()
                                val time = parts[1].trim()
                                dayData.getOrPut(day) { mutableMapOf() }["punchIn"] = time
                                Log.d("DayforceTracker", "Found $day punch in: $time")
                            }
                        }
                        cleanItem.contains(" Punch Out:") -> {
                            val parts = cleanItem.split(" Punch Out:")
                            if (parts.size >= 2) {
                                val day = parts[0].trim()
                                val time = parts[1].trim()
                                dayData.getOrPut(day) { mutableMapOf() }["punchOut"] = time
                                Log.d("DayforceTracker", "Found $day punch out: $time")
                            }
                        }
                        cleanItem.contains(" Hours:") -> {
                            val parts = cleanItem.split(" Hours:")
                            if (parts.size >= 2) {
                                val day = parts[0].trim()
                                val hours = parts[1].trim()
                                dayData.getOrPut(day) { mutableMapOf() }["hours"] = hours
                                Log.d("DayforceTracker", "Found $day hours: $hours")

                                // Calculate total hours
                                if (hours.contains("h")) {
                                    try {
                                        val hoursNum = hours.replace("h", "").trim().toDouble()
                                        totalHours += hoursNum
                                    } catch (e: Exception) {
                                        Log.e("DayforceTracker", "Error parsing hours: $hours")
                                    }
                                }
                            }
                        }
                        cleanItem.startsWith("Error:") -> {
                            Log.e("DayforceTracker", "JavaScript error: $cleanItem")
                        }
                    }
                }

                // Convert to PunchData objects
                Log.d("DayforceTracker", "Converting ${dayData.size} days to PunchData objects")
                for ((day, data) in dayData) {
                    val punchIn = data["punchIn"]
                    val punchOut = data["punchOut"]
                    val hours = data["hours"]

                    Log.d("DayforceTracker", "Day: $day - PunchIn: $punchIn, PunchOut: $punchOut, Hours: $hours")

                    if (punchIn != null || punchOut != null || hours != null) {
                        extractedData.add(PunchData(day, punchIn, punchOut, hours))
                        Log.d("DayforceTracker", "Added $day to extractedData")
                    } else {
                        Log.d("DayforceTracker", "Skipped $day - no data")
                    }
                }

                // Sort by day order
                val dayOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                extractedData.sortBy { punchData ->
                    dayOrder.indexOf(punchData.day).takeIf { it >= 0 } ?: 999
                }
            }

            if (extractedData.isEmpty()) {
                // Show raw data if no parsed data
                extractedData.add(PunchData("DEBUG - Raw Data", null, null, null))
                showResults(extractedData, totalHours, jsonData)
            } else {
                showResults(extractedData, totalHours, jsonData)
            }

        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error processing extracted data", e)
            runOnUiThread {
                Toast.makeText(this, "Error processing timesheet data: ${e.message}", Toast.LENGTH_LONG).show()
                showProgress(false)
            }
        }
    }


    private fun showResults(data: List<PunchData>, totalHours: Double, debugData: String = "") {
        runOnUiThread {
            val builder = StringBuilder()
            var calculatedTotal = 0.0

            Log.d("DayforceTracker", "Showing results for ${data.size} days")

            for (day in data) {
                builder.append("${day.day}:\n")
                Log.d("DayforceTracker", "Processing day: ${day.day}")

                day.punchIn?.let {
                    builder.append("  Punch In: $it\n")
                    Log.d("DayforceTracker", "  Punch In: $it")
                }
                day.punchOut?.let {
                    builder.append("  Punch Out: $it\n")
                    Log.d("DayforceTracker", "  Punch Out: $it")
                }
                day.totalHours?.let {
                    builder.append("  Hours: $it\n")
                    Log.d("DayforceTracker", "  Hours: $it")

                    // Recalculate total to make sure it's correct
                    if (it.contains("h")) {
                        try {
                            val hoursNum = it.replace("h", "").trim().toDouble()
                            calculatedTotal += hoursNum
                            Log.d("DayforceTracker", "  Added $hoursNum to total, now: $calculatedTotal")
                        } catch (e: Exception) {
                            Log.e("DayforceTracker", "Error parsing hours in display: $it")
                        }
                    }
                }
                builder.append("\n")
            }

            // Add debug info at the end
            if (debugData.isNotEmpty()) {
                builder.append("\n--- DEBUG INFO ---\n")
                builder.append("Raw JavaScript result:\n")
                builder.append(debugData)
            }

            resultsText.text = builder.toString()

            // Use the recalculated total to make sure it matches what we're showing
            Log.d("DayforceTracker", "Final totals - Passed: $totalHours, Calculated: $calculatedTotal")
            totalHoursText.text = "Total Hours: ${String.format("%.1f", calculatedTotal)}h"

            // NEW: Process pay calculation using correct total hours
            processPayCalculationSimple(calculatedTotal)

            showProgress(false)
            showResultsView()
        }
    }

    private fun processPayCalculationSimple(totalHours: Double) {
        try {
            if (::payCalculator.isInitialized && totalHours > 0) {
                // Simple calculation: just use total hours and base rate
                val basePayRate = SettingsActivity.Settings.getBasePayRate(this)

                // Calculate pay based on total hours
                val regularHours = kotlin.math.min(totalHours, 40.0)
                val overtimeHours = kotlin.math.max(0.0, totalHours - 40.0)

                val regularPay = regularHours * basePayRate
                val overtimePay = overtimeHours * (basePayRate * 1.5)
                val totalPay = regularPay + overtimePay

                Log.d("DayforceTracker", "Simple pay calc - Total: ${totalHours}h, Regular: ${regularHours}h, OT: ${overtimeHours}h, Pay: $${totalPay}")

                // Check if currently working by looking at extracted data
                val isWorking = checkIfCurrentlyWorking()
                val currentRate = if (isWorking) {
                    if (totalHours >= 40.0) basePayRate * 1.5 else basePayRate
                } else {
                    basePayRate
                }

                // Create a simple PayBreakdown
                currentPayBreakdown = PayBreakdown(
                    regularHours = regularHours,
                    overtimeHours = overtimeHours,
                    sundayPremiumHours = 0.0,
                    regularPay = regularPay,
                    overtimePay = overtimePay,
                    sundayPremiumPay = 0.0,
                    totalPay = totalPay,
                    currentHourlyRate = currentRate,
                    isCurrentlyWorking = isWorking,
                    hoursUntilOvertime = kotlin.math.max(0.0, 40.0 - totalHours),
                    nextRateTier = if (totalHours < 40.0) "Overtime at 40h" else "Overtime active"
                )

                // Store the data
                storeTimesheetData(totalHours, totalPay)

                // Start real-time updates if currently working
                if (isWorking) {
                    startRealTimeUpdates()
                }

                // Go back to dashboard to show the updated data
                showDashboard()
            }
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error in simple pay calculation", e)
        }
    }

    private fun checkIfCurrentlyWorking(): Boolean {
        try {
            val today = LocalDateTime.now().dayOfWeek
            val todayName = today.name.lowercase().replaceFirstChar { it.uppercase() }

            // Find today's data in extracted timesheet
            val todayData = extractedData.find { it.day.equals(todayName, ignoreCase = true) }

            if (todayData != null) {
                val hasPunchIn = !todayData.punchIn.isNullOrEmpty()
                val hasPunchOut = !todayData.punchOut.isNullOrEmpty()

                // If punched in but no punch out, likely still working
                if (hasPunchIn && !hasPunchOut) {
                    Log.d("DayforceTracker", "Currently working - punched in but no punch out")
                    return true
                }

                // If both punch in and out exist, check if it's within work hours
                if (hasPunchIn && hasPunchOut) {
                    val currentTime = LocalDateTime.now().toLocalTime()
                    try {
                        val timeFormat = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
                        val punchInTime = java.time.LocalTime.parse(todayData.punchIn, timeFormat)
                        val punchOutTime = java.time.LocalTime.parse(todayData.punchOut, timeFormat)

                        val isWithinWorkHours = currentTime.isAfter(punchInTime) && currentTime.isBefore(punchOutTime)
                        Log.d("DayforceTracker", "Current time: $currentTime, Punch in: $punchInTime, Punch out: $punchOutTime, Within hours: $isWithinWorkHours")
                        return isWithinWorkHours
                    } catch (e: Exception) {
                        Log.e("DayforceTracker", "Error parsing punch times", e)
                        return false
                    }
                }
            }

            Log.d("DayforceTracker", "Not currently working - no valid punch data for today")
            return false
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error checking if currently working", e)
            return false
        }
    }

    private fun processPayCalculation(data: List<PunchData>) {
        try {
            if (::payCalculator.isInitialized && data.isNotEmpty()) {
                // Parse timesheet data into WorkDay objects
                val workDays = parseTimesheetToWorkDays(data)

                // Calculate pay
                val payBreakdown = payCalculator.calculatePay(workDays)
                currentPayBreakdown = payBreakdown
                updatePayDisplay(payBreakdown)

                // Start real-time updates if currently working
                if (payBreakdown.isCurrentlyWorking) {
                    startRealTimeUpdates()
                } else {
                    stopRealTimeUpdates()
                }
            }
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error in pay calculation", e)
        }
    }

    private fun parseTimesheetToWorkDays(data: List<PunchData>): List<WorkDay> {
        val workDays = mutableListOf<WorkDay>()
        val daysOfWeek = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        )

        for ((index, day) in daysOfWeek.withIndex()) {
            var punchIn: String? = null
            var punchOut: String? = null
            var hours = 0.0

            // Find matching day in extracted data
            val dayName = day.name.lowercase().replaceFirstChar { it.uppercase() }
            val dayData = data.find { it.day.equals(dayName, ignoreCase = true) }

            if (dayData != null) {
                punchIn = dayData.punchIn
                punchOut = dayData.punchOut

                // Parse hours from totalHours string
                dayData.totalHours?.let { hoursStr ->
                    try {
                        val cleanHours = hoursStr.replace("h", "").trim()
                        hours = cleanHours.toDoubleOrNull() ?: 0.0
                    } catch (e: Exception) {
                        Log.e("DayforceTracker", "Error parsing hours for $dayName: $hoursStr")
                    }
                }
            }

            workDays.add(WorkDay(day, punchIn, punchOut, hours))
        }

        Log.d("DayforceTracker", "Parsed ${workDays.size} WorkDay objects")
        return workDays
    }

    private fun updatePayDisplay(payBreakdown: PayBreakdown) {
        runOnUiThread {
            try {
                // Update total earnings
                totalEarningsText.text = payCalculator.formatCurrency(payBreakdown.totalPay)

                // Update current rate info
                val rateText = when {
                    payBreakdown.isCurrentlyWorking -> {
                        val rateName = when (payBreakdown.currentHourlyRate) {
                            payCalculator.baseHourlyRate -> "Regular Rate"
                            payCalculator.baseHourlyRate * PayCalculator.OVERTIME_MULTIPLIER -> "Overtime Rate"
                            payCalculator.baseHourlyRate * PayCalculator.SUNDAY_PREMIUM_MULTIPLIER -> "Sunday Premium"
                            else -> "Current Rate"
                        }
                        "$rateName: ${SettingsActivity.Settings.formatPayRate(payBreakdown.currentHourlyRate)}"
                    }
                    else -> "Not currently working"
                }
                currentRateText.text = rateText

                // Update hours info
                val totalHours = payBreakdown.regularHours + payBreakdown.overtimeHours + payBreakdown.sundayPremiumHours
                val hoursText = if (payBreakdown.hoursUntilOvertime > 0) {
                    "Total: ${payCalculator.formatHours(totalHours)} | ${payBreakdown.nextRateTier}"
                } else {
                    "Total: ${payCalculator.formatHours(totalHours)} | ${payBreakdown.nextRateTier}"
                }
                hoursInfoText.text = hoursText

                // Show/hide live counter based on work status
                if (payBreakdown.isCurrentlyWorking) {
                    liveCounterText.visibility = View.VISIBLE
                    liveCounterText.text = "⏱️ Live: +$0.00/min"
                } else {
                    liveCounterText.visibility = View.GONE
                }

                // Show pay display layout
                payDisplayLayout.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e("DayforceTracker", "Error updating pay display", e)
            }
        }
    }

    private fun updatePayDisplaySimple(totalPay: Double, totalHours: Double, baseRate: Double, regularHours: Double, overtimeHours: Double) {
        runOnUiThread {
            try {
                // Update total earnings
                totalEarningsText.text = "$${String.format("%.2f", totalPay)}"

                // Update current rate info
                val rateText = "Base Rate: $${String.format("%.2f", baseRate)}/hr"
                currentRateText.text = rateText

                // Update hours info
                val hoursText = if (overtimeHours > 0) {
                    "Total: ${String.format("%.1f", totalHours)}h (${String.format("%.1f", regularHours)}h reg + ${String.format("%.1f", overtimeHours)}h OT)"
                } else {
                    val remaining = 40.0 - totalHours
                    "Total: ${String.format("%.1f", totalHours)}h | ${String.format("%.1f", remaining)}h until overtime"
                }
                hoursInfoText.text = hoursText

                // Hide live counter (not working currently)
                liveCounterText.visibility = View.GONE

                // Show pay display layout
                payDisplayLayout.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e("DayforceTracker", "Error updating simple pay display", e)
            }
        }
    }

    private fun startRealTimeUpdates() {
        if (isRealTimeUpdating) return

        isRealTimeUpdating = true
        workStartTime = LocalDateTime.now()
        liveUpdateHandler = Handler(Looper.getMainLooper())

        liveUpdateRunnable = object : Runnable {
            override fun run() {
                updateLiveEarnings()
                liveUpdateHandler?.postDelayed(this, LIVE_UPDATE_INTERVAL_MS)
            }
        }

        liveUpdateHandler?.post(liveUpdateRunnable!!)
        Log.d("DayforceTracker", "Started real-time earnings updates")
    }

    private fun stopRealTimeUpdates() {
        isRealTimeUpdating = false
        liveUpdateRunnable?.let { liveUpdateHandler?.removeCallbacks(it) }
        liveUpdateHandler = null
        liveUpdateRunnable = null
        workStartTime = null
        
        // Hide live daily tracking when not working
        try {
            liveDailyTrackingCard.visibility = View.GONE
        } catch (e: Exception) {
            // Live daily tracking not available
        }
        
        Log.d("DayforceTracker", "Stopped real-time earnings updates")
    }

    private fun updateLiveEarnings() {
        val payBreakdown = currentPayBreakdown ?: return
        val startTime = workStartTime ?: return

        if (!payBreakdown.isCurrentlyWorking) {
            stopRealTimeUpdates()
            return
        }

        try {
            val secondsWorked = ChronoUnit.SECONDS.between(startTime, LocalDateTime.now())
            val liveTotal = payCalculator.calculateLiveEarnings(payBreakdown, secondsWorked)
            val earningsPerSecond = payCalculator.getEarningsPerSecond(payBreakdown.currentHourlyRate)
            val earningsPerMinute = earningsPerSecond * 60

            // Calculate live daily and weekly earnings
            val todayHoursWorked = secondsWorked / 3600.0
            val todayEarnings = calculateLiveDailyEarnings(todayHoursWorked)
            val totalWeeklyEarnings = liveTotal

            // Calculate loss if clocking out now
            val baseRate = SettingsActivity.Settings.getBasePayRate(this)
            val today = LocalDateTime.now().dayOfWeek
            val todayGoal = SettingsActivity.Settings.getDailyGoal(this, today)
            val hoursUntilGoal = kotlin.math.max(0.0, todayGoal - todayHoursWorked)
            val potentialLoss = hoursUntilGoal * payBreakdown.currentHourlyRate

            runOnUiThread {
                // Update main earnings display
                totalEarningsText.text = payCalculator.formatCurrency(liveTotal)
                liveCounterText.text = "⏱️ Live: +${payCalculator.formatCurrency(earningsPerMinute)}/min"

                // Update motivational display with live data
                updateMotivationalDisplayLive(totalWeeklyEarnings, todayEarnings, baseRate, today, potentialLoss, hoursUntilGoal > 0)
                
                // Update live daily tracking display
                updateLiveDailyTracking(todayHoursWorked, todayEarnings, todayGoal, today)
            }
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error updating live earnings", e)
        }
    }

    private fun showDetailedBreakdown() {
        val payBreakdown = currentPayBreakdown ?: run {
            // Create a simple breakdown from stored data if no current breakdown
            val storedData = getStoredData()
            if (storedData != null) {
                val (hours, pay, _) = storedData
                val baseRate = SettingsActivity.Settings.getBasePayRate(this)
                val regularHours = kotlin.math.min(hours, 40.0)
                val overtimeHours = kotlin.math.max(0.0, hours - 40.0)
                val regularPay = regularHours * baseRate
                val overtimePay = overtimeHours * (baseRate * 1.5)
                
                PayBreakdown(
                    regularHours = regularHours,
                    overtimeHours = overtimeHours,
                    sundayPremiumHours = 0.0,
                    regularPay = regularPay,
                    overtimePay = overtimePay,
                    sundayPremiumPay = 0.0,
                    totalPay = pay,
                    currentHourlyRate = baseRate,
                    isCurrentlyWorking = false,
                    hoursUntilOvertime = kotlin.math.max(0.0, 40.0 - hours),
                    nextRateTier = if (hours < 40.0) "Overtime at 40h" else "Overtime active"
                )
            } else {
                Toast.makeText(this, "No pay data available. Please refresh first.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val breakdown = StringBuilder()
        breakdown.append("Detailed Pay Breakdown\n\n")

        // Regular hours
        if (payBreakdown.regularHours > 0) {
            breakdown.append("Regular Hours: ${String.format("%.1f", payBreakdown.regularHours)}h\n")
            breakdown.append("Regular Pay: $${String.format("%.2f", payBreakdown.regularPay)}\n\n")
        }

        // Overtime hours
        if (payBreakdown.overtimeHours > 0) {
            breakdown.append("Overtime Hours: ${String.format("%.1f", payBreakdown.overtimeHours)}h\n")
            breakdown.append("Overtime Pay (1.5x): $${String.format("%.2f", payBreakdown.overtimePay)}\n\n")
        }

        // Sunday premium hours
        if (payBreakdown.sundayPremiumHours > 0) {
            breakdown.append("Sunday Premium Hours: ${String.format("%.1f", payBreakdown.sundayPremiumHours)}h\n")
            breakdown.append("Sunday Premium Pay (2x): $${String.format("%.2f", payBreakdown.sundayPremiumPay)}\n\n")
        }

        breakdown.append("TOTAL: $${String.format("%.2f", payBreakdown.totalPay)}\n\n")

        // Current status
        if (payBreakdown.isCurrentlyWorking) {
            breakdown.append("Currently Working\n")
            breakdown.append("Current Rate: ${SettingsActivity.Settings.formatPayRate(payBreakdown.currentHourlyRate)}\n")
        } else {
            breakdown.append("Not Currently Working\n")
        }

        if (payBreakdown.hoursUntilOvertime > 0) {
            breakdown.append("Next: ${payBreakdown.nextRateTier}")
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Pay Breakdown")
            .setMessage(breakdown.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showHoursReport() {
        val report = StringBuilder()
        report.append("📋 Raw Hours Report\n\n")

        if (extractedData.isEmpty()) {
            report.append("No timesheet data available.\nPlease refresh to get current data.\n\n")
        } else {
            report.append("Extracted Timesheet Data:\n")
            report.append("==============================\n\n")

            var totalCalculatedHours = 0.0
            for (day in extractedData) {
                report.append("${day.day}:\n")

                day.punchIn?.let {
                    report.append("  Punch In: $it\n")
                }
                day.punchOut?.let {
                    report.append("  Punch Out: $it\n")
                }
                day.totalHours?.let {
                    report.append("  Hours: $it\n")

                    // Try to extract hours for total calculation
                    if (it.contains("h")) {
                        try {
                            val hoursNum = it.replace("h", "").trim().toDouble()
                            totalCalculatedHours += hoursNum
                        } catch (e: Exception) {
                            report.append("  [Error parsing hours: $it]\n")
                        }
                    }
                }

                if (day.punchIn == null && day.punchOut == null && day.totalHours == null) {
                    report.append("  No data\n")
                }

                report.append("\n")
            }

            report.append("==============================\n")
            report.append("Total Hours: ${String.format("%.1f", totalCalculatedHours)}h\n\n")

            // Show stored data for comparison
            val storedData = getStoredData()
            if (storedData != null) {
                val (storedHours, storedPay, lastUpdate) = storedData
                report.append("Stored Data:\n")
                report.append("Hours: ${String.format("%.1f", storedHours)}h\n")
                report.append("Pay: $${String.format("%.2f", storedPay)}\n")
                report.append("Last Update: $lastUpdate\n\n")
            }

            // Show current work status
            val isWorking = checkIfCurrentlyWorking()
            val today = LocalDateTime.now().dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            report.append("Current Status:\n")
            report.append("Today: $today\n")
            report.append("Currently Working: ${if (isWorking) "YES" else "NO"}\n")
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Hours Report")
            .setMessage(report.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun refreshTimesheetData() {
        try {
            // Stop live updates while refreshing
            stopRealTimeUpdates()

            // Show progress
            showProgress(true)

            // Clear current data
            extractedData.clear()
            currentPayBreakdown = null

            // Hide pay display during refresh
            try {
                payDisplayLayout.visibility = View.GONE
            } catch (e: Exception) {
                // Pay display not available
            }

            // Reload the timesheet page
            if (isLoggedIn) {
                lifecycleScope.launch {
                    delay(1000)
                    extractPunchTimes()
                }
            } else {
                // Need to login again
                startLogin()
            }
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error refreshing timesheet data", e)
            showProgress(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRealTimeUpdates()
    }

    override fun onPause() {
        super.onPause()
        // Keep running in background if working
        if (currentPayBreakdown?.isCurrentlyWorking != true) {
            stopRealTimeUpdates()
        }
    }

    override fun onResume() {
        super.onResume()
        // Restart if we were working
        currentPayBreakdown?.let { payBreakdown ->
            if (payBreakdown.isCurrentlyWorking && !isRealTimeUpdating) {
                startRealTimeUpdates()
            }
        }
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        loginButton.isEnabled = !show
    }

    private fun showLoginForm() {
        isDashboardMode = false
        loginButton.visibility = View.VISIBLE
        dashboardLayout.visibility = View.GONE
        webView.visibility = View.GONE
        showProgress(false)

        // Hide pay display when in login mode
        try {
            payDisplayLayout.visibility = View.GONE
        } catch (e: Exception) {
            // Pay display not available
        }
    }

    private fun showResultsView() {
        loginButton.visibility = View.GONE
        resultsScrollView.visibility = View.VISIBLE
        webView.visibility = View.GONE
    }

    inner class DayforceInterface {
        @JavascriptInterface
        fun logMessage(message: String) {
            Log.d("DayforceJS", message)
        }

        @JavascriptInterface
        fun extractionComplete(data: String) {
            runOnUiThread {
                processExtractedData(data)
            }
        }
    }

    // Dashboard and storage methods
    private fun hasStoredData(): Boolean {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(PREF_STORED_HOURS) && prefs.contains(PREF_STORED_PAY)
    }

    private fun storeCredentials(username: String, password: String) {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("stored_username", username)
            putString("stored_password", password)
            apply()
        }
    }

    private fun getStoredCredentials(): Pair<String, String>? {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val username = prefs.getString("stored_username", null)
        val password = prefs.getString("stored_password", null)
        return if (username != null && password != null) Pair(username, password) else null
    }

    private fun storeTimesheetData(totalHours: Double, totalPay: Double) {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(PREF_STORED_HOURS, totalHours.toString())
            putString(PREF_STORED_PAY, totalPay.toString())
            putLong(PREF_LAST_UPDATE, System.currentTimeMillis())

            // Store daily breakdown as JSON
            try {
                val dailyBreakdownJson = convertExtractedDataToJson(extractedData)
                putString(PREF_DAILY_BREAKDOWN, dailyBreakdownJson)
                Log.d("DayforceTracker", "Stored daily breakdown: $dailyBreakdownJson")
            } catch (e: Exception) {
                Log.e("DayforceTracker", "Error storing daily breakdown", e)
            }

            apply()
        }
    }

    private fun convertExtractedDataToJson(data: List<PunchData>): String {
        val jsonArray = StringBuilder()
        jsonArray.append("[")

        for (i in data.indices) {
            val punchData = data[i]
            jsonArray.append("{")
            jsonArray.append("\"day\":\"${punchData.day}\",")
            jsonArray.append("\"punchIn\":${if (punchData.punchIn != null) "\"${punchData.punchIn}\"" else "null"},")
            jsonArray.append("\"punchOut\":${if (punchData.punchOut != null) "\"${punchData.punchOut}\"" else "null"},")
            jsonArray.append("\"totalHours\":${if (punchData.totalHours != null) "\"${punchData.totalHours}\"" else "null"}")
            jsonArray.append("}")

            if (i < data.size - 1) {
                jsonArray.append(",")
            }
        }

        jsonArray.append("]")
        return jsonArray.toString()
    }

    private fun convertJsonToExtractedData(json: String): List<PunchData> {
        val data = mutableListOf<PunchData>()

        try {
            // Simple JSON parsing (no external library needed)
            val cleanJson = json.trim().removePrefix("[").removeSuffix("]")
            if (cleanJson.isEmpty()) return data

            val items = splitJsonObjects(cleanJson)

            for (item in items) {
                val day = extractJsonField(item, "day")
                val punchIn = extractJsonField(item, "punchIn")
                val punchOut = extractJsonField(item, "punchOut")
                val totalHours = extractJsonField(item, "totalHours")

                if (day != null) {
                    data.add(PunchData(
                        day = day,
                        punchIn = if (punchIn == "null") null else punchIn,
                        punchOut = if (punchOut == "null") null else punchOut,
                        totalHours = if (totalHours == "null") null else totalHours
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error parsing daily breakdown JSON", e)
        }

        return data
    }

    private fun splitJsonObjects(json: String): List<String> {
        val objects = mutableListOf<String>()
        var braceCount = 0
        var currentObject = StringBuilder()

        for (char in json) {
            if (char == '{') {
                if (braceCount == 0) {
                    currentObject = StringBuilder()
                }
                braceCount++
            }

            currentObject.append(char)

            if (char == '}') {
                braceCount--
                if (braceCount == 0) {
                    objects.add(currentObject.toString())
                }
            }
        }

        return objects
    }

    private fun extractJsonField(json: String, fieldName: String): String? {
        val pattern = "\"$fieldName\":\"([^\"]*)\""
        val regex = Regex(pattern)
        val match = regex.find(json)
        return match?.groupValues?.get(1)
    }

    private fun getStoredData(): Triple<Double, Double, String>? {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val hoursStr = prefs.getString(PREF_STORED_HOURS, null)
        val payStr = prefs.getString(PREF_STORED_PAY, null)
        val lastUpdate = prefs.getLong(PREF_LAST_UPDATE, 0)

        return if (hoursStr != null && payStr != null) {
            val hours = hoursStr.toDoubleOrNull() ?: 0.0
            val pay = payStr.toDoubleOrNull() ?: 0.0
            val updateTime = if (lastUpdate > 0) {
                val date = java.util.Date(lastUpdate)
                java.text.SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", java.util.Locale.getDefault()).format(date)
            } else {
                "Unknown"
            }
            Triple(hours, pay, updateTime)
        } else null
    }

    private fun showDashboard() {
        isDashboardMode = true
        loginButton.visibility = View.GONE
        dashboardLayout.visibility = View.VISIBLE
        webView.visibility = View.GONE

        // Load and display stored data
        loadDashboardData()
    }

    private fun loadDashboardData() {
        val storedData = getStoredData()

        // Restore daily breakdown if available
        try {
            val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val dailyBreakdownJson = prefs.getString(PREF_DAILY_BREAKDOWN, null)

            if (dailyBreakdownJson != null) {
                extractedData.clear()
                extractedData.addAll(convertJsonToExtractedData(dailyBreakdownJson))
                Log.d("DayforceTracker", "Restored ${extractedData.size} days of data from storage")
            } else {
                Log.d("DayforceTracker", "No stored daily breakdown found")
            }
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error restoring daily breakdown", e)
        }

        if (storedData != null) {
            val (hours, pay, lastUpdate) = storedData
            displayDashboardData(hours, pay, lastUpdate)
        } else {
            // No stored data - show initial state
            displayDashboardData(0.0, 0.0, "Never")
        }
    }

    private fun displayDashboardData(hours: Double, pay: Double, lastUpdate: String) {
        runOnUiThread {
            try {
                val baseRate = SettingsActivity.Settings.getBasePayRate(this)
                val today = java.time.LocalDate.now().dayOfWeek

                // Update pay display
                totalEarningsText.text = "$${String.format("%.2f", pay)}"

                // Update hours info
                val regularHours = kotlin.math.min(hours, 40.0)
                val overtimeHours = kotlin.math.max(0.0, hours - 40.0)

                val hoursText = if (overtimeHours > 0) {
                    "Total: ${String.format("%.1f", hours)}h (${String.format("%.1f", regularHours)}h reg + ${String.format("%.1f", overtimeHours)}h OT)"
                } else {
                    val remaining = 40.0 - hours
                    "Total: ${String.format("%.1f", hours)}h | ${String.format("%.1f", remaining)}h until overtime"
                }
                hoursInfoText.text = hoursText

                // Hide rate display on dashboard
                currentRateText.visibility = View.GONE

                // Show last update time
                totalHoursText.text = "Last updated: $lastUpdate"


                // Update motivational display
                updateMotivationalDisplay(hours, pay, baseRate, today)

                // Hide live counter and WebView results
                liveCounterText.visibility = View.GONE
                resultsText.visibility = View.GONE

                // Show pay display
                payDisplayLayout.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e("DayforceTracker", "Error displaying dashboard data", e)
            }
        }
    }

    private fun updateMotivationalDisplay(weeklyHours: Double, weeklyPay: Double, baseRate: Double, today: java.time.DayOfWeek) {
        try {
            // Today's goal
            val todayGoal = SettingsActivity.Settings.getDailyGoal(this, today)
            dailyGoalText.text = "${String.format("%.1f", todayGoal)} hours"

            // Calculate actual daily pay for today
            val dailyPay = calculateTodaysActualPay(weeklyHours, baseRate, today)
            weeklyProgressText.text = "$${String.format("%.2f", weeklyPay)}"
            dailyProgressText.text = "$${String.format("%.2f", dailyPay)}"
            
            // Calculate weekly goal discrepancy (only count completed days)
            val goalDiscrepancy = calculateWeeklyGoalDiscrepancy(weeklyHours, today)
            val discrepancyText = if (goalDiscrepancy >= 0) {
                if (goalDiscrepancy == 0.0) {
                    "On track with weekly goals"
                } else {
                    "+${String.format("%.1f", goalDiscrepancy)}h ahead of goals"
                }
            } else {
                "${String.format("%.1f", goalDiscrepancy)}h behind goals"
            }
            weeklyGoalDiscrepancyText.text = discrepancyText
            
            // Set color based on over/under
            if (goalDiscrepancy >= 0) {
                weeklyGoalDiscrepancyText.setTextColor(android.graphics.Color.parseColor("#10B981"))
                weeklyGoalDiscrepancyText.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F0FDF4"))
            } else {
                weeklyGoalDiscrepancyText.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                weeklyGoalDiscrepancyText.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FEF2F2"))
            }

            // Calculate projected overtime for the week based on normal 8h/day schedule
            val currentWeeklyHours = weeklyHours
            val todayDayOfWeek = today.value // 1=Monday, 7=Sunday
            val remainingDaysInWeek = 7 - todayDayOfWeek

            // Calculate additional pay from remaining work days (using goal hours)
            var remainingHours = 0.0
            for (dayOffset in 1..remainingDaysInWeek) {
                val futureDay = today.plus(dayOffset.toLong())
                remainingHours += SettingsActivity.Settings.getDailyGoal(this, futureDay)
            }
            val currentOvertimeHours = kotlin.math.max(0.0, currentWeeklyHours - 40.0)

            // Calculate additional pay considering Sunday premium (2x) vs overtime (1.5x)
            var additionalPay = 0.0
            var additionalRegularPay = 0.0

            // Check each remaining day
            var totalAdditionalHours = 0.0
            for (dayOffset in 1..remainingDaysInWeek) {
                val futureDay = today.plus(dayOffset.toLong())
                val hoursForDay = SettingsActivity.Settings.getDailyGoal(this, futureDay)
                totalAdditionalHours += hoursForDay

                if (futureDay == java.time.DayOfWeek.SUNDAY) {
                    // Sunday is always 2x rate
                    additionalPay += hoursForDay * (baseRate * 2.0)
                } else {
                    // Check if these hours would be regular or overtime
                    // Calculate hours from previous days in this projection
                    var hoursFromPreviousDays = 0.0
                    for (prevDayOffset in 1 until dayOffset) {
                        val prevDay = today.plus(prevDayOffset.toLong())
                        hoursFromPreviousDays += SettingsActivity.Settings.getDailyGoal(this, prevDay)
                    }
                    val hoursWorkedBeforeThisDay = currentWeeklyHours + hoursFromPreviousDays
                    val regularHoursAvailable = kotlin.math.max(0.0, 40.0 - hoursWorkedBeforeThisDay)
                    val regularHoursThisDay = kotlin.math.min(hoursForDay, regularHoursAvailable)
                    val overtimeHoursThisDay = hoursForDay - regularHoursThisDay

                    additionalRegularPay += regularHoursThisDay * baseRate
                    additionalPay += regularHoursThisDay * baseRate + overtimeHoursThisDay * (baseRate * 1.5)
                }
            }

            // Total projected pay and overtime pay
            val projectedTotalPay = weeklyPay + additionalPay
            val regularPayPortion = 40.0 * baseRate
            val projectedOvertimePay = projectedTotalPay - regularPayPortion
            val projectedOvertimeHours = kotlin.math.max(0.0, (currentWeeklyHours + totalAdditionalHours) - 40.0)

            projectedOvertimeText.text = "${String.format("%.1f", projectedOvertimeHours)}h = $${String.format("%.2f", projectedOvertimePay)}"

            // Calculate and show total projected pay for the week
            val projectedTotalPayForWeek = projectedTotalPay
            projectedTotalPayText.text = "$${String.format("%.2f", projectedTotalPayForWeek)}"

            // Loss calculator (only show if currently working - will be updated in real-time)
            lossCalculatorLayout.visibility = View.GONE

        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error updating motivational display", e)
        }
    }

    private fun calculateTodaysActualPay(weeklyHours: Double, baseRate: Double, today: java.time.DayOfWeek): Double {
        try {
            // First try to find today's data in extracted timesheet
            val todayName = today.name.lowercase().replaceFirstChar { it.uppercase() }

            Log.d("DayforceTracker", "=== DAILY PAY DEBUG ===")
            Log.d("DayforceTracker", "Looking for day: '$todayName'")
            Log.d("DayforceTracker", "extractedData size: ${extractedData.size}")

            for (data in extractedData) {
                Log.d("DayforceTracker", "Available day: '${data.day}' - Hours: '${data.totalHours}'")
            }

            // Try multiple ways to find today's data
            val todayValue = today.value // 1=Monday, 6=Saturday, 7=Sunday

            val todayData = extractedData.find { data ->
                when (today) {
                    java.time.DayOfWeek.SATURDAY -> {
                        // Saturday is stored as "Day 6"
                        data.day.equals("Day 6", ignoreCase = true) ||
                                data.day.equals("Saturday", ignoreCase = true) ||
                                data.day.equals("6", ignoreCase = true)
                    }
                    java.time.DayOfWeek.SUNDAY -> {
                        // Sunday might be "Day 7" or "Sunday" - check both
                        data.day.equals("Day 7", ignoreCase = true) ||
                                data.day.equals("Sunday", ignoreCase = true) ||
                                data.day.equals("7", ignoreCase = true)
                    }
                    else -> {
                        // Monday-Friday use day names
                        data.day.equals(todayName, ignoreCase = true) ||
                                data.day.startsWith(todayName.take(3), ignoreCase = true)
                    }
                }
            }

            Log.d("DayforceTracker", "Today value: $todayValue")
            Log.d("DayforceTracker", "Found today's data: ${todayData != null}")
            if (todayData != null) {
                Log.d("DayforceTracker", "Matched day: '${todayData.day}' with hours: '${todayData.totalHours}'")
            }

            var todayHours = 0.0

            if (todayData?.totalHours != null) {
                // Parse today's hours from extracted data
                val todayHoursStr = todayData.totalHours
                if (todayHoursStr.contains("h")) {
                    try {
                        todayHours = todayHoursStr.replace("h", "").trim().toDouble()
                    } catch (e: Exception) {
                        Log.e("DayforceTracker", "Error parsing today's hours: $todayHoursStr", e)
                    }
                }
            } else {
                // Fallback: if no extracted data, we can't calculate actual daily pay
                Log.d("DayforceTracker", "No extracted data for today ($todayName), cannot calculate accurate daily pay")

                // Return 0 instead of wrong estimate - user should refresh to get accurate data
                return 0.0
            }

            if (todayHours > 0) {
                // Calculate pay based on day and current weekly status
                return when (today) {
                    java.time.DayOfWeek.SUNDAY -> {
                        // Sunday is always 2x rate
                        todayHours * (baseRate * 2.0)
                    }
                    else -> {
                        // Calculate if today's hours are regular or overtime
                        val hoursBeforeToday = weeklyHours - todayHours
                        val regularHoursAvailable = kotlin.math.max(0.0, 40.0 - hoursBeforeToday)
                        val regularHoursToday = kotlin.math.min(todayHours, regularHoursAvailable)
                        val overtimeHoursToday = todayHours - regularHoursToday

                        val regularPay = regularHoursToday * baseRate
                        val overtimePay = overtimeHoursToday * (baseRate * 1.5)
                        regularPay + overtimePay
                    }
                }
            }

            return 0.0
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error calculating today's pay", e)
            return 0.0
        }
    }

    private fun updateMotivationalDisplayLive(weeklyPay: Double, todayPay: Double, baseRate: Double, today: java.time.DayOfWeek, potentialLoss: Double, shouldShowLoss: Boolean) {
        try {
            // Today's goal (doesn't change)
            val todayGoal = SettingsActivity.Settings.getDailyGoal(this, today)
            dailyGoalText.text = "${String.format("%.1f", todayGoal)} hours"

            // Live weekly and daily earnings
            weeklyProgressText.text = "$${String.format("%.2f", weeklyPay)}"
            dailyProgressText.text = "$${String.format("%.2f", todayPay)}"

            // Calculate projected overtime for the week based on normal 8h/day schedule
            // For live updates, estimate current weekly hours from weekly pay
            val todayDayOfWeek = today.value // 1=Monday, 7=Sunday
            val remainingDaysInWeek = 7 - todayDayOfWeek

            // Calculate additional pay from remaining work days (using goal hours)
            var remainingHours = 0.0
            for (dayOffset in 1..remainingDaysInWeek) {
                val futureDay = today.plus(dayOffset.toLong())
                remainingHours += SettingsActivity.Settings.getDailyGoal(this, futureDay)
            }

            // Estimate current weekly hours from pay
            val estimatedWeeklyHours = if (weeklyPay > (40.0 * baseRate)) {
                // Has overtime, so calculate: 40 regular hours + overtime hours at 1.5x rate
                val regularPay = 40.0 * baseRate
                val overtimePay = weeklyPay - regularPay
                val overtimeHours = overtimePay / (baseRate * 1.5)
                40.0 + overtimeHours
            } else {
                // No overtime yet
                weeklyPay / baseRate
            }

            // Calculate additional pay considering Sunday premium (2x) vs overtime (1.5x)
            var additionalPay = 0.0
            var totalAdditionalHours = 0.0

            // Check each remaining day
            for (dayOffset in 1..remainingDaysInWeek) {
                val futureDay = today.plus(dayOffset.toLong())
                val hoursForDay = SettingsActivity.Settings.getDailyGoal(this, futureDay)
                totalAdditionalHours += hoursForDay

                if (futureDay == java.time.DayOfWeek.SUNDAY) {
                    // Sunday is always 2x rate
                    additionalPay += hoursForDay * (baseRate * 2.0)
                } else {
                    // Check if these hours would be regular or overtime
                    // Calculate hours from previous days in this projection
                    var hoursFromPreviousDays = 0.0
                    for (prevDayOffset in 1 until dayOffset) {
                        val prevDay = today.plus(prevDayOffset.toLong())
                        hoursFromPreviousDays += SettingsActivity.Settings.getDailyGoal(this, prevDay)
                    }
                    val hoursWorkedBeforeThisDay = estimatedWeeklyHours + hoursFromPreviousDays
                    val regularHoursAvailable = kotlin.math.max(0.0, 40.0 - hoursWorkedBeforeThisDay)
                    val regularHoursThisDay = kotlin.math.min(hoursForDay, regularHoursAvailable)
                    val overtimeHoursThisDay = hoursForDay - regularHoursThisDay

                    additionalPay += regularHoursThisDay * baseRate + overtimeHoursThisDay * (baseRate * 1.5)
                }
            }

            // Total projected pay and overtime pay
            val projectedTotalPay = weeklyPay + additionalPay
            val regularPayPortion = 40.0 * baseRate
            val projectedOvertimePay = projectedTotalPay - regularPayPortion
            val projectedOvertimeHours = kotlin.math.max(0.0, (estimatedWeeklyHours + totalAdditionalHours) - 40.0)

            projectedOvertimeText.text = "${String.format("%.1f", projectedOvertimeHours)}h = $${String.format("%.2f", projectedOvertimePay)}"

            // Calculate and show total projected pay for the week
            projectedTotalPayText.text = "$${String.format("%.2f", projectedTotalPay)}"

            // Loss calculator (show while working and haven't reached goal)
            if (shouldShowLoss) {
                lossCalculatorText.text = "Clock out now: -$${String.format("%.2f", potentialLoss)}"
                lossCalculatorLayout.visibility = View.VISIBLE
            } else {
                lossCalculatorLayout.visibility = View.GONE
            }

        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error updating live motivational display", e)
        }
    }
    
    private fun calculateWeeklyGoalDiscrepancy(totalWeeklyHours: Double, currentDay: java.time.DayOfWeek): Double {
        try {
            val currentDayValue = currentDay.value // 1=Monday, 7=Sunday
            var completedDaysGoalTotal = 0.0
            var actualHoursFromCompletedDays = 0.0
            
            // List of all days in order
            val daysOfWeek = listOf(
                java.time.DayOfWeek.MONDAY,
                java.time.DayOfWeek.TUESDAY, 
                java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.THURSDAY,
                java.time.DayOfWeek.FRIDAY,
                java.time.DayOfWeek.SATURDAY,
                java.time.DayOfWeek.SUNDAY
            )
            
            // Check each day to see if it's completed (has both punch in and punch out)
            for (day in daysOfWeek) {
                val dayValue = day.value
                val dayGoal = SettingsActivity.Settings.getDailyGoal(this, day)
                
                // Only count days that have already passed or are completed
                val isDayCompleted = isDayCompleted(day)
                val isDayInPast = dayValue < currentDayValue
                
                if (isDayCompleted || isDayInPast) {
                    // Add this day's goal to the total we should have achieved
                    completedDaysGoalTotal += dayGoal
                    
                    // Add actual hours worked for this day
                    val actualHoursForDay = getActualHoursForDay(day)
                    actualHoursFromCompletedDays += actualHoursForDay
                    
                    Log.d("DayforceTracker", "${day.name}: Goal=${dayGoal}h, Actual=${actualHoursForDay}h, Completed=${isDayCompleted}, Past=${isDayInPast}")
                }
            }
            
            // The discrepancy is actual hours minus what we should have achieved by now
            val discrepancy = actualHoursFromCompletedDays - completedDaysGoalTotal
            
            Log.d("DayforceTracker", "Goal Discrepancy Calculation:")
            Log.d("DayforceTracker", "  Completed days goal total: ${completedDaysGoalTotal}h")
            Log.d("DayforceTracker", "  Actual hours from completed days: ${actualHoursFromCompletedDays}h")
            Log.d("DayforceTracker", "  Discrepancy: ${discrepancy}h")
            
            return discrepancy
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error calculating weekly goal discrepancy", e)
            return 0.0
        }
    }
    
    private fun isDayCompleted(day: java.time.DayOfWeek): Boolean {
        try {
            val dayName = day.name.lowercase().replaceFirstChar { it.uppercase() }
            
            // Find this day's data in extracted timesheet
            val dayData = extractedData.find { data ->
                when (day) {
                    java.time.DayOfWeek.SATURDAY -> {
                        data.day.equals("Day 6", ignoreCase = true) ||
                                data.day.equals("Saturday", ignoreCase = true)
                    }
                    java.time.DayOfWeek.SUNDAY -> {
                        data.day.equals("Day 7", ignoreCase = true) ||
                                data.day.equals("Sunday", ignoreCase = true)
                    }
                    else -> {
                        data.day.equals(dayName, ignoreCase = true)
                    }
                }
            }
            
            // A day is considered completed if it has both punch in AND punch out
            val hasPunchIn = !dayData?.punchIn.isNullOrEmpty()
            val hasPunchOut = !dayData?.punchOut.isNullOrEmpty()
            val isCompleted = hasPunchIn && hasPunchOut
            
            Log.d("DayforceTracker", "${day.name} completion check: PunchIn=${hasPunchIn}, PunchOut=${hasPunchOut}, Completed=${isCompleted}")
            
            return isCompleted
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error checking if day is completed", e)
            return false
        }
    }
    
    private fun getActualHoursForDay(day: java.time.DayOfWeek): Double {
        try {
            val dayName = day.name.lowercase().replaceFirstChar { it.uppercase() }
            
            // Find this day's data in extracted timesheet
            val dayData = extractedData.find { data ->
                when (day) {
                    java.time.DayOfWeek.SATURDAY -> {
                        data.day.equals("Day 6", ignoreCase = true) ||
                                data.day.equals("Saturday", ignoreCase = true)
                    }
                    java.time.DayOfWeek.SUNDAY -> {
                        data.day.equals("Day 7", ignoreCase = true) ||
                                data.day.equals("Sunday", ignoreCase = true)
                    }
                    else -> {
                        data.day.equals(dayName, ignoreCase = true)
                    }
                }
            }
            
            // Parse hours from the totalHours string
            if (dayData?.totalHours != null && dayData.totalHours.contains("h")) {
                try {
                    val hoursStr = dayData.totalHours.replace("h", "").trim()
                    return hoursStr.toDoubleOrNull() ?: 0.0
                } catch (e: Exception) {
                    Log.e("DayforceTracker", "Error parsing hours for ${day.name}: ${dayData.totalHours}", e)
                }
            }
            
            return 0.0
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error getting actual hours for ${day.name}", e)
            return 0.0
        }
    }
    
    private fun calculateLiveDailyEarnings(todayHoursWorked: Double): Double {
        try {
            val baseRate = SettingsActivity.Settings.getBasePayRate(this)
            val today = LocalDateTime.now().dayOfWeek
            
            // Get hours worked before today from the week
            val currentPayBreakdown = this.currentPayBreakdown
            if (currentPayBreakdown != null) {
                val totalWeeklyHours = currentPayBreakdown.regularHours + currentPayBreakdown.overtimeHours + currentPayBreakdown.sundayPremiumHours
                val hoursBeforeToday = totalWeeklyHours - todayHoursWorked
                
                return when (today) {
                    java.time.DayOfWeek.SUNDAY -> {
                        // Sunday is always 2x rate
                        todayHoursWorked * (baseRate * 2.0)
                    }
                    else -> {
                        // Calculate if today's hours are regular or overtime
                        val regularHoursAvailable = kotlin.math.max(0.0, 40.0 - hoursBeforeToday)
                        val regularHoursToday = kotlin.math.min(todayHoursWorked, regularHoursAvailable)
                        val overtimeHoursToday = kotlin.math.max(0.0, todayHoursWorked - regularHoursToday)
                        
                        val regularPay = regularHoursToday * baseRate
                        val overtimePay = overtimeHoursToday * (baseRate * 1.5)
                        regularPay + overtimePay
                    }
                }
            } else {
                // Fallback calculation
                return todayHoursWorked * baseRate
            }
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error calculating live daily earnings", e)
            return 0.0
        }
    }
    
    private fun updateLiveDailyTracking(hoursWorked: Double, earnings: Double, goalHours: Double, today: java.time.DayOfWeek) {
        try {
            // Show the live daily tracking card only when actively working
            liveDailyTrackingCard.visibility = View.VISIBLE
            
            // Update goal display
            liveDailyGoalText.text = "${String.format("%.1f", goalHours)} hours"
            
            // Update live earnings (updated every second)
            liveDailyEarningsText.text = "$${String.format("%.2f", earnings)}"
            
            // Calculate and display daily discrepancy
            val hoursDifference = hoursWorked - goalHours
            val discrepancyText = if (hoursDifference >= 0) {
                if (hoursDifference == 0.0) {
                    "Right on goal!"
                } else {
                    "+${String.format("%.1f", hoursDifference)}h ahead"
                }
            } else {
                "${String.format("%.1f", hoursDifference)}h behind"
            }
            
            liveDailyDiscrepancyText.text = discrepancyText
            
            // Set color based on over/under goal
            if (hoursDifference >= 0) {
                liveDailyDiscrepancyText.setTextColor(android.graphics.Color.parseColor("#10B981"))
                liveDailyDiscrepancyText.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F0FDF4"))
            } else {
                liveDailyDiscrepancyText.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                liveDailyDiscrepancyText.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FEF2F2"))
            }
            
            Log.d("DayforceTracker", "Live Daily Tracking - Hours: ${hoursWorked}, Goal: ${goalHours}, Earnings: ${earnings}, Difference: ${hoursDifference}")
            
        } catch (e: Exception) {
            Log.e("DayforceTracker", "Error updating live daily tracking", e)
        }
    }

    private fun updateRateDisplay(baseRate: Double) {
        // Always show rate since user is already authenticated
        val rateText = "Base Rate: $${String.format("%.2f", baseRate)}/hr"
        currentRateText.text = rateText
    }

    private fun startDayforceFetch() {
        val credentials = getStoredCredentials()
        if (credentials == null) {
            Toast.makeText(this, "No stored credentials. Please login again.", Toast.LENGTH_SHORT).show()
            showLoginForm()
            return
        }

        val (username, password) = credentials

        // Show progress
        showProgress(true)

        // Credentials are hardcoded in handleLoginPage() method
        // Start the actual Dayforce fetch
        startLogin()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isDashboardMode) {
            // Allow back press to close app from dashboard
            super.onBackPressed()
        } else {
            // From login or results, go back to dashboard if we have data
            if (hasStoredData()) {
                showDashboard()
            } else {
                super.onBackPressed()
            }
        }
    }
}