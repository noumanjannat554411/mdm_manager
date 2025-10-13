# MDM Server Commands Reference

This document outlines the commands that can be sent from your MDM server to control Android devices.

## Basic Commands

### Device Control
```json
{
  "command": "lock",
  "timestamp": 1697123456789
}
```

```json
{
  "command": "wipe",
  "include_external": true,
  "timestamp": 1697123456789
}
```

### Application Management
```json
{
  "command": "block_installation",
  "timestamp": 1697123456789
}
```

```json
{
  "command": "allow_installation",
  "timestamp": 1697123456789
}
```

```json
{
  "command": "block_apps",
  "packages": ["com.example.app1", "com.example.app2"],
  "timestamp": 1697123456789
}
```

```json
{
  "command": "allow_apps",
  "packages": ["com.example.app1", "com.example.app2"],
  "timestamp": 1697123456789
}
```

### Camera Control
```json
{
  "command": "disable_camera",
  "timestamp": 1697123456789
}
```

```json
{
  "command": "enable_camera",
  "timestamp": 1697123456789
}
```

### Password Policy
```json
{
  "command": "set_password_policy",
  "min_length": 8,
  "require_numbers": true,
  "require_symbols": true,
  "timestamp": 1697123456789
}
```

### System Restrictions
```json
{
  "command": "set_system_restrictions",
  "restrictions": {
    "no_install_apps": true,
    "no_install_unknown_sources": true,
    "no_uninstall_apps": false,
    "no_debugging_features": true,
    "no_usb_file_transfer": true,
    "no_add_managed_profile": true,
    "no_bluetooth": false,
    "no_wifi": false,
    "no_camera": false
  },
  "timestamp": 1697123456789
}
```

### USB Cable Management
```json
{
  "command": "restrict_usb",
  "timestamp": 1697123456789
}
```

```json
{
  "command": "allow_usb",
  "timestamp": 1697123456789
}
```

```json
{
  "command": "get_usb_status",
  "timestamp": 1697123456789
}
```

## Information Gathering

### Device Information
```json
{
  "command": "get_device_info",
  "timestamp": 1697123456789
}
```

### Installed Applications
```json
{
  "command": "get_installed_apps",
  "timestamp": 1697123456789
}
```

### Device Status
```json
{
  "command": "status",
  "timestamp": 1697123456789
}
```

### Ping
```json
{
  "command": "ping",
  "timestamp": 1697123456789
}
```

## Bulk Commands

You can send multiple commands at once:

```json
{
  "commands": [
    {
      "command": "disable_camera",
      "timestamp": 1697123456789
    },
    {
      "command": "block_installation",
      "timestamp": 1697123456789
    },
    {
      "command": "set_password_policy",
      "min_length": 10,
      "require_numbers": true,
      "require_symbols": true,
      "timestamp": 1697123456789
    }
  ]
}
```

## Expected Responses

All commands will return a response in this format:

```json
{
  "command": "lock",
  "status": "success|error",
  "message": "Human readable message",
  "timestamp": 1697123456789,
  "device_id": "device_serial_number",
  "data": {
    // Additional data if applicable
  }
}
```

## Error Handling

If a command fails, you'll receive:

```json
{
  "command": "attempted_command",
  "status": "error",
  "message": "Error description",
  "timestamp": 1697123456789,
  "device_id": "device_serial_number"
}
```

## Device Registration

When a device connects, it automatically sends registration data:

```json
{
  "device_id": "device_serial_number",
  "model": "SM-G991B",
  "manufacturer": "Samsung",
  "android_version": "13",
  "admin_active": true,
  "timestamp": 1697123456789,
  "capabilities": [
    "lock_device",
    "wipe_device",
    "block_installation",
    "disable_camera",
    "set_password_policy",
    "get_device_info",
    "get_installed_apps",
    "set_system_restrictions"
  ],
  "device_info": {
    // Detailed device information
  }
}
```

## Available User Restrictions

These can be used in the `set_system_restrictions` command:

- `no_install_apps` - Block app installation
- `no_install_unknown_sources` - Block installation from unknown sources
- `no_uninstall_apps` - Block app uninstallation
- `no_debugging_features` - Disable debugging features
- `no_usb_file_transfer` - Disable USB file transfer
- `no_add_managed_profile` - Prevent adding work profiles
- `no_bluetooth` - Disable Bluetooth
- `no_wifi` - Disable WiFi
- `no_camera` - Disable camera
- `no_modify_accounts` - Prevent account modifications
- `no_config_wifi` - Prevent WiFi configuration changes
- `no_config_bluetooth` - Prevent Bluetooth configuration changes
- `no_share_location` - Prevent location sharing
- `no_factory_reset` - Prevent factory reset
