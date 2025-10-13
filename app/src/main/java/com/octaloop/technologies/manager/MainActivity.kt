package com.octaloop.technologies.manager

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var compName: ComponentName
    private lateinit var socketManager: EnhancedSocketManager
    private lateinit var mdmPolicyManager: MdmPolicyManager
    private lateinit var appManager: AppManager
    private lateinit var usbReceiver: BroadcastReceiver
    private var usbStatus by mutableStateOf("USB: Disconnected") // State for USB status
    private var usbBlockingEnabled by mutableStateOf(false) // State for USB blocking
    private var isUsbConnected by mutableStateOf(false) // Track connection state
    private var currentUsbMode by mutableStateOf("") // Track current USB mode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "🔥 App launched successfully")
        
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, MyDeviceAdminReceiver::class.java)
        socketManager = EnhancedSocketManager(this)
        mdmPolicyManager = MdmPolicyManager(this)
        appManager = AppManager(this)
        
        // Initialize enhanced USB receiver with comprehensive detection
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    val action = intent.action ?: "null"
                    Log.d("MainActivity", "🔍 ===== BROADCAST RECEIVED =====")
                    Log.d("MainActivity", "📡 Action: $action")
                    Log.d("MainActivity", "📡 Thread: ${Thread.currentThread().name}")
                    println("MainActivity:-🔍 ===== BROADCAST RECEIVED =====")
                    println("MainActivity:-📡 Action: $action")
                    println("MainActivity:-📡 Thread: ${Thread.currentThread().name}")
                    
                    // Log all intent extras for debugging
                    logUsbStateExtras(intent)
                    Toast.makeText(this@MainActivity, "USB RECIEVER CALLED:- Action $action", Toast.LENGTH_LONG).show()
                    when (action) {

                        UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                            Log.d("MainActivity", "🔌 USB_DEVICE_ATTACHED - Cable connected!")
                            isUsbConnected = true
                            
                            // Delay detection to allow system to settle
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                detectAndShowUsbMode()
                                
                                // Automatically apply USB blocking if enabled
                                if (usbBlockingEnabled && devicePolicyManager.isAdminActive(compName)) {
                                    val result = mdmPolicyManager.restrictUsbFileTransfer()
                                    Log.d("MainActivity", "🚫 Auto USB blocking applied: ${result.optString("message")}")
                                }
                            }, 500)
                        }
                        
                        UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                            Log.d("MainActivity", "🔌 USB_DEVICE_DETACHED - Cable disconnected!")
                            isUsbConnected = false
                            currentUsbMode = ""
                            usbStatus = "USB: Disconnected"
                            
                            // Show disconnection toast
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(context, "🔌 USB cable disconnected", Toast.LENGTH_SHORT).show()
                            }
                        }
                        
                        // USB state changes (MTP, PTP, Charging, etc.) - MOST IMPORTANT for mode detection
                        "android.hardware.usb.action.USB_STATE" -> {
                            Log.d("MainActivity", "🔄 USB_STATE change - This indicates mode switch!")
                            println("MainActivity:-🔄 USB_STATE change - Processing...")
                            
                            // Extract USB connection info from intent extras
                            val connected = intent.getBooleanExtra("connected", false)
                            val configured = intent.getBooleanExtra("configured", false)
                            
                            println("MainActivity:-📊 USB_STATE extras - Connected: $connected, Configured: $configured")
                            
                            if (connected) {
                                println("MainActivity:-🔌 USB cable is connected!")
                                isUsbConnected = true
                                
                                // Delay mode detection to allow system to update
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    println("MainActivity:-🔄 Processing USB mode change after delay...")
                                    detectAndShowUsbMode()
                                }, 1000) // Longer delay for mode changes
                            } else {
                                println("MainActivity:-🔌 USB cable disconnected!")
                                isUsbConnected = false
                                currentUsbMode = ""
                                usbStatus = "USB: Disconnected"
                                Toast.makeText(context, "🔌 USB cable disconnected", Toast.LENGTH_SHORT).show()
                            }
                        }
                        
                        // Additional power events
                        Intent.ACTION_POWER_CONNECTED -> {
                            Log.d("MainActivity", "⚡ Power connected - checking if USB")
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                checkUsbStatus()
                            }, 500)
                        }
                        
                        Intent.ACTION_POWER_DISCONNECTED -> {
                            Log.d("MainActivity", "🔌 Power disconnected")
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                checkUsbStatus()
                            }, 500)
                        }
                        
                        // Media and storage events
                        Intent.ACTION_MEDIA_MOUNTED,
                        Intent.ACTION_MEDIA_UNMOUNTED,
                        Intent.ACTION_MEDIA_CHECKING -> {
                            Log.d("MainActivity", "📱 Media state changed: $action")
                            if (isUsbConnected) {
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    Log.d("MainActivity", "📱 Re-checking USB mode due to media change")
                                    detectAndShowUsbMode()
                                }, 1000)
                            }
                        }
                        
                        else -> {
                            Log.d("MainActivity", "❓ Unknown USB-related broadcast: $action")
                        }
                    }
                    
                    Log.d("MainActivity", "===== BROADCAST PROCESSING COMPLETE =====")
                    
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ ERROR in USB broadcast receiver: ${e.message}")
                    Log.e("MainActivity", "❌ Stack trace: ${e.stackTraceToString()}")
                }
            }
        }

        // Register enhanced USB receiver with comprehensive intent filtering
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction("android.hardware.usb.action.USB_STATE")
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            // Media intents need separate filter due to data scheme requirement
        }
        
        // Register main USB receiver
        registerReceiver(usbReceiver, filter)
        
        // Register additional receiver for media events (with data scheme)
        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_CHECKING)
            addDataScheme("file")
        }
        
        try {
            registerReceiver(usbReceiver, mediaFilter)
            Log.d("MainActivity", "📱 Media receiver registered")
        } catch (e: Exception) {
            Log.w("MainActivity", "Could not register media receiver: ${e.message}")
        }
        
        Log.d("MainActivity", "🔌 Enhanced USB receiver registered with ${filter.countActions()} actions")
        
        // Initialize USB status on app start
        checkUsbStatus()
        
        // Commenting out periodic monitoring to prevent infinite loops
        // startUsbModeMonitoring() // We rely on broadcast receivers instead
        
        setContent {
            var currentScreen by remember { mutableStateOf("main") }
            
            when (currentScreen) {
                "main" -> MdmApp(
                    isAdminActive = devicePolicyManager.isAdminActive(compName),
                    usbStatus = usbStatus,
                    onEnableAdmin = { enableAdmin() },
                    onLockDevice = { lockDevice() },
                    onStartService = { startMdmService() },
                    onStopService = { stopMdmService() },
                    onBlockInstallation = { blockInstallation() },
                    onAllowInstallation = { allowInstallation() },
                    onDisableCamera = { disableCamera() },
                    onEnableCamera = { enableCamera() },
                    onApplyLockdown = { applySystemLockdown() },
                    onRemoveLockdown = { removeSystemLockdown() },
                    onBlockCategories = { blockAppCategories() },
                    onUnblockCategories = { unblockAppCategories() },
                    onDisableContentCreation = { disableContentCreation() },
                    onEnableContentCreation = { enableContentCreation() },
                    onManageApps = { currentScreen = "apps" },
                    onEnableAccessibility = { enableAccessibilityService() },
                    onBlockUsbFileTransfer = { blockUsbFileTransfer() },
                    onAllowUsbFileTransfer = { allowUsbFileTransfer() },
                    onToggleUsbBlocking = { toggleUsbBlocking() },
                    onTestUsbDetection = { forceUsbModeDetection() },
                    usbBlockingEnabled = usbBlockingEnabled,
                    isUsbConnected = isUsbConnected
                )
                "apps" -> AppManagementScreen(
                    appManager = appManager,
                    onBack = { currentScreen = "main" }
                )
            }
        }
        
        // Initialize socket connection
        initializeSocket()
        
        // Start background service if admin is active
        if (devicePolicyManager.isAdminActive(compName)) {
            startMdmService()
            startAppInterceptorService()
        }
    }
    
    private fun initializeSocket() {
        Log.d("MainActivity", "🔌 Initializing socket connection...")
//        socketManager.connect()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "📱 onResume called")
        
        // Reconnect if needed when app comes to foreground
//        if (!socketManager.isConnected()) {
//            Log.d("MainActivity", "🔄 Reconnecting socket on resume...")
//            socketManager.connect()
//        }
        
        // Ensure app interceptor is running if admin is active
        if (devicePolicyManager.isAdminActive(compName)) {
            startAppInterceptorService()
        }
        
        // Force USB mode detection when app resumes
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d("MainActivity", "🔍 Checking USB status on resume...")
            checkUsbStatus()
            if (isUsbConnected) {
                Log.d("MainActivity", "🔍 Forcing USB mode detection on resume")
                detectAndShowUsbMode()
            }
        }, 1000)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "🔌 Disconnecting socket on destroy...")
        // Unregister USB receiver to prevent memory leaks
        try {
            unregisterReceiver(usbReceiver)
            Log.d("MainActivity", "🔌 USB receiver unregistered")
        } catch (e: Exception) {
            Log.w("MainActivity", "USB receiver already unregistered: ${e.message}")
        }
        // Don't disconnect socket here as service should handle it
    }
    
    /**
     * Log USB state extras from broadcast intent with enhanced detail
     */
    private fun logUsbStateExtras(intent: Intent) {
        try {
            Log.d("MainActivity", "📊 ===== INTENT DETAILS =====")
            Log.d("MainActivity", "📡 Action: ${intent.action}")
            Log.d("MainActivity", "📡 Data: ${intent.data}")
            Log.d("MainActivity", "📡 Type: ${intent.type}")
            Log.d("MainActivity", "📡 Categories: ${intent.categories}")
            
            val extras = intent.extras
            if (extras != null && !extras.isEmpty) {
                Log.d("MainActivity", "📊 Extras (${extras.size()}):")
                for (key in extras.keySet()) {
                    val value = extras.get(key)
                    val valueStr = when (value) {
                        is Boolean -> "$value"
                        is String -> "'$value'"
                        is Bundle -> "Bundle[${value.keySet().joinToString()}]"
                        else -> "$value (${value?.javaClass?.simpleName})"
                    }
                    Log.d("MainActivity", "  📄 $key = $valueStr")
                }
            } else {
                Log.d("MainActivity", "📊 No extras in intent")
            }
            Log.d("MainActivity", "===== INTENT DETAILS END =====")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error logging USB extras: ${e.message}")
        }
    }

    /**
     * Detect current USB connection mode and show appropriate toast
     */
    private fun detectAndShowUsbMode() {
        try {
            Log.d("MainActivity", "🔍 ===== DETECT AND SHOW USB MODE =====")
            println("MainActivity:-🔍 ===== DETECT AND SHOW USB MODE =====")
            Log.d("MainActivity", "📊 Current state - USB Connected: $isUsbConnected, Current Mode: '$currentUsbMode'")
            println("MainActivity:-📊 Current state - USB Connected: $isUsbConnected, Current Mode: '$currentUsbMode'")
            
            // Don't call checkUsbStatus() here to avoid infinite loop
            
            if (!isUsbConnected) {
                Log.w("MainActivity", "⚠️ detectAndShowUsbMode called but USB not connected")
                println("MainActivity:-⚠️ detectAndShowUsbMode called but USB not connected")
                // Force show a toast anyway to indicate the call happened
                Toast.makeText(this@MainActivity, "🔌 USB State Change Detected (No USB Connected)", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Detect the current USB mode
            val newUsbMode = detectCurrentUsbMode()
            Log.d("MainActivity", "🔍 New mode detected: '$newUsbMode'")
            println("MainActivity:-🔍 New mode detected: '$newUsbMode'")
            
            // Check if mode actually changed or it's the first connection
            val modeChanged = newUsbMode != currentUsbMode
            val isFirstConnection = currentUsbMode.isEmpty()
            
            Log.d("MainActivity", "📊 Mode changed: $modeChanged, First connection: $isFirstConnection")
            println("MainActivity:-📊 Mode changed: $modeChanged, First connection: $isFirstConnection")
            
            if (modeChanged || isFirstConnection) {
                val previousMode = currentUsbMode
                currentUsbMode = newUsbMode
                
                Log.d("MainActivity", "🔄 Mode transition: '$previousMode' → '$newUsbMode'")
                println("MainActivity:-🔄 Mode transition: '$previousMode' → '$newUsbMode'")
                
                // Generate toast message based on detected mode
                val toastMessage = when (newUsbMode) {
                    "charging" -> "🔋 USB Mode: Charging Only"
                    "mtp" -> "📁 USB Mode: File Transfer (MTP) - Files accessible"
                    "ptp" -> "📷 USB Mode: Photo Transfer (PTP) - Photos accessible"
                    "midi" -> "🎵 USB Mode: MIDI Device"
                    "rndis" -> "🌐 USB Mode: USB Tethering - Internet sharing"
                    "adb" -> "🛠️ USB Mode: USB Debugging (ADB) - Developer mode"
                    "blocked" -> "🚫 USB Mode: Data Transfer Blocked - Charging Only"
                    else -> "🔌 USB Connected - Mode: ${newUsbMode.uppercase()}"
                }
                
                // Create final message - show change notification if mode switched
                val finalMessage = if (previousMode.isNotEmpty() && modeChanged) {
                    "🔄 USB Mode Changed!\n${getModeDisplayName(previousMode)} → ${getModeDisplayName(newUsbMode)}"
                } else {
                    toastMessage
                }
                
                // Show toast on main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Log.d("MainActivity", "� Showing toast: $finalMessage")
                    println("MainActivity:-📢 About to show toast: $finalMessage")
                    try {
                        Toast.makeText(this@MainActivity, finalMessage, Toast.LENGTH_LONG).show()
                        println("MainActivity:-✅ Toast displayed successfully")
                    } catch (e: Exception) {
                        println("MainActivity:-❌ Toast error: ${e.message}")
                        Log.e("MainActivity", "❌ Toast error: ${e.message}")
                    }
                }
                
                // Update status display
                usbStatus = "USB: Connected - $toastMessage"
                
                Log.d("MainActivity", "✅ USB mode updated successfully")
                Log.d("MainActivity", "📊 Final status: $usbStatus")
            } else {
                Log.d("MainActivity", "➡️ No mode change detected, skipping toast")
                println("MainActivity:-➡️ No mode change detected - Previous: '$currentUsbMode', New: '$newUsbMode'")
                
                // Show a debug toast anyway to confirm detection is working
                Toast.makeText(this@MainActivity, "🔄 USB Mode: ${getModeDisplayName(newUsbMode)} (No Change)", Toast.LENGTH_SHORT).show()
                println("MainActivity:-📢 Debug toast shown for no-change scenario")
            }
            
            Log.d("MainActivity", "===== DETECT AND SHOW USB MODE COMPLETE =====")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ CRITICAL ERROR in detectAndShowUsbMode: ${e.message}")
            Log.e("MainActivity", "❌ Stack trace: ${e.stackTraceToString()}")
            
            // Fallback toast on error
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(this@MainActivity, "🔌 USB Connected (Error detecting mode)", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Detect current USB connection mode with enhanced reliability
     */
    private fun detectCurrentUsbMode(): String {
        return try {
            Log.d("MainActivity", "🔍 ===== STARTING USB MODE DETECTION =====")
            
            // Method 1: Check if USB blocking is active first
            if (usbBlockingEnabled) {
                Log.d("MainActivity", "🚫 USB blocking enabled, returning 'blocked'")
                return "blocked"
            }
            
            // Method 2: Check USB Manager for connected devices (this is for USB devices connected TO the phone)
            val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = usbManager.deviceList
            Log.d("MainActivity", "📱 USB devices connected TO phone: ${deviceList.size}")
            println("MainActivity:-📱 USB devices connected TO phone: ${deviceList.size}")
            
            // Note: For phone-to-computer USB connection, deviceList will be empty
            // We need to use system properties to detect the actual USB mode
            
            // Method 3: Try to access system properties via reflection (MAIN METHOD for phone USB modes)
            // Method 3: Try to access system properties via reflection (MAIN METHOD for phone USB modes)
            val usbConfig = getSystemProperty("sys.usb.config", "")
            val usbState = getSystemProperty("sys.usb.state", "")
            
            Log.d("MainActivity", "📊 System Properties:")
            Log.d("MainActivity", "   USB Config: '$usbConfig'")
            Log.d("MainActivity", "   USB State: '$usbState'")
            
            println("MainActivity:-📊 System Properties:")
            println("MainActivity:-   USB Config: '$usbConfig'")
            println("MainActivity:-   USB State: '$usbState'")
            
            // Method 4: Check developer options and USB debugging
            val adbEnabled = try {
                android.provider.Settings.Global.getInt(
                    contentResolver, 
                    android.provider.Settings.Global.ADB_ENABLED, 
                    0
                ) == 1
            } catch (e: Exception) { 
                Log.w("MainActivity", "Could not check ADB status: ${e.message}")
                false 
            }
            
            Log.d("MainActivity", "🛠️ ADB Enabled: $adbEnabled")
            
            // Method 5: Check external storage state for MTP indication
            val externalState = android.os.Environment.getExternalStorageState()
            val isStorageMounted = externalState == android.os.Environment.MEDIA_MOUNTED
            Log.d("MainActivity", "💾 External Storage State: '$externalState', Mounted: $isStorageMounted")
            
            // Method 6: Check for USB mass storage or MTP
            val isMtpActive = try {
                val storageManager = getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                // This is a heuristic - if we can access storage manager and external storage is mounted,
                // and USB is connected, it's likely MTP mode
                isStorageMounted && deviceList.isNotEmpty()
            } catch (e: Exception) {
                Log.w("MainActivity", "Could not check storage manager: ${e.message}")
                false
            }
            
            Log.d("MainActivity", "📁 MTP likely active: $isMtpActive")
            
            // Parse USB configuration with enhanced priority logic
            val detectedMode = when {
                // Direct property matches (most reliable)
                usbConfig.contains("mtp") || usbState.contains("mtp") -> {
                    Log.d("MainActivity", "✅ Mode detected via properties: MTP")
                    "mtp"
                }
                usbConfig.contains("ptp") || usbState.contains("ptp") -> {
                    Log.d("MainActivity", "✅ Mode detected via properties: PTP")
                    "ptp"
                }
                usbConfig.contains("midi") || usbState.contains("midi") -> {
                    Log.d("MainActivity", "✅ Mode detected via properties: MIDI")
                    "midi"
                }
                usbConfig.contains("rndis") || usbState.contains("rndis") -> {
                    Log.d("MainActivity", "✅ Mode detected via properties: RNDIS")
                    "rndis"
                }
                // Heuristic detection
                isMtpActive && isStorageMounted -> {
                    Log.d("MainActivity", "✅ Mode detected via heuristics: MTP (storage accessible)")
                    "mtp"
                }
                adbEnabled && (usbConfig.contains("adb") || usbState.contains("adb")) -> {
                    Log.d("MainActivity", "✅ Mode detected: ADB enabled with USB")
                    "adb"
                }
                adbEnabled -> {
                    Log.d("MainActivity", "✅ Mode detected: ADB enabled, likely charging + debugging")
                    "charging"
                }
                else -> {
                    Log.d("MainActivity", "✅ Mode detected: Default charging mode")
                    "charging"
                }
            }
            
            Log.d("MainActivity", "🎯 FINAL DETECTED MODE: '$detectedMode'")
            Log.d("MainActivity", "===== USB MODE DETECTION COMPLETE =====")
            detectedMode
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ CRITICAL ERROR in USB mode detection: ${e.message}")
            Log.e("MainActivity", "❌ Stack trace: ${e.stackTraceToString()}")
            "charging"
        }
    }
    
    /**
     * Safely get system property using reflection
     */
    private fun getSystemProperty(key: String, defaultValue: String): String {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
            getMethod.invoke(null, key, defaultValue) as String
        } catch (e: Exception) {
            Log.w("MainActivity", "Could not access system property $key: ${e.message}")
            // Fallback: try to detect mode using other methods
            when (key) {
                "sys.usb.config", "sys.usb.state" -> {
                    detectUsbModeAlternative()
                }
                else -> defaultValue
            }
        }
    }
    
    /**
     * Alternative USB mode detection using Settings and other APIs
     */
    private fun detectUsbModeAlternative(): String {
        return try {
            // Check developer options and USB debugging
            val adbEnabled = android.provider.Settings.Global.getInt(
                contentResolver, 
                android.provider.Settings.Global.ADB_ENABLED, 
                0
            ) == 1
            
            // Check if external storage is mounted (indicates MTP mode)
            val externalStorageState = android.os.Environment.getExternalStorageState()
            val isMounted = externalStorageState == android.os.Environment.MEDIA_MOUNTED
            
            Log.d("MainActivity", "📊 ADB Enabled: $adbEnabled, Storage Mounted: $isMounted")
            
            when {
                adbEnabled && isMounted -> "mtp" // File transfer with debugging
                isMounted -> "mtp" // File transfer mode
                adbEnabled -> "charging" // Only debugging, likely charging
                else -> "charging" // Default to charging
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Alternative USB detection failed: ${e.message}")
            "charging"
        }
    }
    
    /**
     * Get display name for USB mode
     */
    private fun getModeDisplayName(mode: String): String {
        return when (mode) {
            "charging" -> "Charging Only"
            "mtp" -> "File Transfer"
            "ptp" -> "Photo Transfer"
            "midi" -> "MIDI"
            "rndis" -> "USB Tethering"
            "adb" -> "USB Debugging"
            "blocked" -> "Blocked"
            else -> mode.uppercase()
        }
    }
    
    /**
     * Check current USB connection status
     */
    private fun checkUsbStatus() {
        try {
            println("MainActivity:-🔍 Checking USB status...")
            
            // Method 1: Check USB Manager devices (for USB devices like flash drives)
            val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = usbManager.deviceList
            val hasUsbDevices = deviceList.isNotEmpty()
            
            // Method 2: Check if USB cable is connected (phone to computer)
            val isUsbCableConnected = checkUsbCableConnection()
            
            // Combined detection - either USB devices or cable connection
            val isConnectedNow = hasUsbDevices || isUsbCableConnected
            
            println("MainActivity:-📊 USB devices: ${deviceList.size}, Cable connected: $isUsbCableConnected, Total connected: $isConnectedNow, Previously: $isUsbConnected")
            
            if (isConnectedNow != isUsbConnected) {
                println("MainActivity:-🔄 USB connection state changed!")
                isUsbConnected = isConnectedNow
                if (isConnectedNow) {
                    // USB just connected, detect mode
                    println("MainActivity:-🔌 USB just connected - detecting mode")
                    detectAndShowUsbMode()
                } else {
                    // USB disconnected
                    println("MainActivity:-🔌 USB disconnected - clearing state")
                    currentUsbMode = ""
                    usbStatus = "USB: Disconnected"
                    Toast.makeText(this@MainActivity, "🔌 USB cable disconnected", Toast.LENGTH_SHORT).show()
                }
                Log.d("MainActivity", "📊 USB status checked: $usbStatus")
            } else {
                // Connection state hasn't changed - don't call detectAndShowUsbMode to avoid loop
                println("MainActivity:-➡️ USB connection state unchanged - no action needed")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error checking USB status: ${e.message}")
            println("MainActivity:-❌ Error checking USB status: ${e.message}")
        }
    }
    
    /**
     * Check if USB cable is connected (phone to computer connection)
     */
    private fun checkUsbCableConnection(): Boolean {
        return try {
            // Method 1: Check battery manager for USB power
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val isUsbCharging = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS) != android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING
            
            // Method 2: Check if USB power is connected
            val chargePlug = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            
            // Method 3: Use system properties
            val usbConnected = getSystemProperty("sys.usb.state", "").contains("connected") ||
                              getSystemProperty("sys.usb.config", "").isNotEmpty()
            
            println("MainActivity:-🔋 Battery USB charging: $isUsbCharging, USB connected via properties: $usbConnected")
            
            // Return true if either method indicates USB connection
            isUsbCharging || usbConnected
        } catch (e: Exception) {
            println("MainActivity:-❌ Error checking USB cable: ${e.message}")
            false
        }
    }
    
    /**
     * Block USB file transfer functionality
     */
    private fun blockUsbFileTransfer() {
        try {
            val result = mdmPolicyManager.restrictUsbFileTransfer()
            val message = result.optString("message", "USB file transfer blocked")
            Toast.makeText(this, "🚫 $message", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "🚫 USB file transfer blocked: $message")
            
            // Update status and mode if USB is currently connected
            if (isUsbConnected) {
                currentUsbMode = "blocked"
                usbStatus = "USB: Connected - 🚫 Data Transfer Blocked"
                Toast.makeText(this, "🔄 USB Mode changed to: Charging Only (Data Blocked)", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            val errorMessage = "Failed to block USB: ${e.message}"
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "❌ Error blocking USB: ${e.message}")
        }
    }
    
    /**
     * Allow USB file transfer functionality
     */
    private fun allowUsbFileTransfer() {
        try {
            val result = mdmPolicyManager.allowUsbFileTransfer()
            val message = result.optString("message", "USB file transfer allowed")
            Toast.makeText(this, "✅ $message", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "✅ USB file transfer allowed: $message")
            
            // Update status and re-detect mode if USB is currently connected
            if (isUsbConnected) {
                // Re-detect the actual USB mode after allowing
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    detectAndShowUsbMode()
                    Toast.makeText(this, "🔄 USB Data Transfer Enabled - You can now change USB mode", Toast.LENGTH_LONG).show()
                }, 1000)
            }
        } catch (e: Exception) {
            val errorMessage = "Failed to allow USB: ${e.message}"
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "❌ Error allowing USB: ${e.message}")
        }
    }
    
    /**
     * Toggle automatic USB blocking when USB is connected
     */
    private fun toggleUsbBlocking() {
        usbBlockingEnabled = !usbBlockingEnabled
        val statusMessage = if (usbBlockingEnabled) {
            "🚫 Auto USB blocking ENABLED"
        } else {
            "✅ Auto USB blocking DISABLED"
        }
        
        Toast.makeText(this, statusMessage, Toast.LENGTH_SHORT).show()
        Log.d("MainActivity", "🔄 USB blocking toggled: $usbBlockingEnabled")
        
        // Apply immediate action if USB is currently connected
        if (isUsbConnected) {
            if (usbBlockingEnabled) {
                blockUsbFileTransfer()
            } else {
                allowUsbFileTransfer()
            }
        }
        
        // Update display status
        if (isUsbConnected) {
            usbStatus = "USB: Cable connected - Data transfer ${if (usbBlockingEnabled) "BLOCKED" else "ALLOWED"}"
        }
    }
    
    /**
     * Start periodic USB mode monitoring to catch mode changes
     */
    private fun startUsbModeMonitoring() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isUsbConnected) {
                    Log.d("MainActivity", "🔄 Periodic USB mode check...")
                    detectAndShowUsbMode()
                }
                handler.postDelayed(this, 5000) // Check every 5 seconds (reduced frequency)
            }
        }
        handler.postDelayed(runnable, 5000)
        Log.d("MainActivity", "🔄 USB mode monitoring started (5-second intervals)")
    }
    
    /**
     * Force USB mode detection (for manual testing)
     */
    private fun forceUsbModeDetection() {
        Log.d("MainActivity", "🔍 Manual USB mode detection triggered")
        Toast.makeText(this, "🔍 Checking USB mode...", Toast.LENGTH_SHORT).show()
        
        if (isUsbConnected) {
            detectAndShowUsbMode()
        } else {
            Toast.makeText(this, "❌ No USB cable connected", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun enableAdmin() {
        if (!devicePolicyManager.isAdminActive(compName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Grant MDM permissions for device management.")
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Device Admin Already Active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun lockDevice() {
        val result = mdmPolicyManager.lockDevice()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun blockInstallation() {
        val result = mdmPolicyManager.blockAppInstallation()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun allowInstallation() {
        val result = mdmPolicyManager.allowAppInstallation()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun disableCamera() {
        val result = mdmPolicyManager.disableCamera()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun enableCamera() {
        val result = mdmPolicyManager.enableCamera()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun startMdmService() {
        try {
            val serviceIntent = Intent(this, MdmBackgroundService::class.java).apply {
                action = MdmBackgroundService.ACTION_START_SERVICE
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            Toast.makeText(this, "MDM Service Started", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "✅ MDM background service started")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start service: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "❌ Failed to start MDM service: ${e.message}")
        }
    }
    
    private fun stopMdmService() {
        try {
            val serviceIntent = Intent(this, MdmBackgroundService::class.java).apply {
                action = MdmBackgroundService.ACTION_STOP_SERVICE
            }
            startService(serviceIntent)
            
            Toast.makeText(this, "MDM Service Stopped", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "🛑 MDM background service stopped")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to stop service: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "❌ Failed to stop MDM service: ${e.message}")
        }
    }
    
    private fun applySystemLockdown() {
        val result = mdmPolicyManager.applySystemLockdown()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun removeSystemLockdown() {
        val result = mdmPolicyManager.removeSystemLockdown()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun blockAppCategories() {
        val result = mdmPolicyManager.blockAppCategories()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun unblockAppCategories() {
        val result = mdmPolicyManager.unblockAppCategories()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun disableContentCreation() {
        val result = mdmPolicyManager.disableContentCreation()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun enableContentCreation() {
        val result = mdmPolicyManager.enableContentCreation()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun startAppInterceptorService() {
        try {
            val serviceIntent = Intent(this, AppInterceptorService::class.java).apply {
                action = AppInterceptorService.ACTION_START_MONITORING
            }
            startService(serviceIntent)
            Log.d("MainActivity", "✅ App Interceptor Service started")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Failed to start App Interceptor Service: ${e.message}")
        }
    }
    
    private fun stopAppInterceptorService() {
        try {
            val serviceIntent = Intent(this, AppInterceptorService::class.java).apply {
                action = AppInterceptorService.ACTION_STOP_MONITORING
            }
            startService(serviceIntent)
            Log.d("MainActivity", "🛑 App Interceptor Service stopped")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Failed to stop App Interceptor Service: ${e.message}")
        }
    }
    
    private fun enableAccessibilityService() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Toast.makeText(
                this, 
                "Please enable 'MDM App Blocker' in Accessibility settings for app blocking to work", 
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open accessibility settings", Toast.LENGTH_SHORT).show()
        }
    }
}


@Composable
fun MdmApp(
    isAdminActive: Boolean,
    usbStatus: String,
    onEnableAdmin: () -> Unit,
    onLockDevice: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onBlockInstallation: () -> Unit,
    onAllowInstallation: () -> Unit,
    onDisableCamera: () -> Unit,
    onEnableCamera: () -> Unit,
    onApplyLockdown: () -> Unit,
    onRemoveLockdown: () -> Unit,
    onBlockCategories: () -> Unit,
    onUnblockCategories: () -> Unit,
    onDisableContentCreation: () -> Unit,
    onEnableContentCreation: () -> Unit,
    onManageApps: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onBlockUsbFileTransfer: () -> Unit,
    onAllowUsbFileTransfer: () -> Unit,
    onToggleUsbBlocking: () -> Unit,
    onTestUsbDetection: () -> Unit,
    usbBlockingEnabled: Boolean,
    isUsbConnected: Boolean
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Octaloop MDM Manager",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Admin Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAdminActive)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Admin Status",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (isAdminActive) "✅ Active" else "❌ Inactive",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isAdminActive)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Socket Connection Status
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Connection Status",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "🔄 Connecting to MDM server...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Enhanced USB Status Card with detailed information
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isUsbConnected && usbBlockingEnabled -> MaterialTheme.colorScheme.errorContainer
                            isUsbConnected && !usbBlockingEnabled -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔌 USB Management",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = usbStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isUsbConnected && usbBlockingEnabled -> MaterialTheme.colorScheme.onErrorContainer
                                isUsbConnected && !usbBlockingEnabled -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        
                        // Auto-blocking status indicator
                        Text(
                            text = "Auto-blocking: ${if (usbBlockingEnabled) "🚫 ENABLED" else "✅ DISABLED"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (usbBlockingEnabled) 
                                MaterialTheme.colorScheme.error 
                            else 
                                MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Enable Admin Button
                Button(
                    onClick = onEnableAdmin,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAdminActive
                ) {
                    Text(if (isAdminActive) "Device Admin Active ✓" else "Enable Device Admin")
                }

                // Service Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStartService,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive
                    ) {
                        Text("Start Service")
                    }
                    Button(
                        onClick = onStopService,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Stop Service")
                    }
                }

                // App Management Button
                Button(
                    onClick = onManageApps,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isAdminActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("📱 Manage Apps")
                }

                // Enable Accessibility Service Button
                Button(
                    onClick = onEnableAccessibility,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("🛡️ Enable App Blocking")
                }

                // Device Control Section
                Text(
                    text = "Device Controls",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Button(
                    onClick = onLockDevice,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isAdminActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("🔒 Lock Device")
                }

                // App Installation Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBlockInstallation,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("🚫 Block Install")
                    }
                    Button(
                        onClick = onAllowInstallation,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("✅ Allow Install")
                    }
                }

                // Camera Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDisableCamera,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("📷❌ Disable Camera")
                    }
                    Button(
                        onClick = onEnableCamera,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("📹✅ Enable Camera")
                    }
                }

                // Enhanced USB Controls Section
                Text(
                    text = "🔌 USB Cable Management",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                // Auto USB Blocking Toggle
                Button(
                    onClick = onToggleUsbBlocking,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (usbBlockingEnabled) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(
                        text = if (usbBlockingEnabled) 
                            "🚫 Disable Auto USB Blocking" 
                        else 
                            "🛡️ Enable Auto USB Blocking"
                    )
                }
                
                // Manual USB Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBlockUsbFileTransfer,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("🚫 Block USB Now")
                    }
                    Button(
                        onClick = onAllowUsbFileTransfer,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("✅ Allow USB Now")
                    }
                }
                
                // USB Testing Button
                Button(
                    onClick = onTestUsbDetection,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text("🔍 Test USB Mode Detection")
                }

                // System Lockdown Section
                Text(
                    text = "System Lockdown Controls",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // ... (rest of your existing UI remains unchanged)
            }
        }
    }
}