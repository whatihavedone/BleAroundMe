package com.whatihavedone.blearoundme.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.util.SparseArray
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.whatihavedone.blearoundme.data.MacPrefix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BleScanResult(
    val deviceName: String?,
    val macAddress: String,
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val matchingTag: String? = null
)

class BleScanner(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    private val _scanResults = MutableStateFlow<List<BleScanResult>>(emptyList())
    val scanResults: StateFlow<List<BleScanResult>> = _scanResults

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _foundMatchingDevices = MutableStateFlow<List<BleScanResult>>(emptyList())
    val foundMatchingDevices: StateFlow<List<BleScanResult>> = _foundMatchingDevices

    private var filterCriteria = setOf<MacPrefix>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { scanResult ->
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w("BleScanner", "Missing BLUETOOTH_CONNECT permission")
                    return
                }

                val device = scanResult.device
                val macAddress = device.address
                val deviceName = device.name
                val rssi = scanResult.rssi

                // Find matching criteria and its tag
                val matchingCriteria = findMatchingCriteria(scanResult)
                
                val bleResult = BleScanResult(
                    deviceName = deviceName,
                    macAddress = macAddress,
                    rssi = rssi,
                    matchingTag = matchingCriteria?.tag
                )

                // Update scan results
                val currentResults = _scanResults.value.toMutableList()
                val existingIndex = currentResults.indexOfFirst { it.macAddress == macAddress }
                if (existingIndex != -1) {
                    currentResults[existingIndex] = bleResult
                } else {
                    currentResults.add(bleResult)
                }
                _scanResults.value = currentResults

                // If a match was found, update matching devices
                if (matchingCriteria != null) {
                    Log.i("BleScanner", "Found matching device: $deviceName ($macAddress) Tag: ${matchingCriteria.tag}")
                    val currentMatchingDevices = _foundMatchingDevices.value.toMutableList()
                    val existingMatchIndex = currentMatchingDevices.indexOfFirst { it.macAddress == macAddress }
                    if (existingMatchIndex != -1) {
                        currentMatchingDevices[existingMatchIndex] = bleResult
                    } else {
                        currentMatchingDevices.add(bleResult)
                    }
                    _foundMatchingDevices.value = currentMatchingDevices
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleScanner", "BLE scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }

    fun setFilterCriteria(criteria: Set<MacPrefix>) {
        this.filterCriteria = criteria
        Log.d("BleScanner", "Updated filter criteria: ${criteria.size} items")
    }

    private fun findMatchingCriteria(scanResult: ScanResult): MacPrefix? {
        val macAddress = scanResult.device.address.replace(":", "").uppercase()
        
        // 1. Check MAC Prefixes
        val macMatch = filterCriteria.firstOrNull { criteria ->
            !criteria.isManufacturerId && macAddress.startsWith(criteria.address.uppercase().replace(":", ""))
        }
        if (macMatch != null) return macMatch

        // 2. Check Manufacturer IDs
        val scanRecord = scanResult.scanRecord ?: return null
        val manufacturerData = scanRecord.manufacturerSpecificData
        
        for (i in 0 until manufacturerData.size()) {
            val id = manufacturerData.keyAt(i)
            val idMatch = filterCriteria.firstOrNull { criteria ->
                criteria.isManufacturerId && try {
                    val targetId = criteria.address.lowercase().removePrefix("0x").toInt(16)
                    targetId == id
                } catch (e: Exception) {
                    false
                }
            }
            if (idMatch != null) return idMatch
        }
        
        return null
    }

    fun startScan(): Boolean {
        Log.d("BleScanner", "startScan() called")

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w("BleScanner", "Bluetooth is not enabled or supported")
            Toast.makeText(context, "Please enable Bluetooth to scan for devices", Toast.LENGTH_LONG).show()
            return false
        }

        if (_isScanning.value) {
            Log.w("BleScanner", "Scan is already running")
            return true
        }

        if (!hasRequiredPermissions()) {
            Log.w("BleScanner", "Missing required permissions")
            return false
        }

        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e("BleScanner", "BluetoothLeScanner is null")
            return false
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        try {
            bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
            _isScanning.value = true
            Log.i("BleScanner", "BLE scan started successfully")
            return true
        } catch (e: SecurityException) {
            Log.e("BleScanner", "Security exception starting scan", e)
            return false
        } catch (e: Exception) {
            Log.e("BleScanner", "Unexpected exception starting scan", e)
            return false
        }
    }

    fun stopScan() {
        if (!_isScanning.value) {
            return
        }

        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            _isScanning.value = false
            Log.i("BleScanner", "BLE scan stopped")
        } catch (e: SecurityException) {
            Log.e("BleScanner", "Security exception stopping scan", e)
        } catch (e: Exception) {
            Log.e("BleScanner", "Error stopping scan", e)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        return permissions.all { permission ->
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun clearResults() {
        _scanResults.value = emptyList()
        _foundMatchingDevices.value = emptyList()
    }
}
