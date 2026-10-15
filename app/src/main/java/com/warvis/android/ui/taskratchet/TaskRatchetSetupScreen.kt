package com.warvis.android.ui.taskratchet

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.warvis.android.taskratchet.TASK_RATCHET_AMOUNT_LABEL
import com.warvis.android.taskratchet.TaskRatchetAccountResult
import com.warvis.android.taskratchet.TaskRatchetClient
import com.warvis.android.taskratchet.TaskRatchetConfiguration
import com.warvis.android.taskratchet.TaskRatchetConfigurationMutationResult
import com.warvis.android.taskratchet.TaskRatchetConfigurationRepository
import com.warvis.android.taskratchet.TaskRatchetMode
import com.warvis.android.taskratchet.userMessage
import kotlinx.coroutines.launch

@Composable
internal fun TaskRatchetSetupScreen(
    configuration: TaskRatchetConfiguration,
    repository: TaskRatchetConfigurationRepository,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { TaskRatchetClient() }
    var token by remember { mutableStateOf("") }
    var validating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showLiveConfirmation by remember { mutableStateOf(false) }

    if (showLiveConfirmation) {
        AlertDialog(
            onDismissRequest = { showLiveConfirmation = false },
            title = { Text("Enable immediate live charges?") },
            text = {
                Text(
                    "Each confirmed blocked-target 15-minute override and each confirmed request " +
                        "to open Settings to disable Doom Shield will immediately charge " +
                        "$TASK_RATCHET_AMOUNT_LABEL. Charges count toward your TaskRatchet " +
                        "monthly API charge limit. This does not make Doom Shield tamper-proof.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLiveConfirmation = false
                        message = if (repository.enableLive(confirmationAccepted = true)) {
                            "Live charging enabled."
                        } else {
                            "Live charging could not be enabled. Revalidate TaskRatchet first."
                        }
                    },
                ) {
                    Text("I understand — enable live charges")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLiveConfirmation = false }) {
                    Text("Keep dry-run")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "TaskRatchet setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "WARVIS stores your personal API key encrypted with Android Keystore. The key stays " +
                "on this device and is sent only to TaskRatchet over HTTPS.",
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://app.taskratchet.com/"),
                        ),
                    )
                } catch (_: ActivityNotFoundException) {
                    message = "No browser is available to open TaskRatchet."
                } catch (_: SecurityException) {
                    message = "Android blocked the TaskRatchet browser link."
                }
            },
        ) {
            Text("Open TaskRatchet signup/settings")
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (configuration.connected) "Connected" else "Not connected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                configuration.account?.let { account ->
                    Text(
                        if (account.hasPaymentMethod) {
                            "Payment method: confirmed by TaskRatchet"
                        } else {
                            "Payment method: missing"
                        },
                    )
                    Text(
                        "Monthly API charge limit: " +
                            (
                                account.monthlyChargeLimitCents
                                    ?.let {
                                        "US$${it / 100}.${(it % 100).toString().padStart(2, '0')}"
                                    }
                                    ?: "not reported"
                                ),
                    )
                }
                Text(
                    "Mode: " + if (configuration.mode == TaskRatchetMode.DryRun) {
                        "Dry-run (no money charged)"
                    } else {
                        "LIVE — $TASK_RATCHET_AMOUNT_LABEL per confirmed action"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = token,
            enabled = !validating,
            onValueChange = { token = it },
            label = {
                Text(if (configuration.connected) "Replacement API key" else "Personal API key")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = token.isNotBlank() && !validating,
            onClick = {
                val candidate = token.trim()
                token = ""
                message = null
                when (repository.credentialMutationResult()) {
                    TaskRatchetConfigurationMutationResult.PendingLiveAttempt ->
                        message =
                            "Resolve the pending live TaskRatchet payment before replacing the API key."
                    TaskRatchetConfigurationMutationResult.Failure ->
                        message = "WARVIS could not safely inspect pending payment state."
                    TaskRatchetConfigurationMutationResult.AttemptInProgress ->
                        message =
                            "Wait for the current TaskRatchet request to finish before replacing the API key."
                    TaskRatchetConfigurationMutationResult.Success -> {
                        validating = true
                        scope.launch {
                            message = when (val result = client.validate(candidate)) {
                                is TaskRatchetAccountResult.Failure -> result.kind.userMessage()
                                is TaskRatchetAccountResult.Success -> {
                                    when (
                                        repository.saveValidatedToken(candidate, result.account)
                                    ) {
                                        TaskRatchetConfigurationMutationResult.Success ->
                                            if (result.account.hasPaymentMethod) {
                                                "API key validated. Dry-run remains on."
                                            } else {
                                                "API key validated, but TaskRatchet reports no payment method."
                                            }
                                        TaskRatchetConfigurationMutationResult.PendingLiveAttempt ->
                                            "Resolve the pending live TaskRatchet payment before replacing the API key."
                                        TaskRatchetConfigurationMutationResult.AttemptInProgress ->
                                            "Wait for the current TaskRatchet request to finish before replacing the API key."
                                        TaskRatchetConfigurationMutationResult.Failure ->
                                            "WARVIS could not securely save the API key."
                                    }
                                }
                            }
                            validating = false
                        }
                    }
                }
            },
        ) {
            Text(if (validating) "Validating…" else "Validate and save API key")
        }

        if (configuration.connected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (configuration.mode == TaskRatchetMode.Live) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            message = when (repository.useDryRun()) {
                                TaskRatchetConfigurationMutationResult.Success ->
                                    "Dry-run enabled. No money will be charged."
                                TaskRatchetConfigurationMutationResult.PendingLiveAttempt ->
                                    "Resolve the pending live TaskRatchet payment before changing modes."
                                TaskRatchetConfigurationMutationResult.AttemptInProgress ->
                                    "Wait for the current TaskRatchet request to finish before changing modes."
                                TaskRatchetConfigurationMutationResult.Failure ->
                                    "WARVIS could not change the mode."
                            }
                        },
                    ) {
                        Text("Return to dry-run")
                    }
                } else {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = configuration.account?.hasPaymentMethod == true,
                        onClick = { showLiveConfirmation = true },
                    ) {
                        Text("Enable live charges")
                    }
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        token = ""
                        message = when (repository.disconnect()) {
                            TaskRatchetConfigurationMutationResult.Success ->
                                "TaskRatchet disconnected and the local key was cleared."
                            TaskRatchetConfigurationMutationResult.PendingLiveAttempt ->
                                "Resolve the pending live TaskRatchet payment before disconnecting."
                            TaskRatchetConfigurationMutationResult.AttemptInProgress ->
                                "Wait for the current TaskRatchet request to finish before disconnecting."
                            TaskRatchetConfigurationMutationResult.Failure ->
                                "WARVIS could not safely disconnect TaskRatchet."
                        }
                    },
                ) {
                    Text("Disconnect")
                }
            }
        }

        message?.let {
            Text(
                text = it,
                color = if (
                    it.contains("failed", ignoreCase = true) ||
                    it.contains("could not", ignoreCase = true) ||
                    it.contains("missing", ignoreCase = true) ||
                    it.contains("resolve the pending live", ignoreCase = true) ||
                    it.contains("wait for the current", ignoreCase = true)
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "Security tradeoff: Android Keystore protects the key at rest, but a compromised or " +
                "unlocked device, malicious accessibility tooling, rooted OS, or app tampering can " +
                "still undermine a local-only design. WARVIS has no backend in this version.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onBack) {
            Text("Back")
        }
    }
}
