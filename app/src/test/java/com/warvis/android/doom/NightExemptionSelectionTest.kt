package com.warvis.android.doom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightExemptionSelectionTest {
    @Test
    fun automaticExemption_cannotBeRemovedByUserSelection() {
        val selection = NightExemptionSelection(
            automaticPackages = setOf("com.android.settings"),
            userPackages = emptySet(),
        )

        val updated = selection.withUserExemption("com.android.settings", exempt = false)

        assertEquals(selection, updated)
        assertEquals(true, "com.android.settings" in updated.effectivePackages)
    }

    @Test
    fun userExemptions_canBeAddedAndRemovedIndependently() {
        val initial = NightExemptionSelection(
            automaticPackages = setOf("com.android.settings"),
            userPackages = setOf("com.example.bank"),
        )

        val added = initial.withUserExemption("com.example.authenticator", exempt = true)
        val removed = added.withUserExemption("com.example.bank", exempt = false)

        assertEquals(
            setOf("com.android.settings", "com.example.authenticator"),
            removed.effectivePackages,
        )
        assertEquals(
            setOf("com.example.authenticator"),
            removed.userPackages,
        )
    }

    @Test
    fun automaticResolution_ignoresMailtoHandlers() {
        val resolved = NightAutomaticExemptionResolution.resolvedPackages(
            listOf(
                NightExemptionCapabilityHandler(
                    packageName = "com.example.unlistedbrowser",
                    capability = NightExemptionCapability.Mailto,
                ),
                NightExemptionCapabilityHandler(
                    packageName = "com.example.email",
                    capability = NightExemptionCapability.AppEmail,
                ),
            ),
        )

        assertFalse("com.example.unlistedbrowser" in resolved)
        assertTrue("com.example.email" in resolved)
    }

    @Test
    fun automaticResolution_deduplicatesOemClockHandlers() {
        val resolved = NightAutomaticExemptionResolution.resolvedPackages(
            listOf(
                NightExemptionCapabilityHandler(
                    packageName = "com.oem.clock",
                    capability = NightExemptionCapability.ShowAlarms,
                ),
                NightExemptionCapabilityHandler(
                    packageName = "com.oem.clock",
                    capability = NightExemptionCapability.SetAlarm,
                ),
            ),
        )

        assertEquals(setOf("com.oem.clock"), resolved)
    }
}
