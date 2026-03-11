package com.whatihavedone.blearoundme.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatihavedone.blearoundme.ble.BleScanResult
import com.whatihavedone.blearoundme.data.MacPrefix
import com.whatihavedone.blearoundme.data.MacPrefixRepository
import com.whatihavedone.blearoundme.repository.BleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private var macPrefixRepository: MacPrefixRepository? = null
    private val bleRepository = BleRepository.getInstance()

    private val _macPrefixes = MutableStateFlow<Set<MacPrefix>>(emptySet())
    val macPrefixes: StateFlow<Set<MacPrefix>> = _macPrefixes.asStateFlow()

    // Expose the repository's StateFlows directly
    val scanResults: StateFlow<List<BleScanResult>>
        get() = bleRepository.getScanResultsFlow()

    val isScanning: StateFlow<Boolean>
        get() = bleRepository.getIsScanningFlow()

    val foundMatchingDevices: StateFlow<List<BleScanResult>>
        get() = bleRepository.getFoundMatchingDevicesFlow()

    fun initializeScanner(context: Context) {
        if (macPrefixRepository != null) return

        // Initialize repositories
        macPrefixRepository = MacPrefixRepository(context)
        bleRepository.initialize(context)

        // Initialize default MAC prefixes if this is the first run
        viewModelScope.launch {
            macPrefixRepository?.initializeDefaultsIfNeeded()
        }

        // Observe MAC prefixes from repository
        viewModelScope.launch {
            macPrefixRepository?.macPrefixes?.collect { prefixes ->
                _macPrefixes.value = prefixes
                // The service will automatically observe MAC prefix changes through MacPrefixRepository
            }
        }
    }

    fun addMacPrefix(prefix: String, tag: String = "Custom Device", isManufacturerId: Boolean = false) {
        viewModelScope.launch {
            macPrefixRepository?.addMacPrefix(prefix, tag, isManufacturerId)
        }
    }

    fun removeMacPrefix(macPrefix: MacPrefix) {
        viewModelScope.launch {
            macPrefixRepository?.removeMacPrefix(macPrefix)
        }
    }
}
