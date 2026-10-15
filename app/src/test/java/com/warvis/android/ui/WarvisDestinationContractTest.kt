package com.warvis.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarvisDestinationContractTest {
    @Test
    fun backlogContract_resolvesOnlyStableBacklogValue() {
        assertEquals(
            WarvisDestination.Backlog,
            WarvisDestinationContract.destinationFor(WarvisDestinationContract.BACKLOG),
        )
        assertNull(WarvisDestinationContract.destinationFor(null))
        assertNull(WarvisDestinationContract.destinationFor("unknown"))
    }

    @Test
    fun taskRatchetContract_routesToTodaySetupOverlay() {
        assertEquals(
            WarvisDestination.Today,
            WarvisDestinationContract.destinationFor(
                WarvisDestinationContract.TASK_RATCHET_SETUP,
            ),
        )
        assertTrue(
            WarvisDestinationContract.showsTaskRatchetSetup(
                WarvisDestinationContract.TASK_RATCHET_SETUP,
            ),
        )
        assertFalse(
            WarvisDestinationContract.showsTaskRatchetSetup(
                WarvisDestinationContract.BACKLOG,
            ),
        )
    }
}
