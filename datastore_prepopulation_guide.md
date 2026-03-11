# DataStore Prepopulation Implementation

## What Was Changed

Instead of returning default MAC prefixes as fallbacks when the datastore is empty, the app now **prepopulates the datastore** with default values on first launch.

## Implementation Details

### Key Changes in MacPrefixRepository.kt

#### Before (Fallback Pattern):
```kotlin
val macPrefixes: Flow<Set<String>> = context.dataStore.data
    .map { preferences ->
        preferences[PreferencesKeys.MAC_PREFIXES] ?: getDefaultMacPrefixes()  // ❌ Fallback
    }
```

#### After (Prepopulation Pattern):
```kotlin
val macPrefixes: Flow<Set<String>> = context.dataStore.data
    .map { preferences ->
        preferences[PreferencesKeys.MAC_PREFIXES] ?: emptySet()  // ✅ Empty if not initialized
    }

suspend fun initializeDefaultsIfNeeded() {
    val preferences = context.dataStore.data.first()
    val defaultsInitialized = preferences[DEFAULTS_INITIALIZED] ?: false

    if (!defaultsInitialized) {
        android.util.Log.d("MacPrefixRepository", "Initializing default MAC prefixes")
        val defaultPrefixes = getDefaultMacPrefixes()

        context.dataStore.edit { prefs ->
            prefs[MAC_PREFIXES] = defaultPrefixes                    // ✅ Store in datastore
            prefs[DEFAULTS_INITIALIZED] = true                       // ✅ Mark as initialized
        }
    }
}
```

### New DataStore Keys

```kotlin
private object PreferencesKeys {
    val MAC_PREFIXES = stringSetPreferencesKey("mac_prefixes")
    val DEFAULTS_INITIALIZED = booleanPreferencesKey("defaults_initialized")  // ✅ New flag
}
```

### MainViewModel Integration

The ViewModel now calls initialization during setup:

```kotlin
fun initializeScanner(context: Context) {
    macPrefixRepository = MacPrefixRepository(context)

    // Initialize default MAC prefixes if this is the first run
    viewModelScope.launch {
        macPrefixRepository?.initializeDefaultsIfNeeded()  // ✅ Prepopulate on first run
    }

    // Observe MAC prefixes from repository
    viewModelScope.launch {
        macPrefixRepository?.macPrefixes?.collect { prefixes ->
            _macPrefixes.value = prefixes  // ✅ Now gets actual stored values
        }
    }
}
```

## Benefits of Prepopulation

### ✅ **True Persistence**
- Default MAC prefixes are actually stored in DataStore
- User can see and manage the default values in the UI
- Defaults persist across app restarts/updates

### ✅ **Consistent UI Behavior**
- MAC prefix list shows actual stored values
- Users can remove default prefixes if not needed
- No hidden "magic" defaults that users can't see

### ✅ **Better User Experience**
- First-time users immediately see configured prefixes
- No confusion about where defaults come from
- Clear indication of what the app is monitoring

## Default MAC Prefixes

The following META/Smart Glasses prefixes are prepopulated:

```kotlin
private fun getDefaultMacPrefixes(): Set<String> {
    return setOf(
        "7C2A9E",    // META device prefix
        "CC660A",    // META device prefix
        "F40343",    // META device prefix
        "5CE91E"     // META device prefix
    )
}
```

*Source: [glass-detect project](https://github.com/sh4d0wm45k/glass-detect/blob/main/glass-detect/glass-detect.ino)*

## Testing the Implementation

### 1. Fresh Install Test
To test the prepopulation on a fresh install:

```bash
# Uninstall the app to clear all data
adb uninstall com.whatihavedone.blearoundme

# Install and run the app
./gradlew installDebug
adb shell am start -n com.whatihavedone.blearoundme/.MainActivity

# Check logs for initialization
adb logcat | grep "MacPrefixRepository"
```

**Expected Logs:**
```
MacPrefixRepository: Initializing default MAC prefixes
MacPrefixRepository: Default MAC prefixes initialized: [7C2A9E, CC660A, F40343, 5CE91E]
```

### 2. DataStore Inspection

You can verify the stored data using ADB:

```bash
# View the DataStore file (requires root or debuggable app)
adb shell run-as com.whatihavedone.blearoundme cat files/datastore/settings.preferences_pb
```

### 3. UI Verification

1. **First Launch**: MAC prefix section should show 4 default prefixes
2. **Subsequent Launches**: Same prefixes should appear (stored, not fallback)
3. **User Management**: Users can add/remove prefixes including defaults

### 4. Clear Data Test

To test that initialization only happens once:

```bash
# Clear app data
adb shell pm clear com.whatihavedone.blearoundme

# Launch app again - should see initialization logs again
```

## Migration Considerations

### Existing Users
- Users who already have MAC prefixes stored will not be affected
- The `DEFAULTS_INITIALIZED` flag prevents re-initialization
- Existing data is preserved

### Fresh Users
- First launch triggers initialization
- Default prefixes are immediately available
- Users can customize from there

## Architecture Flow

```
App Launch → MainViewModel.initializeScanner()
    ↓
MacPrefixRepository.initializeDefaultsIfNeeded()
    ↓
Check DEFAULTS_INITIALIZED flag
    ↓
If false: Store defaults + set flag to true
    ↓
UI observes macPrefixes flow → Shows stored values
```

## Debugging Tips

### Check if Defaults Were Initialized
Look for this log on first launch:
```
MacPrefixRepository: Initializing default MAC prefixes
```

### Verify Stored Data
The MAC prefix section in the UI should show exactly 4 prefixes on fresh install.

### Force Re-initialization
Clear app data to trigger initialization again:
```bash
adb shell pm clear com.whatihavedone.blearoundme
```

This implementation ensures users get a consistent, manageable set of default MAC prefixes that are actually stored in the DataStore rather than being virtual fallbacks.