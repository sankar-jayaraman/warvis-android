package com.warvis.android.bedtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BedtimeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BedtimeManager.isEnabledRaw(context)) return
        ensureChannel(context)
        val activityIntent = Intent(context, BedtimeInterventionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(
            context,
            BedtimeManager.NOTIFICATION_CHANNEL_ID,
        )
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("WARVIS — Wind Down")
            .setContentText("It's 7 PM. Time to step away and rest.")
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(BedtimeManager.NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
