package com.warvis.android.doom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoomShieldDisableAuthorizationTest {
    @Test
    fun disabledControl_opensAccessibilitySettingsDirectly() {
        assertEquals(
            DoomShieldControlAction.OpenAccessibilitySettings,
            DoomShieldControlPolicy.decide(enabled = false).action,
        )
    }

    @Test
    fun enabledControl_requiresDisableCommitment() {
        assertEquals(
            DoomShieldControlAction.RequireDisableCommitment,
            DoomShieldControlPolicy.decide(enabled = true).action,
        )
    }

    @Test
    fun validAuthorization_consumesOnce() {
        val issue = DisableAuthorizationState().issue(
            token = "disable-token",
            purpose = DoomShieldDisableAuthorization.PURPOSE,
            nowMillis = 1_000L,
        )

        val first = issue.state.consume(
            token = issue.authorization.token,
            expectedPurpose = DoomShieldDisableAuthorization.PURPOSE,
            nowMillis = 2_000L,
        )
        val duplicate = first.state.consume(
            token = issue.authorization.token,
            expectedPurpose = DoomShieldDisableAuthorization.PURPOSE,
            nowMillis = 2_001L,
        )

        assertTrue(first is DisableAuthorizationConsumeResult.Consumed)
        assertTrue(duplicate is DisableAuthorizationConsumeResult.AlreadyConsumed)
    }

    @Test
    fun expiredAuthorization_isRejectedAndConsumed() {
        val issue = DisableAuthorizationState().issue(
            token = "expired-token",
            purpose = DoomShieldDisableAuthorization.PURPOSE,
            nowMillis = 1_000L,
            lifetimeMillis = 100L,
        )

        val expired = issue.state.consume(
            token = issue.authorization.token,
            expectedPurpose = DoomShieldDisableAuthorization.PURPOSE,
            nowMillis = 1_100L,
        )
        val duplicate = expired.state.consume(
            token = issue.authorization.token,
            expectedPurpose = DoomShieldDisableAuthorization.PURPOSE,
            nowMillis = 1_101L,
        )

        assertTrue(expired is DisableAuthorizationConsumeResult.Expired)
        assertTrue(duplicate is DisableAuthorizationConsumeResult.AlreadyConsumed)
    }

    @Test
    fun targetEntitlementPurpose_cannotConsumeDisableAuthorization() {
        val issue = DisableAuthorizationState().issue(
            token = "purpose-token",
            purpose = DoomShieldDisableAuthorization.PURPOSE,
            nowMillis = 1_000L,
        )

        val wrongPurpose = issue.state.consume(
            token = issue.authorization.token,
            expectedPurpose = "doom-shield:target-entitlement",
            nowMillis = 2_000L,
        )
        val validPurpose = wrongPurpose.state.consume(
            token = issue.authorization.token,
            expectedPurpose = DoomShieldDisableAuthorization.PURPOSE,
            nowMillis = 2_001L,
        )

        assertTrue(wrongPurpose is DisableAuthorizationConsumeResult.WrongPurpose)
        assertTrue(validPurpose is DisableAuthorizationConsumeResult.Consumed)
    }
}
