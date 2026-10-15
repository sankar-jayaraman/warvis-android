package com.warvis.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.warvis.android.bedtime.BedtimeManager
import com.warvis.android.doom.DoomShieldServiceStatusReader
import com.warvis.android.doom.DoomShieldWeeklyReportManager
import com.warvis.android.ui.WarvisDestinationContract
import com.warvis.android.ui.WarvisDestinationRequest
import com.warvis.android.ui.WarvisApp
import com.warvis.android.ui.theme.WarvisTheme

class MainActivity : ComponentActivity() {
    private var destinationRequest by mutableStateOf<WarvisDestinationRequest?>(null)
    private var doomShieldEnabled by mutableStateOf(false)
    private var safeExitToHome by mutableStateOf(false)
    private var nextDestinationRequestId = 0L
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
        if (savedInstanceState == null) {
            handleDestination(intent)
        } else {
            safeExitToHome = savedInstanceState.getBoolean(STATE_SAFE_EXIT_TO_HOME)
        }
        refreshDoomShieldStatus()
        setContent {
            WarvisTheme {
                WarvisApp(
                    destinationRequest = destinationRequest,
                    doomShieldEnabled = doomShieldEnabled,
                    safeExitToHome = safeExitToHome,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDoomShieldStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDestination(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_SAFE_EXIT_TO_HOME, safeExitToHome)
        super.onSaveInstanceState(outState)
    }

    private fun handleDestination(intent: Intent) {
        val destination = WarvisDestinationContract.destinationFor(
            intent.getStringExtra(WarvisDestinationContract.EXTRA_DESTINATION),
        ) ?: return

        safeExitToHome = intent.getBooleanExtra(
            WarvisDestinationContract.EXTRA_SAFE_EXIT_TO_HOME,
            false,
        )
        intent.removeExtra(WarvisDestinationContract.EXTRA_DESTINATION)
        intent.removeExtra(WarvisDestinationContract.EXTRA_SAFE_EXIT_TO_HOME)
        nextDestinationRequestId += 1
        destinationRequest = WarvisDestinationRequest(
            id = nextDestinationRequestId,
            destination = destination,
        )
    }

    private fun refreshDoomShieldStatus() {
        doomShieldEnabled = DoomShieldServiceStatusReader.isEnabled(this)
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

    private companion object {
        const val STATE_SAFE_EXIT_TO_HOME = "safe_exit_to_home"
    }
}
