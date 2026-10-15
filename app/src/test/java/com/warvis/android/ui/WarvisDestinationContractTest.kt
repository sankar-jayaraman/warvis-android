package com.warvis.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
