package com.warvis.android.doom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightCurfewScheduleTest {
    @Test
    fun defaultSchedule_preservesInclusiveStartAndExclusiveEnd() {
        val schedule = NightCurfewSchedule.Default

        assertTrue(schedule.enabled)
        assertEquals("21:00-10:00", schedule.intervalLabel)
        assertTrue(schedule.crossesMidnight)
        assertFalse(isActive(schedule, 20, 59))
        assertTrue(isActive(schedule, 21, 0))
        assertTrue(isActive(schedule, 9, 59))
        assertFalse(isActive(schedule, 10, 0))
    }

    @Test
    fun sameDaySchedule_usesInclusiveStartAndExclusiveEnd() {
        val schedule = requireNotNull(
            NightCurfewSchedule.create(
                enabled = true,
                startMinute = 13 * 60,
                endMinute = 17 * 60,
            ),
        )

        assertFalse(schedule.crossesMidnight)
        assertFalse(isActive(schedule, 12, 59))
        assertTrue(isActive(schedule, 13, 0))
        assertTrue(isActive(schedule, 16, 59))
        assertFalse(isActive(schedule, 17, 0))
    }

    @Test
    fun crossMidnightSchedule_spansEndAndStartOfDay() {
        val schedule = requireNotNull(
            NightCurfewSchedule.create(
                enabled = true,
                startMinute = 22 * 60 + 30,
                endMinute = 6 * 60 + 15,
            ),
        )

        assertFalse(isActive(schedule, 22, 29))
        assertTrue(isActive(schedule, 22, 30))
        assertTrue(isActive(schedule, 0, 0))
        assertTrue(isActive(schedule, 6, 14))
        assertFalse(isActive(schedule, 6, 15))
    }

    @Test
    fun disabledSchedule_isNeverActive() {
        val schedule = NightCurfewSchedule.Default.copy(enabled = false)

        assertFalse(isActive(schedule, 21, 0))
        assertFalse(isActive(schedule, 0, 0))
        assertFalse(isActive(schedule, 9, 59))
    }

    @Test
    fun equalStartAndEnd_isInvalidAndNeverActive() {
        val schedule = requireNotNull(
            NightCurfewSchedule.create(
                enabled = true,
                startMinute = 12 * 60,
                endMinute = 12 * 60,
            ),
        )

        assertFalse(schedule.isValid)
        assertFalse(isActive(schedule, 12, 0))
        assertFalse(isActive(schedule, 0, 0))
    }

    @Test
    fun corruptPersistedValues_fallBackToDefaultSchedule() {
        val cases = listOf(
            NightCurfewPolicy.restoredSchedule(true, -1, 600),
            NightCurfewPolicy.restoredSchedule(true, 1_440, 600),
            NightCurfewPolicy.restoredSchedule(true, 600, 1_440),
            NightCurfewPolicy.restoredSchedule(true, 600, 600),
            NightCurfewPolicy.restoredSchedule(null, 1_260, 600),
            NightCurfewPolicy.restoredSchedule(true, null, 600),
        )

        cases.forEach { schedule ->
            assertEquals(NightCurfewSchedule.Default, schedule)
        }
    }

    private fun isActive(schedule: NightCurfewSchedule, hour: Int, minute: Int): Boolean =
        NightCurfewPolicy.isActive(
            schedule,
            requireNotNull(MinuteOfDay.fromHourMinute(hour, minute)),
        )
}
