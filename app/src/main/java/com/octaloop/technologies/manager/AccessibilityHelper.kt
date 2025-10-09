package com.octaloop.technologies.manager

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

object AccessibilityHelper {
    private const val TAG = "AccessibilityHelper"
    
    fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        if (enabledServices.isNullOrEmpty()) {
            Log.d(TAG, "No accessibility services enabled")
            return false
        }
        
        val serviceName = "${context.packageName}/${service.name}"
        val isEnabled = enabledServices.contains(serviceName)
        
        Log.d(TAG, "Checking accessibility service: $serviceName")
        Log.d(TAG, "Enabled services: $enabledServices")
        Log.d(TAG, "Service is enabled: $isEnabled")
        
        return isEnabled
    }
    
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings: ${e.message}")
        }
    }
    
    fun checkAndPromptAccessibility(context: Context): Boolean {
        return if (!isAccessibilityServiceEnabled(context, MdmAccessibilityService::class.java)) {
            Log.w(TAG, "🚨 Accessibility service not enabled - app locking will not work!")
            false
        } else {
            Log.d(TAG, "✅ Accessibility service is enabled - app locking is active")
            true
        }
    }
}
