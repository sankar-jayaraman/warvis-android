package com.warvis.android.taskratchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRatchetAttemptStateTest {
    private val xAttempt = TaskRatchetAttempt(
        requestId = "request-test-x",
        purpose = TaskRatchetPurpose.BlockedTargetOverride,
        targetKey = "domain:x.com",
        dryRun = true,
        accountUserId = "user-test",
        credentialGenerationId = "generation-test",
        note = "WARVIS blocked-app override: x.com",
        status = TaskRatchetAttemptStatus.Pending,
    )

    @Test
    fun unresolvedAttemptReusesStableRequestIdAcrossRetry() {
        val state = TaskRatchetAttemptState().withAttempt(xAttempt)

        assertEquals(
            "request-test-x",
            state.unresolved(
                TaskRatchetPurpose.BlockedTargetOverride,
                "domain:x.com",
            )?.requestId,
        )
    }

    @Test
    fun purposesAndTargetsRemainSeparated() {
        val disableAttempt = xAttempt.copy(
            requestId = "request-test-disable",
            purpose = TaskRatchetPurpose.DoomShieldDisable,
            targetKey = null,
        )
        val state = TaskRatchetAttemptState()
            .withAttempt(xAttempt)
            .withAttempt(disableAttempt)

        assertEquals(
            "request-test-x",
            state.unresolved(
                TaskRatchetPurpose.BlockedTargetOverride,
                "domain:x.com",
            )?.requestId,
        )
        assertEquals(
            "request-test-disable",
            state.unresolved(TaskRatchetPurpose.DoomShieldDisable, null)?.requestId,
        )
        assertNull(
            state.unresolved(
                TaskRatchetPurpose.BlockedTargetOverride,
                "domain:quora.com",
            ),
        )
    }

    @Test
    fun atomicGetOrCreateReusesOneRequestIdAndPreservesUnrelatedAttempts() {
        val unrelated = xAttempt.copy(
            requestId = "request-test-unrelated",
            targetKey = "domain:unrelated.example",
        )
        val initial = TaskRatchetAttemptState().withAttempt(unrelated)
        val (firstState, first) = initial.getOrCreate(
            purpose = xAttempt.purpose,
            targetKey = xAttempt.targetKey,
            dryRun = xAttempt.dryRun,
            accountUserId = xAttempt.accountUserId,
            credentialGenerationId = xAttempt.credentialGenerationId,
            note = xAttempt.note,
            benefitBinding = null,
            newRequestId = { "request-test-first" },
        )
        val (secondState, second) = firstState.getOrCreate(
            purpose = xAttempt.purpose,
            targetKey = xAttempt.targetKey,
            dryRun = xAttempt.dryRun,
            accountUserId = xAttempt.accountUserId,
            credentialGenerationId = xAttempt.credentialGenerationId,
            note = xAttempt.note,
            benefitBinding = null,
            newRequestId = { "request-test-second" },
        )

        assertEquals("request-test-first", first.requestId)
        assertEquals(first.requestId, second.requestId)
        assertEquals(2, secondState.attempts.size)
        assertNotNull(secondState.unresolved(unrelated.purpose, unrelated.targetKey))
    }

    @Test
    fun ambiguousLiveAttemptBlocksCredentialReplacementAndDisconnect() {
        val pendingLive = xAttempt.copy(dryRun = false)
        val state = TaskRatchetAttemptState().withAttempt(pendingLive)

        val replacementResult = TaskRatchetCredentialMutationPolicy.evaluate(state)
        val disconnectResult = TaskRatchetCredentialMutationPolicy.evaluate(state)

        assertEquals(
            TaskRatchetConfigurationMutationResult.PendingLiveAttempt,
            replacementResult,
        )
        assertEquals(
            TaskRatchetConfigurationMutationResult.PendingLiveAttempt,
            disconnectResult,
        )
    }

    @Test
    fun mismatchedCredentialGenerationNeverInvokesCharge() {
        val pendingLive = xAttempt.copy(dryRun = false)
        val replacementConfiguration = TaskRatchetConfiguration(
            connected = true,
            account = TaskRatchetAccount(
                userId = pendingLive.accountUserId,
                hasPaymentMethod = true,
                monthlyChargeLimitCents = 4_000,
            ),
            credentialGenerationId = "replacement-generation",
            mode = TaskRatchetMode.Live,
        )
        var chargeCalls = 0

        if (TaskRatchetSubmissionPolicy.canSubmit(pendingLive, replacementConfiguration)) {
            chargeCalls += 1
        }

        assertEquals(0, chargeCalls)
    }

    @Test
    fun confirmedAttemptRemainsRecoverableAfterDisconnect() {
        val confirmed = TaskRatchetAttemptState()
            .withAttempt(xAttempt.copy(dryRun = false))
            .confirm(
                purpose = xAttempt.purpose,
                targetKey = xAttempt.targetKey,
                requestId = xAttempt.requestId,
                chargeId = "charge-test-confirmed",
            )
        val disconnected = TaskRatchetConfiguration()

        assertFalse(disconnected.canCharge)
        assertEquals(
            TaskRatchetAttemptStatus.RemoteConfirmed,
            confirmed.unresolved(xAttempt.purpose, xAttempt.targetKey)?.status,
        )
    }

    @Test
    fun pendingDryRunCanBeDiscardedWithoutClearingConfirmedAttempts() {
        val confirmedLive = xAttempt.copy(
            requestId = "request-test-confirmed-live",
            purpose = TaskRatchetPurpose.DoomShieldDisable,
            targetKey = null,
            dryRun = false,
            status = TaskRatchetAttemptStatus.RemoteConfirmed,
            remoteChargeId = "charge-test-confirmed-live",
        )
        val state = TaskRatchetAttemptState()
            .withAttempt(xAttempt)
            .withAttempt(confirmedLive)
            .withoutPendingDryRunAttempts()

        assertNull(state.unresolved(xAttempt.purpose, xAttempt.targetKey))
        assertEquals(
            confirmedLive,
            state.unresolved(confirmedLive.purpose, confirmedLive.targetKey),
        )
    }

    @Test
    fun remoteConfirmationIsRetainedUntilBenefitCompletes() {
        val confirmed = TaskRatchetAttemptState()
            .withAttempt(xAttempt)
            .confirm(
                purpose = xAttempt.purpose,
                targetKey = xAttempt.targetKey,
                requestId = xAttempt.requestId,
                chargeId = "charge-test-confirmed",
            )

        val recoverable = confirmed.unresolved(xAttempt.purpose, xAttempt.targetKey)
        assertNotNull(recoverable)
        assertEquals(TaskRatchetAttemptStatus.RemoteConfirmed, recoverable?.status)

        val completed = confirmed.complete(
            purpose = xAttempt.purpose,
            targetKey = xAttempt.targetKey,
            requestId = xAttempt.requestId,
        )
        assertNull(completed.unresolved(xAttempt.purpose, xAttempt.targetKey))
    }

    @Test
    fun remoteConfirmedAttemptLeaseRejectsConcurrentRecovery() {
        val confirmed = xAttempt.copy(
            status = TaskRatchetAttemptStatus.RemoteConfirmed,
            remoteChargeId = "charge-test-confirmed",
        )
        val firstLease = synchronized(TaskRatchetProcessLock.monitor) {
            TaskRatchetActiveSubmissionRegistry.tryAcquire(confirmed.requestId)
        }

        try {
            assertNotNull(firstLease)
            val concurrentLease = synchronized(TaskRatchetProcessLock.monitor) {
                TaskRatchetActiveSubmissionRegistry.tryAcquire(confirmed.requestId)
            }
            assertNull(concurrentLease)
        } finally {
            firstLease?.release()
        }
    }

    @Test
    fun leaseIsReleasedAfterBenefitFailureSoConfirmedRecoveryCanRetry() {
        val confirmed = xAttempt.copy(
            requestId = "request-test-benefit-failure",
            status = TaskRatchetAttemptStatus.RemoteConfirmed,
            remoteChargeId = "charge-test-benefit-failure",
        )
        val firstLease = synchronized(TaskRatchetProcessLock.monitor) {
            TaskRatchetActiveSubmissionRegistry.tryAcquire(confirmed.requestId)
        }

        var benefitCalls = 0
        val benefitGranted: Boolean
        try {
            assertNotNull(firstLease)
            benefitGranted = false.also { benefitCalls += 1 }
        } finally {
            firstLease?.release()
        }
        assertFalse(benefitGranted)
        assertEquals(1, benefitCalls)

        val retryLease = synchronized(TaskRatchetProcessLock.monitor) {
            TaskRatchetActiveSubmissionRegistry.tryAcquire(confirmed.requestId)
        }
        try {
            assertNotNull(retryLease)
        } finally {
            retryLease?.release()
        }
    }

    @Test
    fun notesArePurposeScopedBoundedAndContainNoBrowsingPath() {
        val blockedNote = taskRatchetNote(
            TaskRatchetPurpose.BlockedTargetOverride,
            "domain:youtube.com",
        )
        val disableNote = taskRatchetNote(TaskRatchetPurpose.DoomShieldDisable, null)

        assertEquals("WARVIS blocked-app override: youtube.com", blockedNote)
        assertEquals("WARVIS Doom Shield disable", disableNote)
        assertTrue(blockedNote.length <= 160)
        assertFalse(blockedNote.contains("/watch"))
    }

    @Test
    fun instagramGamingAttemptKeepsFirstImmutableBenefitBinding() {
        val state = TaskRatchetAttemptState()
        val (firstState, first) = state.getOrCreate(
            purpose = TaskRatchetPurpose.InstagramGamingSession,
            targetKey = "instagram-gaming:app:com.instagram.android+domain:instagram.com",
            dryRun = false,
            accountUserId = "user-test",
            credentialGenerationId = "generation-test",
            note = "WARVIS Instagram gaming session",
            benefitBinding = "1|session-first|180|3|5",
            newRequestId = { "request-instagram" },
        )
        val (_, retry) = firstState.getOrCreate(
            purpose = TaskRatchetPurpose.InstagramGamingSession,
            targetKey = first.targetKey,
            dryRun = false,
            accountUserId = "user-test",
            credentialGenerationId = "generation-test",
            note = "WARVIS Instagram gaming session",
            benefitBinding = "1|session-changed|720|30|60",
            newRequestId = { "request-must-not-be-used" },
        )

        assertEquals("request-instagram", retry.requestId)
        assertEquals("1|session-first|180|3|5", retry.benefitBinding)
        assertEquals(
            "WARVIS Instagram gaming session",
            taskRatchetNote(
                TaskRatchetPurpose.InstagramGamingSession,
                first.targetKey,
            ),
        )
    }
}
