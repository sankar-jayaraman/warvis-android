package com.warvis.android.taskratchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRatchetPolicyTest {
    @Test
    fun fixedAmountAndLabels_areExactAndDoNotClaimExactDkkValue() {
        assertEquals(400, TASK_RATCHET_CHARGE_CENTS)
        assertEquals(
            "US$4.00 (roughly 25-28 DKK; bank conversion and FX fees may vary)",
            TASK_RATCHET_AMOUNT_LABEL,
        )
        assertEquals(
            "TaskRatchet dry run",
            TaskRatchetConfigurationPolicy.actionLabel(TaskRatchetMode.DryRun),
        )
        assertEquals(
            "TaskRatchet live charge",
            TaskRatchetConfigurationPolicy.actionLabel(TaskRatchetMode.Live),
        )
    }

    @Test
    fun liveModeRequiresValidatedConnectionPaymentMethodAndConfirmation() {
        assertFalse(
            TaskRatchetConfigurationPolicy.canEnableLive(
                connected = false,
                hasPaymentMethod = true,
                confirmationAccepted = true,
            ),
        )
        assertFalse(
            TaskRatchetConfigurationPolicy.canEnableLive(
                connected = true,
                hasPaymentMethod = false,
                confirmationAccepted = true,
            ),
        )
        assertFalse(
            TaskRatchetConfigurationPolicy.canEnableLive(
                connected = true,
                hasPaymentMethod = true,
                confirmationAccepted = false,
            ),
        )
        assertTrue(
            TaskRatchetConfigurationPolicy.canEnableLive(
                connected = true,
                hasPaymentMethod = true,
                confirmationAccepted = true,
            ),
        )
    }

    @Test
    fun configurationDefaultsToDryRunAndFailsClosedWithoutPaymentMethod() {
        assertEquals(TaskRatchetMode.DryRun, TaskRatchetConfiguration().mode)
        assertFalse(TaskRatchetConfiguration().canCharge)
        assertFalse(
            TaskRatchetConfiguration(
                connected = true,
                account = TaskRatchetAccount(
                    userId = "user-test",
                    hasPaymentMethod = false,
                    monthlyChargeLimitCents = 2_000,
                ),
                credentialGenerationId = "generation-test",
            ).canCharge,
        )
    }

    @Test
    fun tokenPolicyRejectsBlankAndUnboundedValuesWithoutUsingRealCredentials() {
        assertFalse(TaskRatchetConfigurationPolicy.isTokenCandidateAllowed(" "))
        assertTrue(TaskRatchetConfigurationPolicy.isTokenCandidateAllowed("test-key-placeholder"))
        assertFalse(
            TaskRatchetConfigurationPolicy.isTokenCandidateAllowed("x".repeat(4_097)),
        )
    }
}
