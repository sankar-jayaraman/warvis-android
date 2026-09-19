package com.warvis.android.doom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.warvis.android.MainActivity

class DoomShieldWeeklyReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager)

        val comparison = DoomShieldSwitchLog.weeklyComparison(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(
            context,
            DoomShieldWeeklyReportManager.NOTIFICATION_CHANNEL_ID,
        )
            .setSmallIcon(android.R.drawable.ic_menu_week)
            .setContentTitle("WARVIS Doom Shield weekly result")
            .setContentText(comparison.notificationText())
            .setStyle(
                android.app.Notification.BigTextStyle()
                    .bigText(comparison.notificationText()),
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(DoomShieldWeeklyReportManager.NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (
            notificationManager.getNotificationChannel(
                DoomShieldWeeklyReportManager.NOTIFICATION_CHANNEL_ID,
            ) != null
        ) {
            return
        }

        notificationManager.createNotificationChannel(
            NotificationChannel(
                DoomShieldWeeklyReportManager.NOTIFICATION_CHANNEL_ID,
                "Doom Shield weekly report",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Weekly Kindle redirect count and previous-week comparison"
            },
        )
    }
}
