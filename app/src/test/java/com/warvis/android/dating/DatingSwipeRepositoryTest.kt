package com.warvis.android.dating

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatingSwipeRepositoryTest {
    @Test
    fun increment_persistsIndependentCountsForEachDatingApp() {
        val persistence = FakeDatingSwipePersistence()
        val day = LocalDate.of(2026, 11, 25)
        val repository = DatingSwipeRepository(persistence) { day }

        assertTrue(repository.increment(DatingApp.Hinge))
        assertTrue(repository.increment(DatingApp.Tinder))
        assertTrue(repository.increment(DatingApp.Tinder))
        assertTrue(repository.increment(DatingApp.Bumble))

        val snapshot = repository.readToday()
        assertEquals(day, snapshot.day)
        assertEquals(1, snapshot.hingeCount)
        assertEquals(2, snapshot.tinderCount)
        assertEquals(1, snapshot.bumbleCount)
        assertEquals(4, snapshot.totalCount)
    }

    @Test
    fun readToday_startsFreshOnLocalDayRolloverAndKeepsOnlyNewDayCounts() {
        val persistence = FakeDatingSwipePersistence()
        var day = LocalDate.of(2026, 11, 25)
        val repository = DatingSwipeRepository(persistence) { day }

        repository.increment(DatingApp.Hinge)
        day = day.plusDays(1)

        val rolledOver = repository.readToday()
        assertEquals(DatingSwipeSnapshot.empty(day), rolledOver)

        repository.increment(DatingApp.Bumble)
        val current = repository.readToday()
        assertEquals(0, current.hingeCount)
        assertEquals(0, current.tinderCount)
        assertEquals(1, current.bumbleCount)
    }

    @Test
    fun corruptState_fallsBackToEmptyAndReplacesStoredState() {
        val persistence = FakeDatingSwipePersistence(raw = "not-a-valid-state")
        val day = LocalDate.of(2026, 11, 25)

        val snapshot = DatingSwipeRepository(persistence) { day }.readToday()

        assertEquals(DatingSwipeSnapshot.empty(day), snapshot)
        assertEquals(1, persistence.commits.size)
        assertNotNull(DatingSwipeStateCodec.decode(persistence.raw.orEmpty()))
    }

    @Test
    fun invalidCountState_isRejectedAsCorrupt() {
        val persistence = FakeDatingSwipePersistence(
            raw = "1\t2026-11-25\t-1\t0\t0",
        )
        val day = LocalDate.of(2026, 11, 25)

        val snapshot = DatingSwipeRepository(persistence) { day }.readToday()

        assertEquals(DatingSwipeSnapshot.empty(day), snapshot)
        assertEquals(1, persistence.commits.size)
    }

    private class FakeDatingSwipePersistence(
        var raw: String? = null,
    ) : DatingSwipePersistence {
        val commits = mutableListOf<String>()

        override fun read(): String? = raw

        override fun commit(raw: String): Boolean {
            commits += raw
            this.raw = raw
            return true
        }
    }
}
