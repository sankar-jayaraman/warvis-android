package com.warvis.android.bedtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object BedtimeManager {
    const val BEDTIME_HOUR = 19 // 7 PM
    const val PREFS_NAME = "warvis_bedtime"
    const val KEY_ENABLED = "bedtime_enabled"
    const val NOTIFICATION_CHANNEL_ID = "warvis_bedtime"
    const val NOTIFICATION_ID = 7001
    private const val ALARM_REQUEST_CODE = 7000

    fun isAfterBedtime(): Boolean =
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= BEDTIME_HOUR

    fun isEnabledRaw(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun scheduleIfEnabled(context: Context) {
        if (isEnabledRaw(context)) scheduleAlarm(context)
    }

    fun scheduleAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, BEDTIME_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context),
        )
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, BedtimeAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
