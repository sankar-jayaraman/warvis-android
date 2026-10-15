package com.warvis.android.doom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoomShieldNightPolicyTest {
    private val arbitraryPackage = "com.example.reader"

    @Test
    fun arbitraryApp_isInterceptedAtNightBoundaries() {
        assertEquals(
            "app:$arbitraryPackage",
            classify(arbitraryPackage, hour = 21, minute = 0)?.key,
        )
        assertEquals(
            "app:$arbitraryPackage",
            classify(arbitraryPackage, hour = 9, minute = 59)?.key,
        )
    }

    @Test
    fun arbitraryApp_isAllowedOutsideNightBoundaries() {
        assertNull(classify(arbitraryPackage, hour = 20, minute = 59))
        assertNull(classify(arbitraryPackage, hour = 10, minute = 0))
    }

    @Test
    fun existingNativeTargets_areInterceptedInsideAndOutsideNightWindow() {
        val packages = listOf(
            "com.instagram.android",
            "com.twitter.android",
            "com.quora.android",
            "com.google.android.youtube",
        )

        packages.forEach { packageName ->
            val existingTarget = DoomShieldPolicy.nativeAppTarget(packageName)
            assertEquals(
                existingTarget,
                classify(packageName, hour = 21, minute = 0, existingTarget = existingTarget),
            )
            assertEquals(
                existingTarget,
                classify(packageName, hour = 12, minute = 0, existingTarget = existingTarget),
            )
        }
    }

    @Test
    fun explicitAllowedAliases_areExemptInsideNightWindow() {
        val expectedAliases = setOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.samsung.android.email.provider",
            "com.android.email",
            "com.google.android.email",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.google.android.apps.youtube.music",
        )

        expectedAliases.forEach { packageName ->
            assertEquals(true, packageName in DoomShieldPolicy.explicitNightExemptions)
            assertNull(
                classify(
                    packageName = packageName,
                    hour = 21,
                    minute = 0,
                    exemptions = DoomShieldPolicy.explicitNightExemptions,
                ),
            )
        }
    }

    @Test
    fun mandatoryAndUserExemptions_areAllowedInsideNightWindow() {
        val exemptions = setOf("com.android.settings", "com.example.bank")

        assertNull(classify("com.android.settings", 21, 0, exemptions = exemptions))
        assertNull(classify("com.example.bank", 21, 0, exemptions = exemptions))
    }

    @Test
    fun permissionControllersAndSeparateInCallUis_cannotBecomeNightTargets() {
        val safetyPackages = setOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.samsung.android.permissioncontroller",
            "com.android.incallui",
            "com.google.android.incallui",
            "com.samsung.android.incallui",
            "com.oneplus.incallui",
            "com.miui.incallui",
        )

        safetyPackages.forEach { packageName ->
            assertEquals(
                true,
                packageName in DoomShieldPolicy.mandatorySafetyNightExemptions,
            )
            assertNull(
                classify(
                    packageName = packageName,
                    hour = 21,
                    minute = 0,
                    exemptions = DoomShieldPolicy.mandatorySafetyNightExemptions,
                ),
            )
        }
    }

    @Test
    fun unknownArbitraryApps_getDistinctStableTargetKeys() {
        assertEquals(
            "app:com.example.one",
            classify("com.example.one", 21, 0)?.key,
        )
        assertEquals(
            "app:com.example.two",
            classify("com.example.two", 21, 0)?.key,
        )
    }

    @Test
    fun arbitraryAppEntitlements_remainTargetSpecific() {
        val firstKey = classify("com.example.one", 21, 0)!!.key
        val secondKey = classify("com.example.two", 21, 0)!!.key
        val state = DoomShieldEntitlementState().confirm(firstKey, nowMillis = 1_000L)

        assertEquals(true, state.isActive(firstKey, nowMillis = 2_000L))
        assertEquals(false, state.isActive(secondKey, nowMillis = 2_000L))
    }

    @Test
    fun existingTarget_winsOverAccidentalExemption() {
        val packageName = "com.google.android.youtube"
        val existingTarget = DoomShieldPolicy.nativeAppTarget(packageName)

        assertEquals(
            existingTarget,
            classify(
                packageName = packageName,
                hour = 21,
                minute = 0,
                existingTarget = existingTarget,
                exemptions = setOf(packageName),
            ),
        )
    }

    @Test
    fun existingTargetCanonicalKeys_remainUnchanged() {
        assertEquals(
            "app:com.instagram.android",
            DoomShieldPolicy.nativeAppTarget("com.instagram.android")?.key,
        )
        assertEquals(
            "domain:x.com",
            DoomShieldPolicy.nativeAppTarget("com.twitter.android")?.key,
        )
        assertEquals(
            "app:com.quora.android",
            DoomShieldPolicy.nativeAppTarget("com.quora.android")?.key,
        )
        assertEquals(
            "app:com.google.android.youtube",
            DoomShieldPolicy.nativeAppTarget("com.google.android.youtube")?.key,
        )
    }

    @Test
    fun blankAndTransientForegroundValues_areRejected() {
        assertNull(classify("", 21, 0))
        assertNull(classify("   ", 21, 0))
        assertNull(classify("android", 21, 0))
        assertNull(classify("not a package", 21, 0))
    }

    private fun classify(
        packageName: String,
        hour: Int,
        minute: Int,
        existingTarget: DoomShieldTarget? = null,
        exemptions: Set<String> = emptySet(),
    ): DoomShieldTarget? =
        DoomShieldPolicy.classifyForegroundPackage(
            packageName = packageName,
            insideNightWindow = DoomShieldPolicy.isNightWindow(hour, minute),
            existingTarget = existingTarget,
            exemptionPackages = exemptions,
        )
}
