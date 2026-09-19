package com.warvis.android.doom

import org.junit.Assert.assertEquals
import org.junit.Test

class DoomShieldWeeklyComparisonTest {
    @Test
    fun notificationText_reportsReduction() {
        val comparison = DoomShieldWeeklyComparison(
            currentWeekCount = 3,
            previousWeekCount = 8,
        )

        assertEquals(
            "Kindle redirects this week: 3 — 5 fewer than last week's 8. Goal: zero.",
            comparison.notificationText(),
        )
    }

    @Test
    fun notificationText_reportsIncrease() {
        val comparison = DoomShieldWeeklyComparison(
            currentWeekCount = 7,
            previousWeekCount = 4,
        )

        assertEquals(
            "Kindle redirects this week: 7 — 3 more than last week's 4. Goal: zero.",
            comparison.notificationText(),
        )
    }

    @Test
    fun notificationText_reportsNoChangeAndZeroGoal() {
        val comparison = DoomShieldWeeklyComparison(
            currentWeekCount = 0,
            previousWeekCount = 0,
        )

        assertEquals(
            "Kindle redirects this week: 0 — the same as last week's 0. Goal: zero.",
            comparison.notificationText(),
        )
    }
}
