# DAYFORCE PAY CALCULATOR INTEGRATION GUIDE

## OVERVIEW
This guide explains how to integrate the real-time pay calculator functionality into the existing Dayforce Tracker Android app. The pay calculator builds on the working timesheet extraction to add live earnings tracking.

## PROJECT STATUS BEFORE INTEGRATION
✅ **EXISTING FOUNDATION (Already Working)**:
- MainActivity.kt extracts timesheet data from Dayforce
- WebView successfully navigates and pulls punch times
- Accurate timesheet parsing (27.1h matches Python script)
- Basic UI showing timesheet data

## WHAT WE'RE ADDING
🚀 **NEW PAY CALCULATOR FEATURES**:
- Real-time earnings display with live counter
- Pay rate settings screen
- Overtime/Sunday premium calculations
- Live updates every second while working
- Detailed pay breakdown

---

## FILE PLACEMENT INSTRUCTIONS

### 1. NEW KOTLIN FILES (Add to app/src/main/java/com/dayforcetracker/)

#### A) PayCalculator.kt
- **Purpose**: Core pay calculation logic with overtime rules
- **Location**: `app/src/main/java/com/dayforcetracker/PayCalculator.kt`
- **What it does**:
  * Calculates regular, overtime (1.5x), and Sunday premium (2x) pay
  * Handles real-time earnings updates
  * Determines current hourly rate based on hours worked
  * Formats currency and time displays

#### B) SettingsActivity.kt  
- **Purpose**: Pay rate configuration screen
- **Location**: `app/src/main/java/com/dayforcetracker/SettingsActivity.kt`
- **What it does**:
  * Provides UI for setting base hourly pay rate
  * Saves settings to SharedPreferences
  * Validates pay rate input
  * Shows pay rate preview (regular/overtime/Sunday rates)

### 2. NEW LAYOUT FILE (Add to app/src/main/res/layout/)

#### activity_settings.xml
- **Purpose**: UI layout for the settings screen
- **Location**: `app/src/main/res/layout/activity_settings.xml`
- **What it contains**:
  * Pay rate input field with validation
  * Pay period selection (weekly/bi-weekly)
  * Rate preview showing 1.5x and 2x calculations
  * Save/Cancel buttons
  * Informational cards explaining overtime rules

### 3. UPDATED EXISTING FILES

#### A) MainActivity.kt (MODIFY EXISTING)
- **Location**: `app/src/main/java/com/dayforcetracker/MainActivity.kt`
- **Changes needed**:
  * ADD new imports for pay calculator functionality
  * ADD new UI component references (buttons, text views)
  * ADD pay calculator initialization logic
  * ADD real-time update handling (starts/stops live counter)
  * ADD settings integration (opens settings screen)
  * MODIFY timesheet parsing to create WorkDay objects
  * ADD live earnings calculation that updates every second

#### B) activity_main.xml (MODIFY EXISTING)
- **Location**: `app/src/main/res/layout/activity_main.xml`
- **Changes needed**:
  * ADD pay display card at the top showing total earnings
  * ADD live counter text (updates per second when working)
  * ADD current rate and hours information
  * ADD buttons for Settings, Refresh, and Details
  * KEEP existing WebView for timesheet display
  * ADD status bar showing connection status

#### C) app/build.gradle (MODIFY EXISTING)
- **Location**: `app/build.gradle`
- **Changes needed**:
  * ADD CardView dependency for UI cards
  * UPDATE version number to 2.0
  * ADD Material Design components
  * ADD WebKit dependency (if not already present)

### 4. ANDROID MANIFEST UPDATE (CRITICAL)

#### AndroidManifest.xml (ADD NEW ACTIVITY)
- **Location**: `app/src/main/AndroidManifest.xml`
- **Required addition**:
```xml
<activity 
    android:name=".SettingsActivity"
    android:parentActivityName=".MainActivity"
    android:label="Pay Settings" />
```

---

## INTEGRATION PROCESS

### STEP 1: Add New Files
1. Create `PayCalculator.kt` in the java/com/dayforcetracker/ directory
2. Create `SettingsActivity.kt` in the java/com/dayforcetracker/ directory  
3. Create `activity_settings.xml` in the res/layout/ directory

### STEP 2: Update Existing Files
1. **MainActivity.kt**: Add the new imports and methods shown in the updated version
   - The key integration point is the `onTimesheetDataExtracted()` method
   - This gets called after your existing timesheet extraction completes
   - It parses the timesheet data into WorkDay objects and calculates pay

2. **activity_main.xml**: Replace with the updated layout that includes pay display
   - Adds the pay calculator UI above your existing WebView
   - WebView remains unchanged for timesheet display

3. **app/build.gradle**: Add the new dependencies for CardView and Material Design

4. **AndroidManifest.xml**: Add the SettingsActivity declaration

### STEP 3: Key Integration Points

#### A) Timesheet Data Flow
```
Existing Flow:
WebView extracts data → Display in WebView

New Flow:  
WebView extracts data → Parse to WorkDay objects → Calculate pay → Display earnings + WebView
```

#### B) Real-Time Updates
```
When user is currently working:
Every 1 second → Check elapsed time → Calculate additional earnings → Update display
```

#### C) Settings Integration
```
First launch → Prompt for pay rate → Save to SharedPreferences
Settings button → Open SettingsActivity → Update pay calculations
```

---

## WHAT EACH COMPONENT DOES

### PayCalculator.kt
- **calculatePay()**: Main method that takes WorkDay list and returns PayBreakdown
- **Overtime Logic**: Hours 1-40 = regular, 40+ = 1.5x rate
- **Sunday Premium**: 2x rate but ONLY if already worked 40+ hours that week
- **Real-time**: calculateLiveEarnings() adds current session earnings
- **Format helpers**: Currency and time formatting for display

### SettingsActivity.kt
- **Pay Rate Input**: Validates and saves hourly rate (with $ prefix/suffix)
- **SharedPreferences**: Persistent storage for settings
- **Rate Preview**: Shows regular ($20), overtime ($30), Sunday ($40) rates
- **Validation**: Ensures rate is reasonable (>$0, <$1000)

### Updated MainActivity.kt Integration
- **parseTimesheetToWorkDays()**: Converts your existing extraction into WorkDay objects
- **updatePayDisplay()**: Updates the earnings UI with calculated values
- **startRealTimeUpdates()**: Begins 1-second timer for live earnings
- **Settings integration**: Opens SettingsActivity and handles results

### Updated UI Layout
- **Pay Card**: Large earnings display at top ($1,340.50)
- **Live Counter**: Updates every second (+$0.43/min)
- **Action Buttons**: Settings, Refresh, Details
- **Existing WebView**: Unchanged timesheet display below
- **Status Bar**: Connection status at bottom

---

## DATA FLOW EXAMPLE

### Sample Week Calculation:
```
Monday: 8h @ $20/hr = $160 (regular)
Tuesday: 8h @ $20/hr = $160 (regular)  
Wednesday: 8h @ $20/hr = $160 (regular)
Thursday: 8h @ $20/hr = $160 (regular)
Friday: 8h @ $20/hr = $160 (regular) [40h total - threshold reached]
Saturday: 6h @ $30/hr = $180 (1.5x overtime)
Sunday: 8h @ $40/hr = $320 (2x Sunday premium - only because >40h by Sunday)

Total: $1,340
```

### Real-Time Updates:
```
If currently working on Saturday (overtime tier):
Base total: $1,160 (Mon-Fri complete)
Current rate: $30/hr (overtime)
Live counter: +$0.50/minute
Display updates every second with accumulating earnings
```

---

## TESTING CHECKLIST

### After Integration:
✅ App launches and prompts for pay rate setup (first time)
✅ Settings screen saves/loads pay rate correctly
✅ Timesheet extraction still works (existing functionality preserved)
✅ Pay calculations show correct regular/overtime/Sunday breakdown
✅ Real-time counter updates when currently working
✅ Live counter stops when not working
✅ Settings button opens pay rate configuration
✅ Details button shows breakdown popup
✅ App survives rotation and background/foreground switches

### Edge Cases to Test:
✅ No timesheet data (shows $0.00)
✅ Working exactly 40 hours (tests overtime threshold)
✅ Sunday work with <40 hours (should be regular rate)
✅ Sunday work with >40 hours (should be 2x rate)
✅ Currently working vs not working detection
✅ Invalid pay rate input handling

---

## ARCHITECTURE NOTES

### Design Principles:
- **Non-destructive**: Existing timesheet extraction unchanged
- **Modular**: PayCalculator is self-contained and reusable
- **Persistent**: Settings survive app restarts
- **Performance**: Live updates only run when actually working
- **User-friendly**: Clear UI with helpful information

### Future-Ready:
- PayCalculator designed for Samsung Watch integration (Day 2)
- Data structures ready for multi-week history
- Settings expandable for more pay rules
- Real-time system ready for background operation

---

## TROUBLESHOOTING

### Common Issues:
1. **Settings not saving**: Check SharedPreferences key names match
2. **Live counter not starting**: Verify isCurrentlyAtWork() logic
3. **Wrong calculations**: Check day-of-week parsing and hour accumulation
4. **UI not updating**: Ensure runOnUiThread() wraps UI updates
5. **App crashes on settings**: Verify AndroidManifest.xml has SettingsActivity

### Debug Tips:
- Add Log.d() statements in PayCalculator.calculatePay()
- Test with known timesheet data (27.1h example)
- Verify SharedPreferences values in device settings
- Check that parseTimesheetToWorkDays() correctly maps your extraction format

---

## SUCCESS CRITERIA

✅ **Integration Complete When**:
- Existing timesheet extraction still works perfectly
- Pay calculator shows accurate earnings matching manual calculations  
- Real-time counter updates smoothly when working
- Settings screen saves and loads pay rate
- All overtime/Sunday premium rules work correctly
- UI is clean and informative
- Ready for Samsung Watch integration (Day 2)

This integration transforms your working timesheet extractor into a comprehensive real-time pay tracking system while preserving all existing functionality.