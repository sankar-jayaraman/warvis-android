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
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
internal fun DoomShieldDisableCommitmentScreen(
    onCancel: () -> Unit,
    onOpenAuthorizedSettings: (String) -> DoomShieldSettingsLaunchResult,
) {
    BackHandler(onBack = onCancel)
    var coolingOffStartedAtMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val remainingMillis = coolingOffStartedAtMillis?.let { startedAt ->
        max(0L, DoomShieldPolicy.PAID_OVERRIDE_COOLING_OFF_MILLIS - (nowMillis - startedAt))
    } ?: DoomShieldPolicy.PAID_OVERRIDE_COOLING_OFF_MILLIS
    val remainingSeconds = (remainingMillis + 999L) / 1_000L
    val canConfirm = coolingOffStartedAtMillis != null && remainingMillis == 0L

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
            "This deliberate-payment step simulates a ${DoomShieldPolicy.PAID_OVERRIDE_PRICE_KR} kr " +
                "commitment. No real payment or charge occurs.",
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
                    "Simulated commitment: ${DoomShieldPolicy.PAID_OVERRIDE_PRICE_KR} kr",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("No money will be charged.")
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
                        enabled = canConfirm,
                        onClick = {
                            val authorization = DoomShieldDisableAuthorization.issue()
                            errorMessage = when (
                                onOpenAuthorizedSettings(authorization.token)
                            ) {
                                DoomShieldSettingsLaunchResult.Opened -> null
                                DoomShieldSettingsLaunchResult.AuthorizationExpired ->
                                    "The disable authorization expired. Confirm again to retry."
                                DoomShieldSettingsLaunchResult.AuthorizationAlreadyConsumed,
                                DoomShieldSettingsLaunchResult.AuthorizationWrongPurpose,
                                DoomShieldSettingsLaunchResult.AuthorizationNotFound ->
                                    "WARVIS rejected the disable authorization. Confirm again to retry."
                                DoomShieldSettingsLaunchResult.LaunchFailed ->
                                    "WARVIS could not open Android Accessibility settings. " +
                                        "Doom Shield remains enabled."
                            }
                        },
                    ) {
                        Text("Confirm — open settings to disable")
                    }
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
