package com.octaloop.technologies.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "🚀 Device boot completed - starting MDM service")
                startMdmService(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "📦 Package updated - restarting MDM service")
                startMdmService(context)
            }
        }
    }
    
    private fun startMdmService(context: Context) {
        try {
            val serviceIntent = Intent(context, MdmBackgroundService::class.java).apply {
                action = MdmBackgroundService.ACTION_START_SERVICE
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "✅ MDM service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start MDM service: ${e.message}")
        }
    }
}
