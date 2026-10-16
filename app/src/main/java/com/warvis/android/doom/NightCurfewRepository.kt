package com.warvis.android.doom

import android.content.Context
import android.content.SharedPreferences

internal enum class NightCurfewSaveResult {
    Persisted,
    InvalidSchedule,
    PersistenceFailed,
}

internal interface NightCurfewPersistence {
    fun readSnapshot(): Map<String, Any?>
    fun commit(schedule: NightCurfewSchedule): Boolean
}

internal class NightCurfewRepository(
    private val persistence: NightCurfewPersistence,
) {
    constructor(context: Context) : this(
        SharedPreferencesNightCurfewPersistence(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
    )
    fun readSnapshot(): NightCurfewSchedule {
        val values = try {
            persistence.readSnapshot()
        } catch (_: RuntimeException) {
            return NightCurfewSchedule.Default
        }
        return NightCurfewPolicy.restoredSchedule(
            enabled = values[KEY_ENABLED] as? Boolean,
            startMinute = values[KEY_START_MINUTE] as? Int,
            endMinute = values[KEY_END_MINUTE] as? Int,
        )
    }

    @Synchronized
    fun save(schedule: NightCurfewSchedule): NightCurfewSaveResult {
        if (!schedule.isValid) return NightCurfewSaveResult.InvalidSchedule

        val previous = readSnapshot()
        return if (commitSafely(schedule)) {
            NightCurfewSaveResult.Persisted
        } else {
            commitSafely(previous)
            NightCurfewSaveResult.PersistenceFailed
        }
    }

    private fun commitSafely(schedule: NightCurfewSchedule): Boolean =
        try {
            persistence.commit(schedule)
        } catch (_: RuntimeException) {
            false
        }

    private class SharedPreferencesNightCurfewPersistence(
        private val preferences: SharedPreferences,
    ) : NightCurfewPersistence {
        override fun readSnapshot(): Map<String, Any?> = preferences.all

        override fun commit(schedule: NightCurfewSchedule): Boolean =
            preferences.edit()
                .putBoolean(KEY_ENABLED, schedule.enabled)
                .putInt(KEY_START_MINUTE, schedule.start.value)
                .putInt(KEY_END_MINUTE, schedule.end.value)
                .commit()
    }

    internal companion object {
        const val PREFERENCES_NAME = "doom_shield_night_curfew"
        const val KEY_ENABLED = "enabled"
        const val KEY_START_MINUTE = "start_minute"
        const val KEY_END_MINUTE = "end_minute"
    }
}
