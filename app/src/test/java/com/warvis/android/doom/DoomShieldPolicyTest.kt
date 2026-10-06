package com.warvis.android.doom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoomShieldPolicyTest {
    @Test
    fun nativeAppTarget_blocksXAndQuora() {
        assertEquals(
            "X",
            DoomShieldPolicy.nativeAppTarget("com.twitter.android")?.displayName,
        )
        assertEquals(
            "Quora",
            DoomShieldPolicy.nativeAppTarget("com.quora.android")?.displayName,
        )
    }

    @Test
    fun domainTarget_canonicalizesXAndTwitterDomains() {
        val browserPackage = "com.android.chrome"
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
                "domain:x.com",
                DoomShieldPolicy.domainTarget(domain, browserPackage)?.key,
            )
        }
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
