package com.warvis.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warvis.android.bedtime.BedtimeRepository
import com.warvis.android.data.BacklogRepository
import com.warvis.android.data.WarvisRepository
import com.warvis.android.ui.backlog.BacklogScreen
import com.warvis.android.ui.reports.ReportsScreen
import com.warvis.android.ui.journey.JourneyMapScreen
import com.warvis.android.ui.knowledge.KnowledgeRingsScreen
import com.warvis.android.ui.sprint.SprintBoardScreen
import com.warvis.android.ui.today.TodayScreen

@Composable
fun WarvisApp() {
    val context = LocalContext.current.applicationContext
    val repository = remember { WarvisRepository(context) }
    val state by repository.state.collectAsStateWithLifecycle()
    val backlogRepository = remember { BacklogRepository(context) }
    val backlogState by backlogRepository.state.collectAsStateWithLifecycle()
    val bedtimeRepository = remember { BedtimeRepository(context) }
    val bedtimeEnabled by bedtimeRepository.enabled.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(WarvisDestination.Today) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                WarvisDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        label = { Text(item.label) },
                        icon = { Text(item.shortLabel) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (destination) {
                WarvisDestination.Today -> TodayScreen(
                    state = state,
                    onToggleTask = repository::toggleTask,
                    bedtimeEnabled = bedtimeEnabled,
                    onToggleBedtime = bedtimeRepository::setEnabled,
                )
                WarvisDestination.Sprint -> SprintBoardScreen(
                    state = state,
                    onToggleTask = repository::toggleTask,
                )
                WarvisDestination.Knowledge -> KnowledgeRingsScreen(
                    state = state,
                    onScoreChange = repository::updateKnowledgeScore,
                )
                WarvisDestination.Journey -> JourneyMapScreen(
                    state = state,
                    onAdvanceStage = repository::advanceStage,
                )
                WarvisDestination.Backlog -> BacklogScreen(
                    state = backlogState,
                    onAddItem = backlogRepository::addItem,
                    onEditItem = backlogRepository::editItem,
                    onDeleteItem = backlogRepository::deleteItem,
                )
                WarvisDestination.Reports -> ReportsScreen()
            }
        }
    }
}

private enum class WarvisDestination(val label: String, val shortLabel: String) {
    Today("Today", "T"),
    Sprint("Sprint", "S"),
    Knowledge("Know.", "K"),
    Journey("Journey", "J"),
    Backlog("Backlog", "B"),
    Reports("Reports", "📄"),
}
