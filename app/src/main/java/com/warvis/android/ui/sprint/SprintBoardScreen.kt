package com.warvis.android.ui.sprint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warvis.android.data.model.WarvisState
import com.warvis.android.ui.components.ProgressBlock
import com.warvis.android.ui.components.ScreenScaffold
import com.warvis.android.ui.components.TaskRow
import com.warvis.android.ui.components.WarvisCard

@Composable
fun SprintBoardScreen(
    state: WarvisState,
    onToggleTask: (String) -> Unit,
) {
    val complianceCleared = state.complianceProgress.completed == state.complianceProgress.total
    ScreenScaffold(
        title = "Sprint Board",
        subtitle = "Week 0 compliance plus the 4-week positioning sprint.",
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.tasks.groupBy { it.weekNumber }.toSortedMap().forEach { (weekNumber, tasks) ->
                WarvisCard {
                    val progress = state.progressForWeek(weekNumber)
                    Text(
                        text = if (weekNumber == 0) "Week 0: Compliance Gate" else "Week $weekNumber",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    ProgressBlock(tasks.first().weekTitle, progress)
                    tasks.sortedBy { it.order }.forEach { task ->
                        TaskRow(task = task, complianceCleared = complianceCleared, onToggleTask = onToggleTask)
                    }
                }
            }
        }
    }
}
