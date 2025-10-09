package com.octaloop.technologies.manager

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

enum class AppFilter {
    ALL, USER_APPS, SYSTEM_APPS, LOCKED, HIDDEN
}

@Composable
fun AppManagementScreen(
    appManager: AppManager,
    onBack: () -> Unit
) {
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var filteredApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(AppFilter.ALL) }
    var isLoading by remember { mutableStateOf(true) }
    var showStats by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) } // Add refresh trigger
    
    // Load apps on first composition and when refreshTrigger changes
    LaunchedEffect(refreshTrigger) {
        apps = appManager.getAllInstalledApps()
        isLoading = false
    }
    
    // Filter apps based on search and filter selection
    LaunchedEffect(apps, searchQuery, selectedFilter) {
        filteredApps = apps.filter { app ->
            val matchesSearch = app.appName.contains(searchQuery, ignoreCase = true) ||
                               app.packageName.contains(searchQuery, ignoreCase = true)
            
            val matchesFilter = when (selectedFilter) {
                AppFilter.ALL -> true // Show all apps including hidden ones (for management)
                AppFilter.USER_APPS -> !app.isSystemApp // Show user apps regardless of hidden status
                AppFilter.SYSTEM_APPS -> app.isSystemApp // Show system apps regardless of hidden status
                AppFilter.LOCKED -> app.isLocked
                AppFilter.HIDDEN -> app.isHidden // Only show hidden apps
            }
            
            matchesSearch && matchesFilter
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "App Management",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Row {
                IconButton(onClick = { showStats = !showStats }) {
                    Icon(Icons.Default.Settings, "Statistics")
                }
                
                TextButton(onClick = onBack) {
                    Text("Back")
                }
            }
        }
        
        // Statistics Card
        if (showStats) {
            AppStatisticsCard(
                appManager = appManager,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        // Legend Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Controls:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "✅ Lock: Blocks app access | ➕ Hide: Hides from device launcher",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Filter Chips
        FilterChipsRow(
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it },
            appCounts = apps.let { appList ->
                mapOf(
                    AppFilter.ALL to appList.size,
                    AppFilter.USER_APPS to appList.count { !it.isSystemApp },
                    AppFilter.SYSTEM_APPS to appList.count { it.isSystemApp },
                    AppFilter.LOCKED to appList.count { it.isLocked },
                    AppFilter.HIDDEN to appList.count { it.isHidden }
                )
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Apps List
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps) { app ->
                    AppItem(
                        app = app,
                        onLockToggle = { packageName, shouldLock ->
                            println("🔘 Lock toggle clicked for $packageName, shouldLock: $shouldLock")
                            if (shouldLock) {
                                val result = appManager.lockApp(packageName)
                                println("🔒 Lock result: $result")
                            } else {
                                val result = appManager.unlockApp(packageName)
                                println("🔓 Unlock result: $result")
                            }
                            // Trigger refresh
                            refreshTrigger++
                            println("🔄 Refresh triggered: $refreshTrigger")
                        },
                        onHideToggle = { packageName, shouldHide ->
                            println("👁️ Hide toggle clicked for $packageName, shouldHide: $shouldHide")
                            if (shouldHide) {
                                // Only hide the app, don't lock it
                                val hideResult = appManager.hideApp(packageName)
                                println("👁️‍🗨️ Hide result: $hideResult")
                            } else {
                                // Only show the app, don't unlock it
                                val showResult = appManager.showApp(packageName)
                                println("👁️ Show result: $showResult")
                            }
                            // Trigger refresh
                            refreshTrigger++
                            println("🔄 Refresh triggered: $refreshTrigger")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppStatisticsCard(
    appManager: AppManager,
    modifier: Modifier = Modifier
) {
    var stats by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    
    LaunchedEffect(Unit) {
        val statsJson = appManager.getAppStats()
        stats = mapOf(
            "Total Apps" to statsJson.optInt("total_apps", 0),
            "User Apps" to statsJson.optInt("user_apps", 0),
            "System Apps" to statsJson.optInt("system_apps", 0),
            "Locked Apps" to statsJson.optInt("locked_apps", 0),
            "Hidden Apps" to statsJson.optInt("hidden_apps", 0)
        )
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📊 App Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            stats.forEach { (label, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = label)
                    Text(
                        text = count.toString(),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChipsRow(
    selectedFilter: AppFilter,
    onFilterSelected: (AppFilter) -> Unit,
    appCounts: Map<AppFilter, Int>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppFilter.values().forEach { filter ->
            val isSelected = filter == selectedFilter
            val count = appCounts[filter] ?: 0
            
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text("${filter.name.replace('_', ' ')} ($count)")
                }
            )
        }
    }
}

@Composable
fun AppItem(
    app: AppInfo,
    onLockToggle: (String, Boolean) -> Unit,
    onHideToggle: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            if (app.icon != null) {
                Image(
                    bitmap = app.icon.toBitmap(width = 48, height = 48).asImageBitmap(),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = "Default App Icon",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // App Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Hidden from launcher indicator
                    if (app.isHidden) {
                        Text(
                            text = "HIDDEN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(start = 4.dp)
                        )
                    }
                }
                
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row {
                    if (app.isSystemApp) {
                        Text(
                            text = "SYSTEM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Text(
                        text = "v${app.versionName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Control Buttons
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Lock Toggle
                IconButton(
                    onClick = { 
                        println("🖱️ Lock button clicked for ${app.packageName}, current state: ${app.isLocked}")
                        onLockToggle(app.packageName, !app.isLocked) 
                    }
                ) {
                    Icon(
                        if (app.isLocked) Icons.Default.Lock else Icons.Default.CheckCircle,
                        contentDescription = if (app.isLocked) "Unlock App" else "Lock App",
                        tint = if (app.isLocked) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                }
                
                // Hide Toggle
                IconButton(
                    onClick = { 
                        println("🖱️ Hide button clicked for ${app.packageName}, current state: ${app.isHidden}")
                        onHideToggle(app.packageName, !app.isHidden) 
                    }
                ) {
                    Icon(
                        if (app.isHidden) Icons.Default.Clear else Icons.Default.Add,
                        contentDescription = if (app.isHidden) "Show App" else "Hide App",
                        tint = if (app.isHidden) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.secondary
                    )
                }
                
                // Status indicators
                Row {
                    if (app.isLocked) {
                        Text(
                            text = "🔒",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (app.isHidden) {
                        Text(
                            text = "👁️‍🗨️",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
