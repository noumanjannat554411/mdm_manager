package com.octaloop.technologies.manager

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Build

class AppInterceptorService : Service() {
    private val TAG = "AppInterceptorService"
    private lateinit var appManager: AppManager
    private lateinit var activityManager: ActivityManager
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private var lastCheckedApp = ""
    
    private val monitoringRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                checkRunningApps()
                handler.postDelayed(this, 2000) // Check every 2 seconds
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        appManager = AppManager(this)
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        Log.d(TAG, "🔍 App Interceptor Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
        }
        return START_STICKY
    }
    
    private fun startMonitoring() {
        if (!isMonitoring) {
            isMonitoring = true
            handler.post(monitoringRunnable)
            Log.d(TAG, "✅ Started app monitoring")
        }
    }
    
    private fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(monitoringRunnable)
        Log.d(TAG, "🛑 Stopped app monitoring")
    }
    
    private fun checkRunningApps() {
        try {
            // For newer Android versions, we need a different approach
            // Since getRunningTasks is heavily restricted, we'll use a different strategy
            
            // Check if our app is in foreground, if not, something else is running
            val runningApps = activityManager.runningAppProcesses
            
            runningApps?.forEach { processInfo ->
                if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    processInfo.pkgList?.forEach { packageName ->
                        checkAndBlockApp(packageName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking running apps: ${e.message}")
        }
    }
    
    private fun checkAndBlockApp(packageName: String?) {
        if (packageName != null && 
            packageName != this.packageName && 
            packageName != "com.android.systemui" && 
            packageName != "android" &&
            packageName != lastCheckedApp) {
            
            lastCheckedApp = packageName
            
            if (appManager.isAppLocked(packageName)) {
                Log.d(TAG, "🚫 Detected locked app running: $packageName")
                showAdminToast(packageName)
                
                // Try to close the app and go home
                goHome()
                
                // Also try to kill the app process if possible
                try {
                    activityManager.killBackgroundProcesses(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not kill background process: ${e.message}")
                }
            }
        }
    }
    
    private fun showAdminToast(packageName: String) {
        try {
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
            
            handler.post {
                Toast.makeText(
                    this@AppInterceptorService, 
                    "🔒 Access Blocked: '$appName' has been locked by administrator", 
                    Toast.LENGTH_LONG
                ).show()
            }
            
            Log.d(TAG, "⚠️ Showed admin toast for app: $appName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing admin toast: ${e.message}")
        }
    }
    
    private fun goHome() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error going to home: ${e.message}")
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        Log.d(TAG, "🔍 App Interceptor Service destroyed")
    }
    
    companion object {
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
    }
}
