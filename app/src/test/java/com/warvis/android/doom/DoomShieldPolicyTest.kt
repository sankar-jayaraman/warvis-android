package com.warvis.android.doom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoomShieldPolicyTest {
    @Test
    fun nativeAppTarget_blocksXAndSharesItsEntitlementWithWebAliases() {
        assertEquals(
            "X",
            DoomShieldPolicy.nativeAppTarget("com.twitter.android")?.displayName,
        )
        assertEquals(
            "domain:x.com",
            DoomShieldPolicy.nativeAppTarget("com.twitter.android")?.key,
        )
    }

    @Test
    fun nativeAppTarget_blocksQuora() {
        assertEquals(
            "Quora",
            DoomShieldPolicy.nativeAppTarget("com.quora.android")?.displayName,
        )
    }

    @Test
    fun domainTarget_canonicalizesXAndTwitterDomainsToNativeXEntitlement() {
        val browserPackage = "com.android.chrome"
        val nativeXKey = DoomShieldPolicy.nativeAppTarget("com.twitter.android")?.key
        val aliases = listOf(
            "x.com",
            "www.x.com",
            "mobile.x.com",
            "twitter.com",
            "www.twitter.com",
            "mobile.twitter.com",
        )

        aliases.forEach { domain ->
            assertEquals(
                nativeXKey,
                DoomShieldPolicy.domainTarget(domain, browserPackage)?.key,
            )
        }
    }

    @Test
    fun paidOverrideUsesExactRequestedTerms() {
        assertEquals(25, DoomShieldPolicy.PAID_OVERRIDE_PRICE_KR)
        assertEquals(15, DoomShieldPolicy.PAID_OVERRIDE_DURATION_MINUTES)
        assertEquals(10_000L, DoomShieldPolicy.PAID_OVERRIDE_COOLING_OFF_MILLIS)
        assertEquals(15 * 60 * 1000L, DoomShieldPolicy.GRACE_PERIOD_MILLIS)
    }

    @Test
    fun nightWindow_usesExactCrossMidnightBoundaries() {
        assertEquals(false, DoomShieldPolicy.isNightWindow(hour = 20, minute = 59))
        assertEquals(true, DoomShieldPolicy.isNightWindow(hour = 21, minute = 0))
        assertEquals(true, DoomShieldPolicy.isNightWindow(hour = 9, minute = 59))
        assertEquals(false, DoomShieldPolicy.isNightWindow(hour = 10, minute = 0))
    }

    @Test
    fun entitlementConfirmation_grantsInitialFifteenMinutes() {
        val nowMillis = 1_000L

        val state = DoomShieldEntitlementState().confirm("domain:x.com", nowMillis)

        assertEquals(
            nowMillis + 15 * 60 * 1000L,
            state.deadlineFor("domain:x.com"),
        )
    }

    @Test
    fun entitlementConfirmation_doesNotExtendActiveDeadline() {
        val targetKey = "domain:x.com"
        val initialState = DoomShieldEntitlementState().confirm(targetKey, 1_000L)
        val originalDeadline = initialState.deadlineFor(targetKey)

        val duplicateState = initialState.confirm(targetKey, 2_000L)

        assertEquals(originalDeadline, duplicateState.deadlineFor(targetKey))
    }

    @Test
    fun entitlementConfirmation_afterExpiryGrantsNewDeadline() {
        val targetKey = "domain:x.com"
        val initialState = DoomShieldEntitlementState().confirm(targetKey, 1_000L)
        val expiredAtMillis = initialState.deadlineFor(targetKey)

        val renewedState = initialState.confirm(targetKey, expiredAtMillis)

        assertEquals(
            expiredAtMillis + 15 * 60 * 1000L,
            renewedState.deadlineFor(targetKey),
        )
    }

    @Test
    fun entitlementState_keepsUnrelatedTargetsIsolated() {
        val xKey = DoomShieldPolicy.nativeAppTarget("com.twitter.android")!!.key
        val quoraKey = DoomShieldPolicy.nativeAppTarget("com.quora.android")!!.key
        val state = DoomShieldEntitlementState().confirm(xKey, 1_000L)

        assertEquals(true, state.isActive(xKey, 2_000L))
        assertEquals(false, state.isActive(quoraKey, 2_000L))
        assertEquals(0L, state.deadlineFor(quoraKey))
    }

    @Test
    fun gracePeriodStart_persistsInactiveEntitlement() {
        val targetKey = "domain:x.com"
        val persistence = TestGracePeriodPersistence(persistSucceeds = true)

        val result = DoomShieldGracePeriod.startIfInactive(
            persistence = persistence,
            targetKey = targetKey,
            nowMillis = 1_000L,
        )

        assertEquals(DoomShieldGracePeriodStartResult.Persisted, result)
        assertEquals(
            1_000L + DoomShieldPolicy.GRACE_PERIOD_MILLIS,
            persistence.readDeadline(targetKey),
        )
    }

    @Test
    fun gracePeriodStart_doesNotPersistOrExtendAlreadyActiveEntitlement() {
        val targetKey = "domain:x.com"
        val originalDeadline = 10_000L
        val persistence = TestGracePeriodPersistence(
            initialDeadlineMillis = originalDeadline,
            persistSucceeds = true,
        )

        val result = DoomShieldGracePeriod.startIfInactive(
            persistence = persistence,
            targetKey = targetKey,
            nowMillis = 1_000L,
        )

        assertEquals(DoomShieldGracePeriodStartResult.AlreadyActive, result)
        assertEquals(originalDeadline, persistence.readDeadline(targetKey))
        assertEquals(0, persistence.persistAttempts)
    }

    @Test
    fun gracePeriodPersistenceFailure_rollsBackInMemoryDeadlineAndRemainsInactive() {
        val targetKey = "domain:x.com"
        val expiredDeadline = 500L
        val persistence = TestGracePeriodPersistence(
            initialDeadlineMillis = expiredDeadline,
            persistSucceeds = false,
        )

        val result = DoomShieldGracePeriod.startIfInactive(
            persistence = persistence,
            targetKey = targetKey,
            nowMillis = 1_000L,
        )

        assertEquals(DoomShieldGracePeriodStartResult.PersistenceFailed, result)
        assertEquals(expiredDeadline, persistence.readDeadline(targetKey))
        assertEquals(
            false,
            DoomShieldGracePeriod.isActive(
                persistence = persistence,
                targetKey = targetKey,
                nowMillis = 1_000L,
            ),
        )
    }

    @Test
    fun domainTarget_canonicalizesQuoraDomains() {
        val browserPackage = "com.android.chrome"
        val aliases = listOf("quora.com", "www.quora.com", "m.quora.com")

        aliases.forEach { domain ->
            assertEquals(
                "domain:quora.com",
                DoomShieldPolicy.domainTarget(domain, browserPackage)?.key,
            )
        }
    }

    @Test
    fun findBlockedDomainInText_detectsXAndQuoraUrls() {
        assertEquals(
            "x.com",
            DoomShieldPolicy.findBlockedDomainInText("https://x.com/home"),
        )
        assertEquals(
            "twitter.com",
            DoomShieldPolicy.findBlockedDomainInText("https://twitter.com/explore"),
        )
        assertEquals(
            "quora.com",
            DoomShieldPolicy.findBlockedDomainInText("https://quora.com/For-You"),
        )
    }

    @Test
    fun findBlockedDomainInText_ignoresUnrelatedDomains() {
        assertNull(DoomShieldPolicy.findBlockedDomainInText("https://example.com/x.comparison"))
        assertNull(DoomShieldPolicy.findBlockedDomainInText("https://notx.com"))
        assertNull(DoomShieldPolicy.findBlockedDomainInText("https://notquora.com"))
    }

    private class TestGracePeriodPersistence(
        initialDeadlineMillis: Long = 0L,
        private val persistSucceeds: Boolean,
    ) : DoomShieldGracePeriodPersistence {
        private val deadlinesByTarget = mutableMapOf<String, Long>()
        var persistAttempts = 0
            private set

        init {
            if (initialDeadlineMillis > 0L) {
                deadlinesByTarget["domain:x.com"] = initialDeadlineMillis
            }
        }

        override fun readDeadline(targetKey: String): Long =
            deadlinesByTarget[targetKey] ?: 0L

        override fun persistDeadline(targetKey: String, deadlineMillis: Long): Boolean {
            persistAttempts += 1
            deadlinesByTarget[targetKey] = deadlineMillis
            return persistSucceeds
        }

        override fun restoreDeadline(targetKey: String, deadlineMillis: Long) {
            if (deadlineMillis > 0L) {
                deadlinesByTarget[targetKey] = deadlineMillis
            } else {
                deadlinesByTarget.remove(targetKey)
            }
        }

        override fun removeDeadline(targetKey: String) {
            deadlinesByTarget.remove(targetKey)
        }
    }
}
