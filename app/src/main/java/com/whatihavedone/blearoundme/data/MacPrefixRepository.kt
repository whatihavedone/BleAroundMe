package com.whatihavedone.blearoundme.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MacPrefixRepository(private val context: Context) {

    private object PreferencesKeys {
        val MAC_PREFIXES = stringPreferencesKey("mac_prefixes_json")
        val DEFAULTS_INITIALIZED = booleanPreferencesKey("defaults_initialized")
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
            android.util.Log.d("MacPrefixRepository", "Initializing default MAC prefixes")
            val defaultPrefixes = MacPrefix.getDefaultMacPrefixes()
            val jsonString = Json.encodeToString(defaultPrefixes)

            context.dataStore.edit { prefs ->
                prefs[PreferencesKeys.MAC_PREFIXES] = jsonString
                prefs[PreferencesKeys.DEFAULTS_INITIALIZED] = true
            }

            android.util.Log.d(
                "MacPrefixRepository",
                "Default MAC prefixes initialized: ${defaultPrefixes.size} prefixes"
            )
        }
    }

    suspend fun addMacPrefix(prefix: String, tag: String = "Custom Device") {
        val normalizedPrefix = normalizeMacPrefix(prefix)
        if (isValidMacPrefix(normalizedPrefix)) {
            val newPrefix = MacPrefix(normalizedPrefix, tag)

            context.dataStore.edit { preferences ->
                val jsonString = preferences[PreferencesKeys.MAC_PREFIXES]
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
                preferences[PreferencesKeys.MAC_PREFIXES] = Json.encodeToString(currentPrefixes)
            }
        }
    }

    suspend fun removeMacPrefix(macPrefix: MacPrefix) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[PreferencesKeys.MAC_PREFIXES]
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
            preferences[PreferencesKeys.MAC_PREFIXES] = Json.encodeToString(currentPrefixes)
        }
    }

    private fun normalizeMacPrefix(prefix: String): String {
        // Remove colons and convert to uppercase
        return prefix.replace(":", "").uppercase()
    }

    private fun isValidMacPrefix(prefix: String): Boolean {
        // Check if prefix contains only valid hex characters and is between 2-12 characters
        return prefix.matches(Regex("[0-9A-F]{2,12}"))
    }

}