package com.whatihavedone.blearoundme.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BluetoothStateManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled ?: false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isBluetoothSupported = MutableStateFlow(bluetoothAdapter != null)
    val isBluetoothSupported: StateFlow<Boolean> = _isBluetoothSupported.asStateFlow()

    fun startMonitoring() {
        // Poll Bluetooth state periodically since we can't register broadcast receivers in all contexts
        if (context is ComponentActivity) {
            context.lifecycleScope.launch {
                while (isActive) {
                    val currentState = bluetoothAdapter?.isEnabled ?: false
                    if (_isBluetoothEnabled.value != currentState) {
                        _isBluetoothEnabled.value = currentState
                    }
                    delay(1000) // Check every second
                }
            }
        }
    }

    fun refreshState() {
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled ?: false
        _isBluetoothSupported.value = bluetoothAdapter != null
    }

    fun createEnableBluetoothIntent(): Intent? {
        return if (bluetoothAdapter != null) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        } else {
            null
        }
    }
}

@Composable
fun rememberBluetoothState(): BluetoothState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var bluetoothStateManager by remember { mutableStateOf<BluetoothStateManager?>(null) }
    var isBluetoothEnabled by remember { mutableStateOf(false) }
    var isBluetoothSupported by remember { mutableStateOf(true) }

    // Initialize the manager
    DisposableEffect(context) {
        val manager = BluetoothStateManager(context)
        bluetoothStateManager = manager
        manager.refreshState()

        if (context is ComponentActivity) {
            manager.startMonitoring()

            // Collect state changes
            lifecycleOwner.lifecycleScope.launch {
                manager.isBluetoothEnabled.collect { enabled ->
                    isBluetoothEnabled = enabled
                }
            }

            lifecycleOwner.lifecycleScope.launch {
                manager.isBluetoothSupported.collect { supported ->
                    isBluetoothSupported = supported
                }
            }
        }

        onDispose {
            bluetoothStateManager = null
        }
    }

    return BluetoothState(
        isEnabled = isBluetoothEnabled,
        isSupported = isBluetoothSupported,
        onRefresh = { bluetoothStateManager?.refreshState() },
        createEnableIntent = { bluetoothStateManager?.createEnableBluetoothIntent() }
    )
}

data class BluetoothState(
    val isEnabled: Boolean,
    val isSupported: Boolean,
    val onRefresh: () -> Unit,
    val createEnableIntent: () -> Intent?
)