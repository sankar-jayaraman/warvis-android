package com.warvis.android.ui.today

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warvis.android.data.model.WarvisState
import com.warvis.android.doom.DoomShieldSettingsLaunchResult
import com.warvis.android.doom.NightCurfewSchedule
import com.warvis.android.ui.components.ProgressBlock
import com.warvis.android.ui.components.ScreenScaffold
import com.warvis.android.ui.components.SectionTitle
import com.warvis.android.ui.components.WarvisCard
import com.warvis.android.ui.components.WarvisOutlinedCard

@Composable
internal fun TodayScreen(
    state: WarvisState,
    onToggleTask: (String) -> Unit,
    bedtimeEnabled: Boolean,
    onToggleBedtime: (Boolean) -> Unit,
    onOpenNightExemptions: () -> Unit,
    onOpenTaskRatchetSetup: () -> Unit,
    nightCurfewSchedule: NightCurfewSchedule,
    doomShieldEnabled: Boolean,
    onDoomShieldAction: () -> DoomShieldSettingsLaunchResult?,
) {
    var doomShieldSettingsError by remember { mutableStateOf<String?>(null) }

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
                Text(
                    if (nightCurfewSchedule.enabled) {
                        "Catch existing Doom Shield targets all day. During " +
                            "${nightCurfewSchedule.intervalLabel}, extend the same intervention " +
                            "to all non-essential apps."
                    } else {
                        "Catch existing Doom Shield targets all day. The broad night curfew is disabled."
                    },
                )
                Text(
                    text = "Requires Android Accessibility permission. Matching happens locally; " +
                        "WARVIS does not store full URLs. TaskRatchet gates target-specific " +
                        "15-minute overrides and requests to disable Doom Shield.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (doomShieldEnabled) {
                        "Status: Enabled"
                    } else {
                        "Status: Disabled"
                    },
                    color = if (doomShieldEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = {
                        doomShieldSettingsError = when (onDoomShieldAction()) {
                            null,
                            DoomShieldSettingsLaunchResult.Opened -> null
                            else -> "WARVIS could not open Android Accessibility settings. " +
                                "Doom Shield status was not changed."
                        }
                    },
                ) {
                    Text(
                        if (doomShieldEnabled) {
                            "Disable Doom Shield"
                        } else {
                            "Enable Doom Shield"
                        },
                    )
                }
                doomShieldSettingsError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(onClick = onOpenNightExemptions) {
                    Text("Configure night curfew and exemptions")
                }
                Button(onClick = onOpenTaskRatchetSetup) {
                    Text("Set up TaskRatchet payments")
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
