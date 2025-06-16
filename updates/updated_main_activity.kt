// Add these imports to your existing MainActivity.kt imports
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// Add these properties to your MainActivity class
class MainActivity : AppCompatActivity() {
    
    // Existing properties...
    private lateinit var webView: WebView
    
    // New pay calculator properties
    private lateinit var payCalculator: PayCalculator
    private lateinit var payDisplayLayout: LinearLayout
    private lateinit var totalEarningsText: TextView
    private lateinit var liveCounterText: TextView
    private lateinit var currentRateText: TextView
    private lateinit var hoursInfoText: TextView
    private lateinit var settingsButton: Button
    private lateinit var refreshButton: Button
    private lateinit var detailsButton: Button
    
    private var isRealTimeUpdating = false
    private var liveUpdateHandler: Handler? = null
    private var liveUpdateRunnable: Runnable? = null
    private var workStartTime: LocalDateTime? = null
    private var currentPayBreakdown: PayBreakdown? = null
    
    companion object {
        private const val SETTINGS_REQUEST_CODE = 1001
        private const val LIVE_UPDATE_INTERVAL_MS = 1000L // Update every second
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize views (existing + new)
        initializeViews()
        
        // Check if pay settings are configured
        checkPaySettings()
        
        // Initialize WebView (your existing code)
        setupWebView()
        
        // Set up button listeners
        setupButtonListeners()
    }
    
    private fun initializeViews() {
        // Existing WebView
        webView = findViewById(R.id.webView)
        
        // New pay calculator UI elements
        payDisplayLayout = findViewById(R.id.payDisplayLayout)
        totalEarningsText = findViewById(R.id.totalEarningsText)
        liveCounterText = findViewById(R.id.liveCounterText)
        currentRateText = findViewById(R.id.currentRateText)
        hoursInfoText = findViewById(R.id.hoursInfoText)
        settingsButton = findViewById(R.id.settingsButton)
        refreshButton = findViewById(R.id.refreshButton)
        detailsButton = findViewById(R.id.detailsButton)
        
        // Initially hide pay display until data is loaded
        payDisplayLayout.visibility = View.GONE
    }
    
    private fun checkPaySettings() {
        if (!SettingsActivity.Settings.isSettingsConfigured(this)) {
            // First time - show settings
            showFirstTimeSetup()
        } else {
            // Settings exist - initialize calculator
            initializePayCalculator()
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
                payDisplayLayout.visibility = View.GONE
            }
            .setCancelable(false)
            .show()
    }
    
    private fun initializePayCalculator() {
        val basePayRate = SettingsActivity.Settings.getBasePayRate(this)
        payCalculator = PayCalculator(basePayRate)
        payDisplayLayout.visibility = View.VISIBLE
    }
    
    private fun setupButtonListeners() {
        settingsButton.setOnClickListener {
            openSettings()
        }
        
        refreshButton.setOnClickListener {
            // Your existing refresh logic + recalculate pay
            refreshTimesheetData()
        }
        
        detailsButton.setOnClickListener {
            showDetailedBreakdown()
        }
    }
    
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivityForResult(intent, SETTINGS_REQUEST_CODE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK) {
            // Settings were saved - reinitialize calculator
            initializePayCalculator()
            // Recalculate if we have timesheet data
            currentPayBreakdown?.let { updatePayDisplay(it) }
        }
    }
    
    // Call this method after your existing timesheet extraction is complete
    private fun onTimesheetDataExtracted(extractedData: String) {
        // Your existing display logic...
        
        // Parse timesheet data into WorkDay objects
        val workDays = parseTimesheetToWorkDays(extractedData)
        
        // Calculate pay if calculator is initialized
        if (::payCalculator.isInitialized) {
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
    }
    
    private fun parseTimesheetToWorkDays(extractedData: String): List<WorkDay> {
        val workDays = mutableListOf<WorkDay>()
        val lines = extractedData.split("\n")
        
        val daysOfWeek = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        )
        
        for ((index, day) in daysOfWeek.withIndex()) {
            var punchIn: String? = null
            var punchOut: String? = null
            var hours = 0.0
            
            // Parse the extracted data for this day
            val dayName = day.name.lowercase().replaceFirstChar { it.uppercase() }
            
            // Look for punch in/out times and hours for this day
            for (line in lines) {
                when {
                    line.contains("$dayName Punch In:") -> {
                        punchIn = line.substringAfter("$dayName Punch In:").trim()
                        if (punchIn.isBlank() || punchIn == "null") punchIn = null
                    }
                    line.contains("$dayName Punch Out:") -> {
                        punchOut = line.substringAfter("$dayName Punch Out:").trim()
                        if (punchOut.isBlank() || punchOut == "null") punchOut = null
                    }
                    line.contains("$dayName Hours:") -> {
                        val hoursStr = line.substringAfter("$dayName Hours:").trim().removeSuffix("h")
                        hours = hoursStr.toDoubleOrNull() ?: 0.0
                    }
                }
            }
            
            workDays.add(WorkDay(day, punchIn, punchOut, hours))
        }
        
        return workDays
    }
    
    private fun updatePayDisplay(payBreakdown: PayBreakdown) {
        runOnUiThread {
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
            currentRateText.text = rateName
            
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
    }
    
    private fun stopRealTimeUpdates() {
        isRealTimeUpdating = false
        liveUpdateRunnable?.let { liveUpdateHandler?.removeCallbacks(it) }
        liveUpdateHandler = null
        liveUpdateRunnable = null
        workStartTime = null
    }
    
    private fun updateLiveEarnings() {
        val payBreakdown = currentPayBreakdown ?: return
        val startTime = workStartTime ?: return
        
        if (!payBreakdown.isCurrentlyWorking) {
            stopRealTimeUpdates()
            return
        }
        
        val secondsWorked = ChronoUnit.SECONDS.between(startTime, LocalDateTime.now())
        val liveTotal = payCalculator.calculateLiveEarnings(payBreakdown, secondsWorked)
        val earningsPerSecond = payCalculator.getEarningsPerSecond(payBreakdown.currentHourlyRate)
        val earningsPerMinute = earningsPerSecond * 60
        
        runOnUiThread {
            totalEarningsText.text = payCalculator.formatCurrency(liveTotal)
            liveCounterText.text = "⏱️ Live: +${payCalculator.formatCurrency(earningsPerMinute)}/min"
        }
    }
    
    private fun showDetailedBreakdown() {
        val payBreakdown = currentPayBreakdown ?: return
        
        val breakdown = StringBuilder()
        breakdown.append("💰 Detailed Pay Breakdown\n\n")
        
        // Regular hours
        if (payBreakdown.regularHours > 0) {
            breakdown.append("Regular Hours: ${payCalculator.formatHours(payBreakdown.regularHours)}\n")
            breakdown.append("Regular Pay: ${payCalculator.formatCurrency(payBreakdown.regularPay)}\n\n")
        }
        
        // Overtime hours
        if (payBreakdown.overtimeHours > 0) {
            breakdown.append("Overtime Hours: ${payCalculator.formatHours(payBreakdown.overtimeHours)}\n")
            breakdown.append("Overtime Pay (1.5x): ${payCalculator.formatCurrency(payBreakdown.overtimePay)}\n\n")
        }
        
        // Sunday premium hours
        if (payBreakdown.sundayPremiumHours > 0) {
            breakdown.append("Sunday Premium Hours: ${payCalculator.formatHours(payBreakdown.sundayPremiumHours)}\n")
            breakdown.append("Sunday Premium Pay (2x): ${payCalculator.formatCurrency(payBreakdown.sundayPremiumPay)}\n\n")
        }
        
        breakdown.append("📊 TOTAL: ${payCalculator.formatCurrency(payBreakdown.totalPay)}\n\n")
        
        // Current status
        if (payBreakdown.isCurrentlyWorking) {
            breakdown.append("🟢 Currently Working\n")
            breakdown.append("Current Rate: ${SettingsActivity.Settings.formatPayRate(payBreakdown.currentHourlyRate)}\n")
        } else {
            breakdown.append("🔴 Not Currently Working\n")
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
    
    private fun refreshTimesheetData() {
        // Stop live updates while refreshing
        stopRealTimeUpdates()
        
        // Your existing refresh logic here...
        // After refresh completes, call onTimesheetDataExtracted() again
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
    
    // Your existing methods continue here...
}
            