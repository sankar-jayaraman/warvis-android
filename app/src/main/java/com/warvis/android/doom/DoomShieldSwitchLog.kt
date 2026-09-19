package com.warvis.android.doom

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

internal data class DoomShieldWeeklyComparison(
    val currentWeekCount: Int,
    val previousWeekCount: Int,
) {
    fun notificationText(): String {
        val difference = currentWeekCount - previousWeekCount
        val comparison = when {
            difference < 0 -> "${-difference} fewer than last week's $previousWeekCount"
            difference > 0 -> "$difference more than last week's $previousWeekCount"
            else -> "the same as last week's $previousWeekCount"
        }
        return "Kindle redirects this week: $currentWeekCount — $comparison. Goal: zero."
    }
}

internal object DoomShieldSwitchLog {
    private const val PREFERENCES_NAME = "doom_shield_switch_log"
    private const val KEY_EVENTS = "switch_events"

    fun recordSwitch(
        context: Context,
        target: DoomShieldTarget,
        timestampMillis: Long = System.currentTimeMillis(),
    ) {
        val preferences = preferences(context)
        val events = preferences.getStringSet(KEY_EVENTS, emptySet()).orEmpty().toMutableSet()
        events += "$timestampMillis|${target.key}|${UUID.randomUUID()}"
        preferences.edit().putStringSet(KEY_EVENTS, events).apply()
    }

    fun weeklyComparison(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DoomShieldWeeklyComparison {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val currentWeekStart = now
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val previousWeekStart = Instant.ofEpochMilli(currentWeekStart)
            .atZone(zoneId)
            .minusWeeks(1)
            .toInstant()
            .toEpochMilli()

        val timestamps = readEvents(context)
        val comparison = DoomShieldWeeklyComparison(
            currentWeekCount = timestamps.count { it in currentWeekStart..nowMillis },
            previousWeekCount = timestamps.count { it in previousWeekStart until currentWeekStart },
        )
        removeEventsBefore(context, previousWeekStart)
        return comparison
    }

    private fun readEvents(context: Context): List<Long> =
        preferences(context)
            .getStringSet(KEY_EVENTS, emptySet())
            .orEmpty()
            .mapNotNull { event -> event.substringBefore('|').toLongOrNull() }

    private fun removeEventsBefore(context: Context, cutoffMillis: Long) {
        val preferences = preferences(context)
        val retainedEvents = preferences
            .getStringSet(KEY_EVENTS, emptySet())
            .orEmpty()
            .filterTo(mutableSetOf()) { event ->
                event.substringBefore('|').toLongOrNull()?.let { it >= cutoffMillis } == true
            }
        preferences.edit().putStringSet(KEY_EVENTS, retainedEvents).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
