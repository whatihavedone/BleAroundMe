package com.whatihavedone.blearoundme.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

object PermissionManager {

    fun getRequiredPermissions(): List<String> {
        return buildList {
            // BLE permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }

            // Location permissions (required for BLE scanning)
            add(Manifest.permission.ACCESS_FINE_LOCATION)

            // Notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getMissingPermissions(context: Context): List<String> {
        return getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberPermissionState(): PermissionState {
    val permissions = remember { PermissionManager.getRequiredPermissions() }
    val permissionsState = rememberMultiplePermissionsState(permissions)

    var hasRequestedPermissions by remember { mutableStateOf(false) }

    return PermissionState(
        permissionsState = permissionsState,
        hasRequestedPermissions = hasRequestedPermissions,
        onRequestPermissions = { hasRequestedPermissions = true }
    )
}

data class PermissionState @OptIn(ExperimentalPermissionsApi::class) constructor(
    val permissionsState: MultiplePermissionsState,
    val hasRequestedPermissions: Boolean,
    val onRequestPermissions: () -> Unit
) {
    @OptIn(ExperimentalPermissionsApi::class)
    val allPermissionsGranted: Boolean
        get() = permissionsState.allPermissionsGranted

    @OptIn(ExperimentalPermissionsApi::class)
    val shouldShowRationale: Boolean
        get() = permissionsState.shouldShowRationale

    @OptIn(ExperimentalPermissionsApi::class)
    val deniedPermissions: List<String>
        get() = permissionsState.permissions.filter { !it.status.isGranted }.map { it.permission }

    @OptIn(ExperimentalPermissionsApi::class)
    fun launchPermissionRequest() {
        onRequestPermissions()
        permissionsState.launchMultiplePermissionRequest()
    }
}

@OptIn(ExperimentalPermissionsApi::class)
fun getPermissionDisplayName(permission: String): String {
    return when (permission) {
        Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth Scanning"
        Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth Connection"
        Manifest.permission.BLUETOOTH -> "Bluetooth"
        Manifest.permission.BLUETOOTH_ADMIN -> "Bluetooth Admin"
        Manifest.permission.ACCESS_FINE_LOCATION -> "Location Access"
        Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
        else -> permission.substringAfterLast(".")
    }
}

@OptIn(ExperimentalPermissionsApi::class)
fun getPermissionRationale(permission: String): String {
    return when (permission) {
        Manifest.permission.BLUETOOTH_SCAN -> "Required to scan for nearby BLE devices"
        Manifest.permission.BLUETOOTH_CONNECT -> "Required to identify BLE devices"
        Manifest.permission.BLUETOOTH -> "Required to access Bluetooth functionality"
        Manifest.permission.BLUETOOTH_ADMIN -> "Required to manage Bluetooth operations"
        Manifest.permission.ACCESS_FINE_LOCATION -> "Required for BLE scanning on this Android version"
        Manifest.permission.POST_NOTIFICATIONS -> "Required to show notifications when devices are found"
        else -> "Required for app functionality"
    }
}