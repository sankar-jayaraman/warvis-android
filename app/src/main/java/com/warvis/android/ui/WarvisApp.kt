package com.warvis.android.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.warvis.android.doom.DoomShieldControlAction
import com.warvis.android.doom.DoomShieldControlPolicy
import com.warvis.android.doom.DoomShieldSettingsGateway
import com.warvis.android.ui.backlog.BacklogScreen
import com.warvis.android.ui.doom.DoomShieldDisableCommitmentScreen
import com.warvis.android.ui.doom.NightExemptionsScreen
import com.warvis.android.ui.reports.ReportsScreen
import com.warvis.android.ui.journey.JourneyMapScreen
import com.warvis.android.ui.knowledge.KnowledgeRingsScreen
import com.warvis.android.ui.sprint.SprintBoardScreen
import com.warvis.android.ui.today.TodayScreen

@Composable
internal fun WarvisApp(
    destinationRequest: WarvisDestinationRequest? = null,
    doomShieldEnabled: Boolean = false,
    safeExitToHome: Boolean = false,
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val repository = remember { WarvisRepository(applicationContext) }
    val state by repository.state.collectAsStateWithLifecycle()
    val backlogRepository = remember { BacklogRepository(applicationContext) }
    val backlogState by backlogRepository.state.collectAsStateWithLifecycle()
    val bedtimeRepository = remember { BedtimeRepository(applicationContext) }
    val bedtimeEnabled by bedtimeRepository.enabled.collectAsStateWithLifecycle()
    var destination by rememberSaveable {
        mutableStateOf(destinationRequest?.destination ?: WarvisDestination.Today)
    }
    var showingNightExemptions by rememberSaveable { mutableStateOf(false) }
    var showingDisableCommitment by rememberSaveable { mutableStateOf(false) }
    val settingsGateway = remember(context) { DoomShieldSettingsGateway(context) }

    LaunchedEffect(destinationRequest?.id) {
        destinationRequest?.let {
            showingNightExemptions = false
            showingDisableCommitment = false
            destination = it.destination
        }
    }

    LaunchedEffect(doomShieldEnabled) {
        if (!doomShieldEnabled) {
            showingDisableCommitment = false
        }
    }

    BackHandler(
        enabled = safeExitToHome && !showingNightExemptions && !showingDisableCommitment,
    ) {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    Scaffold(
        bottomBar = {
            if (!showingNightExemptions && !showingDisableCommitment) {
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
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (showingDisableCommitment) {
                DoomShieldDisableCommitmentScreen(
                    onCancel = { showingDisableCommitment = false },
                    onOpenAuthorizedSettings = settingsGateway::openForAuthorizedDisable,
                )
            } else if (showingNightExemptions) {
                NightExemptionsScreen(onBack = { showingNightExemptions = false })
            } else when (destination) {
                WarvisDestination.Today -> TodayScreen(
                    state = state,
                    onToggleTask = repository::toggleTask,
                    bedtimeEnabled = bedtimeEnabled,
                    onToggleBedtime = bedtimeRepository::setEnabled,
                    onOpenNightExemptions = { showingNightExemptions = true },
                    doomShieldEnabled = doomShieldEnabled,
                    onDoomShieldAction = {
                        when (DoomShieldControlPolicy.decide(doomShieldEnabled).action) {
                            DoomShieldControlAction.OpenAccessibilitySettings ->
                                settingsGateway.openForEnable()
                            DoomShieldControlAction.RequireDisableCommitment -> {
                                showingDisableCommitment = true
                                null
                            }
                        }
                    },
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
