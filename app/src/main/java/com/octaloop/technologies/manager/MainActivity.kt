package com.octaloop.technologies.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.octaloop.technologies.manager.SocketManager

class MainActivity : ComponentActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var compName: ComponentName
    private lateinit var socketManager: SocketManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "🔥 App launched successfully")
        
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, MyDeviceAdminReceiver::class.java)
        socketManager = SocketManager(this)
        
        setContent {
            MdmApp(
                isAdminActive = devicePolicyManager.isAdminActive(compName),
                onEnableAdmin = { enableAdmin() },
                onLockDevice = { lockDevice() }
            )
        }
        
        // Initialize socket connection
        initializeSocket()
    }
    
    private fun initializeSocket() {
        Log.d("MainActivity", "🔌 Initializing socket connection...")
        socketManager.connect()
    }
    
    override fun onResume() {
        super.onResume()
        // Reconnect if needed when app comes to foreground
//        if (!socketManager.isConnected()) {
//            Log.d("MainActivity", "🔄 Reconnecting socket on resume...")
//            socketManager.connect()
//        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "🔌 Disconnecting socket on destroy...")
        socketManager.disconnect()
    }
    private fun enableAdmin() {
        if (!devicePolicyManager.isAdminActive(compName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Grant MDM permissions.")
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Already active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun lockDevice() {
        if (devicePolicyManager.isAdminActive(compName)) {
            devicePolicyManager.lockNow()
        } else {
            Toast.makeText(this, "Enable Admin First", Toast.LENGTH_SHORT).show()
        }
    }
}


@Composable
fun MdmApp(
    isAdminActive: Boolean,
    onEnableAdmin: () -> Unit,
    onLockDevice: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Octaloop MDM Manager",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
                            text = "Socket Status",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Connecting to server...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onEnableAdmin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isAdminActive) "Admin Active ✓" else "Enable Device Admin")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onLockDevice,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isAdminActive
                ) {
                    Text("Lock Device")
                }
            }
        }
    }
}

