package com.octaloop.technologies.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import android.os.Handler
import android.os.Looper

class AppLaunchReceiver : BroadcastReceiver() {
    private val TAG = "AppLaunchReceiver"
    
    companion object {
        fun register(context: Context): AppLaunchReceiver {
            val receiver = AppLaunchReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            context.registerReceiver(receiver, filter)
            Log.d("AppLaunchReceiver", "📡 App launch receiver registered")
            return receiver
        }
        
        fun unregister(context: Context, receiver: AppLaunchReceiver) {
            try {
                context.unregisterReceiver(receiver)
                Log.d("AppLaunchReceiver", "📡 App launch receiver unregistered")
            } catch (e: Exception) {
                Log.w("AppLaunchReceiver", "Failed to unregister receiver: ${e.message}")
            }
        }
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        try {
            val packageName = intent.data?.schemeSpecificPart
            val action = intent.action
            
            Log.d(TAG, "📱 Package action: $action for package: $packageName")
            
            if (packageName != null) {
                val appManager = AppManager(context)
                
                // Check if this is a launch attempt of a locked app
                if (appManager.isAppLocked(packageName)) {
                    Log.d(TAG, "🚫 Attempted to launch locked app: $packageName")
                    
                    // Show blocking message
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            "🔒 Access Blocked: This app has been locked by administrator",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    
                    // Try to go back to home
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(homeIntent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in app launch receiver: ${e.message}")
        }
    }
}
