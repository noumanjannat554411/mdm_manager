package com.octaloop.technologies.manager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object DeviceInfoCollector {
    
    private const val TAG = "DeviceInfoCollector"
    
    fun getCompleteDeviceInfo(context: Context): JSONObject {
        return JSONObject().apply {
            put("basic_info", getBasicDeviceInfo())
            put("hardware_info", getHardwareInfo(context))
            put("network_info", getNetworkInfo(context))
            put("storage_info", getStorageInfo())
            put("battery_info", getBatteryInfo(context))
            put("location_info", getLocationInfo(context))
            put("security_info", getSecurityInfo(context))
            put("system_apps", getSystemApps(context))
            put("installed_apps", getInstalledApps(context))
            put("device_settings", getDeviceSettings(context))
            put("timestamp", System.currentTimeMillis())
            put("collected_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        }
    }
    
    private fun getBasicDeviceInfo(): JSONObject {
        return JSONObject().apply {
            put("device_id", Build.SERIAL)
            put("model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("brand", Build.BRAND)
            put("device", Build.DEVICE)
            put("product", Build.PRODUCT)
            put("hardware", Build.HARDWARE)
            put("board", Build.BOARD)
            put("android_version", Build.VERSION.RELEASE)
            put("api_level", Build.VERSION.SDK_INT)
            put("build_number", Build.DISPLAY)
            put("fingerprint", Build.FINGERPRINT)
            put("bootloader", Build.BOOTLOADER)
            put("radio_version", Build.getRadioVersion())
        }
    }
    
    private fun getHardwareInfo(context: Context): JSONObject {
        return JSONObject().apply {
            try {
                // CPU Info
                put("cpu_abi", Build.CPU_ABI)
                put("cpu_abi2", Build.CPU_ABI2)
                
                // Memory Info
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val memInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                
                put("total_memory", memInfo.totalMem)
                put("available_memory", memInfo.availMem)
                put("low_memory", memInfo.lowMemory)
                put("memory_threshold", memInfo.threshold)
                
                // Display Info
                val displayMetrics = context.resources.displayMetrics
                put("screen_width", displayMetrics.widthPixels)
                put("screen_height", displayMetrics.heightPixels)
                put("screen_density", displayMetrics.density)
                put("screen_dpi", displayMetrics.densityDpi)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting hardware info: ${e.message}")
                put("error", "Failed to get hardware info")
            }
        }
    }
    
    private fun getNetworkInfo(context: Context): JSONObject {
        return JSONObject().apply {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = connectivityManager.activeNetwork
                
                if (activeNetwork != null) {
                    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    capabilities?.let {
                        put("has_internet", it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                        put("has_wifi", it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                        put("has_cellular", it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                        put("has_ethernet", it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                        put("is_metered", !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                    }
                }
                
                // Telephony Info
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                put("carrier_name", telephonyManager.networkOperatorName)
                put("country_iso", telephonyManager.networkCountryIso)
                put("network_type", telephonyManager.networkType)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting network info: ${e.message}")
                put("error", "Failed to get network info")
            }
        }
    }
    
    private fun getStorageInfo(): JSONObject {
        return JSONObject().apply {
            try {
                // Internal storage
                val internalStat = StatFs(Environment.getDataDirectory().path)
                val internalTotal = internalStat.blockSizeLong * internalStat.blockCountLong
                val internalAvailable = internalStat.blockSizeLong * internalStat.availableBlocksLong
                
                put("internal_total_bytes", internalTotal)
                put("internal_available_bytes", internalAvailable)
                put("internal_used_bytes", internalTotal - internalAvailable)
                put("internal_usage_percent", ((internalTotal - internalAvailable) * 100 / internalTotal))
                
                // External storage
                if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                    val externalStat = StatFs(Environment.getExternalStorageDirectory().path)
                    val externalTotal = externalStat.blockSizeLong * externalStat.blockCountLong
                    val externalAvailable = externalStat.blockSizeLong * externalStat.availableBlocksLong
                    
                    put("external_total_bytes", externalTotal)
                    put("external_available_bytes", externalAvailable)
                    put("external_used_bytes", externalTotal - externalAvailable)
                    put("external_usage_percent", ((externalTotal - externalAvailable) * 100 / externalTotal))
                } else {
                    put("external_storage_available", false)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting storage info: ${e.message}")
                put("error", "Failed to get storage info")
            }
        }
    }
    
    private fun getBatteryInfo(context: Context): JSONObject {
        return JSONObject().apply {
            try {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                
                put("battery_level", batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                put("is_charging", batteryManager.isCharging)
                put("charge_counter", batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
                put("current_average", batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE))
                put("current_now", batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
                put("energy_counter", batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER))
                
                val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                batteryIntent?.let {
                    put("battery_health", it.getIntExtra(BatteryManager.EXTRA_HEALTH, -1))
                    put("battery_status", it.getIntExtra(BatteryManager.EXTRA_STATUS, -1))
                    put("battery_temperature", it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1))
                    put("battery_voltage", it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting battery info: ${e.message}")
                put("error", "Failed to get battery info")
            }
        }
    }
    
    private fun getLocationInfo(context: Context): JSONObject {
        return JSONObject().apply {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                
                put("gps_enabled", locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                put("network_location_enabled", locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                put("passive_location_enabled", locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER))
                
                val providers = JSONArray()
                locationManager.allProviders.forEach { provider ->
                    providers.put(provider)
                }
                put("location_providers", providers)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting location info: ${e.message}")
                put("error", "Failed to get location info")
            }
        }
    }
    
    private fun getSecurityInfo(context: Context): JSONObject {
        return JSONObject().apply {
            try {
                // Screen lock info
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                put("screen_locked", keyguardManager.isKeyguardLocked)
                put("screen_secure", keyguardManager.isKeyguardSecure)
                
                // Developer options
                put("developer_options_enabled", Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
                ) == 1)
                
                // USB debugging
                put("usb_debugging_enabled", Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.ADB_ENABLED, 0
                ) == 1)
                
                // Unknown sources
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    put("unknown_sources_enabled", context.packageManager.canRequestPackageInstalls())
                } else {
                    @Suppress("DEPRECATION")
                    put("unknown_sources_enabled", Settings.Secure.getInt(
                        context.contentResolver,
                        Settings.Secure.INSTALL_NON_MARKET_APPS, 0
                    ) == 1)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting security info: ${e.message}")
                put("error", "Failed to get security info")
            }
        }
    }
    
    private fun getSystemApps(context: Context): JSONArray {
        val systemApps = JSONArray()
        try {
            val packageManager = context.packageManager
            val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            packages.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
                .forEach { appInfo ->
                    val appData = JSONObject().apply {
                        put("package_name", appInfo.packageName)
                        put("app_name", packageManager.getApplicationLabel(appInfo).toString())
                        put("enabled", appInfo.enabled)
                        put("is_system", true)
                    }
                    systemApps.put(appData)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system apps: ${e.message}")
        }
        return systemApps
    }
    
    private fun getInstalledApps(context: Context): JSONArray {
        val installedApps = JSONArray()
        try {
            val packageManager = context.packageManager
            val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            packages.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .forEach { appInfo ->
                    val appData = JSONObject().apply {
                        put("package_name", appInfo.packageName)
                        put("app_name", packageManager.getApplicationLabel(appInfo).toString())
                        put("enabled", appInfo.enabled)
                        put("is_system", false)
                        
                        try {
                            val packageInfo = packageManager.getPackageInfo(appInfo.packageName, 0)
                            put("version_name", packageInfo.versionName)
                            put("version_code", packageInfo.versionCode)
                            put("install_time", packageInfo.firstInstallTime)
                            put("update_time", packageInfo.lastUpdateTime)
                        } catch (e: Exception) {
                            Log.d(TAG, "Could not get package info for ${appInfo.packageName}")
                        }
                    }
                    installedApps.put(appData)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps: ${e.message}")
        }
        return installedApps
    }
    
    private fun getDeviceSettings(context: Context): JSONObject {
        return JSONObject().apply {
            try {
                // Various device settings
                put("auto_time", Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0) == 1)
                put("auto_time_zone", Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, 0) == 1)
                put("airplane_mode", Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
                put("wifi_on", Settings.Global.getInt(context.contentResolver, Settings.Global.WIFI_ON, 0) == 1)
                put("bluetooth_on", Settings.Global.getInt(context.contentResolver, Settings.Global.BLUETOOTH_ON, 0) == 1)
                put("data_roaming", Settings.Global.getInt(context.contentResolver, Settings.Global.DATA_ROAMING, 0) == 1)
                
                // Screen settings
                put("screen_brightness", Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1))
                put("screen_brightness_mode", Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1))
                put("screen_timeout", Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1))
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting device settings: ${e.message}")
                put("error", "Failed to get device settings")
            }
        }
    }
}
