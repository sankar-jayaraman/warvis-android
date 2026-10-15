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
}
