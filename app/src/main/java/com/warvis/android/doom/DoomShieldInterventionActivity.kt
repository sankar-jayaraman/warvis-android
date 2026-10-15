package com.warvis.android.doom

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Surface
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
import com.warvis.android.ui.theme.WarvisTheme
import kotlinx.coroutines.delay
import kotlin.math.max

class DoomShieldInterventionActivity : ComponentActivity() {
    private var targetKey: String? = null
    private var targetLabel: String = "a blocked app or site"
    private var sourceLabel: String = "your phone"
    private var sourcePackage: String? = null
    private var confirmationInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetKey = intent.getStringExtra(EXTRA_TARGET_KEY)
        targetLabel = intent.getStringExtra(EXTRA_TARGET_LABEL) ?: targetLabel
        sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL) ?: sourceLabel
        sourcePackage = intent.getStringExtra(EXTRA_SOURCE_PACKAGE)
        val suggestionTitle = intent.getStringExtra(EXTRA_SUGGESTION_TITLE)
        val suggestionBucket = intent.getStringExtra(EXTRA_SUGGESTION_BUCKET)
        val isBedtime = intent.getBooleanExtra(EXTRA_IS_BEDTIME, false)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goHome()
            },
        )

        setContent {
            WarvisTheme {
                DoomShieldInterventionScreen(
                    targetLabel = targetLabel,
                    sourceLabel = sourceLabel,
                    suggestionTitle = suggestionTitle,
                    suggestionBucket = suggestionBucket,
                    isBedtime = isBedtime,
                    onOpenKindle = ::openKindle,
                    onGoHome = ::goHome,
                    onConfirmDryRun = ::confirmDryRun,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        DoomShieldInterventionState.markVisible()
    }

    override fun onStop() {
        DoomShieldInterventionState.markHidden()
        super.onStop()
    }

    private fun openKindle() {
        val key = targetKey ?: return
        DoomShieldKindleLauncher.open(
            this,
            DoomShieldTarget(
                key = key,
                displayName = targetLabel,
                sourceLabel = sourceLabel,
            ),
        )
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    private fun confirmDryRun() {
        if (confirmationInProgress) return
        val key = targetKey ?: return
        confirmationInProgress = true

        DoomShieldGracePeriod.startIfInactive(this, key)
        reopenOriginalTarget()
    }

    private fun reopenOriginalTarget() {
        val targetIntent = sourcePackage
            ?.let(packageManager::getLaunchIntentForPackage)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (targetIntent != null) {
            try {
                startActivity(targetIntent)
            } catch (exception: ActivityNotFoundException) {
                Log.e(TAG, "Original target launch activity was not found", exception)
            } catch (exception: SecurityException) {
                Log.e(TAG, "Original target launch was blocked", exception)
            }
        }
        finish()
    }

    companion object {
        private const val TAG = "DoomShield"
        const val EXTRA_TARGET_KEY = "com.warvis.android.doom.EXTRA_TARGET_KEY"
        const val EXTRA_TARGET_LABEL = "com.warvis.android.doom.EXTRA_TARGET_LABEL"
        const val EXTRA_SOURCE_LABEL = "com.warvis.android.doom.EXTRA_SOURCE_LABEL"
        const val EXTRA_SOURCE_PACKAGE = "com.warvis.android.doom.EXTRA_SOURCE_PACKAGE"
        const val EXTRA_SUGGESTION_TITLE = "com.warvis.android.doom.EXTRA_SUGGESTION_TITLE"
        const val EXTRA_SUGGESTION_BUCKET = "com.warvis.android.doom.EXTRA_SUGGESTION_BUCKET"
        const val EXTRA_IS_BEDTIME = "com.warvis.android.doom.EXTRA_IS_BEDTIME"
    }
}

@Composable
private fun DoomShieldInterventionScreen(
    targetLabel: String,
    sourceLabel: String,
    suggestionTitle: String?,
    suggestionBucket: String?,
    isBedtime: Boolean,
    onOpenKindle: () -> Unit,
    onGoHome: () -> Unit,
    onConfirmDryRun: () -> Unit,
) {
    var coolingOffStartedAtMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Doom Shield caught $targetLabel",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isBedtime) {
                Text(
                    text = "🌙 It's past 7 PM — time to wind down, not scroll.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (suggestionTitle != null) {
                Text(
                    text = "Instead of scrolling, try this:",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = suggestionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (suggestionBucket != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = suggestionBucket,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "This is the loop that steals your PE sprint time. Take one deliberate action instead of scrolling.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Detected in: $sourceLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenKindle) {
                Text("Open Kindle instead")
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (coolingOffStartedAtMillis == null) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        nowMillis = System.currentTimeMillis()
                        coolingOffStartedAtMillis = nowMillis
                    },
                ) {
                    Text("Test 25 kr unlock")
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Dry-run paid override",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Price: ${DoomShieldPolicy.PAID_OVERRIDE_PRICE_KR} kr")
                        Text(
                            "Unlock: ${DoomShieldPolicy.PAID_OVERRIDE_DURATION_MINUTES} " +
                                "minutes for $targetLabel only",
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No money will be charged in this test version.",
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                            text = if (canConfirm) {
                                "Cooling-off complete. Confirm only if you deliberately choose to continue."
                            } else {
                                "Cooling off: $remainingSeconds seconds remaining"
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canConfirm,
                            onClick = onConfirmDryRun,
                        ) {
                            Text(
                                "Confirm dry run — unlock " +
                                    "${DoomShieldPolicy.PAID_OVERRIDE_DURATION_MINUTES} minutes",
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onGoHome) {
                Text("Go Home")
            }
        }
    }
}
