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
import androidx.compose.material.icons.filled.BluetoothDisabled
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatihavedone.blearoundme.bluetooth.rememberBluetoothState
import com.whatihavedone.blearoundme.data.MacPrefix
import com.whatihavedone.blearoundme.permissions.PermissionState
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
        } else if (!bluetoothState.isEnabled) {
            BluetoothDisabledSection(
                state = bluetoothState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // --- DETECTION CRITERIA ---
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Detection Criteria", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showAddPrefixDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Criteria")
                        }
                    }
                }

                if (macPrefixes.isEmpty()) {
                    item {
                        Text("No detection criteria configured.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(macPrefixes.toList()) { criteria ->
                        MacPrefixItem(criteria, onRemove = { viewModel.removeMacPrefix(criteria) })
                    }
                }

                // --- DETECTED WEARABLES ---
                item {
                    Text("Detected Wearables", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                if (foundMatchingDevices.isEmpty()) {
                    item {
                        Text(if (isScanning) "Scanning..." else "Start scanning to detect devices", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    items(foundMatchingDevices, key = { it.macAddress }) { device ->
                        MatchingDeviceItem(device, macPrefixes)
                    }
                }

                // --- ALL SCAN RESULTS ---
                item {
                    Text("All BLE Devices", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }

                if (scanResults.isEmpty()) {
                    item { Text("No devices found nearby", style = MaterialTheme.typography.bodyMedium) }
                } else {
                    items(scanResults, key = { it.macAddress }) { device ->
                        DeviceItem(device)
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddPrefixDialog) {
        AddFilterCriteriaDialog(
            onDismiss = { showAddPrefixDialog = false },
            onAddCriteria = { value, tag, isManufacturerId ->
                viewModel.addMacPrefix(value, tag, isManufacturerId)
                showAddPrefixDialog = false
            }
        )
    }
}

@Composable
fun MacPrefixItem(macPrefix: MacPrefix, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (macPrefix.isManufacturerId) macPrefix.address else formatMacPrefix(macPrefix.address),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "${macPrefix.tag} (${if (macPrefix.isManufacturerId) "ID" else "MAC"})", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun MatchingDeviceItem(device: com.whatihavedone.blearoundme.ble.BleScanResult, macPrefixes: Set<MacPrefix>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${device.rssi}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
            Column {
                Text(device.matchingTag ?: "Detected Device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(device.deviceName ?: "Unknown Name", style = MaterialTheme.typography.bodyMedium)
                Text(device.macAddress, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun DeviceItem(device: com.whatihavedone.blearoundme.ble.BleScanResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${device.rssi}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(40.dp))
            Column {
                Text(device.deviceName ?: "Unknown Device", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(device.macAddress, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilterCriteriaDialog(onDismiss: () -> Unit, onAddCriteria: (String, String, Boolean) -> Unit) {
    var valueText by remember { mutableStateOf("") }
    var tagText by remember { mutableStateOf("") }
    var isMfrId by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isMfrId) "Add Manufacturer ID" else "Add MAC Prefix") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Manufacturer ID mode")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = isMfrId, onCheckedChange = { isMfrId = it; error = null })
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it; error = null },
                    label = { Text(if (isMfrId) "ID (e.g. 0x01AB)" else "MAC Prefix") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = tagText, onValueChange = { tagText = it }, label = { Text("Tag") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isMfrId) {
                    if (valueText.lowercase().removePrefix("0x").matches(Regex("[0-9a-f]{1,4}"))) {
                        onAddCriteria(valueText, tagText, true)
                    } else error = "Invalid Hex ID"
                } else {
                    if (valueText.replace(":", "").matches(Regex("[0-9A-F]{2,12}"))) {
                        onAddCriteria(valueText.replace(":", ""), tagText, false)
                    } else error = "Invalid MAC"
                }
            }) { Text("Add") }
        }
    )
}

private fun formatMacPrefix(prefix: String) = prefix.chunked(2).take(3).joinToString(":")

private fun startScanningService(context: Context) {
    context.startForegroundService(Intent(context, BleScanService::class.java).apply { action = BleScanService.ACTION_START_SCANNING })
}

private fun stopScanningService(context: Context) {
    context.startService(Intent(context, BleScanService::class.java).apply { action = BleScanService.ACTION_STOP_SCANNING })
}

@Composable
fun BluetoothDisabledSection(
    state: com.whatihavedone.blearoundme.bluetooth.BluetoothState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BluetoothDisabled,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Bluetooth is Disabled",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "To scan for nearby devices and wearables, this app needs Bluetooth to be active. Please enable it to continue.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { state.createEnableIntent()?.let { context.startActivity(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Enable Bluetooth", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun PermissionSection(state: PermissionState) {
    Column {
        Text("Permissions Required", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { state.launchPermissionRequest() }) { Text("Grant") }
    }
}
