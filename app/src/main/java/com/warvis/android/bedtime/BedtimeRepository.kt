package com.warvis.android.bedtime

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BedtimeRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(BedtimeManager.PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(BedtimeManager.KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(BedtimeManager.KEY_ENABLED, value).apply()
        _enabled.value = value
        if (value) BedtimeManager.scheduleAlarm(context) else BedtimeManager.cancelAlarm(context)
    }
}
