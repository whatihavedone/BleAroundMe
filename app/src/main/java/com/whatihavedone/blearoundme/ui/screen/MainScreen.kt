package com.whatihavedone.blearoundme.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatihavedone.blearoundme.bluetooth.rememberBluetoothState
import com.whatihavedone.blearoundme.data.MacPrefix
import com.whatihavedone.blearoundme.permissions.PermissionState
import com.whatihavedone.blearoundme.permissions.getPermissionDisplayName
import com.whatihavedone.blearoundme.permissions.getPermissionRationale
import com.whatihavedone.blearoundme.service.BleScanService
import com.whatihavedone.blearoundme.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    permissionState: PermissionState,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val bluetoothState = rememberBluetoothState()
    val macPrefixes by viewModel.macPrefixes.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val foundMatchingDevices by viewModel.foundMatchingDevices.collectAsState()

    var showAddPrefixDialog by remember { mutableStateOf(false) }

    LaunchedEffect(permissionState.allPermissionsGranted, bluetoothState.isEnabled) {
        if (permissionState.allPermissionsGranted && bluetoothState.isEnabled) {
            viewModel.initializeScanner(context)
        }
    }

    // Repository cleanup is handled at Application level

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE devices Detector") }
            )
        },
        floatingActionButton = {
            if (permissionState.allPermissionsGranted && bluetoothState.isEnabled) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (isScanning) {
                            stopScanningService(context)
                        } else {
                            startScanningService(context)
                        }
                    },
                    icon = {
                        if (isScanning) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                        }
                    },
                    text = {
                        Text(if (isScanning) "Stop Scanning" else "Start Scanning")
                    }
                )
            }
        }
    ) { paddingValues ->
        if (!permissionState.allPermissionsGranted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                PermissionSection(permissionState)
            }
        } else if (!bluetoothState.isSupported) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                BluetoothNotSupportedSection()
            }
        } else if (!bluetoothState.isEnabled) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                BluetoothDisabledSection(bluetoothState)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // MAC Prefixes Section
                item {
                    MacPrefixSection(
                        macPrefixes = macPrefixes,
                        onAddPrefix = { showAddPrefixDialog = true },
                        onRemovePrefix = { viewModel.removeMacPrefix(it) }
                    )
                }

                // Matching Devices Section
                item {
                    MatchingDevicesSection(
                        matchingDevices = foundMatchingDevices,
                        macPrefixes = macPrefixes,
                        isScanning = isScanning
                    )
                }

                // All Scan Results Section
                item {
                    ScanResultsSection(
                        scanResults = scanResults,
                        isScanning = isScanning
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                }
            }
        }
    }

    if (showAddPrefixDialog) {
        AddMacPrefixDialog(
            onDismiss = { showAddPrefixDialog = false },
            onAddPrefix = { prefix, tag ->
                viewModel.addMacPrefix(prefix, tag)
                showAddPrefixDialog = false
            }
        )
    }
}

@Composable
fun PermissionSection(permissionState: PermissionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "The following permissions are needed for the app to function:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            permissionState.deniedPermissions.forEach { permission ->
                Text(
                    text = "• ${getPermissionDisplayName(permission)}: ${getPermissionRationale(permission)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { permissionState.launchPermissionRequest() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
fun MacPrefixSection(
    macPrefixes: Set<MacPrefix>,
    onAddPrefix: () -> Unit,
    onRemovePrefix: (MacPrefix) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MAC Address Prefixes",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onAddPrefix) {
                    Icon(Icons.Default.Add, contentDescription = "Add Prefix")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (macPrefixes.isEmpty()) {
                Text(
                    text = "No MAC prefixes configured. Add prefixes to detect specific devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp), // Fixed height to allow scrolling
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(macPrefixes.toList()) { macPrefix ->
                        MacPrefixItem(
                            macPrefix = macPrefix,
                            onRemove = { onRemovePrefix(macPrefix) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MacPrefixItem(
    macPrefix: MacPrefix,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = formatMacPrefix(macPrefix.address),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = macPrefix.tag,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun MatchingDevicesSection(
    matchingDevices: List<com.whatihavedone.blearoundme.ble.BleScanResult>,
    macPrefixes: Set<MacPrefix>,
    isScanning: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Detected Wearables",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (matchingDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "${matchingDevices.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                if (isScanning) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (matchingDevices.isEmpty()) {
                if (isScanning) {
                    Text(
                        text = "Scanning for wearable devices...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column {
                        Text(
                            text = "No wearable devices detected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Start scanning to detect nearby AR glasses, smartwatches, and other wearables",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp), // Fixed height for scrolling
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = matchingDevices,
                        key = { it.macAddress }
                    ) { device ->
                        MatchingDeviceItem(
                            device = device,
                            macPrefixes = macPrefixes
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MatchingDeviceItem(
    device: com.whatihavedone.blearoundme.ble.BleScanResult,
    macPrefixes: Set<MacPrefix>
) {
    val matchingPrefix = macPrefixes.firstOrNull {
        device.macAddress.replace(":", "").uppercase().startsWith(it.address)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal strength indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(56.dp)
            ) {
                val signalColor = when {
                    device.rssi >= -60 -> MaterialTheme.colorScheme.primary
                    device.rssi >= -80 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                }

                Text(
                    text = "${device.rssi}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = signalColor
                )
                Text(
                    text = "dBm",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Device info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = matchingPrefix?.tag ?: "Unknown Wearable",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = device.deviceName ?: "No name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = device.macAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ScanResultsSection(
    scanResults: List<com.whatihavedone.blearoundme.ble.BleScanResult>,
    isScanning: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All BLE Devices",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (isScanning) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (scanResults.isEmpty()) {
                if (isScanning) {
                    Text(
                        text = "Scanning for devices...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "No devices found. Start scanning to detect nearby devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp), // Increased height for better viewing
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = scanResults,
                        key = { it.macAddress }
                    ) { device ->
                        DeviceItem(device = device)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: com.whatihavedone.blearoundme.ble.BleScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal strength indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(48.dp)
            ) {
                val signalStrength = when {
                    device.rssi >= -40 -> "Excellent"
                    device.rssi >= -60 -> "Good"
                    device.rssi >= -80 -> "Fair"
                    else -> "Weak"
                }
                val signalColor = when {
                    device.rssi >= -40 -> MaterialTheme.colorScheme.primary
                    device.rssi >= -60 -> MaterialTheme.colorScheme.secondary
                    device.rssi >= -80 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }

                Text(
                    text = "${device.rssi}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = signalColor
                )
                Text(
                    text = "dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Device info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.deviceName ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = device.macAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Last seen: ${formatTimestamp(device.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 1000 -> "Just now"
        diff < 60000 -> "${diff / 1000}s ago"
        diff < 3600000 -> "${diff / 60000}m ago"
        else -> "${diff / 3600000}h ago"
    }
}

@Composable
fun AddMacPrefixDialog(
    onDismiss: () -> Unit,
    onAddPrefix: (String, String) -> Unit
) {
    var prefixText by remember { mutableStateOf("") }
    var tagText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add MAC Prefix") },
        text = {
            Column {
                Text("Enter a MAC address prefix and device tag:")

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = prefixText,
                    onValueChange = {
                        prefixText = it
                        error = null
                    },
                    label = { Text("MAC Prefix") },
                    placeholder = { Text("24:F0:94") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    label = { Text("Device Tag") },
                    placeholder = { Text("Custom Device") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized = prefixText.replace(":", "").uppercase()
                    if (normalized.matches(Regex("[0-9A-F]{2,12}"))) {
                        val tag = if (tagText.isBlank()) "Custom Device" else tagText
                        onAddPrefix(normalized, tag)
                    } else {
                        error = "Invalid MAC prefix format"
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatMacPrefix(prefix: String): String {
    return if (prefix.length >= 6) {
        prefix.chunked(2).take(3).joinToString(":")
    } else {
        prefix.chunked(2).joinToString(":")
    }
}

private fun startScanningService(context: Context) {
    android.util.Log.d("MainScreen", "Starting BLE scan service")
    val intent = Intent(context, BleScanService::class.java).apply {
        action = BleScanService.ACTION_START_SCANNING
    }
    try {
        context.startForegroundService(intent)
        android.util.Log.d("MainScreen", "Foreground service start command sent")
    } catch (e: Exception) {
        android.util.Log.e("MainScreen", "Error starting foreground service", e)
    }
}

private fun stopScanningService(context: Context) {
    android.util.Log.d("MainScreen", "Stopping BLE scan service")
    val intent = Intent(context, BleScanService::class.java).apply {
        action = BleScanService.ACTION_STOP_SCANNING
    }
    try {
        context.startService(intent)
        android.util.Log.d("MainScreen", "Service stop command sent")
    } catch (e: Exception) {
        android.util.Log.e("MainScreen", "Error stopping service", e)
    }
}

@Composable
fun BluetoothNotSupportedSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Bluetooth Not Supported",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your device does not support Bluetooth Low Energy (BLE) which is required for this app to function.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun BluetoothDisabledSection(bluetoothState: com.whatihavedone.blearoundme.bluetooth.BluetoothState) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Bluetooth Disabled",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Bluetooth is turned off. Please enable Bluetooth to scan for nearby devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        bluetoothState.onRefresh()
                        android.util.Log.d("MainScreen", "Refreshing Bluetooth state")
                    }
                ) {
                    Text("Refresh")
                }

                Button(
                    onClick = {
                        val enableIntent = bluetoothState.createEnableIntent()
                        enableIntent?.let { intent ->
                            try {
                                context.startActivity(intent)
                                android.util.Log.d("MainScreen", "Started Bluetooth enable intent")
                            } catch (e: Exception) {
                                android.util.Log.e("MainScreen", "Failed to start Bluetooth enable intent", e)
                            }
                        }
                    }
                ) {
                    Text("Enable Bluetooth")
                }
            }
        }
    }
}