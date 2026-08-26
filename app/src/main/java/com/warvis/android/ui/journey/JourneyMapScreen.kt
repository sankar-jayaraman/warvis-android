package com.warvis.android.ui.journey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warvis.android.data.model.CareerStage
import com.warvis.android.data.model.WarvisState
import com.warvis.android.ui.components.ScreenScaffold
import com.warvis.android.ui.components.WarvisCard
import com.warvis.android.ui.components.WarvisOutlinedCard

@Composable
fun JourneyMapScreen(
    state: WarvisState,
    onAdvanceStage: () -> Unit,
) {
    var showAdvanceDialog by remember { mutableStateOf(false) }
    val currentStage = state.currentStage
    val canAdvance = currentStage != null && currentStage.stageNumber < state.careerStages.size

    ScreenScaffold(
        title = "Journey Map",
        subtitle = "Six stages from positioning to scout/angel investor.",
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (canAdvance) {
                Button(onClick = { showAdvanceDialog = true }) {
                    Text("Advance active stage")
                }
            }

            state.careerStages.sortedBy { it.stageNumber }.forEach { stage ->
                CareerStageCard(stage)
            }
        }
    }

    if (showAdvanceDialog) {
        AlertDialog(
            onDismissRequest = { showAdvanceDialog = false },
            title = { Text("Advance stage?") },
            text = { Text("Only advance if the current stage criteria are actually met. Stage 3 is intentionally non-skippable.") },
            confirmButton = {
                Button(
                    onClick = {
                        onAdvanceStage()
                        showAdvanceDialog = false
                    },
                ) {
                    Text("Advance")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAdvanceDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun CareerStageCard(stage: CareerStage) {
    val content: @Composable () -> Unit = {
        Text(
            text = "Stage ${stage.stageNumber}: ${stage.title}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(stage.timeline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stage.description)
        if (stage.isActive) {
            Text("Active stage", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        if (stage.isNonSkippable) {
            Text("Non-skippable operator credential", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
    }

    if (stage.isActive) {
        WarvisCard(content = content)
    } else {
        WarvisOutlinedCard(content = content)
    }
}
