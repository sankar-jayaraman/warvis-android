package com.warvis.android.doom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramGamingPolicyTest {
    private val config = InstagramGamingConfig()
    private val purchase = InstagramGamingPurchaseSnapshot(
        sessionId = "11111111-1111-4111-8111-111111111111",
        config = config,
    )
    private val session = InstagramGamingSession(
        purchase = purchase,
        taskRatchetRequestId = "request-test",
        startedAtEpochMillis = 1_000L,
        deadlineEpochMillis = 1_000L + config.sessionDurationMillis,
    )

    @Test
    fun defaultsAndBoundsAreExplicitAndValidatedAtEdges() {
        assertEquals(180, config.sessionMinutes)
        assertEquals(3, config.allowanceMinutes)
        assertEquals(5, config.quarantineMinutes)
        assertTrue(InstagramGamingConfig(15, 1, 1).isValid)
        assertTrue(InstagramGamingConfig(720, 30, 60).isValid)
        assertFalse(InstagramGamingConfig(14, 1, 1).isValid)
        assertFalse(InstagramGamingConfig(721, 1, 1).isValid)
        assertFalse(InstagramGamingConfig(15, 0, 1).isValid)
        assertFalse(InstagramGamingConfig(15, 31, 1).isValid)
        assertFalse(InstagramGamingConfig(15, 1, 0).isValid)
        assertFalse(InstagramGamingConfig(15, 1, 61).isValid)
    }

    @Test
    fun sessionDeadlineIsExclusiveAndQuarantineWinsUntilItsExclusiveDeadline() {
        val deadline = session.deadlineEpochMillis
        assertEquals(
            InstagramGamingPhase.Active,
            InstagramGamingPolicy.status(
                config,
                session,
                clock(deadline - 1L),
            ).phase,
        )
        assertEquals(
            InstagramGamingPhase.Inactive,
            InstagramGamingPolicy.status(config, session, clock(deadline)).phase,
        )

        val quarantined = session.copy(quarantineUntilEpochMillis = 20_000L)
        assertEquals(
            InstagramGamingPhase.Quarantine,
            InstagramGamingPolicy.status(
                config,
                quarantined,
                clock(19_999L),
            ).phase,
        )
        val reconciled = InstagramGamingPolicy.reconciledSession(
            quarantined,
            clock(20_000L),
        )
        assertEquals(null, reconciled?.quarantineUntilEpochMillis)
        assertEquals(0L, reconciled?.consumedForegroundMillis)
    }

    @Test
    fun allowanceBoundaryIsInclusiveAndCumulativeAcrossForegroundSegments() {
        val persistence = FakeInstagramGamingPersistence()
        val repository = InstagramGamingRepository(persistence)
        val binding = purchase.encode()
        assertEquals(
            InstagramGamingGrantResult.Granted,
            repository.grant("request-test", binding, 1_000L),
        )

        val first = repository.addForegroundUsage(60_000L, 2_000L)
        val second = repository.addForegroundUsage(119_999L, 3_000L)
        val boundary = repository.addForegroundUsage(1L, 4_000L)

        assertEquals(120_000L, first.allowanceRemainingMillis)
        assertEquals(1L, second.allowanceRemainingMillis)
        assertEquals(0L, boundary.allowanceRemainingMillis)
        assertEquals(
            InstagramGamingPhase.Quarantine,
            repository.enterQuarantine(4_000L).phase,
        )
    }

    @Test
    fun accessPrecedencePutsQuarantineBeforeGamingAndGenericGrace() {
        assertEquals(
            InstagramAccessDecision.BlockQuarantine,
            InstagramAccessPolicy.decide(
                INSTAGRAM_APP_TARGET_KEY,
                InstagramGamingPhase.Quarantine,
                genericGraceActive = true,
            ),
        )
        assertEquals(
            InstagramAccessDecision.AllowPaidGaming,
            InstagramAccessPolicy.decide(
                INSTAGRAM_DOMAIN_TARGET_KEY,
                InstagramGamingPhase.Active,
                genericGraceActive = true,
            ),
        )
        assertEquals(
            InstagramAccessDecision.NotInstagram,
            InstagramAccessPolicy.decide(
                "domain:youtube.com",
                InstagramGamingPhase.Quarantine,
                genericGraceActive = false,
            ),
        )
    }

    @Test
    fun foregroundTransitionsUseTheActualActiveWindowNotTheEventSource() {
        val instagram = InstagramActiveWindow.Instagram(
            INSTAGRAM_APP_TARGET_KEY,
            "com.instagram.android",
        )
        assertEquals(
            InstagramForegroundTransition.Ignore,
            InstagramForegroundTransitionPolicy.decide(
                relevantEvent = false,
                activeWindow = InstagramActiveWindow.Other,
                meteredTargetKey = INSTAGRAM_APP_TARGET_KEY,
                meteredPackageName = "com.instagram.android",
            ),
        )
        assertEquals(
            InstagramForegroundTransition.Keep,
            InstagramForegroundTransitionPolicy.decide(
                relevantEvent = true,
                activeWindow = instagram,
                meteredTargetKey = INSTAGRAM_APP_TARGET_KEY,
                meteredPackageName = "com.instagram.android",
            ),
        )
        assertEquals(
            InstagramForegroundTransition.Ignore,
            InstagramForegroundTransitionPolicy.decide(
                relevantEvent = true,
                activeWindow = InstagramActiveWindow.Unavailable,
                meteredTargetKey = INSTAGRAM_APP_TARGET_KEY,
                meteredPackageName = "com.instagram.android",
            ),
        )
        assertEquals(
            InstagramForegroundTransition.Stop,
            InstagramForegroundTransitionPolicy.decide(
                relevantEvent = true,
                activeWindow = InstagramActiveWindow.Other,
                meteredTargetKey = INSTAGRAM_APP_TARGET_KEY,
                meteredPackageName = "com.instagram.android",
            ),
        )
    }

    @Test
    fun browserSamePackageUrlChangesStopAndRestartByObservedDomain() {
        assertEquals(
            InstagramForegroundTransition.Stop,
            InstagramForegroundTransitionPolicy.decide(
                relevantEvent = true,
                activeWindow = InstagramActiveWindow.Other,
                meteredTargetKey = INSTAGRAM_DOMAIN_TARGET_KEY,
                meteredPackageName = "com.android.chrome",
            ),
        )
        assertEquals(
            InstagramForegroundTransition.StartOrSwitch,
            InstagramForegroundTransitionPolicy.decide(
                relevantEvent = true,
                activeWindow = InstagramActiveWindow.Instagram(
                    INSTAGRAM_DOMAIN_TARGET_KEY,
                    "com.android.chrome",
                ),
                meteredTargetKey = null,
                meteredPackageName = null,
            ),
        )
    }

    @Test
    fun screenOffSettlesOnlyTheCurrentSegmentAndStaleCallbacksAreRejected() {
        val screenOffDelta = InstagramForegroundUsagePolicy.settledDeltaMillis(
            startedAtElapsedMillis = 10_000L,
            nowElapsedMillis = 25_000L,
        )
        val unlockedDelta = InstagramForegroundUsagePolicy.settledDeltaMillis(
            startedAtElapsedMillis = 125_000L,
            nowElapsedMillis = 130_000L,
        )

        assertEquals(15_000L, screenOffDelta)
        assertEquals(5_000L, unlockedDelta)
        assertTrue(
            InstagramMeterCallbackPolicy.shouldRun(
                callbackGeneration = 4L,
                currentGeneration = 4L,
                meterActive = true,
            ),
        )
        assertFalse(
            InstagramMeterCallbackPolicy.shouldRun(
                callbackGeneration = 3L,
                currentGeneration = 4L,
                meterActive = true,
            ),
        )
        assertFalse(
            InstagramMeterCallbackPolicy.shouldRun(
                callbackGeneration = 4L,
                currentGeneration = 4L,
                meterActive = false,
            ),
        )
    }

    private fun clock(epochMillis: Long): InstagramGamingClockReading =
        InstagramGamingClockReading(
            epochMillis = epochMillis,
            elapsedRealtimeMillis = 0L,
            bootId = null,
        )
}

internal class FakeInstagramGamingPersistence(
    var raw: String? = null,
    private val commitResults: ArrayDeque<Boolean> = ArrayDeque(),
) : InstagramGamingPersistence {
    val commits = mutableListOf<String>()

    override fun read(): String? = raw

    override fun commit(raw: String): Boolean {
        commits += raw
        val result = if (commitResults.isEmpty()) true else commitResults.removeFirst()
        if (result) this.raw = raw
        return result
    }
}
