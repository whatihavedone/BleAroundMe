package com.whatihavedone.blearoundme.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.whatihavedone.blearoundme.MainActivity
import com.whatihavedone.blearoundme.R
import com.whatihavedone.blearoundme.ble.BleScanner
import com.whatihavedone.blearoundme.data.MacPrefixRepository
import com.whatihavedone.blearoundme.notification.NotificationHelper
import kotlinx.coroutines.launch

class BleScanService : LifecycleService() {

    companion object {
        const val ACTION_START_SCANNING = "com.whatihavedone.blearoundme.START_SCANNING"
        const val ACTION_STOP_SCANNING = "com.whatihavedone.blearoundme.STOP_SCANNING"
         const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ble_scan_service_channel"
    }

    private lateinit var bleScanner: BleScanner
    private lateinit var macPrefixRepository: MacPrefixRepository
    private lateinit var notificationHelper: NotificationHelper

    inner class LocalBinder : Binder() {
        fun getService(): BleScanService = this@BleScanService
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        Log.d("BleScanService", "Service created")

        bleScanner = BleScanner(this)
        macPrefixRepository = MacPrefixRepository(this)
        notificationHelper = NotificationHelper(this)

        createNotificationChannel()
        observeScanResults()
        observeMacPrefixes()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        Log.d("BleScanService", "onStartCommand called with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_SCANNING -> {
                Log.i("BleScanService", "Starting scanning service")
                startForegroundService()
                startScanning()
            }
            ACTION_STOP_SCANNING -> {
                Log.i("BleScanService", "Stopping scanning service")
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopScanning()
                stopSelf()
            }
            else -> {
                Log.w("BleScanService", "Unknown action: ${intent?.action}")
                // If no action specified, assume we want to start scanning
                startForegroundService()
                startScanning()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    // Public methods to expose scanner state
    fun getScanResults() = bleScanner.scanResults
    fun getFoundMatchingDevices() = bleScanner.foundMatchingDevices
    fun isScanning() = bleScanner.isScanning

    // Method to clear scan results
    fun clearScanResults() {
        bleScanner.clearResults()
    }

    private fun startForegroundService() {
        val notification = createForegroundNotification()
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        Log.i("BleScanService", "Started as foreground service")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BLE Scanning Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background BLE scanning for \"smart\" devices detection"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BleScanService::class.java).apply {
            action = ACTION_STOP_SCANNING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE devices Detector")
            .setContentText("Scanning for nearby devices...")
            .setSmallIcon(R.drawable.ic_bluetooth_searching)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_bluetooth_searching,
                "Stop",
                stopPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateForegroundNotification(devicesFound: Int) {
        // If we are no longer scanning, don't update/re-post the notification
        if (!bleScanner.isScanning.value) return

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BleScanService::class.java).apply {
            action = ACTION_STOP_SCANNING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (devicesFound == 0) {
            "Scanning for devices..."
        } else {
            "Found $devicesFound devices"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE devices Detector")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_bluetooth_searching)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_bluetooth_searching,
                "Stop",
                stopPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun observeScanResults() {
        lifecycleScope.launch {
            bleScanner.scanResults.collect { allDevices ->
                // Update foreground notification with scan progress
                updateForegroundNotification(allDevices.size)
            }
        }

        lifecycleScope.launch {
            bleScanner.foundMatchingDevices.collect { matchingDevices ->
                if (matchingDevices.isNotEmpty() && bleScanner.isScanning.value) {
                    val latestDevice = matchingDevices.maxByOrNull { it.timestamp }
                    latestDevice?.let { device ->
                        Log.i("BleScanService", "Showing notification for device: ${device.deviceName} (${device.macAddress})")
                        notificationHelper.showDeviceFoundNotification(device)
                    }
                }
            }
        }
    }

    private fun observeMacPrefixes() {
        lifecycleScope.launch {
            macPrefixRepository.macPrefixes.collect { prefixes ->
                bleScanner.setFilterCriteria(prefixes)
                Log.d("BleScanService", "Updated scanner with ${prefixes.size} filter criteria")
            }
        }
    }

    private fun startScanning() {
        Log.i("BleScanService", "Starting BLE scan")
        val success = bleScanner.startScan()
        if (!success) {
            Log.e("BleScanService", "Failed to start BLE scan")
            stopSelf()
        }
    }

    private fun stopScanning() {
        Log.i("BleScanService", "Stopping BLE scan")
        bleScanner.stopScan()
        // Explicitly cancel notifications when stopping
        notificationHelper.cancelNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        Log.d("BleScanService", "Service destroyed")
    }
}
