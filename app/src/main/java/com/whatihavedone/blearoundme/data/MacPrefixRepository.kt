package com.whatihavedone.blearoundme.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MacPrefixRepository(private val context: Context) {

    private object PreferencesKeys {
        val MAC_PREFIXES = stringPreferencesKey("mac_prefixes_json")
        val DEFAULTS_INITIALIZED = booleanPreferencesKey("defaults_initialized_v2") // Updated version
    }

    val macPrefixes: Flow<Set<MacPrefix>> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[PreferencesKeys.MAC_PREFIXES]
            if (jsonString != null) {
                try {
                    Json.decodeFromString<Set<MacPrefix>>(jsonString)
                } catch (e: Exception) {
                    android.util.Log.e("MacPrefixRepository", "Failed to parse MAC prefixes", e)
                    emptySet()
                }
            } else {
                emptySet()
            }
        }

    suspend fun initializeDefaultsIfNeeded() {
        val preferences = context.dataStore.data.first()
        val defaultsInitialized = preferences[PreferencesKeys.DEFAULTS_INITIALIZED] ?: false

        if (!defaultsInitialized) {
            android.util.Log.d("MacPrefixRepository", "Updating default detection criteria")
            val defaultPrefixes = MacPrefix.getDefaultMacPrefixes()
            
            context.dataStore.edit { prefs ->
                val jsonString = prefs[stringPreferencesKey("mac_prefixes_json")]
                val currentPrefixes = if (jsonString != null) {
                    try {
                        Json.decodeFromString<Set<MacPrefix>>(jsonString).toMutableSet()
                    } catch (e: Exception) {
                        mutableSetOf()
                    }
                } else {
                    mutableSetOf()
                }

                // Add all defaults that aren't already there (by address)
                val existingAddresses = currentPrefixes.map { it.address }.toSet()
                val newDefaults = defaultPrefixes.filter { it.address !in existingAddresses }
                
                if (newDefaults.isNotEmpty()) {
                    currentPrefixes.addAll(newDefaults)
                    prefs[stringPreferencesKey("mac_prefixes_json")] = Json.encodeToString(currentPrefixes)
                }
                
                prefs[PreferencesKeys.DEFAULTS_INITIALIZED] = true
            }
        }
    }

    suspend fun addMacPrefix(prefix: String, tag: String = "Custom Device", isManufacturerId: Boolean = false) {
        val normalizedPrefix = normalizePrefix(prefix, isManufacturerId)
        if (isValidPrefix(normalizedPrefix, isManufacturerId)) {
            val newPrefix = MacPrefix(normalizedPrefix, tag, isManufacturerId)

            context.dataStore.edit { preferences ->
                val jsonString = preferences[stringPreferencesKey("mac_prefixes_json")]
                val currentPrefixes = if (jsonString != null) {
                    try {
                        Json.decodeFromString<Set<MacPrefix>>(jsonString).toMutableSet()
                    } catch (e: Exception) {
                        mutableSetOf()
                    }
                } else {
                    mutableSetOf()
                }

                currentPrefixes.add(newPrefix)
                preferences[stringPreferencesKey("mac_prefixes_json")] = Json.encodeToString(currentPrefixes)
            }
        }
    }

    suspend fun removeMacPrefix(macPrefix: MacPrefix) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[stringPreferencesKey("mac_prefixes_json")]
            val currentPrefixes = if (jsonString != null) {
                try {
                    Json.decodeFromString<Set<MacPrefix>>(jsonString).toMutableSet()
                } catch (e: Exception) {
                    mutableSetOf()
                }
            } else {
                mutableSetOf()
            }

            currentPrefixes.remove(macPrefix)
            preferences[stringPreferencesKey("mac_prefixes_json")] = Json.encodeToString(currentPrefixes)
        }
    }

    private fun normalizePrefix(prefix: String, isManufacturerId: Boolean): String {
        return if (isManufacturerId) {
            if (prefix.lowercase().startsWith("0x")) prefix else "0x$prefix"
        } else {
            prefix.replace(":", "").uppercase()
        }
    }

    private fun isValidPrefix(prefix: String, isManufacturerId: Boolean): Boolean {
        return if (isManufacturerId) {
            val clean = prefix.lowercase().removePrefix("0x")
            clean.isNotEmpty() && clean.matches(Regex("[0-9a-f]{1,4}"))
        } else {
            prefix.matches(Regex("[0-9A-F]{2,12}"))
        }
    }
}
