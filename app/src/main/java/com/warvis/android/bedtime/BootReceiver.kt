package com.warvis.android.bedtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.warvis.android.doom.DoomShieldWeeklyReportManager
import com.warvis.android.doom.InstagramGamingRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            BedtimeManager.scheduleIfEnabled(context)
            DoomShieldWeeklyReportManager.schedule(context)
            InstagramGamingRepository(context).reconcile()
        }
    }
}
