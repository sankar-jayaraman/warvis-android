package com.warvis.android.ui

internal enum class WarvisDestination(val label: String, val shortLabel: String) {
    Today("Today", "T"),
    Sprint("Sprint", "S"),
    Knowledge("Know.", "K"),
    Journey("Journey", "J"),
    Backlog("Backlog", "B"),
    Reports("Reports", "📄"),
}

internal data class WarvisDestinationRequest(
    val id: Long,
    val destination: WarvisDestination,
    val showTaskRatchetSetup: Boolean = false,
)

internal object WarvisDestinationContract {
    const val EXTRA_DESTINATION = "com.warvis.android.extra.DESTINATION"
    const val EXTRA_SAFE_EXIT_TO_HOME = "com.warvis.android.extra.SAFE_EXIT_TO_HOME"
    const val BACKLOG = "backlog"
    const val TASK_RATCHET_SETUP = "taskratchet_setup"

    fun destinationFor(value: String?): WarvisDestination? = when (value) {
        BACKLOG -> WarvisDestination.Backlog
        TASK_RATCHET_SETUP -> WarvisDestination.Today
        else -> null
    }

    fun showsTaskRatchetSetup(value: String?): Boolean = value == TASK_RATCHET_SETUP
}
