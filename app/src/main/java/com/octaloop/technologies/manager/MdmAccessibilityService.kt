package com.octaloop.technologies.manager

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import android.content.Intent
import android.widget.Toast
import android.os.Handler
import android.os.Looper

class MdmAccessibilityService : AccessibilityService() {
    private val TAG = "MdmAccessibilityService"
    private lateinit var appManager: AppManager
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        appManager = AppManager(this)
        Log.d(TAG, "🛡️ MDM Accessibility Service connected")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        try {
            // Check for window state changes (app launches)
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val packageName = event.packageName?.toString()
                
                if (packageName != null && 
                    packageName != this.packageName && 
                    packageName != "com.android.systemui" &&
                    packageName != "android") {
                    
                    if (appManager.isAppLocked(packageName)) {
                        Log.d(TAG, "🚫 Blocked access to locked app: $packageName")
                        blockApp(packageName, "locked")
                    } else if (appManager.isAppHidden(packageName)) {
                        Log.d(TAG, "👁️‍🗨️ Blocked access to hidden app: $packageName")
                        blockApp(packageName, "hidden")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in accessibility event: ${e.message}")
        }
    }
    
    private fun blockApp(packageName: String, blockType: String) {
        try {
            // Get app name for user-friendly message
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
            
            // Show different messages based on block type
            val message = when (blockType) {
                "locked" -> "🔒 Access Blocked: '$appName' has been locked by administrator"
                "hidden" -> "👁️‍🗨️ Access Restricted: '$appName' has been hidden by administrator"
                else -> "� Access Denied: '$appName' is restricted by administrator"
            }
            
            // Show blocking message
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
            
            // Perform back action to exit the app
            performGlobalAction(GLOBAL_ACTION_BACK)
            
            // If back doesn't work, go to home
            Handler(Looper.getMainLooper()).postDelayed({
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(homeIntent)
            }, 500)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error blocking app: ${e.message}")
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "🛡️ MDM Accessibility Service interrupted")
    }
}
