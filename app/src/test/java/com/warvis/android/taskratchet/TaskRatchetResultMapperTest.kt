package com.warvis.android.taskratchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRatchetResultMapperTest {
    @Test
    fun validAccountResponseMapsPaymentMethodAndLimit() {
        val account = TaskRatchetAccount(
            userId = "user-test",
            hasPaymentMethod = true,
            monthlyChargeLimitCents = 4_000,
        )

        assertEquals(
            TaskRatchetAccountResult.Success(account),
            TaskRatchetResultMapper.account(200, account),
        )
    }

    @Test
    fun successfulChargeRequiresSucceededStatusAndId() {
        val success = TaskRatchetResultMapper.charge(
            statusCode = 200,
            chargeId = "charge-test-id",
            remoteStatus = "succeeded",
            remoteCents = 400,
            dryRun = true,
        )
        val malformed = TaskRatchetResultMapper.charge(
            statusCode = 200,
            chargeId = null,
            remoteStatus = "succeeded",
            remoteCents = 400,
            dryRun = true,
        )
        val wrongAmount = TaskRatchetResultMapper.charge(
            statusCode = 200,
            chargeId = "charge-test-wrong-amount",
            remoteStatus = "succeeded",
            remoteCents = 401,
            dryRun = true,
        )

        assertEquals(
            TaskRatchetChargeResult.Success("charge-test-id", dryRun = true),
            success,
        )
        assertEquals(
            TaskRatchetChargeResult.Failure(TaskRatchetFailureKind.MalformedResponse),
            malformed,
        )
        assertEquals(
            TaskRatchetChargeResult.Failure(TaskRatchetFailureKind.MalformedResponse),
            wrongAmount,
        )
    }

    @Test
    fun documentedHttpFailuresMapToTypedResults() {
        val expected = mapOf(
            400 to TaskRatchetFailureKind.NoPaymentMethodOrBadRequest,
            401 to TaskRatchetFailureKind.Authentication,
            403 to TaskRatchetFailureKind.Authentication,
            402 to TaskRatchetFailureKind.PaymentFailed,
            409 to TaskRatchetFailureKind.ChargeInProgress,
            422 to TaskRatchetFailureKind.InvalidOrMonthlyLimit,
            429 to TaskRatchetFailureKind.RateLimited,
        )

        expected.forEach { (status, kind) ->
            val result = TaskRatchetResultMapper.charge(
                statusCode = status,
                chargeId = null,
                remoteStatus = null,
                remoteCents = null,
                dryRun = false,
            )
            assertTrue(result is TaskRatchetChargeResult.Failure)
            assertEquals(kind, (result as TaskRatchetChargeResult.Failure).kind)
        }
    }
}
