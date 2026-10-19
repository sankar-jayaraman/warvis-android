package com.warvis.android.ui.doom

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warvis.android.doom.DoomShieldPolicy
import com.warvis.android.doom.INSTAGRAM_GAMING_PAYMENT_TARGET
import com.warvis.android.doom.InstagramGamingConfig
import com.warvis.android.doom.InstagramGamingGrantResult
import com.warvis.android.doom.InstagramGamingPhase
import com.warvis.android.doom.InstagramGamingPurchaseSnapshot
import com.warvis.android.doom.InstagramGamingRepository
import com.warvis.android.doom.InstagramGamingSaveResult
import com.warvis.android.taskratchet.TASK_RATCHET_AMOUNT_LABEL
import com.warvis.android.taskratchet.TaskRatchetConfiguration
import com.warvis.android.taskratchet.TaskRatchetMode
import com.warvis.android.taskratchet.TaskRatchetAttemptStatus
import com.warvis.android.taskratchet.TaskRatchetPaymentCoordinator
import com.warvis.android.taskratchet.TaskRatchetPaymentResult
import com.warvis.android.taskratchet.TaskRatchetPurpose
import com.warvis.android.taskratchet.taskRatchetNote
import com.warvis.android.taskratchet.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
internal fun InstagramGamingScreen(
    repository: InstagramGamingRepository,
    paymentCoordinator: TaskRatchetPaymentCoordinator,
    taskRatchetConfiguration: TaskRatchetConfiguration,
    onOpenTaskRatchetSetup: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
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
    var coolingOffStartedAtMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var liveAcknowledged by rememberSaveable { mutableStateOf(false) }
    val unresolvedAttempt = paymentCoordinator.unresolvedAttempt(
        TaskRatchetPurpose.InstagramGamingSession,
        INSTAGRAM_GAMING_PAYMENT_TARGET,
    )
    val pendingPurchase = InstagramGamingPurchaseSnapshot.decode(
        unresolvedAttempt?.benefitBinding,
    )
    val displayMode = unresolvedAttempt?.let {
        if (it.dryRun) TaskRatchetMode.DryRun else TaskRatchetMode.Live
    } ?: taskRatchetConfiguration.mode
    val draftConfig = InstagramGamingConfig(
        sessionMinutes = sessionMinutes.toConfigMinutes(),
        allowanceMinutes = allowanceMinutes.toConfigMinutes(),
        quarantineMinutes = quarantineMinutes.toConfigMinutes(),
    )
    val coolingRemainingMillis = coolingOffStartedAtMillis?.let {
        max(
            0L,
            DoomShieldPolicy.PAID_OVERRIDE_COOLING_OFF_MILLIS -
                (System.currentTimeMillis() - it),
        )
    } ?: DoomShieldPolicy.PAID_OVERRIDE_COOLING_OFF_MILLIS
    val statusLabel = when (status.phase) {
        InstagramGamingPhase.Inactive -> "Inactive"
        InstagramGamingPhase.Active -> "Active"
        InstagramGamingPhase.Quarantine -> "Quarantine"
    }
    val quarantineActive = status.phase == InstagramGamingPhase.Quarantine
    val sessionActive = status.phase != InstagramGamingPhase.Inactive
    val canStartActivationReview =
        !sessionActive && draftConfig.isValid
    val canConfirmActivation =
        coolingRemainingMillis == 0L &&
            !activationInProgress &&
            !sessionActive &&
            draftConfig.isValid &&
            (displayMode != TaskRatchetMode.Live || liveAcknowledged) &&
            (taskRatchetConfiguration.canCharge ||
                unresolvedAttempt?.status == TaskRatchetAttemptStatus.RemoteConfirmed)

    LaunchedEffect(Unit) {
        while (true) {
            nowEpochMillis = System.currentTimeMillis()
            status = repository.status()
            delay(1_000L)
        }
    }
    LaunchedEffect(coolingOffStartedAtMillis) {
        val started = coolingOffStartedAtMillis ?: return@LaunchedEffect
        while (
            System.currentTimeMillis() - started <
            DoomShieldPolicy.PAID_OVERRIDE_COOLING_OFF_MILLIS
        ) {
            delay(250L)
            nowEpochMillis = System.currentTimeMillis()
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
            text = "Instagram gaming session",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "A confirmed session permits only canonical Instagram app and browser targets. " +
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
            "Validated bounds: session 15-720 minutes, allowance 1-30 minutes, " +
                "quarantine 1-60 minutes. Session end and quarantine end are exclusive; " +
                "the allowance boundary is blocked immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = sessionMinutes,
            onValueChange = {
                sessionMinutes = it.filter { character -> character.isDigit() }.take(4)
            },
            label = { Text("Session duration (minutes)") },
            enabled = unresolvedAttempt == null,
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = allowanceMinutes,
            onValueChange = {
                allowanceMinutes = it.filter { character -> character.isDigit() }.take(2)
            },
            label = { Text("Foreground allowance (minutes)") },
            enabled = unresolvedAttempt == null,
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = quarantineMinutes,
            onValueChange = {
                quarantineMinutes = it.filter { character -> character.isDigit() }.take(2)
            },
            label = { Text("Quarantine duration (minutes)") },
            enabled = unresolvedAttempt == null,
            singleLine = true,
        )
        draftConfig.validationError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        Button(
            enabled = draftConfig.isValid && unresolvedAttempt == null,
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
            Text("Save configuration")
        }
        if (pendingPurchase != null) {
            Text(
                "Pending purchase is locked to ${pendingPurchase.config.sessionMinutes} / " +
                    "${pendingPurchase.config.allowanceMinutes} / " +
                    "${pendingPurchase.config.quarantineMinutes} minutes. Settings cannot " +
                    "change this attempt.",
                fontWeight = FontWeight.Bold,
            )
        }
        Text("Amount: $TASK_RATCHET_AMOUNT_LABEL", fontWeight = FontWeight.Bold)
        Text(
            if (displayMode == TaskRatchetMode.Live) {
                "Live mode: TaskRatchet charges US$4 immediately after confirmation."
            } else {
                "Dry-run mode: TaskRatchet validates the flow without charging money."
            },
        )
        if (displayMode == TaskRatchetMode.Live) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = liveAcknowledged,
                    onCheckedChange = { liveAcknowledged = it },
                )
                Text("I understand this confirmation immediately charges US$4.")
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenTaskRatchetSetup,
        ) {
            Text("Open TaskRatchet setup")
        }
        if (coolingOffStartedAtMillis == null) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = canStartActivationReview,
                onClick = {
                    coolingOffStartedAtMillis = System.currentTimeMillis()
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
                    val purchase = pendingPurchase
                        ?: InstagramGamingPurchaseSnapshot.create(draftConfig)
                    scope.launch {
                        when (
                            val result = paymentCoordinator.execute(
                                purpose = TaskRatchetPurpose.InstagramGamingSession,
                                targetKey = INSTAGRAM_GAMING_PAYMENT_TARGET,
                                note = taskRatchetNote(
                                    TaskRatchetPurpose.InstagramGamingSession,
                                    INSTAGRAM_GAMING_PAYMENT_TARGET,
                                ),
                                benefitBinding = purchase.encode(),
                                grantBenefit = { attempt ->
                                    repository.grant(
                                        taskRatchetRequestId = attempt.requestId,
                                        encodedPurchase = attempt.benefitBinding,
                                    ).grantsSession
                                },
                            )
                        ) {
                            is TaskRatchetPaymentResult.Granted -> {
                                message = if (result.recovered) {
                                    "Confirmed purchase recovered without another charge."
                                } else if (result.dryRun) {
                                    "Dry-run gaming session activated."
                                } else {
                                    "Paid Instagram gaming session activated."
                                }
                                coolingOffStartedAtMillis = null
                                liveAcknowledged = false
                            }
                            is TaskRatchetPaymentResult.BenefitPending ->
                                message = "Payment is confirmed, but local activation is pending. " +
                                    "Retry to recover without another charge."
                            TaskRatchetPaymentResult.AlreadyInProgress ->
                                message = "This activation is already in progress."
                            is TaskRatchetPaymentResult.Failure ->
                                message = result.kind.userMessage()
                        }
                        status = repository.status()
                        activationInProgress = false
                    }
                },
            ) {
                Text(
                    if (activationInProgress) {
                        "Confirming…"
                    } else if (displayMode == TaskRatchetMode.Live) {
                        "Confirm live US$4 gaming session"
                    } else {
                        "Confirm dry-run gaming session"
                    },
                )
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
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onBack) {
            Text("Back to WARVIS")
        }
    }
}

private val InstagramGamingGrantResult.grantsSession: Boolean
    get() =
        this == InstagramGamingGrantResult.Granted ||
            this == InstagramGamingGrantResult.AlreadyGranted

private fun String.toConfigMinutes(): Int = toIntOrNull() ?: Int.MIN_VALUE

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
