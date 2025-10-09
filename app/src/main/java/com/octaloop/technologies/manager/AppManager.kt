package com.octaloop.technologies.manager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val icon: android.graphics.drawable.Drawable?,
    var isLocked: Boolean = false,
    var isHidden: Boolean = false
)

class AppManager(private val context: Context) {
    private val packageManager = context.packageManager
    private val TAG = "AppManager"
    
    // SharedPreferences to store app lock/hide states
    private val prefs = context.getSharedPreferences("app_manager_prefs", Context.MODE_PRIVATE)
    
    fun getAllInstalledApps(): List<AppInfo> {
        return try {
            val appList = mutableListOf<AppInfo>()
            val mdmPolicyManager = MdmPolicyManager(context)
            val currentPackageName = context.packageName
            
            // Method 1: Get installed applications including hidden ones
            val installedApps = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES)
            } else {
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            }
            
            // Method 2: Also try getting packages to catch any missed apps
            val packageInfoList = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    packageManager.getInstalledPackages(PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES)
                } else {
                    packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                }
            } catch (e: Exception) {
                emptyList()
            }
            
            val processedPackages = mutableSetOf<String>()
            
            // Process applications first
            installedApps.forEach { appInfo ->
                try {
                    if (appInfo.packageName == currentPackageName) {
                        Log.d(TAG, "Skipping our own app: ${appInfo.packageName}")
                        return@forEach
                    }
                    
                    val packageInfo = packageManager.getPackageInfo(appInfo.packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    val isLockedPref = prefs.getBoolean("locked_${appInfo.packageName}", false)
                    val isHiddenPref = prefs.getBoolean("hidden_${appInfo.packageName}", false)
                    val isHiddenDevice = mdmPolicyManager.isAppHiddenOnDevice(appInfo.packageName)
                    
                    val isHidden = isHiddenDevice || isHiddenPref
                    if (isHiddenDevice != isHiddenPref) {
                        prefs.edit().putBoolean("hidden_${appInfo.packageName}", isHiddenDevice).apply()
                    }
                    
                    Log.d(TAG, "App: ${appInfo.packageName}, Hidden Device: $isHiddenDevice, Hidden Pref: $isHiddenPref, Final Hidden: $isHidden")
                    
                    val app = AppInfo(
                        packageName = appInfo.packageName,
                        appName = appName,
                        versionName = packageInfo.versionName ?: "unknown",
                        versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode.toLong()
                        },
                        isSystemApp = isSystemApp,
                        isEnabled = appInfo.enabled,
                        icon = try { packageManager.getApplicationIcon(appInfo.packageName) } catch (e: Exception) { null },
                        isLocked = isLockedPref,
                        isHidden = isHidden
                    )
                    appList.add(app)
                    processedPackages.add(appInfo.packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing app ${appInfo.packageName}: ${e.message}")
                }
            }
            
            // Process any additional packages not found in applications list
            packageInfoList.forEach { packageInfo ->
                try {
                    if (packageInfo.packageName in processedPackages || packageInfo.packageName == currentPackageName) {
                        return@forEach
                    }
                    
                    val applicationInfo = packageInfo.applicationInfo ?: return@forEach
                    val appName = packageManager.getApplicationLabel(applicationInfo).toString()
                    val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    val isLockedPref = prefs.getBoolean("locked_${packageInfo.packageName}", false)
                    val isHiddenPref = prefs.getBoolean("hidden_${packageInfo.packageName}", false)
                    val isHiddenDevice = mdmPolicyManager.isAppHiddenOnDevice(packageInfo.packageName)
                    
                    val isHidden = isHiddenDevice || isHiddenPref
                    if (isHiddenDevice != isHiddenPref) {
                        prefs.edit().putBoolean("hidden_${packageInfo.packageName}", isHiddenDevice).apply()
                    }
                    
                    Log.d(TAG, "Additional App: ${packageInfo.packageName}, Hidden Device: $isHiddenDevice, Hidden Pref: $isHiddenPref, Final Hidden: $isHidden")
                    
                    val app = AppInfo(
                        packageName = packageInfo.packageName,
                        appName = appName,
                        versionName = packageInfo.versionName ?: "unknown",
                        versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode.toLong()
                        },
                        isSystemApp = isSystemApp,
                        isEnabled = applicationInfo.enabled,
                        icon = try { packageManager.getApplicationIcon(packageInfo.packageName) } catch (e: Exception) { null },
                        isLocked = isLockedPref,
                        isHidden = isHidden
                    )
                    appList.add(app)
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing package ${packageInfo.packageName}: ${e.message}")
                }
            }
            
            // Also check our preferences for any apps we've previously hidden
            // This ensures we don't lose track of hidden apps even if they don't appear in the system lists
            val allPrefs = prefs.all
            allPrefs.keys.forEach { key ->
                if (key.startsWith("hidden_") && prefs.getBoolean(key, false)) {
                    val packageName = key.removePrefix("hidden_")
                    if (packageName != currentPackageName && !processedPackages.contains(packageName)) {
                        try {
                            val packageInfo = packageManager.getPackageInfo(packageName, 0)
                            val applicationInfo = packageInfo.applicationInfo ?: return@forEach
                            val appName = packageManager.getApplicationLabel(applicationInfo).toString()
                            val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            
                            val isLockedPref = prefs.getBoolean("locked_$packageName", false)
                            val isHiddenDevice = mdmPolicyManager.isAppHiddenOnDevice(packageName)
                            
                            Log.d(TAG, "From Prefs - Hidden App: $packageName, Hidden Device: $isHiddenDevice")
                            
                            val app = AppInfo(
                                packageName = packageName,
                                appName = appName,
                                versionName = packageInfo.versionName ?: "unknown",
                                versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                    packageInfo.longVersionCode
                                } else {
                                    @Suppress("DEPRECATION")
                                    packageInfo.versionCode.toLong()
                                },
                                isSystemApp = isSystemApp,
                                isEnabled = applicationInfo.enabled,
                                icon = try { packageManager.getApplicationIcon(packageName) } catch (e: Exception) { null },
                                isLocked = isLockedPref,
                                isHidden = true // We know it's hidden from our preferences
                            )
                            appList.add(app)
                            Log.d(TAG, "Added hidden app from preferences: $packageName")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error processing hidden app from preferences $packageName: ${e.message}")
                        }
                    }
                }
            }
            
            // Sort by app name
            appList.sortBy { it.appName.lowercase() }
            
            Log.d(TAG, "✅ Loaded ${appList.size} installed apps (excluding our own app)")
            appList
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting installed apps: ${e.message}")
            emptyList()
        }
    }
    
    fun lockApp(packageName: String): Boolean {
        return try {
            prefs.edit().putBoolean("locked_$packageName", true).apply()
            Log.d(TAG, "🔒 App locked: $packageName")
            Log.d(TAG, "🔍 Verification - App is now locked: ${isAppLocked(packageName)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error locking app $packageName: ${e.message}")
            false
        }
    }
    
    fun unlockApp(packageName: String): Boolean {
        return try {
            prefs.edit().putBoolean("locked_$packageName", false).apply()
            Log.d(TAG, "🔓 App unlocked: $packageName")
            Log.d(TAG, "🔍 Verification - App is now locked: ${isAppLocked(packageName)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unlocking app $packageName: ${e.message}")
            false
        }
    }
    
    fun hideApp(packageName: String): Boolean {
        return try {
            // Use device admin to hide app from launcher
            val mdmPolicyManager = MdmPolicyManager(context)
            val result = mdmPolicyManager.hideAppsFromDevice(listOf(packageName))
            val success = result.optString("status") == "success"
            
            if (success) {
                // Also store in preferences for tracking
                prefs.edit().putBoolean("hidden_$packageName", true).apply()
                Log.d(TAG, "👁️‍🗨️ App hidden from device: $packageName")
            } else {
                Log.w(TAG, "❌ Failed to hide app from device: $packageName")
            }
            
            Log.d(TAG, "🔍 Verification - App is now hidden on device: ${mdmPolicyManager.isAppHiddenOnDevice(packageName)}")
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error hiding app $packageName: ${e.message}")
            false
        }
    }
    
    fun showApp(packageName: String): Boolean {
        return try {
            // Use device admin to show app in launcher
            val mdmPolicyManager = MdmPolicyManager(context)
            val result = mdmPolicyManager.showAppsOnDevice(listOf(packageName))
            val success = result.optString("status") == "success"
            
            if (success) {
                // Also update preferences for tracking
                prefs.edit().putBoolean("hidden_$packageName", false).apply()
                Log.d(TAG, "👁️ App shown on device: $packageName")
            } else {
                Log.w(TAG, "❌ Failed to show app on device: $packageName")
            }
            
            Log.d(TAG, "🔍 Verification - App is now hidden on device: ${mdmPolicyManager.isAppHiddenOnDevice(packageName)}")
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing app $packageName: ${e.message}")
            false
        }
    }
    
    fun isAppLocked(packageName: String): Boolean {
        return prefs.getBoolean("locked_$packageName", false)
    }
    
    fun isAppHidden(packageName: String): Boolean {
        return try {
            val mdmPolicyManager = MdmPolicyManager(context)
            val deviceHidden = mdmPolicyManager.isAppHiddenOnDevice(packageName)
            val prefHidden = prefs.getBoolean("hidden_$packageName", false)
            
            // Return true if hidden at device level or in preferences
            deviceHidden || prefHidden
        } catch (e: Exception) {
            // Fallback to preferences only
            prefs.getBoolean("hidden_$packageName", false)
        }
    }
    
    fun getLockedApps(): List<String> {
        val lockedApps = mutableListOf<String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("locked_") && value == true) {
                lockedApps.add(key.removePrefix("locked_"))
            }
        }
        return lockedApps
    }
    
    fun getHiddenApps(): List<String> {
        val hiddenApps = mutableListOf<String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("hidden_") && value == true) {
                hiddenApps.add(key.removePrefix("hidden_"))
            }
        }
        return hiddenApps
    }
    
    fun clearAllRestrictions(): Boolean {
        return try {
            prefs.edit().clear().apply()
            Log.d(TAG, "🧹 All app restrictions cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing restrictions: ${e.message}")
            false
        }
    }
    


    fun getAppStats(): JSONObject {
        val allApps = getAllInstalledApps()
        val lockedCount = allApps.count { it.isLocked }
        val hiddenCount = allApps.count { it.isHidden }
        val systemAppsCount = allApps.count { it.isSystemApp }
        val userAppsCount = allApps.count { !it.isSystemApp }
        
        return JSONObject().apply {
            put("total_apps", allApps.size)
            put("system_apps", systemAppsCount)
            put("user_apps", userAppsCount)
            put("locked_apps", lockedCount)
            put("hidden_apps", hiddenCount)
            put("enabled_apps", allApps.count { it.isEnabled })
            put("disabled_apps", allApps.count { !it.isEnabled })
        }
    }
}
