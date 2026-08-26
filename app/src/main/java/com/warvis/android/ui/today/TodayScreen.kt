package com.warvis.android.ui.today

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warvis.android.data.model.WarvisState
import com.warvis.android.ui.components.ProgressBlock
import com.warvis.android.ui.components.ScreenScaffold
import com.warvis.android.ui.components.SectionTitle
import com.warvis.android.ui.components.WarvisCard
import com.warvis.android.ui.components.WarvisOutlinedCard

@Composable
fun TodayScreen(
    state: WarvisState,
    onToggleTask: (String) -> Unit,
    bedtimeEnabled: Boolean,
    onToggleBedtime: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    ScreenScaffold(
        title = "WARVIS",
        subtitle = "Your PE career-switch operating system.",
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WarvisCard {
                SectionTitle("Compliance gate")
                ProgressBlock("Before external PE activity", state.complianceProgress)
                if (state.complianceProgress.completed < state.complianceProgress.total) {
                    Text(
                        text = "Complete this before expert-network registration, paid calls, or publishing external material.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text("Gate cleared. Keep using the call and publication protocols.")
                }
            }

            WarvisCard {
                SectionTitle("Today's focus")
                val focusTask = state.focusTask
                if (focusTask == null) {
                    Text("Sprint complete. Use the review flow to create the Month 2 plan.")
                } else {
                    Text(text = focusTask.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(focusTask.description)
                    Text(
                        text = "${focusTask.weekTitle} - ${focusTask.category.label} - ${focusTask.estimatedMinutes ?: "?"} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { onToggleTask(focusTask.id) }) {
                        Text("Mark done")
                    }
                }
            }

            WarvisOutlinedCard {
                SectionTitle("Current stage")
                val stage = state.currentStage
                Text(text = stage?.title ?: "No active stage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (stage != null) {
                    Text(stage.timeline)
                    Text(stage.description)
                }
            }

            WarvisOutlinedCard {
                SectionTitle("Weekly momentum")
                ProgressBlock("4-week sprint", state.sprintProgress)
                Text("North star: build a compliant PE operator/TDD path before chasing outcomes.")
            }

            WarvisOutlinedCard {
                SectionTitle("Doom Shield")
                Text("Catch Facebook, Instagram, and YouTube loops, then show a WARVIS intervention before doom scrolling starts.")
                Text(
                    text = "Requires Android Accessibility permission. Matching happens locally; WARVIS does not store full URLs. Continue gives you 5 minutes before the next intervention.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                ) {
                    Text("Enable in Accessibility Settings")
                }
            }

            WarvisOutlinedCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle("Bedtime Mode")
                        Text(
                            text = "Sends a wind-down reminder at 7 PM and prompts you to enable Airplane Mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bedtimeEnabled,
                        onCheckedChange = onToggleBedtime,
                    )
                }
            }
        }
    }
}
