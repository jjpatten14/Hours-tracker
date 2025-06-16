package com.dayforcetracker

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

data class WorkDay(
    val dayOfWeek: DayOfWeek,
    val punchIn: String?,
    val punchOut: String?,
    val hours: Double
)

data class PayBreakdown(
    val regularHours: Double,
    val overtimeHours: Double,
    val sundayPremiumHours: Double,
    val regularPay: Double,
    val overtimePay: Double,
    val sundayPremiumPay: Double,
    val totalPay: Double,
    val currentHourlyRate: Double,
    val isCurrentlyWorking: Boolean,
    val hoursUntilOvertime: Double,
    val nextRateTier: String
)

class PayCalculator(val baseHourlyRate: Double) {
    
    companion object {
        const val OVERTIME_THRESHOLD = 40.0
        const val OVERTIME_MULTIPLIER = 1.5
        const val SUNDAY_PREMIUM_MULTIPLIER = 2.0
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")
    }
    
    fun calculatePay(workDays: List<WorkDay>): PayBreakdown {
        var totalRegularHours = 0.0
        var totalOvertimeHours = 0.0
        var totalSundayPremiumHours = 0.0
        var cumulativeHours = 0.0
        
        android.util.Log.d("PayCalculator", "=== PAY CALCULATION DEBUG ===")
        android.util.Log.d("PayCalculator", "Base hourly rate: $baseHourlyRate")
        android.util.Log.d("PayCalculator", "Processing ${workDays.size} work days")
        
        // Sort days by order (Monday first)
        val sortedDays = workDays.sortedBy { it.dayOfWeek.value }
        
        for (day in sortedDays) {
            val dayHours = day.hours
            android.util.Log.d("PayCalculator", "${day.dayOfWeek.name}: ${dayHours}h (cumulative: ${cumulativeHours}h)")
            
            if (day.dayOfWeek == DayOfWeek.SUNDAY) {
                // Sunday is ALWAYS 2x rate, regardless of 40h threshold
                totalSundayPremiumHours += dayHours
            } else {
                // Non-Sunday: regular hours until 40, then overtime
                val hoursBeforeOvertime = max(0.0, OVERTIME_THRESHOLD - cumulativeHours)
                val regularDayHours = kotlin.math.min(dayHours, hoursBeforeOvertime)
                val overtimeDayHours = max(0.0, dayHours - regularDayHours)
                
                totalRegularHours += regularDayHours
                totalOvertimeHours += overtimeDayHours
            }
            
            cumulativeHours += dayHours
        }
        
        // Calculate pay amounts
        val regularPay = totalRegularHours * baseHourlyRate
        val overtimePay = totalOvertimeHours * (baseHourlyRate * OVERTIME_MULTIPLIER)
        val sundayPremiumPay = totalSundayPremiumHours * (baseHourlyRate * SUNDAY_PREMIUM_MULTIPLIER)
        val totalPay = regularPay + overtimePay + sundayPremiumPay
        
        android.util.Log.d("PayCalculator", "=== FINAL CALCULATION ===")
        android.util.Log.d("PayCalculator", "Regular: ${totalRegularHours}h × $${baseHourlyRate} = $${regularPay}")
        android.util.Log.d("PayCalculator", "Overtime: ${totalOvertimeHours}h × $${baseHourlyRate * OVERTIME_MULTIPLIER} = $${overtimePay}")
        android.util.Log.d("PayCalculator", "Sunday Premium: ${totalSundayPremiumHours}h × $${baseHourlyRate * SUNDAY_PREMIUM_MULTIPLIER} = $${sundayPremiumPay}")
        android.util.Log.d("PayCalculator", "TOTAL PAY: $${totalPay}")
        
        // Determine current status
        val isCurrentlyWorking = isCurrentlyAtWork(workDays)
        val currentRate = getCurrentHourlyRate(cumulativeHours)
        val hoursUntilOvertime = max(0.0, OVERTIME_THRESHOLD - cumulativeHours)
        val nextTier = getNextRateTier(cumulativeHours)
        
        return PayBreakdown(
            regularHours = totalRegularHours,
            overtimeHours = totalOvertimeHours,
            sundayPremiumHours = totalSundayPremiumHours,
            regularPay = regularPay,
            overtimePay = overtimePay,
            sundayPremiumPay = sundayPremiumPay,
            totalPay = totalPay,
            currentHourlyRate = currentRate,
            isCurrentlyWorking = isCurrentlyWorking,
            hoursUntilOvertime = hoursUntilOvertime,
            nextRateTier = nextTier
        )
    }
    
    private fun isCurrentlyAtWork(workDays: List<WorkDay>): Boolean {
        val today = java.time.LocalDate.now().dayOfWeek
        val currentTime = LocalTime.now()
        
        val todayWork = workDays.find { it.dayOfWeek == today }
        
        return if (todayWork?.punchIn != null && todayWork.punchOut == null) {
            // Punched in but not out yet
            true
        } else if (todayWork?.punchIn != null && todayWork.punchOut != null) {
            // Both punch in and out exist - check if currently between them
            try {
                val punchInTime = LocalTime.parse(todayWork.punchIn, TIME_FORMATTER)
                val punchOutTime = LocalTime.parse(todayWork.punchOut, TIME_FORMATTER)
                currentTime.isAfter(punchInTime) && currentTime.isBefore(punchOutTime)
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }
    
    private fun getCurrentHourlyRate(totalHours: Double): Double {
        return when {
            java.time.LocalDate.now().dayOfWeek == DayOfWeek.SUNDAY && totalHours >= OVERTIME_THRESHOLD -> {
                baseHourlyRate * SUNDAY_PREMIUM_MULTIPLIER
            }
            totalHours >= OVERTIME_THRESHOLD -> {
                baseHourlyRate * OVERTIME_MULTIPLIER
            }
            else -> {
                baseHourlyRate
            }
        }
    }
    
    private fun getNextRateTier(totalHours: Double): String {
        return when {
            totalHours < OVERTIME_THRESHOLD -> {
                val hoursRemaining = OVERTIME_THRESHOLD - totalHours
                "Overtime (${String.format("%.1f", hoursRemaining)}h remaining)"
            }
            java.time.LocalDate.now().dayOfWeek != DayOfWeek.SUNDAY -> "Sunday Premium (work Sunday for 2x rate)"
            else -> "Maximum rate achieved"
        }
    }
    
    fun calculateLiveEarnings(payBreakdown: PayBreakdown, secondsWorkedToday: Long): Double {
        if (!payBreakdown.isCurrentlyWorking) return payBreakdown.totalPay
        
        val additionalHours = secondsWorkedToday / 3600.0
        val additionalPay = additionalHours * payBreakdown.currentHourlyRate
        
        return payBreakdown.totalPay + additionalPay
    }
    
    fun getEarningsPerSecond(hourlyRate: Double): Double {
        return hourlyRate / 3600.0
    }
    
    fun formatCurrency(amount: Double): String {
        return "$${String.format("%.2f", amount)}"
    }
    
    fun formatHours(hours: Double): String {
        return "${String.format("%.2f", hours)}h"
    }
}