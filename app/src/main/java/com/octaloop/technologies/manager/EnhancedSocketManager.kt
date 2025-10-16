package com.octaloop.technologies.manager

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.json.JSONArray
import java.net.URISyntaxException

class EnhancedSocketManager(private val context: Context) {
    private var socket: Socket? = null
    private var isConnecting = false
    private val mdmPolicyManager = MdmPolicyManager(context)
    private val locationManager = LocationManager(context)
    
    // Get device ID for socket identification
    fun getDeviceId(): String {
        return try {
            android.os.Build.SERIAL.takeIf { it.isNotEmpty() && it != "unknown" }
                ?: android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
        } catch (e: Exception) {
            "mdm_device_${System.currentTimeMillis()}"
        }
    }

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
            val deviceId = getDeviceId()
            Log.d("SocketManager", "🔥 Starting connection process with Device ID: $deviceId")
            Log.d("SocketManager", "📶 Network type: ${NetworkUtils.getNetworkType(context)}")

            val opts = IO.Options().apply {
                // Add ngrok bypass header for free tunnels
                extraHeaders = mapOf(
                    "ngrok-skip-browser-warning" to listOf("true"),
                    "User-Agent" to listOf("Android-MDM-Client/1.0"),
                    "Accept" to listOf("*/*"),
                    "Connection" to listOf("keep-alive"),
                    "X-Device-ID" to listOf(deviceId)  // Send device ID in headers
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
                
                // Additional stability settings with device ID
                query = "client=android&version=1.0&device_id=$deviceId&platform=mdm"
                
                // Use custom OkHttpClient for better ngrok compatibility
                callFactory = NgrokOkHttpClient.createClient()
                webSocketFactory = NgrokOkHttpClient.createClient()
            }

            socket = IO.socket("https://mdm-backend.octaloop.dev", opts)

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
                println("SocketManagerConnected:-✅ Connected successfully to server")
                // Wait a bit before sending registration to ensure connection is stable
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    sendDeviceRegistration()
                    emit("message", "Hello from Android MDM device!")
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
                try {
                    sendDeviceRegistration()
                } catch (e: Exception) {
                    TODO("Not yet implemented")
                }
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
                        val deviceId = getDeviceId()
                        Log.d("SocketManager", "📱 Device registration confirmed for: $deviceId")
                        Log.d("SocketManager", "✅ Registration status: ${data.optString("status")}")
                        Log.d("SocketManager", "💬 Server message: ${data.optString("message")}")
                        Log.d("SocketManager", "🆔 Server assigned ID: ${data.optString("server_device_id", "None")}")
                        
                        // Join device-specific room for targeted commands
                        val joinRoom = JSONObject().apply {
                            put("device_id", deviceId)
                            put("room", "device_$deviceId")
                            put("timestamp", System.currentTimeMillis())
                        }
                        emit("join_device_room", joinRoom)
                        
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error parsing device registration: ${e.message}")
                    }
                }
            }

            // Handle room join confirmation
            on("room_joined") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        Log.d("SocketManager", "🏠 Joined room: ${data.optString("room")}")
                        Log.d("SocketManager", "👥 Room members: ${data.optInt("member_count", 0)}")
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error parsing room join: ${e.message}")
                    }
                }
            }

            // Handle device-specific commands (sent to specific device)
            on("device_command") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        val targetDeviceId = data.optString("target_device_id")
                        val currentDeviceId = getDeviceId()
                        
                        if (targetDeviceId == currentDeviceId || targetDeviceId.isEmpty()) {
                            Log.d("SocketManager", "🎯 Device-specific command received for: $currentDeviceId")
                            handleCommand(data)
                        } else {
                            Log.d("SocketManager", "📋 Command not for this device (target: $targetDeviceId, current: $currentDeviceId)")
                        }
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error handling device command: ${e.message}")
                    }
                }
            }

            // Handle server pings and respond with pong
            on("server_ping") { args ->
                if (args.isNotEmpty()) {
                    println("Server pinged me $args")
                    try {
                        val data = args[0] as JSONObject
                        val deviceId = getDeviceId()
                        Log.d("SocketManager", "💓 Server ping received for device: $deviceId")
                        
                        // Respond with client pong including device ID
                        val pong = JSONObject().apply {
                            put("device_id", deviceId)
                            put("timestamp", System.currentTimeMillis())
                            put("client_id", deviceId)
                            put("status", "alive")
                            put("admin_active", mdmPolicyManager.isAdminActive())
                            put("network_type", NetworkUtils.getNetworkType(context))
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
            // Handle lock command event
            on("lock") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        val targetDeviceId = data.optString("target_device_id")
                        val command = data.optString("command")
                        val fromDeviceId = data.optString("from")
                        val fromSocketId = data.optString("fromSocketId")
                        val timestamp = data.optLong("timestamp")
                        val currentDeviceId = getDeviceId()
                        
                        Log.d("SocketManager", "🔒 Lock event received from server")
                        Log.d("SocketManager", "📋 Command: $command")
                        Log.d("SocketManager", "🎯 Target Device ID: $targetDeviceId")
                        Log.d("SocketManager", "📤 From Device ID: $fromDeviceId")
                        Log.d("SocketManager", "🔌 From Socket ID: $fromSocketId")
                        Log.d("SocketManager", "⏰ Timestamp: $timestamp")
                        println("Lock Command Received with Arguments: ${args[0]}")
                        
                        // Check if this lock command is for this device
                        if (targetDeviceId == currentDeviceId || targetDeviceId.isEmpty()) {
                            Log.d("SocketManager", "🎯 Lock command is for this device: $currentDeviceId")
                            
                            // Execute the lock command
                            val lockResult = mdmPolicyManager.lockDevice()
                            
                            // Emit lock response back to server with targetDeviceId
                            val lockResponse = JSONObject().apply {
                                put("targetDeviceId", currentDeviceId)
                                put("target_device_id", currentDeviceId)
                                put("deviceId", currentDeviceId)
                                put("command", "lock")
                                put("status", "executed")
                                put("message", "Device locked successfully")
                                put("timestamp", System.currentTimeMillis())
                                put("originalTimestamp", timestamp)
                                put("from", fromDeviceId)
                                put("fromSocketId", fromSocketId)
                                put("result", lockResult.optString("status", "success"))
                                put("responseType", "lock_confirmation")
                            }
//                            emit("lock_Response", lockResponse)
                            handleCommand(args[0] as JSONObject)
                            Log.d("SocketManager", "📤 Lock response emitted with targetDeviceId: $currentDeviceId")
                        } else {
                            Log.d("SocketManager", "📋 Lock command not for this device (target: $targetDeviceId, current: $currentDeviceId)")
                            
                            // Emit rejection response
                            val rejectionResponse = JSONObject().apply {
                                put("targetDeviceId", targetDeviceId)
                                put("target_device_id", targetDeviceId)
                                put("deviceId", currentDeviceId)
                                put("command", "lock")
                                put("status", "rejected")
                                put("message", "Command not intended for this device")
                                put("timestamp", System.currentTimeMillis())
                                put("originalTimestamp", timestamp)
                                put("from", fromDeviceId)
                                put("fromSocketId", fromSocketId)
                                put("responseType", "lock_rejection")
                            }
                            emit("lock_Response", rejectionResponse)
                        }
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error handling lock event: ${e.message}")
                        
                        // Emit error response
                        val errorResponse = JSONObject().apply {
                            put("targetDeviceId", getDeviceId())
                            put("target_device_id", getDeviceId())
                            put("deviceId", getDeviceId())
                            put("command", "lock")
                            put("status", "error")
                            put("message", "Error executing lock: ${e.message}")
                            put("timestamp", System.currentTimeMillis())
                            put("responseType", "lock_error")
                            put("error_type", e.javaClass.simpleName)
                        }
                        emit("lock_response", errorResponse)
                    }
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

            // Enhanced command handling
            on("command") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        Log.d("SocketManager", "📋 Command received: $data")
                        println("SocketManager:- 📋 Command received: $data")
                        handleCommand(data)
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error handling command: ${e.message}")
                    }
                }
            }

            on("location_update") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        println("SocketManager:- The data from server is $data")
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error parsing welcome message: ${e.message}")
                    }
                }
            }
            // Handle bulk commands
            on("bulk_commands") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        val commands = data.getJSONArray("commands")
                        Log.d("SocketManager", "📋 Bulk commands received: ${commands.length()} commands")
                        handleBulkCommands(commands)
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error handling bulk commands: ${e.message}")
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
                println("SocketManager:- Event to emit $event and data is $data")
                socket?.emit(event, data)
                println("SocketManager:- Event to emit $event has been emitted")
                Log.d("SocketManager", "📤 Emitted '$event': $data")
            } else {
                println("SocketManager:- Socket is not connected")
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
            val deviceId = getDeviceId()
            val deviceInfo = mdmPolicyManager.getDeviceInfo()
            val payload = JSONObject().apply {
                // Primary identification - deviceId as required parameter
                put("deviceId", deviceId)  // Main deviceId parameter as specified
                put("device_id", deviceId) // Keep both for compatibility
                put("serial_number", android.os.Build.SERIAL)
                put("android_id", android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ))
                
                // Device details
                put("model", android.os.Build.MODEL)
                put("manufacturer", android.os.Build.MANUFACTURER)
                put("brand", android.os.Build.BRAND)
                put("device", android.os.Build.DEVICE)
                put("product", android.os.Build.PRODUCT)
                put("android_version", android.os.Build.VERSION.RELEASE)
                put("api_level", android.os.Build.VERSION.SDK_INT)
                put("build_number", android.os.Build.DISPLAY)
                put("fingerprint", android.os.Build.FINGERPRINT)
                
                // MDM specific info
                put("admin_active", mdmPolicyManager.isAdminActive())
                put("app_version", "1.0.0")
                put("mdm_client_type", "Android-Enterprise")
                put("connection_timestamp", System.currentTimeMillis())
                put("network_type", NetworkUtils.getNetworkType(context))
                
                // Device capabilities
                put("capabilities", getDeviceCapabilities())
                
                // Detailed device info
                deviceInfo.optJSONObject("data")?.let { data ->
                    put("device_info", data)
                }
                
                // Location info (if available)
                try {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                    put("carrier_name", telephonyManager.networkOperatorName ?: "Unknown")
                    put("country_iso", telephonyManager.networkCountryIso ?: "Unknown")
                } catch (e: Exception) {
                    Log.w("SocketManager", "Could not get carrier info: ${e.message}")
                }
            }
            
            emit("register_device", payload)
            Log.d("SocketManager", "📤 Enhanced device registration sent for deviceId: $deviceId")
            Log.d("SocketManager", "📋 Registration payload size: ${payload.toString().length} characters")
        } catch (e: Exception) {
            Log.e("SocketManager", "Error sending device registration: ${e.message}")
        }
    }

    private fun getDeviceCapabilities(): JSONArray {
        return JSONArray().apply {
            put("lock_device")
            put("wipe_device")
            put("block_installation")
            put("allow_installation")
            put("disable_camera")
            put("enable_camera")
            put("set_password_policy")
            put("get_device_info")
            put("get_installed_apps")
            put("set_system_restrictions")
            put("apply_lockdown")
            put("remove_lockdown")
            put("block_categories")
            put("unblock_categories")
            put("disable_content_creation")
            put("enable_content_creation")
            put("restrict_usb")
            put("allow_usb")
            put("get_usb_status")
            put("location_tracking")
            put("remote_control")
            put("content_restriction")
            put("system_lockdown")
        }
    }

    private fun handleCommand(data: JSONObject) {
        try {
            val command = data.optString("command", data.optString("action"))
            println("SocketManager:- 🎯 Processing command: $command")

            val result: JSONObject = when (command) {
                "lock" -> {
                    print("Locking the device")
                    Log.d("SocketManager", "🔒 Lock command received")
                    val lockResult = mdmPolicyManager.lockDevice()
                    
                    // Emit lock event with targetDeviceId
                    val currentDeviceId = getDeviceId()
                    val lockEvent = JSONObject().apply {
                        put("targetDeviceId", currentDeviceId)
                        put("deviceId", currentDeviceId)
                        put("command", "lock")
                        put("status", "executed")
                        put("message", "Device lock command processed")
                        put("timestamp", System.currentTimeMillis())
                        put("result", lockResult.optString("status", "success"))
                    }
                    emit("lock_Response", lockEvent)
                    Log.d("SocketManager", "📤 Lock event emitted with targetDeviceId: $currentDeviceId")
                    
                    lockResult
                }

                "wipe" -> {
                    Log.d("SocketManager", "💥 Wipe command received")
                    val includeExternal = data.optBoolean("include_external", false)
                    val wipeResult = mdmPolicyManager.wipeDevice(includeExternal)
                    wipeResult ?: JSONObject().apply {
                        put("command", "wipe")
                        put("status", "success")
                        put("message", "Device wipe initiated")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                        put("include_external", includeExternal)
                    }
                }

                "block_installation" -> {
                    Log.d("SocketManager", "🚫 Block installation command received")
                    val blockResult = mdmPolicyManager.blockAppInstallation()
                    blockResult ?: JSONObject().apply {
                        put("command", "block_installation")
                        put("status", "success")
                        put("message", "App installation blocked")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "allow_installation" -> {
                    Log.d("SocketManager", "✅ Allow installation command received")
                    val allowResult = mdmPolicyManager.allowAppInstallation()
                    allowResult ?: JSONObject().apply {
                        put("command", "allow_installation")
                        put("status", "success")
                        put("message", "App installation allowed")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "disable_camera" -> {
                    Log.d("SocketManager", "📷 Disable camera command received")
                    val disableResult = mdmPolicyManager.disableCamera()
                    disableResult ?: JSONObject().apply {
                        put("command", "disable_camera")
                        put("status", "success")
                        put("message", "Camera disabled")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "enable_camera" -> {
                    Log.d("SocketManager", "📹 Enable camera command received")
                    val enableResult = mdmPolicyManager.enableCamera()
                    enableResult ?: JSONObject().apply {
                        put("command", "enable_camera")
                        put("status", "success")
                        put("message", "Camera enabled")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "set_password_policy" -> {
                    Log.d("SocketManager", "🔐 Set password policy command received")
                    val minLength = data.optInt("min_length", 8)
                    val requireNumbers = data.optBoolean("require_numbers", true)
                    val requireSymbols = data.optBoolean("require_symbols", true)
                    val policyResult = mdmPolicyManager.setPasswordPolicy(minLength, requireNumbers, requireSymbols)
                    policyResult ?: JSONObject().apply {
                        put("command", "set_password_policy")
                        put("status", "success")
                        put("message", "Password policy set")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                        put("min_length", minLength)
                        put("require_numbers", requireNumbers)
                        put("require_symbols", requireSymbols)
                    }
                }

                "get_device_info" -> {
                    Log.d("SocketManager", "📊 Get device info command received")
                    mdmPolicyManager.getDeviceInfo()
                }

                "get_installed_apps" -> {
                    Log.d("SocketManager", "📱 Get installed apps command received")
                    mdmPolicyManager.getInstalledApps()
                }

                "set_system_restrictions" -> {
                    Log.d("SocketManager", "⚙️ Set system restrictions command received")
                    val restrictions = mutableMapOf<String, Boolean>()
                    val restrictionsData = data.optJSONObject("restrictions")
                    restrictionsData?.keys()?.forEach { key ->
                        restrictions[key] = restrictionsData.getBoolean(key)
                    }
                    val restrictionsResult = mdmPolicyManager.setSystemRestrictions(restrictions)
                    restrictionsResult ?: JSONObject().apply {
                        put("command", "set_system_restrictions")
                        put("status", "success")
                        put("message", "System restrictions set")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                        put("restrictions_count", restrictions.size)
                    }
                }

                "apply_lockdown" -> {
                    Log.d("SocketManager", "🔒 Apply system lockdown command received")
                    val lockdownResult = mdmPolicyManager.applySystemLockdown()
                    lockdownResult ?: JSONObject().apply {
                        put("command", "apply_lockdown")
                        put("status", "success")
                        put("message", "System lockdown applied")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "remove_lockdown" -> {
                    Log.d("SocketManager", "🔓 Remove system lockdown command received")
                    val removeLockdownResult = mdmPolicyManager.removeSystemLockdown()
                    removeLockdownResult ?: JSONObject().apply {
                        put("command", "remove_lockdown")
                        put("status", "success")
                        put("message", "System lockdown removed")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "block_categories" -> {
                    Log.d("SocketManager", "🚫 Block app categories command received")
                    val blockCategoriesResult = mdmPolicyManager.blockAppCategories()
                    blockCategoriesResult ?: JSONObject().apply {
                        put("command", "block_categories")
                        put("status", "success")
                        put("message", "App categories blocked")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "unblock_categories" -> {
                    Log.d("SocketManager", "✅ Unblock app categories command received")
                    val unblockCategoriesResult = mdmPolicyManager.unblockAppCategories()
                    unblockCategoriesResult ?: JSONObject().apply {
                        put("command", "unblock_categories")
                        put("status", "success")
                        put("message", "App categories unblocked")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "disable_content_creation" -> {
                    Log.d("SocketManager", "✏️❌ Disable content creation command received")
                    val disableContentResult = mdmPolicyManager.disableContentCreation()
                    disableContentResult ?: JSONObject().apply {
                        put("command", "disable_content_creation")
                        put("status", "success")
                        put("message", "Content creation disabled")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "enable_content_creation" -> {
                    Log.d("SocketManager", "✏️✅ Enable content creation command received")
                    val enableContentResult = mdmPolicyManager.enableContentCreation()
                    enableContentResult ?: JSONObject().apply {
                        put("command", "enable_content_creation")
                        put("status", "success")
                        put("message", "Content creation enabled")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "block_apps" -> {
                    Log.d("SocketManager", "🚫 Block apps command received")
                    val packages = mutableListOf<String>()
                    val packagesArray = data.optJSONArray("packages")
                    if (packagesArray != null) {
                        for (i in 0 until packagesArray.length()) {
                            packages.add(packagesArray.getString(i))
                        }
                    }
                    val blockAppsResult = mdmPolicyManager.setAppBlacklist(packages, true)
                    blockAppsResult ?: JSONObject().apply {
                        put("command", "block_apps")
                        put("status", "success")
                        put("message", "Apps blocked")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                        put("packages_count", packages.size)
                    }
                }

                "allow_apps" -> {
                    Log.d("SocketManager", "✅ Allow apps command received")
                    val packages = mutableListOf<String>()
                    val packagesArray = data.optJSONArray("packages")
                    if (packagesArray != null) {
                        for (i in 0 until packagesArray.length()) {
                            packages.add(packagesArray.getString(i))
                        }
                    }
                    val allowAppsResult = mdmPolicyManager.setAppBlacklist(packages, false)
                    allowAppsResult ?: JSONObject().apply {
                        put("command", "allow_apps")
                        put("status", "success")
                        put("message", "Apps allowed")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                        put("packages_count", packages.size)
                    }
                }

                "restrict_usb" -> {
                    Log.d("SocketManager", "🚫 Restrict USB command received")
                    val restrictResult = mdmPolicyManager.restrictUsbFileTransfer()
                    restrictResult ?: JSONObject().apply {
                        put("command", "restrict_usb")
                        put("status", "success")
                        put("message", "USB restricted")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "allow_usb" -> {
                    Log.d("SocketManager", "✅ Allow USB command received")
                    val allowResult = mdmPolicyManager.allowUsbFileTransfer()
                    allowResult ?: JSONObject().apply {
                        put("command", "allow_usb")
                        put("status", "success")
                        put("message", "USB allowed")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "block_usb_file_transfer" -> {
                    Log.d("SocketManager", "🚫 Block USB file transfer command received")
                    val blockUsbResult = mdmPolicyManager.restrictUsbFileTransfer()
                    
                    val result = JSONObject().apply {
                        put("device_id", getDeviceId())
                        put("timestamp", System.currentTimeMillis())
                        put("command", "block_usb_file_transfer")
                        put("status", "success")
                        put("message", "File transfer blocked successfully")
                    }
                    emit("block_usb_file_transfer", result)
                    
                    blockUsbResult ?: result
                }

                "allow_usb_file_transfer" -> {
                    Log.d("SocketManager", "✅ Allow USB file transfer command received")
                    val allowUsbResult = mdmPolicyManager.allowUsbFileTransfer()
                    
                    val result = JSONObject().apply {
                        put("device_id", getDeviceId())
                        put("timestamp", System.currentTimeMillis())
                        put("command", "allow_usb_file_transfer")
                        put("status", "success")
                        put("message", "File transfer unblocked successfully")
                    }
                    emit("allow_usb_file_transfer", result)
                    
                    allowUsbResult ?: result
                }

                "get_usb_status" -> {
                    Log.d("SocketManager", "📊 Get USB status command received")
                    val usbStatusResult = mdmPolicyManager.getUsbFileTransferStatus()
                    
                    val result = JSONObject().apply {
                        put("device_id", getDeviceId())
                        put("timestamp", System.currentTimeMillis())
                        put("command", "get_usb_status")
                        put("status", "success")
                        put("message", "USB status retrieved successfully")
                    }
                    emit("get_usb_status", result)
                    
                    usbStatusResult ?: result
                }

                "ping" -> {
                    Log.d("SocketManager", "🏓 Ping received, sending pong")
                    val deviceId = getDeviceId()
                    val pong = JSONObject().apply {
                        put("device_id", deviceId)
                        put("status", "alive")
                        put("timestamp", System.currentTimeMillis())
                        put("admin_active", mdmPolicyManager.isAdminActive())
                        put("network_type", NetworkUtils.getNetworkType(context))
                    }
                    emit("pong", pong)
                    JSONObject().apply {
                        put("command", "ping")
                        put("status", "success")
                        put("message", "Pong sent")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", deviceId)
                    }
                }

                "status" -> {
                    Log.d("SocketManager", "📊 Status request received")
                    sendDeviceStatus()
                    JSONObject().apply {
                        put("command", "status")
                        put("status", "success")
                        put("message", "Status sent")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "get_location" -> {
                    println("SocketManager:- 📍 Get location command received")

                    val result = JSONObject()
                    locationManager.getCurrentLocation { locationData ->
                        println("SocketManager:- Location data is $locationData")
                        if (locationData != null) {
                            println("SocketManager:- Location data is not null")
                            result.apply {
                                put("command", "get_location")
                                put("status", "success")
                                put("message", "Location obtained successfully")
                                put("timestamp", System.currentTimeMillis())
                                put("device_id", getDeviceId())
                                put("location", locationData.toJson())
                            }
                            println("SocketManager:- Data to send to $result")

                            // Also emit device_location event
                            val deviceLocationEvent = JSONObject().apply {
                                put("device_id", getDeviceId())
                                put("timestamp", System.currentTimeMillis())
                                put("coordinates", JSONObject().apply {
                                    put("latitude", locationData.latitude)
                                    put("longitude", locationData.longitude)
                                    put("accuracy", locationData.accuracy)
                                    put("address", locationData.address)
                                    put("provider", locationData.provider)
                                    put("location_time", locationData.timestamp)
                                })
                                put("location", JSONObject().apply {
                                    put("accuracy", locationData.accuracy)
                                    put("provider", locationData.provider)
                                })


                            }
                            println("SocketManager:- 📍 Device location event emitted: ${locationData.latitude}, ${locationData.longitude}")

                            emit("location_update", deviceLocationEvent)
                            Log.d("SocketManager", "📍 Device location event emitted: ${locationData.latitude}, ${locationData.longitude}")
                        } else {
                            result.apply {
                                println("SocketManager:- Else Runned")
                                put("command", "get_location")
                                put("status", "error")
                                put("message", "Failed to get location")
                                put("timestamp", System.currentTimeMillis())
                                put("device_id", getDeviceId())
                                put("location_status", locationManager.getLocationStatus())
                            }
                        }
                        // Send location result
                        println("SocketManager:- The result is $result")
                        emit("command_result", result)
                        emit("location_update", result)
                    }
                    
                    // Return immediate acknowledgment
                    JSONObject().apply {
                        put("command", "get_location")
                        put("status", "processing")
                        put("message", "Location request processing")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "start_location_tracking" -> {
                    Log.d("SocketManager", "📍 Start location tracking command received")
                    locationManager.startLocationUpdates { locationData ->
                        val locationUpdate = JSONObject().apply {
                            put("event", "location_update")
                            put("device_id", getDeviceId())
                            put("timestamp", System.currentTimeMillis())
                            put("location", locationData.toJson())
                        }
                        emit("location_update", locationUpdate)
                        
                        // Also emit device_location event
                        val deviceLocationEvent = JSONObject().apply {
                            put("device_id", getDeviceId())
                            put("timestamp", System.currentTimeMillis())
                            put("coordinates", JSONObject().apply {
                                put("latitude", locationData.latitude)
                                put("longitude", locationData.longitude)
                                put("accuracy", locationData.accuracy)
                                put("provider", locationData.provider)
                                put("location_time", locationData.timestamp)
                            })
                            put("address", locationData.address)
                        }
                        emit("device_location", deviceLocationEvent)
                        
                        Log.d("SocketManager", "📍 Location update and device location sent: ${locationData.latitude}, ${locationData.longitude}")
                    }
                    
                    JSONObject().apply {
                        put("command", "start_location_tracking")
                        put("status", "success")
                        put("message", "Location tracking started")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                        put("location_status", locationManager.getLocationStatus())
                    }
                }

                "stop_location_tracking" -> {
                    Log.d("SocketManager", "🛑 Stop location tracking command received")
                    locationManager.stopLocationUpdates()
                    
                    JSONObject().apply {
                        put("command", "stop_location_tracking")
                        put("status", "success")
                        put("message", "Location tracking stopped")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }

                "get_location_status" -> {
                    Log.d("SocketManager", "📊 Get location status command received")
                    JSONObject().apply {
                        put("command", "get_location_status")
                        put("status", "success")
                        put("message", "Location status retrieved")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                        put("location_status", locationManager.getLocationStatus())
                    }
                }

                else -> {
                    Log.w("SocketManager", "⚠️ Unknown command: $command")
                    JSONObject().apply {
                        put("command", command)
                        put("status", "error")
                        put("message", "Unknown command: $command")
                        put("timestamp", System.currentTimeMillis())
                        put("device_id", getDeviceId())
                    }
                }
            }

            // Send result back to server
            emit("command_result", result)
            
        } catch (e: Exception) {
            Log.e("SocketManager", "Error handling command: ${e.message}")
            val errorResult = JSONObject().apply {
                put("command", data.optString("command", "unknown"))
                put("status", "error")
                put("message", "Error processing command: ${e.message}")
                put("timestamp", System.currentTimeMillis())
                put("device_id", getDeviceId())
                put("error_type", e.javaClass.simpleName)
            }
            emit("command_result", errorResult)
        }
    }

    private fun handleBulkCommands(commands: JSONArray) {
        val results = JSONArray()
        for (i in 0 until commands.length()) {
            try {
                val command = commands.getJSONObject(i)
                handleCommand(command)
                // Results are sent individually in handleCommand
            } catch (e: Exception) {
                Log.e("SocketManager", "Error processing bulk command $i: ${e.message}")
            }
        }
    }

    private fun sendDeviceStatus() {
        try {
            val deviceId = getDeviceId()
            val deviceInfo = mdmPolicyManager.getDeviceInfo()
            val status = JSONObject().apply {
                put("device_id", deviceId)
                put("serial_number", android.os.Build.SERIAL)
                put("battery_level", getBatteryLevel())
                put("storage_available", getStorageInfo())
                put("last_seen", System.currentTimeMillis())
                put("status", "online")
                put("model", android.os.Build.MODEL)
                put("manufacturer", android.os.Build.MANUFACTURER)
                put("android_version", android.os.Build.VERSION.RELEASE)
                put("admin_active", mdmPolicyManager.isAdminActive())
                put("network_type", NetworkUtils.getNetworkType(context))
                put("capabilities", getDeviceCapabilities())
                put("connection_quality", if (isConnected()) "good" else "poor")
                put("uptime", System.currentTimeMillis())
                
                // Include detailed device info
                deviceInfo.optJSONObject("data")?.let { data ->
                    put("detailed_info", data)
                }
            }
            emit("device_status", status)
            Log.d("SocketManager", "📊 Device status sent for: $deviceId")
        } catch (e: Exception) {
            Log.e("SocketManager", "Error sending device status: ${e.message}")
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            -1
        }
    }

    private fun getStorageInfo(): JSONObject {
        return try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
            val bytesTotal = stat.blockSizeLong * stat.blockCountLong
            
            JSONObject().apply {
                put("available_bytes", bytesAvailable)
                put("total_bytes", bytesTotal)
                put("available_gb", bytesAvailable / (1024 * 1024 * 1024))
                put("total_gb", bytesTotal / (1024 * 1024 * 1024))
                put("usage_percent", ((bytesTotal - bytesAvailable) * 100 / bytesTotal))
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", "Unable to get storage info")
            }
        }
    }
}
