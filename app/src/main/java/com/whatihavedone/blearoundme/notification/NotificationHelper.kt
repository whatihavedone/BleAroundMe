package com.whatihavedone.blearoundme.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.whatihavedone.blearoundme.MainActivity
import com.whatihavedone.blearoundme.R
import com.whatihavedone.blearoundme.ble.BleScanResult
import com.whatihavedone.blearoundme.data.MacPrefixRepository
import com.whatihavedone.blearoundme.service.BleScanService.Companion.FOREGROUND_NOTIFICATION_ID
import kotlinx.coroutines.flow.first

class NotificationHelper(private val context: Context) {

    companion object {
        private const val DEVICE_FOUND_CHANNEL_ID = "device_found_channel"
        private const val DEVICE_FOUND_NOTIFICATION_ID = 2001
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for device found notifications
            val deviceFoundChannel = NotificationChannel(
                DEVICE_FOUND_CHANNEL_ID,
                "Device Found Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when matching BLE devices are found"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(deviceFoundChannel)
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(DEVICE_FOUND_NOTIFICATION_ID)
        notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
    }

    suspend fun showDeviceFoundNotification(device: BleScanResult) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deviceName = device.deviceName ?: "Unknown Device"
        val title = "Target device Detected!"
        val text = "$deviceName found nearby (${device.macAddress})"
        val bigText = """
            Device: $deviceName
            Tag: ${device.matchingTag ?: "Matched Device"}
            MAC: ${device.macAddress}
            Signal Strength: ${device.rssi} dBm
            Time: ${formatTimestamp(device.timestamp)}
        """.trimIndent()

        val notification = NotificationCompat.Builder(context, DEVICE_FOUND_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_bluetooth_searching)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        try {
            notificationManager.notify(DEVICE_FOUND_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Handle case where notification permission is not granted
            android.util.Log.w("NotificationHelper", "Failed to show notification: ${e.message}")
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }
    }
}
