package com.warvis.android.ui.doom

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warvis.android.doom.DoomShieldDisableAuthorization
import com.warvis.android.doom.DoomShieldPolicy
import com.warvis.android.doom.DoomShieldSettingsLaunchResult
import com.warvis.android.taskratchet.TASK_RATCHET_AMOUNT_LABEL
import com.warvis.android.taskratchet.TaskRatchetConfiguration
import com.warvis.android.taskratchet.TaskRatchetMode
import com.warvis.android.taskratchet.TaskRatchetPaymentCoordinator
import com.warvis.android.taskratchet.TaskRatchetPaymentResult
import com.warvis.android.taskratchet.TaskRatchetPurpose
import com.warvis.android.taskratchet.taskRatchetNote
import com.warvis.android.taskratchet.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
internal fun DoomShieldDisableCommitmentScreen(
    onCancel: () -> Unit,
    onOpenAuthorizedSettings: (String) -> DoomShieldSettingsLaunchResult,
    paymentCoordinator: TaskRatchetPaymentCoordinator,
    configuration: TaskRatchetConfiguration,
    onOpenTaskRatchetSetup: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val scope = rememberCoroutineScope()
    var coolingOffStartedAtMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var paymentInProgress by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val remainingMillis = coolingOffStartedAtMillis?.let { startedAt ->
        max(0L, DoomShieldPolicy.PAID_OVERRIDE_COOLING_OFF_MILLIS - (nowMillis - startedAt))
    } ?: DoomShieldPolicy.PAID_OVERRIDE_COOLING_OFF_MILLIS
    val remainingSeconds = (remainingMillis + 999L) / 1_000L
    val canConfirm = coolingOffStartedAtMillis != null && remainingMillis == 0L
    val hasConfirmedRecovery = paymentCoordinator.hasConfirmedAttempt(
        TaskRatchetPurpose.DoomShieldDisable,
        null,
    )
    val unresolvedMode = paymentCoordinator.unresolvedAttemptMode(
        TaskRatchetPurpose.DoomShieldDisable,
        null,
    )
    val displayMode = unresolvedMode ?: configuration.mode
    val canSubmit = configuration.canCharge || hasConfirmedRecovery

    LaunchedEffect(coolingOffStartedAtMillis) {
        val startedAtMillis = coolingOffStartedAtMillis ?: return@LaunchedEffect
        while (
            System.currentTimeMillis() - startedAtMillis <
            DoomShieldPolicy.PAID_OVERRIDE_COOLING_OFF_MILLIS
        ) {
            nowMillis = System.currentTimeMillis()
            delay(250L)
        }
        nowMillis = System.currentTimeMillis()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Disable Doom Shield?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "This deliberate-payment step uses TaskRatchet. " +
                if (displayMode == TaskRatchetMode.Live) {
                    "$TASK_RATCHET_AMOUNT_LABEL will be charged immediately after confirmation."
                } else {
                    "Dry-run mode is active; TaskRatchet will validate the request without charging."
                },
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "After confirmation, Android Accessibility settings will open. If you disable " +
                "WARVIS Doom Shield there, it remains disabled indefinitely until you manually " +
                "enable it again. WARVIS cannot re-enable its own Accessibility Service.",
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (displayMode == TaskRatchetMode.Live) {
                        "Live TaskRatchet charge: $TASK_RATCHET_AMOUNT_LABEL"
                    } else {
                        "TaskRatchet dry run: $TASK_RATCHET_AMOUNT_LABEL"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (displayMode == TaskRatchetMode.Live) {
                        "Direct charges are captured immediately."
                    } else {
                        "No money will be charged in dry-run mode."
                    },
                )
                if (hasConfirmedRecovery) {
                    Text(
                        "A previously confirmed TaskRatchet attempt is ready for local recovery. " +
                            "Retrying will not create a new charge.",
                        fontWeight = FontWeight.Bold,
                    )
                } else if (unresolvedMode != null) {
                    Text(
                        "An unresolved attempt will reuse its original request ID and " +
                            "${if (displayMode == TaskRatchetMode.Live) "live" else "dry-run"} mode.",
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!configuration.canCharge && !hasConfirmedRecovery) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "TaskRatchet is not ready or has no confirmed payment method.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenTaskRatchetSetup,
                ) {
                    Text("Open TaskRatchet setup")
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (coolingOffStartedAtMillis == null) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            errorMessage = null
                            nowMillis = System.currentTimeMillis()
                            coolingOffStartedAtMillis = nowMillis
                        },
                    ) {
                        Text("Start 10-second cooldown")
                    }
                } else {
                    Text(
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        text = if (canConfirm) {
                            "Cooldown complete. Confirm only if you deliberately choose to disable."
                        } else {
                            "Cooling off: $remainingSeconds seconds remaining"
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canConfirm && !paymentInProgress && canSubmit,
                        onClick = {
                            paymentInProgress = true
                            errorMessage = null
                            statusMessage = "Confirming with TaskRatchet…"
                            scope.launch {
                                var launchResult: DoomShieldSettingsLaunchResult? = null
                                when (
                                    val result = paymentCoordinator.execute(
                                        purpose = TaskRatchetPurpose.DoomShieldDisable,
                                        targetKey = null,
                                        note = taskRatchetNote(
                                            TaskRatchetPurpose.DoomShieldDisable,
                                            null,
                                        ),
                                        grantBenefit = {
                                            val authorization =
                                                DoomShieldDisableAuthorization.issue()
                                            onOpenAuthorizedSettings(authorization.token)
                                                .also { launchResult = it } ==
                                                DoomShieldSettingsLaunchResult.Opened
                                        },
                                    )
                                ) {
                                    is TaskRatchetPaymentResult.Failure -> {
                                        errorMessage = result.kind.userMessage()
                                        statusMessage = null
                                    }
                                    is TaskRatchetPaymentResult.BenefitPending -> {
                                        errorMessage = when (launchResult) {
                                            DoomShieldSettingsLaunchResult.LaunchFailed ->
                                                "Payment was confirmed, but Android Settings " +
                                                    "did not open. Retry to recover without " +
                                                    "creating a new charge."
                                            else ->
                                                "Payment was confirmed, but WARVIS could not " +
                                                    "complete the disable authorization. Retry " +
                                                    "to recover without a new charge."
                                        }
                                        statusMessage = null
                                    }
                                    TaskRatchetPaymentResult.AlreadyInProgress -> {
                                        errorMessage =
                                            "This TaskRatchet request is already being completed. " +
                                                "Wait for it to finish."
                                        statusMessage = null
                                    }
                                    is TaskRatchetPaymentResult.Granted -> {
                                        errorMessage = null
                                        statusMessage = if (result.dryRun) {
                                            "Dry-run confirmed. Opening Accessibility settings."
                                        } else {
                                            "Live charge confirmed. Opening Accessibility settings."
                                        }
                                    }
                                }
                                paymentInProgress = false
                            }
                        },
                    ) {
                        Text(
                            if (paymentInProgress) {
                                "Confirming…"
                            } else {
                                "Confirm ${if (displayMode == TaskRatchetMode.Live) "live charge" else "dry run"} — open settings"
                            },
                        )
                    }
                }
                statusMessage?.let { message ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(message, fontWeight = FontWeight.Bold)
                }
                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCancel,
        ) {
            Text("Cancel")
        }
    }
}
