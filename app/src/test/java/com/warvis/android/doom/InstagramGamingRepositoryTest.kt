package com.warvis.android.doom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramGamingRepositoryTest {
    private val config = InstagramGamingConfig(180, 3, 5)
    private val purchase = InstagramGamingPurchaseSnapshot(
        "22222222-2222-4222-8222-222222222222",
        config,
    )

    @Test
    fun stateRoundTripsWithVersionedClockAnchorsAndConsumedDuration() {
        val persistence = FakeInstagramGamingPersistence()
        val first = InstagramGamingRepository(persistence)
        first.saveConfig(config)
        first.grant("request-roundtrip", purchase.encode(), 10_000L)
        first.addForegroundUsage(42_000L, 20_000L)

        val restored = InstagramGamingRepository(persistence).status(30_000L)

        assertEquals(InstagramGamingPhase.Active, restored.phase)
        assertEquals(138_000L, restored.allowanceRemainingMillis)
        assertNotNull(restored.session)
        assertTrue(persistence.raw.orEmpty().contains("42000"))
        assertTrue(persistence.raw.orEmpty().startsWith("3\t"))
    }

    @Test
    fun corruptStateFailsClosedToDefaultsAndIsReplacedAtomically() {
        val persistence = FakeInstagramGamingPersistence("not-a-valid-schema")
        val status = InstagramGamingRepository(persistence).status(1_000L)

        assertEquals(InstagramGamingPhase.Inactive, status.phase)
        assertEquals(InstagramGamingConfig(), status.config)
        assertEquals(1, persistence.commits.size)
        assertNotNull(InstagramGamingStateCodec.decode(persistence.raw.orEmpty()))
    }

    @Test
    fun rebootRecoveryDoesNotCountOfflineTimeAndExpiredSessionClears() {
        val persistence = FakeInstagramGamingPersistence()
        val first = InstagramGamingRepository(persistence)
        first.grant("request-reboot", purchase.encode(), 1_000L)
        first.addForegroundUsage(30_000L, 2_000L)

        val afterReboot = InstagramGamingRepository(persistence).status(3_600_000L)
        assertEquals(150_000L, afterReboot.allowanceRemainingMillis)

        val afterDeadline = InstagramGamingRepository(persistence).status(
            1_000L + config.sessionDurationMillis,
        )
        assertEquals(InstagramGamingPhase.Inactive, afterDeadline.phase)
        assertNull(afterDeadline.session)
    }

    @Test
    fun confirmedAttemptGrantIsIdempotentEvenAfterSessionExpires() {
        val persistence = FakeInstagramGamingPersistence()
        val repository = InstagramGamingRepository(persistence)

        assertEquals(
            InstagramGamingGrantResult.Granted,
            repository.grant("request-once", purchase.encode(), 1_000L),
        )
        repository.reconcile(1_000L + config.sessionDurationMillis)
        assertEquals(
            InstagramGamingGrantResult.AlreadyGranted,
            repository.grant(
                "request-once",
                InstagramGamingPurchaseSnapshot(
                    "33333333-3333-4333-8333-333333333333",
                    config,
                ).encode(),
                1_000L + config.sessionDurationMillis + 1L,
            ),
        )
        assertEquals(
            InstagramGamingPhase.Inactive,
            repository.status(1_000L + config.sessionDurationMillis + 1L).phase,
        )
    }

    @Test
    fun quarantineResumesByEpochAndResetsAllowanceWithoutExtendingSession() {
        val persistence = FakeInstagramGamingPersistence()
        val repository = InstagramGamingRepository(persistence)
        repository.grant("request-quarantine", purchase.encode(), 1_000L)
        repository.addForegroundUsage(config.allowanceDurationMillis, 2_000L)
        val quarantined = repository.enterQuarantine(2_000L)
        val originalDeadline = quarantined.session?.deadlineEpochMillis
        val quarantineDeadline = quarantined.session?.quarantineUntilEpochMillis!!

        val resumed = InstagramGamingRepository(persistence).status(quarantineDeadline)

        assertEquals(InstagramGamingPhase.Active, resumed.phase)
        assertEquals(config.allowanceDurationMillis, resumed.allowanceRemainingMillis)
        assertEquals(originalDeadline, resumed.session?.deadlineEpochMillis)
    }

    @Test
    fun sameBootElapsedDeadlineWinsOverBackwardAndForwardWallClockChanges() {
        val persistence = FakeInstagramGamingPersistence()
        val repository = InstagramGamingRepository(persistence)
        val grantedAt = clock(epoch = 1_000_000L, elapsed = 10_000L, boot = 7L)
        repository.grant("request-clock", purchase.encode(), grantedAt)
        val afterRollback = repository.status(
            clock(epoch = 100L, elapsed = 20_000L, boot = 7L),
        )
        val afterForwardJump = repository.status(
            clock(
                epoch = 1_000_000L + config.sessionDurationMillis + 1L,
                elapsed = 30_000L,
                boot = 7L,
            ),
        )

        assertEquals(InstagramGamingPhase.Active, afterRollback.phase)
        assertEquals(InstagramGamingPhase.Active, afterForwardJump.phase)
        assertEquals(
            config.sessionDurationMillis - 20_000L,
            afterForwardJump.sessionRemainingMillis,
        )
        assertEquals(7L, afterForwardJump.session?.bootId)
    }

    @Test
    fun serviceRecreationPreservesConsumedAllowanceWithoutCountingDowntime() {
        val persistence = FakeInstagramGamingPersistence()
        val first = InstagramGamingRepository(persistence)
        first.grant(
            "request-recreate",
            purchase.encode(),
            clock(epoch = 100_000L, elapsed = 5_000L, boot = 9L),
        )
        first.addForegroundUsage(
            40_000L,
            clock(epoch = 140_000L, elapsed = 45_000L, boot = 9L),
        )

        val restored = InstagramGamingRepository(persistence).reconcile(
            clock(epoch = 200_000L, elapsed = 105_000L, boot = 9L),
        )

        assertEquals(InstagramGamingPhase.Active, restored.phase)
        assertEquals(140_000L, restored.allowanceRemainingMillis)
    }

    @Test
    fun rebootWithBackwardWallRollbackPreservesOnlyLastTrustedRemaining() {
        val persistence = FakeInstagramGamingPersistence()
        val repository = InstagramGamingRepository(persistence)
        repository.grant(
            "request-rollback",
            purchase.encode(),
            clock(epoch = 500_000L, elapsed = 10_000L, boot = 1L),
        )

        val afterReboot = InstagramGamingRepository(persistence).reconcile(
            clock(epoch = 400_000L, elapsed = 1_000L, boot = 2L),
        )

        assertEquals(InstagramGamingPhase.Active, afterReboot.phase)
        assertEquals(config.sessionDurationMillis, afterReboot.sessionRemainingMillis)
        assertEquals(2L, afterReboot.session?.bootId)
    }

    @Test
    fun rebootRollbackCannotRestoreAlreadyTrustedElapsedSessionTime() {
        val persistence = FakeInstagramGamingPersistence()
        val repository = InstagramGamingRepository(persistence)
        repository.grant(
            "request-trusted-rollback",
            purchase.encode(),
            clock(epoch = 500_000L, elapsed = 10_000L, boot = 1L),
        )
        repository.reconcile(
            clock(epoch = 560_000L, elapsed = 70_000L, boot = 1L),
        )

        val afterReboot = InstagramGamingRepository(persistence).reconcile(
            clock(epoch = 100_000L, elapsed = 1_000L, boot = 2L),
        )

        assertEquals(
            config.sessionDurationMillis - 60_000L,
            afterReboot.sessionRemainingMillis,
        )
    }

    @Test
    fun sameBootRollbackCanEnterQuarantineWithoutCorruptingPersistedState() {
        val shortAllowanceConfig = InstagramGamingConfig(180, 1, 5)
        val shortAllowancePurchase = purchase.copy(config = shortAllowanceConfig)
        val persistence = FakeInstagramGamingPersistence()
        val repository = InstagramGamingRepository(persistence)
        repository.grant(
            "request-same-boot-rollback",
            shortAllowancePurchase.encode(),
            clock(epoch = 1_000_000L, elapsed = 10_000L, boot = 4L),
        )
        repository.addForegroundUsage(
            shortAllowanceConfig.allowanceDurationMillis,
            clock(epoch = 100L, elapsed = 70_000L, boot = 4L),
        )
        repository.enterQuarantine(
            clock(epoch = 100L, elapsed = 70_000L, boot = 4L),
        )

        val restored = InstagramGamingRepository(persistence).status(
            clock(epoch = 200L, elapsed = 80_000L, boot = 4L),
        )

        assertEquals(InstagramGamingPhase.Quarantine, restored.phase)
        assertEquals(
            shortAllowanceConfig.quarantineDurationMillis - 10_000L,
            restored.quarantineRemainingMillis,
        )
    }

    @Test
    fun implausibleForwardJumpAfterRebootDoesNotShortenQuarantine() {
        val longConfig = InstagramGamingConfig(720, 1, 5)
        val longPurchase = purchase.copy(config = longConfig)
        val persistence = FakeInstagramGamingPersistence()
        val repository = InstagramGamingRepository(persistence)
        repository.grant(
            "request-forward",
            longPurchase.encode(),
            clock(epoch = 1_000_000L, elapsed = 10_000L, boot = 1L),
        )
        repository.addForegroundUsage(
            longConfig.allowanceDurationMillis,
            clock(epoch = 1_060_000L, elapsed = 70_000L, boot = 1L),
        )
        repository.enterQuarantine(
            clock(epoch = 1_060_000L, elapsed = 70_000L, boot = 1L),
        )

        val jump = InstagramGamingClockPolicy.MAX_PLAUSIBLE_FORWARD_JUMP_MILLIS + 1L
        val afterReboot = InstagramGamingRepository(persistence).reconcile(
            clock(epoch = 1_060_000L + jump, elapsed = 1_000L, boot = 2L),
        )

        assertEquals(InstagramGamingPhase.Quarantine, afterReboot.phase)
        assertEquals(
            longConfig.quarantineDurationMillis,
            afterReboot.quarantineRemainingMillis,
        )
        assertTrue(
            afterReboot.sessionRemainingMillis <= longConfig.sessionDurationMillis,
        )
    }

    @Test
    fun legacySchemaRemainsReadableAndUpgradesOnClockReconciliation() {
        val legacyRaw = listOf(
            "1",
            config.sessionMinutes,
            config.allowanceMinutes,
            config.quarantineMinutes,
            "request-legacy",
            purchase.encode(),
            "request-legacy",
            "1000",
            (1_000L + config.sessionDurationMillis).toString(),
            "",
            "30000",
        ).joinToString("\t")
        val persistence = FakeInstagramGamingPersistence(legacyRaw)

        val restored = InstagramGamingRepository(persistence).reconcile(
            clock(epoch = 2_000L, elapsed = 500L, boot = 3L),
        )

        assertEquals(InstagramGamingPhase.Active, restored.phase)
        assertEquals(150_000L, restored.allowanceRemainingMillis)
        assertTrue(persistence.raw.orEmpty().startsWith("3\t"))
    }

    @Test
    fun productionStatusUsesFullClockAndEpochStatusCannotDowngradeAnchors() {
        val persistence = FakeInstagramGamingPersistence()
        val grantClock = clock(epoch = 1_000_000L, elapsed = 10_000L, boot = 12L)
        InstagramGamingRepository(persistence).grant(
            "request-ui-clock",
            purchase.encode(),
            grantClock,
        )
        var suppliedClock = clock(epoch = 1_001_000L, elapsed = 11_000L, boot = 12L)
        val repository = InstagramGamingRepository(persistence) { suppliedClock }

        val fullClockStatus = repository.status()
        val anchoredRaw = persistence.raw
        suppliedClock = clock(epoch = 1_002_000L, elapsed = 12_000L, boot = 12L)
        val legacyStatus = repository.status(suppliedClock.epochMillis)

        assertEquals(InstagramGamingPhase.Active, fullClockStatus.phase)
        assertEquals(12L, fullClockStatus.session?.bootId)
        assertEquals(InstagramGamingPhase.Active, legacyStatus.phase)
        assertEquals(anchoredRaw, persistence.raw)
        assertEquals(12L, InstagramGamingStateCodec.decode(persistence.raw.orEmpty())
            ?.session?.bootId)
    }

    @Test
    fun interruptedSegmentRecoveryChargesBoundedTailOnce() {
        val persistence = FakeInstagramGamingPersistence()
        val repository = InstagramGamingRepository(persistence)
        repository.grant(
            "request-interrupted",
            purchase.encode(),
            clock(epoch = 100_000L, elapsed = 1_000L, boot = 21L),
        )
        repository.startForegroundSegment(
            clock(epoch = 101_000L, elapsed = 2_000L, boot = 21L),
        )
        repository.checkpointForegroundSegment(
            clock(epoch = 116_000L, elapsed = 17_000L, boot = 21L),
        )

        val restarted = InstagramGamingRepository(persistence)
        val recovered = restarted.recoverInterruptedForegroundSegment(
            clock(epoch = 126_000L, elapsed = 27_000L, boot = 21L),
        )
        val repeated = restarted.recoverInterruptedForegroundSegment(
            clock(epoch = 130_000L, elapsed = 31_000L, boot = 21L),
        )

        assertEquals(25_000L, recovered.session?.consumedForegroundMillis)
        assertEquals(25_000L, repeated.session?.consumedForegroundMillis)
        assertNull(repeated.session?.activeSegmentBootId)
    }

    @Test
    fun interruptedSegmentExcludesUnknownDowntimeBeyondOneCheckpointCadence() {
        val persistence = FakeInstagramGamingPersistence()
        val repository = InstagramGamingRepository(persistence)
        repository.grant(
            "request-bounded",
            purchase.encode(),
            clock(epoch = 100_000L, elapsed = 1_000L, boot = 22L),
        )
        repository.startForegroundSegment(
            clock(epoch = 101_000L, elapsed = 2_000L, boot = 22L),
        )

        val recovered = InstagramGamingRepository(persistence)
            .recoverInterruptedForegroundSegment(
                clock(epoch = 701_000L, elapsed = 602_000L, boot = 22L),
            )

        assertEquals(
            InstagramGamingRepository.MAX_INTERRUPTED_SEGMENT_RECOVERY_MILLIS,
            recovered.session?.consumedForegroundMillis,
        )
    }

    @Test
    fun lifecycleSettlementAndScreenOffDoNotCountOfflineTime() {
        val persistence = FakeInstagramGamingPersistence()
        val repository = InstagramGamingRepository(persistence)
        repository.grant(
            "request-screen-off",
            purchase.encode(),
            clock(epoch = 100_000L, elapsed = 1_000L, boot = 23L),
        )
        repository.startForegroundSegment(
            clock(epoch = 101_000L, elapsed = 2_000L, boot = 23L),
        )
        val screenOff = repository.checkpointForegroundSegment(
            clock(epoch = 106_000L, elapsed = 7_000L, boot = 23L),
            endSegment = true,
        )
        val afterOffline = InstagramGamingRepository(persistence)
            .recoverInterruptedForegroundSegment(
                clock(epoch = 706_000L, elapsed = 607_000L, boot = 23L),
            )

        assertEquals(5_000L, screenOff.session?.consumedForegroundMillis)
        assertEquals(5_000L, afterOffline.session?.consumedForegroundMillis)
        assertNull(afterOffline.session?.activeSegmentBootId)
    }

    @Test
    fun restartWhileScreenOffClearsInterruptedSegmentWithoutChargingTail() {
        val persistence = FakeInstagramGamingPersistence()
        val repository = InstagramGamingRepository(persistence)
        repository.grant(
            "request-restart-screen-off",
            purchase.encode(),
            clock(epoch = 100_000L, elapsed = 1_000L, boot = 24L),
        )
        repository.startForegroundSegment(
            clock(epoch = 101_000L, elapsed = 2_000L, boot = 24L),
        )

        val recovered = InstagramGamingRepository(persistence)
            .recoverInterruptedForegroundSegment(
                clock = clock(epoch = 111_000L, elapsed = 12_000L, boot = 24L),
                recoverBoundedTail = false,
            )

        assertEquals(0L, recovered.session?.consumedForegroundMillis)
        assertNull(recovered.session?.activeSegmentBootId)
    }

    @Test
    fun rebootClockPolicyHandlesPlausibleAndImplausibleForwardAdjustments() {
        val plausiblePersistence = FakeInstagramGamingPersistence()
        InstagramGamingRepository(plausiblePersistence).grant(
            "request-plausible",
            purchase.encode(),
            clock(epoch = 1_000_000L, elapsed = 10_000L, boot = 30L),
        )
        val plausible = InstagramGamingRepository(plausiblePersistence).reconcile(
            clock(epoch = 1_060_000L, elapsed = 1_000L, boot = 31L),
        )
        assertEquals(
            config.sessionDurationMillis - 60_000L,
            plausible.sessionRemainingMillis,
        )

        val jumpPersistence = FakeInstagramGamingPersistence()
        InstagramGamingRepository(jumpPersistence).grant(
            "request-jump",
            purchase.encode(),
            clock(epoch = 1_000_000L, elapsed = 10_000L, boot = 40L),
        )
        val jumped = InstagramGamingRepository(jumpPersistence).reconcile(
            clock(
                epoch = 1_000_000L +
                    InstagramGamingClockPolicy.MAX_PLAUSIBLE_FORWARD_JUMP_MILLIS + 1L,
                elapsed = 1_000L,
                boot = 41L,
            ),
        )
        val rebootedAgain = InstagramGamingRepository(jumpPersistence).reconcile(
            clock(
                epoch = 1_000_000L +
                    InstagramGamingClockPolicy.MAX_PLAUSIBLE_FORWARD_JUMP_MILLIS + 1_001L,
                elapsed = 1_000L,
                boot = 42L,
            ),
        )
        val eventuallyExpired = InstagramGamingRepository(jumpPersistence).status(
            clock(
                epoch = 1_000_000L,
                elapsed = 1_000L + config.sessionDurationMillis,
                boot = 42L,
            ),
        )

        assertEquals(config.sessionDurationMillis, jumped.sessionRemainingMillis)
        assertEquals(
            config.sessionDurationMillis - 1_000L,
            rebootedAgain.sessionRemainingMillis,
        )
        assertEquals(InstagramGamingPhase.Inactive, eventuallyExpired.phase)
    }

    private fun clock(
        epoch: Long,
        elapsed: Long,
        boot: Long,
    ): InstagramGamingClockReading =
        InstagramGamingClockReading(epoch, elapsed, boot)
}
