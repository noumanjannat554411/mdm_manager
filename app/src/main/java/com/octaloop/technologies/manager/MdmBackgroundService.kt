package com.octaloop.technologies.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class MdmBackgroundService : Service() {
    
    private lateinit var socketManager: EnhancedSocketManager
    private val serviceId = 1001
    private val channelId = "MDM_SERVICE_CHANNEL"
    
    companion object {
        private const val TAG = "MdmBackgroundService"
        const val ACTION_START_SERVICE = "START_MDM_SERVICE"
        const val ACTION_STOP_SERVICE = "STOP_MDM_SERVICE"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔧 MDM Background Service created")
        
        socketManager = EnhancedSocketManager(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                Log.d(TAG, "🚀 Starting MDM service")
                startForegroundService()
                connectToServer()
            }
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "🛑 Stopping MDM service")
                stopForegroundService()
            }
            else -> {
                Log.d(TAG, "🚀 Starting MDM service (default)")
                startForegroundService()
                connectToServer()
            }
        }
        
        // Restart service if it gets killed
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "💀 MDM Background Service destroyed")
        socketManager.disconnect()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MDM Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for Mobile Device Management"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundService() {
        val notification = createServiceNotification()
        startForeground(serviceId, notification)
        Log.d(TAG, "📱 Foreground service started")
    }
    
    private fun stopForegroundService() {
        socketManager.disconnect()
        stopForeground(true)
        stopSelf()
        Log.d(TAG, "🛑 Foreground service stopped")
    }
    
    private fun createServiceNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, MdmBackgroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("MDM Manager")
            .setContentText("Device management service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Service",
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun connectToServer() {
        Thread {
            try {
                // Wait a bit before connecting to ensure service is fully started
                Thread.sleep(2000)
                
                if (!socketManager.isConnected()) {
                    Log.d(TAG, "🔌 Connecting to MDM server...")
                    socketManager.connect()
                    
                    // Update notification to show connection status
                    updateNotification("Connected to MDM server")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error connecting to server: ${e.message}")
                updateNotification("Failed to connect to server")
            }
        }.start()
    }
    
    private fun updateNotification(status: String) {
        try {
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("MDM Manager")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(serviceId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification: ${e.message}")
        }
    }
}
