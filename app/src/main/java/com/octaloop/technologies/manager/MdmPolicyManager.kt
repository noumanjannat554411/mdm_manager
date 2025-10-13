package com.octaloop.technologies.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresApi
import org.json.JSONObject
import org.json.JSONArray

class MdmPolicyManager(private val context: Context) {
    
    private val devicePolicyManager: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
    
    private val componentName: ComponentName by lazy {
        ComponentName(context, MyDeviceAdminReceiver::class.java)
    }
    
    private val packageManager: PackageManager by lazy {
        context.packageManager
    }
    
    private val userManager: UserManager by lazy {
        context.getSystemService(Context.USER_SERVICE) as UserManager
    }
    
    companion object {
        private const val TAG = "MdmPolicyManager"
    }
    /**
     * Enhanced USB file transfer restriction with comprehensive blocking
     * Supports Android 12+ with setUsbDataSignalingEnabled and Android 5+ with user restrictions
     */
    fun restrictUsbFileTransfer(): JSONObject {
        return try {
            if (devicePolicyManager.isAdminActive(componentName)) {
                var successCount = 0
                var errorCount = 0
                
                // Method 1: Disable USB data signaling (Android 12+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        devicePolicyManager.setUsbDataSignalingEnabled(false)
                        successCount++
                        Log.d(TAG, "✅ USB data signaling disabled (Android 12+ API)")
                    } catch (e: Exception) {
                        errorCount++
                        Log.w(TAG, "Could not disable USB data signaling: ${e.message}")
                    }
                } else {
                    Log.d(TAG, "📱 Android 12+ API not available, using fallback method")
                }
                
                // Method 2: Apply user restrictions (fallback for older versions)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        devicePolicyManager.addUserRestriction(componentName, UserManager.DISALLOW_USB_FILE_TRANSFER)
                        successCount++
                        Log.d(TAG, "✅ USB file transfer user restriction applied")
                    } catch (e: Exception) {
                        errorCount++
                        Log.w(TAG, "Could not apply USB file transfer restriction: ${e.message}")
                    }
                }
                
                val message = if (successCount > 0) {
                    "USB file transfer blocked successfully ($successCount methods applied)"
                } else {
                    "Failed to block USB file transfer"
                }
                
                Log.d(TAG, "🚫 $message")
                createResult("restrict_usb", "success", message)
            } else {
                Log.e(TAG, "❌ Cannot restrict USB - admin not active")
                createResult("restrict_usb", "error", "Device admin not active")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security error restricting USB: ${e.message}")
            createResult("restrict_usb", "error", "Security error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error restricting USB: ${e.message}")
            createResult("restrict_usb", "error", "Error: ${e.message}")
        }
    }

    /**
     * Enhanced USB file transfer allowance with comprehensive enabling
     * Supports Android 12+ with setUsbDataSignalingEnabled and Android 5+ with user restrictions
     */
    fun allowUsbFileTransfer(): JSONObject {
        return try {
            if (devicePolicyManager.isAdminActive(componentName)) {
                var successCount = 0
                var errorCount = 0
                
                // Method 1: Enable USB data signaling (Android 12+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        devicePolicyManager.setUsbDataSignalingEnabled(true)
                        successCount++
                        Log.d(TAG, "✅ USB data signaling enabled (Android 12+ API)")
                    } catch (e: Exception) {
                        errorCount++
                        Log.w(TAG, "Could not enable USB data signaling: ${e.message}")
                    }
                } else {
                    Log.d(TAG, "📱 Android 12+ API not available, using fallback method")
                }
                
                // Method 2: Remove user restrictions (fallback for older versions)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        devicePolicyManager.clearUserRestriction(componentName, UserManager.DISALLOW_USB_FILE_TRANSFER)
                        successCount++
                        Log.d(TAG, "✅ USB file transfer user restriction removed")
                    } catch (e: Exception) {
                        errorCount++
                        Log.w(TAG, "Could not remove USB file transfer restriction: ${e.message}")
                    }
                }
                
                val message = if (successCount > 0) {
                    "USB file transfer allowed successfully ($successCount methods applied)"
                } else {
                    "Failed to allow USB file transfer"
                }
                
                Log.d(TAG, "✅ $message")
                createResult("allow_usb", "success", message)
            } else {
                Log.e(TAG, "❌ Cannot allow USB - admin not active")
                createResult("allow_usb", "error", "Device admin not active")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security error allowing USB: ${e.message}")
            createResult("allow_usb", "error", "Security error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error allowing USB: ${e.message}")
            createResult("allow_usb", "error", "Error: ${e.message}")
        }
    }
    
    /**
     * Check current USB file transfer status
     */
    fun getUsbFileTransferStatus(): JSONObject {
        return try {
            if (devicePolicyManager.isAdminActive(componentName)) {
                val isRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    userManager.hasUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER)
                } else {
                    false
                }
                
                createResult("usb_status", "success", "USB transfer ${if (isRestricted) "blocked" else "allowed"}")
                    .apply {
                        put("is_restricted", isRestricted)
                        put("api_level", Build.VERSION.SDK_INT)
                    }
            } else {
                createResult("usb_status", "error", "Device admin not active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking USB status: ${e.message}")
            createResult("usb_status", "error", "Error checking status: ${e.message}")
        }
    }
    fun isAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(componentName)
    }
    
    /**
     * Lock the device immediately
     */
    fun lockDevice(): JSONObject {
        return try {
            if (isAdminActive()) {
                devicePolicyManager.lockNow()
                Log.d(TAG, "✅ Device locked successfully")
                createResult("lock", "success", "Device locked successfully")
            } else {
                Log.e(TAG, "❌ Cannot lock device - admin not active")
                createResult("lock", "error", "Device admin not active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error locking device: ${e.message}")
            createResult("lock", "error", "Failed to lock device: ${e.message}")
        }
    }
    
    /**
     * Wipe device data (factory reset)
     */
    fun wipeDevice(externalStorage: Boolean = false): JSONObject {
        return try {
            if (isAdminActive()) {
                val flags = if (externalStorage) {
                    DevicePolicyManager.WIPE_EXTERNAL_STORAGE
                } else {
                    0
                }
                devicePolicyManager.wipeData(flags)
                Log.d(TAG, "✅ Device wipe initiated")
                createResult("wipe", "success", "Device wipe initiated")
            } else {
                Log.e(TAG, "❌ Cannot wipe device - admin not active")
                createResult("wipe", "error", "Device admin not active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error wiping device: ${e.message}")
            createResult("wipe", "error", "Failed to wipe device: ${e.message}")
        }
    }
    
    /**
     * Block app installations
     */
    fun blockAppInstallation(): JSONObject {
        return try {
            if (isAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                devicePolicyManager.addUserRestriction(componentName, UserManager.DISALLOW_INSTALL_APPS)
                Log.d(TAG, "✅ App installation blocked")
                createResult("block_install", "success", "App installation blocked")
            } else {
                Log.e(TAG, "❌ Cannot block installation - admin not active or API level too low")
                createResult("block_install", "error", "Device admin not active or unsupported API level")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error blocking installation: ${e.message}")
            createResult("block_install", "error", "Failed to block installation: ${e.message}")
        }
    }
    
    /**
     * Allow app installations
     */
    fun allowAppInstallation(): JSONObject {
        return try {
            if (isAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                devicePolicyManager.clearUserRestriction(componentName, UserManager.DISALLOW_INSTALL_APPS)
                Log.d(TAG, "✅ App installation allowed")
                createResult("allow_install", "success", "App installation allowed")
            } else {
                Log.e(TAG, "❌ Cannot allow installation - admin not active or API level too low")
                createResult("allow_install", "error", "Device admin not active or unsupported API level")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error allowing installation: ${e.message}")
            createResult("allow_install", "error", "Failed to allow installation: ${e.message}")
        }
    }
    
    /**
     * Disable camera
     */
    fun disableCamera(): JSONObject {
        return try {
            if (isAdminActive()) {
                devicePolicyManager.setCameraDisabled(componentName, true)
                Log.d(TAG, "✅ Camera disabled")
                createResult("disable_camera", "success", "Camera disabled")
            } else {
                Log.e(TAG, "❌ Cannot disable camera - admin not active")
                createResult("disable_camera", "error", "Device admin not active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error disabling camera: ${e.message}")
            createResult("disable_camera", "error", "Failed to disable camera: ${e.message}")
        }
    }
    
    /**
     * Enable camera
     */
    fun enableCamera(): JSONObject {
        return try {
            if (isAdminActive()) {
                devicePolicyManager.setCameraDisabled(componentName, false)
                Log.d(TAG, "✅ Camera enabled")
                createResult("enable_camera", "success", "Camera enabled")
            } else {
                Log.e(TAG, "❌ Cannot enable camera - admin not active")
                createResult("enable_camera", "error", "Device admin not active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enabling camera: ${e.message}")
            createResult("enable_camera", "error", "Failed to enable camera: ${e.message}")
        }
    }
    
    /**
     * Set password requirements
     */
    fun setPasswordPolicy(minLength: Int = 8, requireNumbers: Boolean = true, requireSymbols: Boolean = true): JSONObject {
        return try {
            if (isAdminActive()) {
                devicePolicyManager.setPasswordMinimumLength(componentName, minLength)
                
                var quality = DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC
                if (requireNumbers && requireSymbols) {
                    quality = DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
                } else if (requireNumbers) {
                    quality = DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC
                }
                
                devicePolicyManager.setPasswordQuality(componentName, quality)
                Log.d(TAG, "✅ Password policy set")
                createResult("set_password_policy", "success", "Password policy updated")
            } else {
                Log.e(TAG, "❌ Cannot set password policy - admin not active")
                createResult("set_password_policy", "error", "Device admin not active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting password policy: ${e.message}")
            createResult("set_password_policy", "error", "Failed to set password policy: ${e.message}")
        }
    }
    
    /**
     * Block/unblock specific apps
     */
    fun setAppBlacklist(packageNames: List<String>, block: Boolean): JSONObject {
        return try {
            if (isAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // This requires device owner permissions for full functionality
                // For device admin, we can only add user restrictions
                if (block) {
                    // Note: This is a general restriction, not per-app
                    devicePolicyManager.addUserRestriction(componentName, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                    Log.d(TAG, "✅ App restrictions applied")
                    createResult("block_apps", "success", "App restrictions applied")
                } else {
                    devicePolicyManager.clearUserRestriction(componentName, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                    Log.d(TAG, "✅ App restrictions removed")
                    createResult("allow_apps", "success", "App restrictions removed")
                }
            } else {
                Log.e(TAG, "❌ Cannot modify app restrictions - admin not active or API level too low")
                createResult("block_apps", "error", "Device admin not active or unsupported API level")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error modifying app restrictions: ${e.message}")
            createResult("block_apps", "error", "Failed to modify app restrictions: ${e.message}")
        }
    }
    
    /**
     * Get device information
     */
    fun getDeviceInfo(): JSONObject {
        return try {
            val deviceInfo = JSONObject().apply {
                put("device_id", android.os.Build.SERIAL)
                put("model", android.os.Build.MODEL)
                put("manufacturer", android.os.Build.MANUFACTURER)
                put("android_version", android.os.Build.VERSION.RELEASE)
                put("api_level", android.os.Build.VERSION.SDK_INT)
                put("brand", android.os.Build.BRAND)
                put("device", android.os.Build.DEVICE)
                put("hardware", android.os.Build.HARDWARE)
                put("admin_active", isAdminActive())
                put("camera_disabled", if (isAdminActive()) devicePolicyManager.getCameraDisabled(componentName) else false)
                put("timestamp", System.currentTimeMillis())
                
                // Add installed apps count
                put("installed_apps_count", packageManager.getInstalledApplications(PackageManager.GET_META_DATA).size)
                
                // Add user restrictions
                val restrictions = JSONArray()
                if (isAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    val userRestrictions = listOf(
                        UserManager.DISALLOW_INSTALL_APPS,
                        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                        UserManager.DISALLOW_UNINSTALL_APPS
                    )
                    userRestrictions.forEach { restriction ->
                        try {
                            if (userManager.hasUserRestriction(restriction)) {
                                restrictions.put(restriction)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not check restriction $restriction: ${e.message}")
                        }
                    }
                }
                put("active_restrictions", restrictions)
            }
            
            Log.d(TAG, "✅ Device info collected")
            createResult("device_info", "success", "Device info collected", deviceInfo)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error collecting device info: ${e.message}")
            createResult("device_info", "error", "Failed to collect device info: ${e.message}")
        }
    }
    
    /**
     * Get installed applications
     */
    fun getInstalledApps(): JSONObject {
        return try {
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val appsList = JSONArray()
            
            apps.forEach { appInfo ->
                val appData = JSONObject().apply {
                    put("package_name", appInfo.packageName)
                    put("app_name", packageManager.getApplicationLabel(appInfo).toString())
                    put("version_code", try {
                        packageManager.getPackageInfo(appInfo.packageName, 0).versionCode
                    } catch (e: Exception) { -1 })
                    put("version_name", try {
                        packageManager.getPackageInfo(appInfo.packageName, 0).versionName
                    } catch (e: Exception) { "unknown" })
                    put("is_system_app", (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                    put("enabled", appInfo.enabled)
                }
                appsList.put(appData)
            }
            
            Log.d(TAG, "✅ Installed apps collected: ${apps.size} apps")
            createResult("installed_apps", "success", "${apps.size} apps found", 
                JSONObject().put("apps", appsList).put("count", apps.size))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error collecting installed apps: ${e.message}")
            createResult("installed_apps", "error", "Failed to collect apps: ${e.message}")
        }
    }
    
    /**
     * Apply comprehensive system restrictions to disable various user activities
     */
    fun applySystemLockdown(): JSONObject {
        return try {
            if (isAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val restrictions = mapOf(
                    // Contact Management
                    UserManager.DISALLOW_MODIFY_ACCOUNTS to true,
                    UserManager.DISALLOW_ADD_USER to true,
                    
                    // App and Content Management
                    UserManager.DISALLOW_INSTALL_APPS to true,
                    UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES to true,
                    UserManager.DISALLOW_UNINSTALL_APPS to true,
                    
                    // System Settings
                    UserManager.DISALLOW_CONFIG_WIFI to true,
                    UserManager.DISALLOW_CONFIG_BLUETOOTH to true,
                    UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS to true,
                    UserManager.DISALLOW_CONFIG_TETHERING to true,
                    UserManager.DISALLOW_CONFIG_VPN to true,
                    
                    // Device Features
                    UserManager.DISALLOW_FACTORY_RESET to true,
                    UserManager.DISALLOW_ADD_MANAGED_PROFILE to true,
                    UserManager.DISALLOW_REMOVE_MANAGED_PROFILE to true,
                    
                    // Content and Storage
                    UserManager.DISALLOW_SHARE_LOCATION to true,
                    UserManager.DISALLOW_USB_FILE_TRANSFER to true,
                    
                    // Development and Debugging
                    UserManager.DISALLOW_DEBUGGING_FEATURES to true,
                    
                    // Additional restrictions for newer APIs
                    UserManager.DISALLOW_DATA_ROAMING to true,
                    UserManager.DISALLOW_SMS to true,
                    UserManager.DISALLOW_OUTGOING_CALLS to true
                )
                
                restrictions.forEach { (restriction, enable) ->
                    try {
                        if (enable) {
                            devicePolicyManager.addUserRestriction(componentName, restriction)
                            Log.d(TAG, "✅ Applied restriction: $restriction")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not apply restriction $restriction: ${e.message}")
                    }
                }
                
                Log.d(TAG, "✅ System lockdown applied")
                createResult("apply_lockdown", "success", "System lockdown restrictions applied")
            } else {
                Log.e(TAG, "❌ Cannot apply lockdown - admin not active or API level too low")
                createResult("apply_lockdown", "error", "Device admin not active or unsupported API level")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error applying system lockdown: ${e.message}")
            createResult("apply_lockdown", "error", "Failed to apply lockdown: ${e.message}")
        }
    }

    /**
     * Remove system restrictions to restore normal functionality
     */
    fun removeSystemLockdown(): JSONObject {
        return try {
            if (isAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val restrictions = listOf(
                    UserManager.DISALLOW_MODIFY_ACCOUNTS,
                    UserManager.DISALLOW_ADD_USER,
                    UserManager.DISALLOW_INSTALL_APPS,
                    UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                    UserManager.DISALLOW_UNINSTALL_APPS,
                    UserManager.DISALLOW_CONFIG_WIFI,
                    UserManager.DISALLOW_CONFIG_BLUETOOTH,
                    UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
                    UserManager.DISALLOW_CONFIG_TETHERING,
                    UserManager.DISALLOW_CONFIG_VPN,
                    UserManager.DISALLOW_FACTORY_RESET,
                    UserManager.DISALLOW_ADD_MANAGED_PROFILE,
                    UserManager.DISALLOW_REMOVE_MANAGED_PROFILE,
                    UserManager.DISALLOW_SHARE_LOCATION,
                    UserManager.DISALLOW_USB_FILE_TRANSFER,
                    UserManager.DISALLOW_DEBUGGING_FEATURES,
                    UserManager.DISALLOW_DATA_ROAMING,
                    UserManager.DISALLOW_SMS,
                    UserManager.DISALLOW_OUTGOING_CALLS
                )
                
                restrictions.forEach { restriction ->
                    try {
                        devicePolicyManager.clearUserRestriction(componentName, restriction)
                        Log.d(TAG, "✅ Removed restriction: $restriction")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not remove restriction $restriction: ${e.message}")
                    }
                }
                
                Log.d(TAG, "✅ System lockdown removed")
                createResult("remove_lockdown", "success", "System lockdown restrictions removed")
            } else {
                Log.e(TAG, "❌ Cannot remove lockdown - admin not active or API level too low")
                createResult("remove_lockdown", "error", "Device admin not active or unsupported API level")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error removing system lockdown: ${e.message}")
            createResult("remove_lockdown", "error", "Failed to remove lockdown: ${e.message}")
        }
    }

    /**
     * Block specific app categories (contacts, notes, messaging, etc.)
     */
    fun blockAppCategories(): JSONObject {
        return try {
            if (isAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Block common system apps that allow content creation
                val restrictedCategories = mapOf(
                    // Contact and communication restrictions
                    UserManager.DISALLOW_MODIFY_ACCOUNTS to true,
                    UserManager.DISALLOW_SMS to true,
                    UserManager.DISALLOW_OUTGOING_CALLS to true,
                    
                    // Prevent new installations
                    UserManager.DISALLOW_INSTALL_APPS to true,
                    UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES to true,
                    
                    // File and content sharing
                    UserManager.DISALLOW_USB_FILE_TRANSFER to true,
                    UserManager.DISALLOW_SHARE_LOCATION to true
                )
                
                restrictedCategories.forEach { (restriction, enable) ->
                    try {
                        if (enable) {
                            devicePolicyManager.addUserRestriction(componentName, restriction)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not apply category restriction $restriction: ${e.message}")
                    }
                }
                
                Log.d(TAG, "✅ App category restrictions applied")
                createResult("block_categories", "success", "App category restrictions applied")
            } else {
                Log.e(TAG, "❌ Cannot block categories - admin not active or API level too low")
                createResult("block_categories", "error", "Device admin not active or unsupported API level")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error blocking app categories: ${e.message}")
            createResult("block_categories", "error", "Failed to block categories: ${e.message}")
        }
    }

    /**
     * Unblock app categories - restore category functionality
     */
    fun unblockAppCategories(): JSONObject {
        return try {
            if (isAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val restrictions = listOf(
                    UserManager.DISALLOW_MODIFY_ACCOUNTS,
                    UserManager.DISALLOW_SMS,
                    UserManager.DISALLOW_OUTGOING_CALLS,
                    UserManager.DISALLOW_INSTALL_APPS,
                    UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                    UserManager.DISALLOW_USB_FILE_TRANSFER,
                    UserManager.DISALLOW_SHARE_LOCATION
                )
                
                restrictions.forEach { restriction ->
                    try {
                        devicePolicyManager.clearUserRestriction(componentName, restriction)
                        Log.d(TAG, "✅ Removed category restriction: $restriction")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not remove category restriction $restriction: ${e.message}")
                    }
                }
                
                Log.d(TAG, "✅ App category restrictions removed")
                createResult("unblock_categories", "success", "App category restrictions removed")
            } else {
                Log.e(TAG, "❌ Cannot unblock categories - admin not active or API level too low")
                createResult("unblock_categories", "error", "Device admin not active or unsupported API level")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unblocking app categories: ${e.message}")
            createResult("unblock_categories", "error", "Failed to unblock categories: ${e.message}")
        }
    }

    /**
     * Disable device features that allow content creation/modification
     */
    fun disableContentCreation(): JSONObject {
        return try {
            if (isAdminActive()) {
                val result = JSONObject()
                var successCount = 0
                var errorCount = 0
                
                // Disable camera (prevents photo/video creation)
                try {
                    devicePolicyManager.setCameraDisabled(componentName, true)
                    successCount++
                    Log.d(TAG, "✅ Camera disabled")
                } catch (e: Exception) {
                    errorCount++
                    Log.w(TAG, "Could not disable camera: ${e.message}")
                }
                
                // Apply user restrictions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val restrictions = listOf(
                        UserManager.DISALLOW_MODIFY_ACCOUNTS,
                        UserManager.DISALLOW_SMS,
                        UserManager.DISALLOW_USB_FILE_TRANSFER,
                        UserManager.DISALLOW_SHARE_LOCATION
                    )
                    
                    restrictions.forEach { restriction ->
                        try {
                            devicePolicyManager.addUserRestriction(componentName, restriction)
                            successCount++
                        } catch (e: Exception) {
                            errorCount++
                            Log.w(TAG, "Could not apply restriction $restriction: ${e.message}")
                        }
                    }
                }
                
                val message = "Content creation disabled: $successCount succeeded, $errorCount failed"
                Log.d(TAG, "✅ $message")
                createResult("disable_content_creation", "success", message)
            } else {
                Log.e(TAG, "❌ Cannot disable content creation - admin not active")
                createResult("disable_content_creation", "error", "Device admin not active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error disabling content creation: ${e.message}")
            createResult("disable_content_creation", "error", "Failed to disable content creation: ${e.message}")
        }
    }

    /**
     * Enable content creation - restore content creation capabilities
     */
    fun enableContentCreation(): JSONObject {
        return try {
            if (isAdminActive()) {
                var successCount = 0
                var errorCount = 0
                
                // Enable camera (allows photo/video creation)
                try {
                    devicePolicyManager.setCameraDisabled(componentName, false)
                    successCount++
                    Log.d(TAG, "✅ Camera enabled")
                } catch (e: Exception) {
                    errorCount++
                    Log.w(TAG, "Could not enable camera: ${e.message}")
                }
                
                // Remove user restrictions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val restrictions = listOf(
                        UserManager.DISALLOW_MODIFY_ACCOUNTS,
                        UserManager.DISALLOW_SMS,
                        UserManager.DISALLOW_USB_FILE_TRANSFER,
                        UserManager.DISALLOW_SHARE_LOCATION
                    )
                    
                    restrictions.forEach { restriction ->
                        try {
                            devicePolicyManager.clearUserRestriction(componentName, restriction)
                            successCount++
                            Log.d(TAG, "✅ Removed restriction: $restriction")
                        } catch (e: Exception) {
                            errorCount++
                            Log.w(TAG, "Could not remove restriction $restriction: ${e.message}")
                        }
                    }
                }
                
                val message = "Content creation enabled: $successCount succeeded, $errorCount failed"
                Log.d(TAG, "✅ $message")
                createResult("enable_content_creation", "success", message)
            } else {
                Log.e(TAG, "❌ Cannot enable content creation - admin not active")
                createResult("enable_content_creation", "error", "Device admin not active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enabling content creation: ${e.message}")
            createResult("enable_content_creation", "error", "Failed to enable content creation: ${e.message}")
        }
    }
    
    /**
     * Set system settings restrictions (flexible method for custom restrictions)
     */
    fun setSystemRestrictions(restrictions: Map<String, Boolean>): JSONObject {
        return try {
            if (isAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                restrictions.forEach { (restriction, enable) ->
                    try {
                        if (enable) {
                            devicePolicyManager.addUserRestriction(componentName, restriction)
                        } else {
                            devicePolicyManager.clearUserRestriction(componentName, restriction)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not set restriction $restriction: ${e.message}")
                    }
                }
                Log.d(TAG, "✅ System restrictions updated")
                createResult("system_restrictions", "success", "System restrictions updated")
            } else {
                Log.e(TAG, "❌ Cannot set system restrictions - admin not active or API level too low")
                createResult("system_restrictions", "error", "Device admin not active or unsupported API level")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting system restrictions: ${e.message}")
            createResult("system_restrictions", "error", "Failed to set restrictions: ${e.message}")
        }
    }
    
    private fun createResult(command: String, status: String, message: String, data: JSONObject? = null): JSONObject {
        return JSONObject().apply {
            put("command", command)
            put("status", status)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
            put("device_id", android.os.Build.SERIAL)
            data?.let { put("data", it) }
        }
    }
    
    /**
     * Lock specific apps (using AppManager integration)
     */
    fun lockApps(packageNames: List<String>): JSONObject {
        return try {
            val appManager = AppManager(context)
            var successCount = 0
            var errorCount = 0
            
            packageNames.forEach { packageName ->
                if (appManager.lockApp(packageName)) {
                    successCount++
                    Log.d(TAG, "✅ Locked app: $packageName")
                } else {
                    errorCount++
                    Log.w(TAG, "❌ Failed to lock app: $packageName")
                }
            }
            
            val message = "App locking completed: $successCount succeeded, $errorCount failed"
            Log.d(TAG, message)
            createResult("lock_apps", "success", message, 
                JSONObject().apply {
                    put("locked_count", successCount)
                    put("failed_count", errorCount)
                    put("total_requested", packageNames.size)
                })
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error locking apps: ${e.message}")
            createResult("lock_apps", "error", "Failed to lock apps: ${e.message}")
        }
    }
    
    /**
     * Unlock specific apps (using AppManager integration)
     */
    fun unlockApps(packageNames: List<String>): JSONObject {
        return try {
            val appManager = AppManager(context)
            var successCount = 0
            var errorCount = 0
            
            packageNames.forEach { packageName ->
                if (appManager.unlockApp(packageName)) {
                    successCount++
                    Log.d(TAG, "✅ Unlocked app: $packageName")
                } else {
                    errorCount++
                    Log.w(TAG, "❌ Failed to unlock app: $packageName")
                }
            }
            
            val message = "App unlocking completed: $successCount succeeded, $errorCount failed"
            Log.d(TAG, message)
            createResult("unlock_apps", "success", message, 
                JSONObject().apply {
                    put("unlocked_count", successCount)
                    put("failed_count", errorCount)
                    put("total_requested", packageNames.size)
                })
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unlocking apps: ${e.message}")
            createResult("unlock_apps", "error", "Failed to unlock apps: ${e.message}")
        }
    }
    
    /**
     * Hide specific apps (using AppManager integration)
     */
    fun hideApps(packageNames: List<String>): JSONObject {
        return try {
            val appManager = AppManager(context)
            var successCount = 0
            var errorCount = 0
            
            packageNames.forEach { packageName ->
                if (appManager.hideApp(packageName)) {
                    successCount++
                    Log.d(TAG, "✅ Hidden app: $packageName")
                } else {
                    errorCount++
                    Log.w(TAG, "❌ Failed to hide app: $packageName")
                }
            }
            
            val message = "App hiding completed: $successCount succeeded, $errorCount failed"
            Log.d(TAG, message)
            createResult("hide_apps", "success", message, 
                JSONObject().apply {
                    put("hidden_count", successCount)
                    put("failed_count", errorCount)
                    put("total_requested", packageNames.size)
                })
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error hiding apps: ${e.message}")
            createResult("hide_apps", "error", "Failed to hide apps: ${e.message}")
        }
    }
    
    /**
     * Show specific apps (using AppManager integration)
     */
    fun showApps(packageNames: List<String>): JSONObject {
        return try {
            val appManager = AppManager(context)
            var successCount = 0
            var errorCount = 0
            
            packageNames.forEach { packageName ->
                if (appManager.showApp(packageName)) {
                    successCount++
                    Log.d(TAG, "✅ Shown app: $packageName")
                } else {
                    errorCount++
                    Log.w(TAG, "❌ Failed to show app: $packageName")
                }
            }
            
            val message = "App showing completed: $successCount succeeded, $errorCount failed"
            Log.d(TAG, message)
            createResult("show_apps", "success", message, 
                JSONObject().apply {
                    put("shown_count", successCount)
                    put("failed_count", errorCount)
                    put("total_requested", packageNames.size)
                })
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing apps: ${e.message}")
            createResult("show_apps", "error", "Failed to show apps: ${e.message}")
        }
    }
    
    /**
     * Hide apps from device launcher (system-wide)
     */
    fun hideAppsFromDevice(packageNames: List<String>): JSONObject {
        return try {
            if (isAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                var successCount = 0
                var errorCount = 0
                
                packageNames.forEach { packageName ->
                    try {
                        // Hide the app from launcher
                        devicePolicyManager.setApplicationHidden(componentName, packageName, true)
                        successCount++
                        Log.d(TAG, "✅ Hidden app from device: $packageName")
                    } catch (e: Exception) {
                        errorCount++
                        Log.w(TAG, "❌ Failed to hide app from device: $packageName - ${e.message}")
                    }
                }
                
                val message = "Device app hiding completed: $successCount succeeded, $errorCount failed"
                Log.d(TAG, message)
                createResult("hide_apps_device", "success", message, 
                    JSONObject().apply {
                        put("hidden_count", successCount)
                        put("failed_count", errorCount)
                        put("total_requested", packageNames.size)
                    })
            } else {
                Log.e(TAG, "❌ Cannot hide apps from device - admin not active or API level too low")
                createResult("hide_apps_device", "error", "Device admin not active or unsupported API level")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error hiding apps from device: ${e.message}")
            createResult("hide_apps_device", "error", "Failed to hide apps from device: ${e.message}")
        }
    }
    
    /**
     * Show apps on device launcher (system-wide)
     */
    fun showAppsOnDevice(packageNames: List<String>): JSONObject {
        return try {
            if (isAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                var successCount = 0
                var errorCount = 0
                
                packageNames.forEach { packageName ->
                    try {
                        // Show the app in launcher
                        devicePolicyManager.setApplicationHidden(componentName, packageName, false)
                        successCount++
                        Log.d(TAG, "✅ Shown app on device: $packageName")
                    } catch (e: Exception) {
                        errorCount++
                        Log.w(TAG, "❌ Failed to show app on device: $packageName - ${e.message}")
                    }
                }
                
                val message = "Device app showing completed: $successCount succeeded, $errorCount failed"
                Log.d(TAG, message)
                createResult("show_apps_device", "success", message, 
                    JSONObject().apply {
                        put("shown_count", successCount)
                        put("failed_count", errorCount)
                        put("total_requested", packageNames.size)
                    })
            } else {
                Log.e(TAG, "❌ Cannot show apps on device - admin not active or API level too low")
                createResult("show_apps_device", "error", "Device admin not active or unsupported API level")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing apps on device: ${e.message}")
            createResult("show_apps_device", "error", "Failed to show apps on device: ${e.message}")
        }
    }
    
    /**
     * Check if app is hidden on device
     */
    fun isAppHiddenOnDevice(packageName: String): Boolean {
        return try {
            if (isAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                devicePolicyManager.isApplicationHidden(componentName, packageName)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "❌ Error checking if app is hidden on device: ${e.message}")
            false
        }
    }
}
