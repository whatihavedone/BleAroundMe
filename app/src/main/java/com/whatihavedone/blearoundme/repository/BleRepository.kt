package com.whatihavedone.blearoundme.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.whatihavedone.blearoundme.ble.BleScanResult
import com.whatihavedone.blearoundme.service.BleScanService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BleRepository private constructor() {

    private var bleScanService: BleScanService? = null
    private var isBound = false
    private var applicationContext: Context? = null

    // Repository scope for handling coroutines
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _scanResults = MutableStateFlow<List<BleScanResult>>(emptyList())
    val scanResults: StateFlow<List<BleScanResult>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _foundMatchingDevices = MutableStateFlow<List<BleScanResult>>(emptyList())
    val foundMatchingDevices: StateFlow<List<BleScanResult>> = _foundMatchingDevices.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d("BleRepository", "Service connected")
            val binder = service as BleScanService.LocalBinder
            bleScanService = binder.getService()
            isBound = true
            observeServiceState()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Log.d("BleRepository", "Service disconnected")
            bleScanService = null
            isBound = false
            // Reset state when service disconnects
            _scanResults.value = emptyList()
            _isScanning.value = false
            _foundMatchingDevices.value = emptyList()
        }
    }

    fun initialize(context: Context) {
        if (applicationContext != null) return

        applicationContext = context.applicationContext
        bindToService()
    }

    private fun bindToService() {
        applicationContext?.let { context ->
            val intent = Intent(context, BleScanService::class.java)
            try {
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                Log.d("BleRepository", "Attempting to bind to service")
            } catch (e: Exception) {
                Log.e("BleRepository", "Error binding to service", e)
            }
        }
    }

    private fun observeServiceState() {
        bleScanService?.let { service ->
            Log.d("BleRepository", "Starting service state observation")

            // Observe scan results
            repositoryScope.launch {
                service.getScanResults().collect { results ->
                    _scanResults.value = results
                }
            }

            // Observe scanning state
            repositoryScope.launch {
                service.isScanning().collect { scanning ->
                    _isScanning.value = scanning
                }
            }

            // Observe matching devices
            repositoryScope.launch {
                service.getFoundMatchingDevices().collect { matchingDevices ->
                    _foundMatchingDevices.value = matchingDevices
                }
            }

            Log.d("BleRepository", "Service state observation established")
        }
    }

    fun startScanning(): Boolean {
        applicationContext?.let { context ->
            val intent = Intent(context, BleScanService::class.java).apply {
                action = BleScanService.ACTION_START_SCANNING
            }
            try {
                context.startForegroundService(intent)
                Log.d("BleRepository", "Start scanning service command sent")
                return true
            } catch (e: Exception) {
                Log.e("BleRepository", "Error starting scanning service", e)
                return false
            }
        }
        return false
    }

    fun stopScanning(): Boolean {
        applicationContext?.let { context ->
            val intent = Intent(context, BleScanService::class.java).apply {
                action = BleScanService.ACTION_STOP_SCANNING
            }
            try {
                context.startService(intent)
                Log.d("BleRepository", "Stop scanning service command sent")
                return true
            } catch (e: Exception) {
                Log.e("BleRepository", "Error stopping scanning service", e)
                return false
            }
        }
        return false
    }

    fun clearScanResults() {
        bleScanService?.clearScanResults()
    }

    // Expose the repository's StateFlows (which are populated from the service)
    fun getScanResultsFlow(): StateFlow<List<BleScanResult>> = _scanResults

    fun getIsScanningFlow(): StateFlow<Boolean> = _isScanning

    fun getFoundMatchingDevicesFlow(): StateFlow<List<BleScanResult>> = _foundMatchingDevices

    fun cleanup() {
        // Cancel all coroutines
        repositoryScope.cancel()

        if (isBound) {
            applicationContext?.let { context ->
                try {
                    context.unbindService(serviceConnection)
                    isBound = false
                    Log.d("BleRepository", "Unbound from service")
                } catch (e: Exception) {
                    Log.e("BleRepository", "Error unbinding from service", e)
                }
            }
        }

        bleScanService = null
        applicationContext = null

        // Reset state
        _scanResults.value = emptyList()
        _isScanning.value = false
        _foundMatchingDevices.value = emptyList()
    }

    companion object {
        @Volatile
        private var INSTANCE: BleRepository? = null

        fun getInstance(): BleRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BleRepository().also { INSTANCE = it }
            }
        }

        fun cleanup() {
            INSTANCE?.cleanup()
            INSTANCE = null
        }
    }
}