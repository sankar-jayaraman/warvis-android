package com.warvis.android.ui.doom

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warvis.android.doom.InstagramGamingActivationSnapshot
import com.warvis.android.doom.InstagramGamingConfig
import com.warvis.android.doom.InstagramGamingGrantResult
import com.warvis.android.doom.InstagramGamingPhase
import com.warvis.android.doom.InstagramGamingPolicy
import com.warvis.android.doom.InstagramGamingRepository
import com.warvis.android.doom.InstagramGamingSaveResult
import com.warvis.android.doom.instagramActivationReviewRemainingMillis
import kotlinx.coroutines.delay

private data class InstagramActivationReview(
    val reviewedTerms: InstagramGamingActivationSnapshot,
    val startedAtElapsedRealtimeMillis: Long,
)

@Composable
internal fun InstagramGamingScreen(
    repository: InstagramGamingRepository,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var nowElapsedRealtimeMillis by remember {
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }
    var status by remember { mutableStateOf(repository.status()) }
    var sessionMinutes by rememberSaveable {
        mutableStateOf(status.config.sessionMinutes.toString())
    }
    var allowanceMinutes by rememberSaveable {
        mutableStateOf(status.config.allowanceMinutes.toString())
    }
    var quarantineMinutes by rememberSaveable {
        mutableStateOf(status.config.quarantineMinutes.toString())
    }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var activationInProgress by remember { mutableStateOf(false) }
    var activationReview by remember { mutableStateOf<InstagramActivationReview?>(null) }
    val draftConfig = InstagramGamingConfig(
        sessionMinutes = sessionMinutes.toConfigMinutes(),
        allowanceMinutes = allowanceMinutes.toConfigMinutes(),
        quarantineMinutes = quarantineMinutes.toConfigMinutes(),
    )
    val reviewedTerms = activationReview?.reviewedTerms
    val coolingRemainingMillis = activationReview?.let {
        instagramActivationReviewRemainingMillis(
            startedAtElapsedRealtimeMillis = it.startedAtElapsedRealtimeMillis,
            nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
        )
    } ?: InstagramGamingPolicy.ACTIVATION_REVIEW_MILLIS
    val statusLabel = when (status.phase) {
        InstagramGamingPhase.Inactive -> "Inactive"
        InstagramGamingPhase.Active -> "Active"
        InstagramGamingPhase.Quarantine -> "Quarantine"
    }
    val quarantineActive = status.phase == InstagramGamingPhase.Quarantine
    val sessionActive = status.phase != InstagramGamingPhase.Inactive
    val canStartActivationReview =
        !sessionActive && draftConfig.isValid
    val settingsEnabled = activationReview == null
    val canConfirmActivation =
        coolingRemainingMillis == 0L &&
            !activationInProgress &&
            !sessionActive &&
            reviewedTerms?.config?.isValid == true

    LaunchedEffect(Unit) {
        while (true) {
            nowElapsedRealtimeMillis = SystemClock.elapsedRealtime()
            status = repository.status()
            delay(1_000L)
        }
    }
    LaunchedEffect(activationReview) {
        val review = activationReview ?: return@LaunchedEffect
        while (
            instagramActivationReviewRemainingMillis(
                startedAtElapsedRealtimeMillis = review.startedAtElapsedRealtimeMillis,
                nowElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            ) > 0L
        ) {
            delay(250L)
            nowElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Instagram access",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Temporarily permit canonical Instagram app and browser targets through " +
                "Doom Shield for free. " +
                "Foreground use is cumulative; quarantine blocks both forms and always wins.",
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Status: $statusLabel",
                    fontWeight = FontWeight.Bold,
                    color = if (quarantineActive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                if (sessionActive) {
                    Text("Session remaining: ${formatDuration(status.sessionRemainingMillis)}")
                }
                when (status.phase) {
                    InstagramGamingPhase.Active ->
                        Text(
                            "Foreground allowance remaining: " +
                                formatDuration(status.allowanceRemainingMillis),
                        )
                    InstagramGamingPhase.Quarantine ->
                        Text(
                            "Quarantine remaining: " +
                                formatDuration(status.quarantineRemainingMillis),
                        )
                    InstagramGamingPhase.Inactive -> Unit
                }
            }
        }
        Text(
            text = "Free activation review",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (reviewedTerms != null) {
            Text(
                "Reviewed terms are locked to ${reviewedTerms.config.sessionMinutes} minutes " +
                    "overall, ${reviewedTerms.config.allowanceMinutes} foreground minutes, " +
                    "and ${reviewedTerms.config.quarantineMinutes} quarantine minutes. " +
                    "Cancel the review to change them.",
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "No payment or TaskRatchet setup is required. The 10-second review is a " +
                "non-payment safeguard before access starts.",
        )
        if (activationReview == null) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = canStartActivationReview,
                onClick = {
                    activationReview = InstagramActivationReview(
                        reviewedTerms = repository.createActivationTerms(draftConfig),
                        startedAtElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                    )
                    nowElapsedRealtimeMillis =
                        checkNotNull(activationReview).startedAtElapsedRealtimeMillis
                    message = null
                },
            ) {
                Text("Start 10-second activation review")
            }
        } else {
            Text(
                if (coolingRemainingMillis > 0L) {
                    "Cooling off: ${(coolingRemainingMillis + 999L) / 1_000L} seconds"
                } else {
                    "Cooling-off complete. Confirm deliberately."
                },
                fontWeight = FontWeight.Bold,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = canConfirmActivation,
                onClick = {
                    activationInProgress = true
                    message = null
                    when (repository.activate(checkNotNull(reviewedTerms))) {
                        InstagramGamingGrantResult.Granted -> {
                            message = "Free Instagram access activated."
                            activationReview = null
                        }
                        InstagramGamingGrantResult.AlreadyGranted ->
                            message = "This Instagram activation was already applied."
                        InstagramGamingGrantResult.ActiveSessionConflict ->
                            message = "An Instagram access session is already active."
                        InstagramGamingGrantResult.InvalidBinding ->
                            message = "The reviewed activation terms are invalid. Start again."
                        InstagramGamingGrantResult.PersistenceFailed ->
                            message = "WARVIS could not persist the activation."
                    }
                    status = repository.status()
                    activationInProgress = false
                },
            ) {
                Text(
                    if (activationInProgress) {
                        "Activating…"
                    } else {
                        "Confirm free Instagram access"
                    },
                )
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !activationInProgress,
                onClick = {
                    activationReview = null
                    message = null
                },
            ) {
                Text("Cancel activation review")
            }
        }
        message?.let {
            Text(
                text = it,
                color = if (it.contains("activated") || it.contains("recovered")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Session settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Configure the next free activation. Validated bounds: session 15-720 minutes, " +
                "allowance 1-30 minutes, quarantine 1-60 minutes. Session end and " +
                "quarantine end are exclusive; the allowance boundary is blocked immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = sessionMinutes,
            onValueChange = {
                sessionMinutes = it.digitsOnly(maxLength = 4)
            },
            label = { Text("Session duration (minutes)") },
            enabled = settingsEnabled,
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = allowanceMinutes,
            onValueChange = {
                allowanceMinutes = it.digitsOnly(maxLength = 2)
            },
            label = { Text("Foreground allowance (minutes)") },
            enabled = settingsEnabled,
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = quarantineMinutes,
            onValueChange = {
                quarantineMinutes = it.digitsOnly(maxLength = 2)
            },
            label = { Text("Quarantine duration (minutes)") },
            enabled = settingsEnabled,
            singleLine = true,
        )
        draftConfig.validationError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        Button(
            enabled = draftConfig.isValid && settingsEnabled,
            onClick = {
                message = when (val result = repository.saveConfig(draftConfig)) {
                    InstagramGamingSaveResult.Persisted -> "Configuration saved."
                    is InstagramGamingSaveResult.Invalid -> result.message
                    InstagramGamingSaveResult.PersistenceFailed ->
                        "WARVIS could not save the configuration."
                }
                status = repository.status()
            },
        ) {
            Text("Save session settings")
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onBack) {
            Text("Back to WARVIS")
        }
    }
}

private fun String.toConfigMinutes(): Int = toIntOrNull() ?: Int.MIN_VALUE

private fun String.digitsOnly(maxLength: Int): String =
    filter(Char::isDigit).take(maxLength)

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
