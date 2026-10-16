package com.warvis.android.ui.doom

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warvis.android.doom.NightExemptionRepository
import com.warvis.android.doom.NightExemptionResolver
import com.warvis.android.doom.NightExemptionSelection
import com.warvis.android.doom.MinuteOfDay
import com.warvis.android.doom.NightCurfewRepository
import com.warvis.android.doom.NightCurfewSaveResult
import com.warvis.android.doom.NightCurfewSchedule

@Composable
internal fun NightExemptionsScreen(
    onBack: () -> Unit,
    onSchedulePersisted: (NightCurfewSchedule) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val repository = remember { NightExemptionRepository(applicationContext) }
    val resolver = remember { NightExemptionResolver(applicationContext) }
    val curfewRepository = remember { NightCurfewRepository(applicationContext) }
    var persistedSchedule by remember { mutableStateOf(curfewRepository.readSnapshot()) }
    var draftStart by remember { mutableStateOf(persistedSchedule.start) }
    var draftEnd by remember { mutableStateOf(persistedSchedule.end) }
    var scheduleError by remember { mutableStateOf<String?>(null) }
    val apps = remember { resolver.launchableApps() }
    var selection by remember {
        mutableStateOf(
            NightExemptionSelection(
                automaticPackages = resolver.automaticPackages(),
                userPackages = repository.userPackages(),
            ),
        )
    }
    var query by remember { mutableStateOf("") }
    val persistSchedule: (NightCurfewSchedule) -> Unit = { candidate ->
        when (curfewRepository.save(candidate)) {
            NightCurfewSaveResult.Persisted -> {
                persistedSchedule = candidate
                draftStart = candidate.start
                draftEnd = candidate.end
                scheduleError = null
                onSchedulePersisted(candidate)
            }
            NightCurfewSaveResult.InvalidSchedule -> {
                scheduleError = "Start and end times must be different."
            }
            NightCurfewSaveResult.PersistenceFailed -> {
                draftStart = persistedSchedule.start
                draftEnd = persistedSchedule.end
                scheduleError = "WARVIS could not save the night curfew. The saved schedule is unchanged."
            }
        }
    }
    val updateTime: (Boolean, MinuteOfDay) -> Unit = { isStart, selected ->
        val nextStart = if (isStart) selected else draftStart
        val nextEnd = if (isStart) draftEnd else selected
        draftStart = nextStart
        draftEnd = nextEnd
        val candidate = NightCurfewSchedule(
            enabled = persistedSchedule.enabled,
            start = nextStart,
            end = nextEnd,
        )
        if (candidate.isValid) {
            persistSchedule(candidate)
        } else {
            scheduleError = "Start and end times must be different."
        }
    }
    val visibleApps = remember(apps, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(normalizedQuery, ignoreCase = true) ||
                    app.packageName.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Night app exemptions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Broad night curfew",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = persistedSchedule.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = persistedSchedule.enabled,
                onCheckedChange = { enabled ->
                    val draft = NightCurfewSchedule(
                        enabled = enabled,
                        start = draftStart,
                        end = draftEnd,
                    )
                    if (enabled && !draft.isValid) {
                        scheduleError = "Choose different start and end times before enabling."
                    } else if (draft.isValid) {
                        persistSchedule(draft)
                    } else {
                        persistSchedule(persistedSchedule.copy(enabled = false))
                    }
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            MinuteOfDay.fromHourMinute(hour, minute)?.let {
                                updateTime(true, it)
                            }
                        },
                        draftStart.hour,
                        draftStart.minute,
                        DateFormat.is24HourFormat(context),
                    ).show()
                },
            ) {
                Text("Start ${draftStart.label}")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            MinuteOfDay.fromHourMinute(hour, minute)?.let {
                                updateTime(false, it)
                            }
                        },
                        draftEnd.hour,
                        draftEnd.minute,
                        DateFormat.is24HourFormat(context),
                    ).show()
                },
            ) {
                Text("End ${draftEnd.label}")
            }
        }
        scheduleError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = if (persistedSchedule.enabled) {
                "During ${persistedSchedule.intervalLabel}, WARVIS intervenes in every app except " +
                    "the ones allowed here. Email, messaging defaults, phone/emergency, alarms, " +
                    "keyboards, accessibility services, system escape routes, and other critical " +
                    "apps are allowed automatically."
            } else {
                "The broad night curfew is disabled. Existing Doom Shield targets remain blocked " +
                    "at all times; these exemptions apply when the broad curfew is enabled."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "WARVIS automatically protects Android's default payment app and the " +
                "safety-critical apps listed above when Android exposes reliable signals. " +
                "Manually select banking, authenticator, password-manager, and non-default " +
                "payment apps; selected apps are then treated as protected exemptions. " +
                "Automatic exemptions cannot be removed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Button(onClick = onBack) {
            Text("Back to WARVIS")
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = query,
            onValueChange = { query = it },
            label = { Text("Find an installed app") },
            singleLine = true,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = visibleApps,
                key = { app -> app.packageName },
            ) { app ->
                val automatic = app.packageName in selection.automaticPackages
                val checked = app.packageName in selection.effectivePackages
                val updateSelection = {
                    val exempt = !checked
                    selection = selection.withUserExemption(
                        packageName = app.packageName,
                        exempt = exempt,
                    )
                    repository.setUserExempt(
                        packageName = app.packageName,
                        exempt = exempt,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !automatic, onClick = updateSelection)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (automatic) {
                                "${app.packageName} · Automatic"
                            } else {
                                app.packageName
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = checked,
                        enabled = !automatic,
                        onCheckedChange = { updateSelection() },
                    )
                }
            }
        }
    }
}
