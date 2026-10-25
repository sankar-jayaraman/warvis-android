package com.warvis.android.ui.today

import com.warvis.android.doom.InstagramGamingPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayInstagramGamingPresentationTest {
    @Test
    fun inactivePhaseOffersActivation() {
        assertEquals(
            "Activate Instagram access",
            instagramGamingTodayActionLabel(InstagramGamingPhase.Inactive),
        )
    }

    @Test
    fun activePhaseOffersCurrentTime() {
        assertEquals(
            "Instagram access active — view time",
            instagramGamingTodayActionLabel(InstagramGamingPhase.Active),
        )
    }

    @Test
    fun quarantinePhaseOffersCurrentStatus() {
        assertEquals(
            "Instagram quarantined — view status",
            instagramGamingTodayActionLabel(InstagramGamingPhase.Quarantine),
        )
    }
}
