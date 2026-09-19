package com.warvis.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.warvis.android.bedtime.BedtimeManager
import com.warvis.android.doom.DoomShieldWeeklyReportManager
import com.warvis.android.ui.WarvisApp
import com.warvis.android.ui.theme.WarvisTheme

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureBedtimeChannel()
        BedtimeManager.scheduleIfEnabled(this)
        DoomShieldWeeklyReportManager.schedule(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            WarvisTheme {
                WarvisApp()
            }
        }
    }

    private fun ensureBedtimeChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(BedtimeManager.NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            BedtimeManager.NOTIFICATION_CHANNEL_ID,
            "Bedtime",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Daily 7 PM wind-down reminder"
        }
        nm.createNotificationChannel(channel)
    }
}
