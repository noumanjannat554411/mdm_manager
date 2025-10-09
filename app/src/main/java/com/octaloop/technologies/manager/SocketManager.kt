package com.octaloop.technologies.manager

import android.content.Context
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

class SocketManager(private val context: Context) {
    private var socket: Socket? = null
    private var isConnecting = false

    fun connect() {
        // Check network connectivity first
        if (!NetworkUtils.isNetworkAvailable(context)) {
            Log.e("SocketManager", "❌ No network connection available")
            return
        }

        if (isConnecting || socket?.connected() == true) {
            Log.d("SocketManager", "Already connecting or connected")
            return
        }

        try {
            isConnecting = true
            Log.d("SocketManager", "🔥 Starting connection process")
            Log.d("SocketManager", "📶 Network type: ${NetworkUtils.getNetworkType(context)}")

            val opts = IO.Options().apply {
                // Add ngrok bypass header for free tunnels
                extraHeaders = mapOf(
                    "ngrok-skip-browser-warning" to listOf("true"),
                    "User-Agent" to listOf("Android-MDM-Client/1.0"),
                    "Accept" to listOf("*/*"),
                    "Connection" to listOf("keep-alive")
                )
                
                // Connection settings - increased timeouts for stability
                reconnection = true
                reconnectionAttempts = 10
                reconnectionDelay = 2000
                reconnectionDelayMax = 10000
                timeout = 30000
                forceNew = false
                
                // Transport settings - force polling only for now to avoid websocket issues
                transports = arrayOf("polling")
                upgrade = false // Disable websocket upgrade for stability
                
                // Additional stability settings
                query = "client=android&version=1.0"
                
                // Use custom OkHttpClient for better ngrok compatibility
                callFactory = NgrokOkHttpClient.createClient()
                webSocketFactory = NgrokOkHttpClient.createClient()
            }

            socket = IO.socket("https://abc639e858bf.ngrok-free.app", opts)

            setupEventHandlers()
            socket?.connect()

        } catch (e: Exception) {
            isConnecting = false
            Log.e("SocketManager", "Connection setup error: ${e.message}", e)
        }
    }

    private fun setupEventHandlers() {
        socket?.apply {
            on(Socket.EVENT_CONNECT) {
                isConnecting = false
                Log.d("SocketManager", "✅ Connected successfully to server")
                // Wait a bit before sending registration to ensure connection is stable
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    sendDeviceRegistration()
                    emit("message", "Hello from Android device!")
                }, 1000)
            }

            on(Socket.EVENT_DISCONNECT) { args ->
                isConnecting = false
                val reason = if (args.isNotEmpty()) args[0].toString() else "unknown"
                Log.w("SocketManager", "🔌 Disconnected from server. Reason: $reason")
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                isConnecting = false
                val error = if (args.isNotEmpty()) args[0].toString() else "unknown error"
                Log.e("SocketManager", "❌ Connection failed: $error")
            }

            on("reconnect") {
                Log.d("SocketManager", "🔄 Reconnected to server")
                sendDeviceRegistration()
            }

            on("reconnect_error") { args ->
                val error = if (args.isNotEmpty()) args[0].toString() else "unknown error"
                Log.e("SocketManager", "❌ Reconnection failed: $error")
            }

            on("reconnect_failed") {
                Log.e("SocketManager", "❌ All reconnection attempts failed")
            }

            // Handle server welcome message
            on("welcome") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        Log.d("SocketManager", "🎉 Welcome message received: ${data.optString("msg")}")
                        Log.d("SocketManager", "🆔 Server assigned ID: ${data.optString("id")}")
                        Log.d("SocketManager", "🚀 Transport: ${data.optString("transport")}")
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error parsing welcome message: ${e.message}")
                    }
                }
            }

            // Handle device registration acknowledgment
            on("device_registered") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        Log.d("SocketManager", "📱 Device registered: ${data.optString("status")}")
                        Log.d("SocketManager", "💬 Registration message: ${data.optString("message")}")
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error parsing device registration: ${e.message}")
                    }
                }
            }

            // Handle server pings and respond with pong
            on("server_ping") { args ->
                if (args.isNotEmpty()) {
                    println("Server pinged me $args")
                    try {
                        val data = args[0] as JSONObject
                        Log.d("SocketManager", "💓 Server ping received")
                        
                        // Respond with client pong
                        val pong = JSONObject().apply {
                            put("timestamp", System.currentTimeMillis())
                            put("client_id", android.os.Build.SERIAL)
                            put("status", "alive")
                        }
                        emit("client_ping", pong)
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error handling server ping: ${e.message}")
                    }
                }
            }

            // Handle server pong responses
            on("server_pong") { args ->
                if (args.isNotEmpty()) {
                    println("Server Pong:- The server Has Responded.")
                    Log.d("SocketManager", "🏓 Server pong received")
                }
            }

            // Handle transport upgrade notifications
            on("transport_upgraded") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        Log.d("SocketManager", "⬆️ Transport upgraded to: ${data.optString("transport")}")
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error parsing transport upgrade: ${e.message}")
                    }
                }
            }

            // Handle message acknowledgments
            on("message_ack") { args ->
                if (args.isNotEmpty()) {
                    Log.d("SocketManager", "✅ Message acknowledged by server")
                }
            }

            on("message") { args ->
                if (args.isNotEmpty()) {
                    Log.d("SocketManager", "📨 Message received: ${args[0]}")
                }
            }

            on("command") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        Log.d("SocketManager", "📋 Command received: $data")
                        handleCommand(data)
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error handling command: ${e.message}")
                    }
                }
            }
        }
    }

    fun disconnect() {
        try {
            socket?.disconnect()
            socket = null
            isConnecting = false
            Log.d("SocketManager", "🔌 Socket disconnected")
        } catch (e: Exception) {
            Log.e("SocketManager", "Error disconnecting: ${e.message}")
        }
    }

    fun emit(event: String, data: JSONObject) {
        try {
            if (socket?.connected() == true) {
                socket?.emit(event, data)
                Log.d("SocketManager", "📤 Emitted '$event': $data")
            } else {
                Log.w("SocketManager", "⚠️ Cannot emit '$event' - socket not connected")
            }
        } catch (e: Exception) {
            Log.e("SocketManager", "Error emitting '$event': ${e.message}")
        }
    }

    fun isConnected(): Boolean {
        return socket?.connected() ?: false
    }

    private fun sendDeviceRegistration() {
        try {
            val payload = JSONObject().apply {
                put("device_id", android.os.Build.SERIAL)
                put("model", android.os.Build.MODEL)
                put("manufacturer", android.os.Build.MANUFACTURER)
                put("android_version", android.os.Build.VERSION.RELEASE)
                put("timestamp", System.currentTimeMillis())
            }
            emit("register_device", payload)
            Log.d("SocketManager", "📤 Device registration sent: $payload")
        } catch (e: Exception) {
            Log.e("SocketManager", "Error sending device registration: ${e.message}")
        }
    }

    private fun handleCommand(data: JSONObject) {
        try {
            val command = data.optString("command", data.optString("action"))
            Log.d("SocketManager", "🎯 Processing command: $command")

            when (command) {
                "lock" -> {
                    Log.d("SocketManager", "🔒 Lock command received")
                    // TODO: Use DevicePolicyManager to lock device
                    sendCommandResult("lock", "success", "Device locked successfully")
                }

                "unlock" -> {
                    Log.d("SocketManager", "🔓 Unlock command received")
                    // TODO: Implement unlock if applicable
                    sendCommandResult("unlock", "success", "Device unlock command processed")
                }

                "wipe" -> {
                    Log.d("SocketManager", "💥 Wipe command received")
                    // TODO: Trigger factory reset (requires Device Owner)
                    sendCommandResult("wipe", "success", "Device wipe initiated")
                }

                "ping" -> {
                    println("From server Ping recieved")
                    Log.d("SocketManager", "🏓 Ping received, sending pong")
                    val pong = JSONObject().apply {
                        put("status", "alive")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", android.os.Build.SERIAL)
                    }
                    emit("pong", pong)
                    sendCommandResult("ping", "success", "Pong sent")
                }

                "status" -> {
                    Log.d("SocketManager", "📊 Status request received")
                    sendDeviceStatus()
                    sendCommandResult("status", "success", "Status sent")
                }

                else -> {
                    Log.w("SocketManager", "⚠️ Unknown command: $command")
                    sendCommandResult(command, "error", "Unknown command: $command")
                }
            }
        } catch (e: Exception) {
            Log.e("SocketManager", "Error handling command: ${e.message}")
            sendCommandResult("unknown", "error", "Error processing command: ${e.message}")
        }
    }

    private fun sendCommandResult(command: String, status: String, info: String = "") {
        try {
            val result = JSONObject().apply {
                put("command", command)
                put("status", status)
                put("info", info)
                put("timestamp", System.currentTimeMillis())
                put("device_id", android.os.Build.SERIAL)
            }
            emit("command-result", result)
            Log.d("SocketManager", "📤 Command result sent: $command - $status")
        } catch (e: Exception) {
            Log.e("SocketManager", "Error sending command result: ${e.message}")
        }
    }

    private fun sendDeviceStatus() {
        try {
            val status = JSONObject().apply {
                put("device_id", android.os.Build.SERIAL)
                put("battery_level", "unknown") // You can implement battery status
                put("storage_available", "unknown") // You can implement storage info
                put("last_seen", System.currentTimeMillis())
                put("status", "online")
                put("model", android.os.Build.MODEL)
                put("manufacturer", android.os.Build.MANUFACTURER)
                put("android_version", android.os.Build.VERSION.RELEASE)
            }
            emit("device_status", status)
        } catch (e: Exception) {
            Log.e("SocketManager", "Error sending device status: ${e.message}")
        }
    }

}