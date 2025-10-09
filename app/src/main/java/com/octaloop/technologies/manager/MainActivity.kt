package com.octaloop.technologies.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var compName: ComponentName
    private lateinit var socketManager: EnhancedSocketManager
    private lateinit var mdmPolicyManager: MdmPolicyManager
    private lateinit var appManager: AppManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "🔥 App launched successfully")
        
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, MyDeviceAdminReceiver::class.java)
        socketManager = EnhancedSocketManager(this)
        mdmPolicyManager = MdmPolicyManager(this)
        appManager = AppManager(this)
        
        setContent {
            var currentScreen by remember { mutableStateOf("main") }
            
            when (currentScreen) {
                "main" -> MdmApp(
                    isAdminActive = devicePolicyManager.isAdminActive(compName),
                    onEnableAdmin = { enableAdmin() },
                    onLockDevice = { lockDevice() },
                    onStartService = { startMdmService() },
                    onStopService = { stopMdmService() },
                    onBlockInstallation = { blockInstallation() },
                    onAllowInstallation = { allowInstallation() },
                    onDisableCamera = { disableCamera() },
                    onEnableCamera = { enableCamera() },
                    onApplyLockdown = { applySystemLockdown() },
                    onRemoveLockdown = { removeSystemLockdown() },
                    onBlockCategories = { blockAppCategories() },
                    onUnblockCategories = { unblockAppCategories() },
                    onDisableContentCreation = { disableContentCreation() },
                    onEnableContentCreation = { enableContentCreation() },
                    onManageApps = { currentScreen = "apps" },
                    onEnableAccessibility = { enableAccessibilityService() }
                )
                "apps" -> AppManagementScreen(
                    appManager = appManager,
                    onBack = { currentScreen = "main" }
                )
            }
        }
        
        // Initialize socket connection
        initializeSocket()
        
        // Start background service if admin is active
        if (devicePolicyManager.isAdminActive(compName)) {
            startMdmService()
            startAppInterceptorService()
        }
    }
    
    private fun initializeSocket() {
        Log.d("MainActivity", "🔌 Initializing socket connection...")
        socketManager.connect()
    }
    
    override fun onResume() {
        super.onResume()
        // Reconnect if needed when app comes to foreground
        if (!socketManager.isConnected()) {
            Log.d("MainActivity", "🔄 Reconnecting socket on resume...")
            socketManager.connect()
        }
        
        // Ensure app interceptor is running if admin is active
        if (devicePolicyManager.isAdminActive(compName)) {
            startAppInterceptorService()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "🔌 Disconnecting socket on destroy...")
        // Don't disconnect here as service should handle it
    }
    
    private fun enableAdmin() {
        if (!devicePolicyManager.isAdminActive(compName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Grant MDM permissions for device management.")
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Device Admin Already Active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun lockDevice() {
        val result = mdmPolicyManager.lockDevice()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun blockInstallation() {
        val result = mdmPolicyManager.blockAppInstallation()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun allowInstallation() {
        val result = mdmPolicyManager.allowAppInstallation()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun disableCamera() {
        val result = mdmPolicyManager.disableCamera()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun enableCamera() {
        val result = mdmPolicyManager.enableCamera()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun startMdmService() {
        try {
            val serviceIntent = Intent(this, MdmBackgroundService::class.java).apply {
                action = MdmBackgroundService.ACTION_START_SERVICE
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            Toast.makeText(this, "MDM Service Started", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "✅ MDM background service started")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start service: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "❌ Failed to start MDM service: ${e.message}")
        }
    }
    
    private fun stopMdmService() {
        try {
            val serviceIntent = Intent(this, MdmBackgroundService::class.java).apply {
                action = MdmBackgroundService.ACTION_STOP_SERVICE
            }
            startService(serviceIntent)
            
            Toast.makeText(this, "MDM Service Stopped", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "🛑 MDM background service stopped")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to stop service: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "❌ Failed to stop MDM service: ${e.message}")
        }
    }
    
    private fun applySystemLockdown() {
        val result = mdmPolicyManager.applySystemLockdown()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun removeSystemLockdown() {
        val result = mdmPolicyManager.removeSystemLockdown()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun blockAppCategories() {
        val result = mdmPolicyManager.blockAppCategories()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun unblockAppCategories() {
        val result = mdmPolicyManager.unblockAppCategories()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun disableContentCreation() {
        val result = mdmPolicyManager.disableContentCreation()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun enableContentCreation() {
        val result = mdmPolicyManager.enableContentCreation()
        val message = result.optString("message", "Operation completed")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun startAppInterceptorService() {
        try {
            val serviceIntent = Intent(this, AppInterceptorService::class.java).apply {
                action = AppInterceptorService.ACTION_START_MONITORING
            }
            startService(serviceIntent)
            Log.d("MainActivity", "✅ App Interceptor Service started")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Failed to start App Interceptor Service: ${e.message}")
        }
    }
    
    private fun stopAppInterceptorService() {
        try {
            val serviceIntent = Intent(this, AppInterceptorService::class.java).apply {
                action = AppInterceptorService.ACTION_STOP_MONITORING
            }
            startService(serviceIntent)
            Log.d("MainActivity", "🛑 App Interceptor Service stopped")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Failed to stop App Interceptor Service: ${e.message}")
        }
    }
    
    private fun enableAccessibilityService() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Toast.makeText(
                this, 
                "Please enable 'MDM App Blocker' in Accessibility settings for app blocking to work", 
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open accessibility settings", Toast.LENGTH_SHORT).show()
        }
    }
}


@Composable
fun MdmApp(
    isAdminActive: Boolean,
    onEnableAdmin: () -> Unit,
    onLockDevice: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onBlockInstallation: () -> Unit,
    onAllowInstallation: () -> Unit,
    onDisableCamera: () -> Unit,
    onEnableCamera: () -> Unit,
    onApplyLockdown: () -> Unit,
    onRemoveLockdown: () -> Unit,
    onBlockCategories: () -> Unit,
    onUnblockCategories: () -> Unit,
    onDisableContentCreation: () -> Unit,
    onEnableContentCreation: () -> Unit,
    onManageApps: () -> Unit,
    onEnableAccessibility: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Octaloop MDM Manager",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                
                // Admin Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAdminActive) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Admin Status",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (isAdminActive) "✅ Active" else "❌ Inactive",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isAdminActive) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                // Socket Connection Status
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Connection Status",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "🔄 Connecting to MDM server...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Enable Admin Button
                Button(
                    onClick = onEnableAdmin,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAdminActive
                ) {
                    Text(if (isAdminActive) "Device Admin Active ✓" else "Enable Device Admin")
                }

                // Service Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStartService,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive
                    ) {
                        Text("Start Service")
                    }
                    
                    Button(
                        onClick = onStopService,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Stop Service")
                    }
                }

                // App Management Button
                Button(
                    onClick = onManageApps,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isAdminActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("📱 Manage Apps")
                }

                // Enable Accessibility Service Button
                Button(
                    onClick = onEnableAccessibility,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("🛡️ Enable App Blocking")
                }

                // Device Control Section
                Text(
                    text = "Device Controls",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Button(
                    onClick = onLockDevice,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isAdminActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("🔒 Lock Device")
                }

                // App Installation Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBlockInstallation,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("🚫 Block Install")
                    }
                    
                    Button(
                        onClick = onAllowInstallation,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("✅ Allow Install")
                    }
                }

                // Camera Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDisableCamera,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("📷❌ Disable Camera")
                    }
                    
                    Button(
                        onClick = onEnableCamera,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("📹✅ Enable Camera")
                    }
                }

                // System Lockdown Section
                Text(
                    text = "System Lockdown Controls",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // Comprehensive Lockdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onApplyLockdown,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("🔒 Apply Lockdown")
                    }
                    
                    Button(
                        onClick = onRemoveLockdown,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("🔓 Remove Lockdown")
                    }
                }

                // Content Creation Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDisableContentCreation,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("✏️❌ Block Content")
                    }
                    
                    Button(
                        onClick = onEnableContentCreation,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("✏️✅ Allow Content")
                    }
                }

                // App Category Restrictions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBlockCategories,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("🚫 Block Categories")
                    }
                    
                    Button(
                        onClick = onUnblockCategories,
                        modifier = Modifier.weight(1f),
                        enabled = isAdminActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("✅ Unblock Categories")
                    }
                }
                
                // Information Text
                if (!isAdminActive) {
                    Text(
                        text = "⚠️ Enable Device Admin to access MDM features",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

