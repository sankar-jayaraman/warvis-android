package com.warvis.android.doom

import org.junit.Assert.assertEquals
import org.junit.Test

class NightCurfewRepositoryTest {
    @Test
    fun readSnapshot_fallsBackForWrongPersistedTypes() {
        val persistence = FakeNightCurfewPersistence(
            values = mutableMapOf(
                NightCurfewRepository.KEY_ENABLED to "true",
                NightCurfewRepository.KEY_START_MINUTE to 1_260,
                NightCurfewRepository.KEY_END_MINUTE to 600,
            ),
        )

        assertEquals(
            NightCurfewSchedule.Default,
            NightCurfewRepository(persistence).readSnapshot(),
        )
    }

    @Test
    fun save_commitsTheWholeValidScheduleOnce() {
        val persistence = FakeNightCurfewPersistence()
        val repository = NightCurfewRepository(persistence)
        val schedule = requireNotNull(
            NightCurfewSchedule.create(
                enabled = false,
                startMinute = 13 * 60,
                endMinute = 17 * 60,
            ),
        )

        assertEquals(NightCurfewSaveResult.Persisted, repository.save(schedule))
        assertEquals(listOf(schedule), persistence.commitAttempts)
        assertEquals(schedule, repository.readSnapshot())
    }

    @Test
    fun save_rejectsEqualTimesWithoutWriting() {
        val persistence = FakeNightCurfewPersistence()
        val repository = NightCurfewRepository(persistence)
        val invalid = requireNotNull(
            NightCurfewSchedule.create(
                enabled = false,
                startMinute = 600,
                endMinute = 600,
            ),
        )

        assertEquals(NightCurfewSaveResult.InvalidSchedule, repository.save(invalid))
        assertEquals(emptyList<NightCurfewSchedule>(), persistence.commitAttempts)
    }

    @Test
    fun failedSave_returnsFailureAndRestoresPreviousTruth() {
        val persistence = FakeNightCurfewPersistence(
            commitResults = ArrayDeque(listOf(false, true)),
        )
        val repository = NightCurfewRepository(persistence)
        val requested = requireNotNull(
            NightCurfewSchedule.create(
                enabled = true,
                startMinute = 13 * 60,
                endMinute = 17 * 60,
            ),
        )

        assertEquals(NightCurfewSaveResult.PersistenceFailed, repository.save(requested))
        assertEquals(
            listOf(requested, NightCurfewSchedule.Default),
            persistence.commitAttempts,
        )
        assertEquals(NightCurfewSchedule.Default, repository.readSnapshot())
    }

    private class FakeNightCurfewPersistence(
        private val values: MutableMap<String, Any?> = mutableMapOf(),
        private val commitResults: ArrayDeque<Boolean> = ArrayDeque(),
    ) : NightCurfewPersistence {
        val commitAttempts = mutableListOf<NightCurfewSchedule>()

        override fun readSnapshot(): Map<String, Any?> = values.toMap()

        override fun commit(schedule: NightCurfewSchedule): Boolean {
            commitAttempts += schedule
            values[NightCurfewRepository.KEY_ENABLED] = schedule.enabled
            values[NightCurfewRepository.KEY_START_MINUTE] = schedule.start.value
            values[NightCurfewRepository.KEY_END_MINUTE] = schedule.end.value
            return if (commitResults.isEmpty()) true else commitResults.removeFirst()
        }
    }
}
