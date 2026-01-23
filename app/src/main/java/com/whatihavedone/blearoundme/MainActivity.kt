package com.whatihavedone.blearoundme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.whatihavedone.blearoundme.permissions.rememberPermissionState
import com.whatihavedone.blearoundme.repository.BleRepository
import com.whatihavedone.blearoundme.ui.screen.MainScreen
import com.whatihavedone.blearoundme.ui.theme.BLEAroudMeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BLEAroudMeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val permissionState = rememberPermissionState()
                    MainScreen(permissionState = permissionState)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up repository if this is the final activity destruction
        if (isFinishing) {
            BleRepository.cleanup()
        }
    }
}