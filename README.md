# Octaloop MDM Manager - Comprehensive Mobile Device Management Solution

A powerful Android Mobile Device Management (MDM) application that provides comprehensive device control capabilities through Socket.IO real-time communication.

## 🚀 Features

### Core MDM Capabilities
- **Device Lock Control** - Remotely lock devices instantly
- **Application Management** - Block/allow app installations
- **Camera Control** - Enable/disable device cameras
- **Factory Reset** - Remote device wiping capability
- **Password Policies** - Enforce strong password requirements
- **System Restrictions** - Control various device features and settings
- **USB Cable Management** - Real-time USB detection and data transfer control
- **Real-time Communication** - Instant command execution via Socket.IO
- **Background Service** - Persistent MDM functionality
- **Auto-start** - Service starts automatically on device boot

### Advanced Features
- **Comprehensive Device Information** - Detailed hardware, software, and usage analytics
- **Network Monitoring** - Track connection types and status
- **Battery Monitoring** - Real-time battery level and health information
- **Storage Analytics** - Internal and external storage usage tracking
- **Application Inventory** - Complete list of installed applications
- **Security Status** - Monitor security settings and compliance
- **Bulk Commands** - Execute multiple commands simultaneously
- **Error Handling** - Robust error reporting and recovery

### 🔌 USB Cable Management
- **Real-time Detection** - Instant USB cable connection/disconnection detection
- **Automatic Blocking** - Auto-block USB data transfer when cable connected
- **Manual Control** - Block/allow USB data transfer on demand
- **Charging-only Mode** - Allow charging while blocking data access
- **Visual Indicators** - Color-coded status cards and real-time updates
- **Server Commands** - Remote USB control via `restrict_usb`, `allow_usb`, `get_usb_status`
- **Android 12+ Support** - Leverages latest USB data signaling APIs
- **Fallback Support** - User restrictions for older Android versions

## 📱 Architecture

### Components Overview

1. **MainActivity** - Main UI and user interaction
2. **EnhancedSocketManager** - Real-time server communication
3. **MdmPolicyManager** - Device policy enforcement
4. **MdmBackgroundService** - Persistent background operations
5. **DeviceInfoCollector** - Comprehensive device analytics
6. **BootReceiver** - Auto-start functionality
7. **MyDeviceAdminReceiver** - Device admin event handling

### Key Classes

#### EnhancedSocketManager
```kotlin
// Handles real-time communication with MDM server
class EnhancedSocketManager(private val context: Context) {
    // Socket.IO connection management
    // Command processing and response handling
    // Device registration and status reporting
}
```

#### MdmPolicyManager
```kotlin
// Manages all device policy operations
class MdmPolicyManager(private val context: Context) {
    // Device locking, wiping, camera control
    // App installation management
    // Password policy enforcement
    // System restrictions
}
```

#### DeviceInfoCollector
```kotlin
// Collects comprehensive device information
object DeviceInfoCollector {
    // Hardware specifications
    // Software details
    // Network and security status
    // Application inventory
}
```

## 🔧 Setup Instructions

### 1. Permissions Configuration

The app requires extensive permissions for full MDM functionality:

```xml
<!-- Device Management -->
<uses-permission android:name="android.permission.MANAGE_DEVICE_ADMINS" />
<uses-permission android:name="android.permission.MANAGE_USERS" />

<!-- Network -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- System Control -->
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Hardware Control -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

### 2. Device Admin Setup

Enable device admin policies in `device_admin_receiver.xml`:

```xml
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <force-lock />
        <wipe-data />
        <disable-camera />
        <limit-password />
        <reset-password />
        <encrypted-storage />
    </uses-policies>
</device-admin>
```

### 3. Server Configuration

Update the server URL in `EnhancedSocketManager`:

```kotlin
socket = IO.socket("https://your-server-url.com", opts)
```

### 4. Background Service

The MDM service runs persistently in the background:

```kotlin
// Auto-starts on device boot
// Maintains server connection
// Processes commands even when app is closed
// Foreground service with notification
```

## 📋 Supported Commands

### Device Control Commands

| Command | Description | Parameters |
|---------|-------------|------------|
| `lock` | Lock device immediately | None |
| `wipe` | Factory reset device | `include_external: boolean` |
| `ping` | Check device connectivity | None |
| `status` | Get device status | None |

### Application Management

| Command | Description | Parameters |
|---------|-------------|------------|
| `block_installation` | Block app installations | None |
| `allow_installation` | Allow app installations | None |
| `block_apps` | Block specific apps | `packages: string[]` |
| `allow_apps` | Allow specific apps | `packages: string[]` |

### Hardware Control

| Command | Description | Parameters |
|---------|-------------|------------|
| `disable_camera` | Disable device camera | None |
| `enable_camera` | Enable device camera | None |

### Security Policies

| Command | Description | Parameters |
|---------|-------------|------------|
| `set_password_policy` | Set password requirements | `min_length`, `require_numbers`, `require_symbols` |
| `set_system_restrictions` | Apply system restrictions | `restrictions: object` |

### Information Gathering

| Command | Description | Response |
|---------|-------------|----------|
| `get_device_info` | Complete device information | Hardware, software, security details |
| `get_installed_apps` | List all installed apps | App names, versions, packages |

## 🔄 Command Flow

1. **Server sends command** via Socket.IO
2. **SocketManager receives** and validates command
3. **MdmPolicyManager processes** the command
4. **Device executes** the requested action
5. **Result is sent back** to server with status

```json
// Example command from server
{
  "command": "lock",
  "timestamp": 1697123456789
}

// Response from device
{
  "command": "lock",
  "status": "success",
  "message": "Device locked successfully",
  "timestamp": 1697123456789,
  "device_id": "ABC123XYZ"
}
```

## 🛡️ Security Features

### Device Admin Protection
- Requires explicit user permission
- Protected against unauthorized removal
- Secure command validation

### Network Security
- SSL/TLS encrypted communication
- Ngrok compatibility for development
- Authentication headers and validation

### Error Handling
- Comprehensive exception catching
- Detailed error reporting
- Graceful degradation

## 📊 Device Analytics

The system collects extensive device information:

### Hardware Information
- CPU architecture and specifications
- Memory usage and availability
- Display metrics and resolution
- Storage capacity and usage

### Software Information
- Android version and API level
- Installed applications (system and user)
- Security settings and compliance
- Network configuration and status

### Real-time Monitoring
- Battery level and health
- Network connectivity status
- Location services status
- Camera and hardware availability

## 🔧 Development Setup

### Prerequisites
- Android Studio Arctic Fox or later
- Kotlin 1.7+
- Target SDK 33+
- Socket.IO Client library

### Build Configuration

```kotlin
dependencies {
    implementation 'io.socket:socket.io-client:2.0.0'
    implementation 'androidx.compose.ui:ui:1.5.0'
    implementation 'androidx.compose.material3:material3:1.1.0'
    // ... other dependencies
}
```

### Running the Application

1. **Clone the repository**
2. **Update server URL** in `EnhancedSocketManager`
3. **Build and install** on target device
4. **Enable Device Admin** permissions
5. **Start MDM service** from the app
6. **Send commands** from your server

## 🚨 Important Notes

### Permissions
- Some features require system-level permissions
- Full functionality may require root access or device owner status
- Test thoroughly on target devices

### Testing
- Use development certificates for testing
- Ngrok recommended for development server access
- Monitor logs for debugging information

### Production Deployment
- Use proper SSL certificates
- Implement authentication and authorization
- Consider compliance requirements (GDPR, etc.)

## 📝 Server Integration

### Socket.IO Events

#### Device Registration
```javascript
socket.on('register_device', (data) => {
  // Handle device registration
  console.log('Device registered:', data.device_id);
});
```

#### Command Execution
```javascript
// Send command to device
socket.emit('command', {
  command: 'lock',
  timestamp: Date.now()
});

// Receive command result
socket.on('command_result', (result) => {
  console.log('Command result:', result);
});
```

#### Device Status Updates
```javascript
socket.on('device_status', (status) => {
  // Update device status in your system
  console.log('Device status:', status);
});
```

## 🔮 Future Enhancements

- **Geofencing** - Location-based policy enforcement
- **Application Whitelisting** - Advanced app control
- **Remote Screen Control** - View and control device screens
- **File Management** - Remote file operations
- **Certificate Management** - Enterprise certificate deployment
- **Compliance Reporting** - Automated compliance checks
- **Multi-user Support** - Work profile management

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📞 Support

For support and questions:
- Create an issue in the repository
- Contact: [your-email@domain.com]
- Documentation: [your-docs-url]

---

**Note**: This MDM solution provides powerful device control capabilities. Ensure compliance with local laws and organizational policies when deploying in production environments.
