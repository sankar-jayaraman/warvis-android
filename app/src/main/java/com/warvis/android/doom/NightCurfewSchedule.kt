package com.warvis.android.doom

@JvmInline
internal value class MinuteOfDay private constructor(val value: Int) {
    val hour: Int
        get() = value / MINUTES_PER_HOUR

    val minute: Int
        get() = value % MINUTES_PER_HOUR

    val label: String
        get() = "${hour.twoDigits()}:${minute.twoDigits()}"

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        private const val MINUTES_PER_HOUR = 60

        fun fromValue(value: Int): MinuteOfDay? =
            value.takeIf { it in 0 until MINUTES_PER_DAY }?.let(::MinuteOfDay)

        fun fromHourMinute(hour: Int, minute: Int): MinuteOfDay? =
            if (hour in 0..23 && minute in 0..59) {
                MinuteOfDay(hour * MINUTES_PER_HOUR + minute)
            } else {
                null
            }
    }
}

internal data class NightCurfewSchedule(
    val enabled: Boolean,
    val start: MinuteOfDay,
    val end: MinuteOfDay,
) {
    val isValid: Boolean
        get() = start != end

    val crossesMidnight: Boolean
        get() = isValid && start.value > end.value

    val intervalLabel: String
        get() = "${start.label}-${end.label}"

    val summary: String
        get() = buildString {
            append(if (enabled) "Enabled" else "Disabled")
            append(" · ")
            append(intervalLabel)
            if (isValid) {
                append(if (crossesMidnight) " · crosses midnight" else " · same day")
            }
        }

    companion object {
        val Default = NightCurfewSchedule(
            enabled = true,
            start = requireNotNull(MinuteOfDay.fromHourMinute(21, 0)),
            end = requireNotNull(MinuteOfDay.fromHourMinute(10, 0)),
        )

        fun create(enabled: Boolean, startMinute: Int, endMinute: Int): NightCurfewSchedule? {
            val start = MinuteOfDay.fromValue(startMinute) ?: return null
            val end = MinuteOfDay.fromValue(endMinute) ?: return null
            return NightCurfewSchedule(enabled = enabled, start = start, end = end)
        }
    }
}

internal object NightCurfewPolicy {
    fun isActive(schedule: NightCurfewSchedule, minuteOfDay: MinuteOfDay): Boolean {
        if (!schedule.enabled || !schedule.isValid) return false

        return if (schedule.start.value < schedule.end.value) {
            minuteOfDay.value >= schedule.start.value &&
                minuteOfDay.value < schedule.end.value
        } else {
            minuteOfDay.value >= schedule.start.value ||
                minuteOfDay.value < schedule.end.value
        }
    }

    fun restoredSchedule(
        enabled: Boolean?,
        startMinute: Int?,
        endMinute: Int?,
    ): NightCurfewSchedule {
        val schedule = if (enabled != null && startMinute != null && endMinute != null) {
            NightCurfewSchedule.create(enabled, startMinute, endMinute)
        } else {
            null
        }
        return schedule?.takeIf(NightCurfewSchedule::isValid) ?: NightCurfewSchedule.Default
    }
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')
