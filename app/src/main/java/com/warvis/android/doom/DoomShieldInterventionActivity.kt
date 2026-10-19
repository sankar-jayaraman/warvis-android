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
import com.warvis.android.MainActivity
import com.warvis.android.taskratchet.TASK_RATCHET_AMOUNT_LABEL
import com.warvis.android.taskratchet.TaskRatchetConfiguration
import com.warvis.android.taskratchet.TaskRatchetConfigurationRepository
import com.warvis.android.taskratchet.TaskRatchetMode
import com.warvis.android.taskratchet.TaskRatchetPaymentCoordinator
import com.warvis.android.taskratchet.TaskRatchetPaymentResult
import com.warvis.android.taskratchet.TaskRatchetPurpose
import com.warvis.android.taskratchet.taskRatchetNote
import com.warvis.android.taskratchet.userMessage
import com.warvis.android.ui.WarvisDestinationContract
import com.warvis.android.ui.theme.WarvisTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class DoomShieldInterventionActivity : ComponentActivity() {
    private var targetKey: String? = null
    private var targetLabel: String = "a blocked app or site"
    private var sourceLabel: String = "your phone"
    private var sourcePackage: String? = null
    private var instagramQuarantineUntilEpochMillis: Long? = null
    private var confirmationInProgress = false
    private lateinit var taskRatchetConfigurationRepository:
        TaskRatchetConfigurationRepository
    private lateinit var taskRatchetPaymentCoordinator: TaskRatchetPaymentCoordinator
    private var paymentStatus by mutableStateOf<PaymentStatus>(PaymentStatus.Idle)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetKey = intent.getStringExtra(EXTRA_TARGET_KEY)
        targetLabel = intent.getStringExtra(EXTRA_TARGET_LABEL) ?: targetLabel
        sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL) ?: sourceLabel
        sourcePackage = intent.getStringExtra(EXTRA_SOURCE_PACKAGE)
        instagramQuarantineUntilEpochMillis =
            intent.getLongExtra(EXTRA_INSTAGRAM_QUARANTINE_UNTIL, 0L)
                .takeIf { it > 0L }
        taskRatchetConfigurationRepository = TaskRatchetConfigurationRepository(
            applicationContext,
        )
        taskRatchetPaymentCoordinator = TaskRatchetPaymentCoordinator(
            context = applicationContext,
            configurationRepository = taskRatchetConfigurationRepository,
        )
        val suggestionTitle = intent.getStringExtra(EXTRA_SUGGESTION_TITLE)
        val suggestionBucket = intent.getStringExtra(EXTRA_SUGGESTION_BUCKET)
        val curfewLabel = intent.getStringExtra(EXTRA_CURFEW_LABEL)
            ?.takeIf(NightCurfewLabelPolicy::isBoundedIntervalLabel)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goHome()
            },
        )

        setContent {
            val taskRatchetConfiguration by
                taskRatchetConfigurationRepository.state.collectAsStateWithLifecycle()
            WarvisTheme {
                DoomShieldInterventionScreen(
                    targetLabel = targetLabel,
                    sourceLabel = sourceLabel,
                    suggestionTitle = suggestionTitle,
                    suggestionBucket = suggestionBucket,
                    curfewLabel = curfewLabel,
                    instagramQuarantineUntilEpochMillis =
                        instagramQuarantineUntilEpochMillis,
                    onOpenKindle = ::openKindle,
                    onOpenBacklog = ::openBacklog,
                    onOpenTaskRatchetSetup = ::openTaskRatchetSetup,
                    onGoHome = ::goHome,
                    taskRatchetConfiguration = taskRatchetConfiguration,
                    paymentStatus = paymentStatus,
                    hasConfirmedRecovery =
                        taskRatchetPaymentCoordinator.hasConfirmedAttempt(
                            TaskRatchetPurpose.BlockedTargetOverride,
                            targetKey,
                        ),
                    unresolvedAttemptMode =
                        taskRatchetPaymentCoordinator.unresolvedAttemptMode(
                            TaskRatchetPurpose.BlockedTargetOverride,
                            targetKey,
                        ),
                    onConfirmPayment = ::confirmPayment,
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

    private fun openBacklog() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(
                    WarvisDestinationContract.EXTRA_DESTINATION,
                    WarvisDestinationContract.BACKLOG,
                )
                .putExtra(WarvisDestinationContract.EXTRA_SAFE_EXIT_TO_HOME, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    private fun openTaskRatchetSetup() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(
                    WarvisDestinationContract.EXTRA_DESTINATION,
                    WarvisDestinationContract.TASK_RATCHET_SETUP,
                )
                .putExtra(WarvisDestinationContract.EXTRA_SAFE_EXIT_TO_HOME, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    private fun confirmPayment() {
        if (confirmationInProgress) return
        val key = targetKey ?: return
        if (
            DoomShieldGracePeriod.isActive(this, key) &&
            !taskRatchetPaymentCoordinator.hasUnresolvedAttempt(
                TaskRatchetPurpose.BlockedTargetOverride,
                key,
            )
        ) {
            reopenOriginalTarget()
            return
        }
        confirmationInProgress = true
        paymentStatus = PaymentStatus.InProgress
        lifecycleScope.launch {
            when (
                val result = taskRatchetPaymentCoordinator.execute(
                    purpose = TaskRatchetPurpose.BlockedTargetOverride,
                    targetKey = key,
                    note = taskRatchetNote(
                        TaskRatchetPurpose.BlockedTargetOverride,
                        key,
                    ),
                    grantBenefit = {
                        when (DoomShieldGracePeriod.startIfInactive(this@DoomShieldInterventionActivity, key)) {
                            DoomShieldGracePeriodStartResult.AlreadyActive,
                            DoomShieldGracePeriodStartResult.Persisted -> true
                            DoomShieldGracePeriodStartResult.PersistenceFailed -> false
                        }
                    },
                )
            ) {
                is TaskRatchetPaymentResult.Granted -> {
                    paymentStatus = PaymentStatus.Success(result.dryRun)
                    reopenOriginalTarget()
                }
                is TaskRatchetPaymentResult.BenefitPending -> {
                    paymentStatus = PaymentStatus.Error(
                        "TaskRatchet confirmed this attempt, but WARVIS could not finish the " +
                            "15-minute entitlement. Retry to recover without a new charge.",
                    )
                }
                TaskRatchetPaymentResult.AlreadyInProgress -> {
                    paymentStatus = PaymentStatus.Error(
                        "This TaskRatchet request is already being completed. Wait for it to finish.",
                    )
                }
                is TaskRatchetPaymentResult.Failure -> {
                    paymentStatus = PaymentStatus.Error(result.kind.userMessage())
                }
            }
            if (paymentStatus !is PaymentStatus.Success) {
                confirmationInProgress = false
            }
        }
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
        const val EXTRA_CURFEW_LABEL = "com.warvis.android.doom.EXTRA_CURFEW_LABEL"
        const val EXTRA_INSTAGRAM_QUARANTINE_UNTIL =
            "com.warvis.android.doom.EXTRA_INSTAGRAM_QUARANTINE_UNTIL"
    }

    internal sealed interface PaymentStatus {
        data object Idle : PaymentStatus
        data object InProgress : PaymentStatus
        data class Success(val dryRun: Boolean) : PaymentStatus
        data class Error(val message: String) : PaymentStatus
    }
}

@Composable
private fun DoomShieldInterventionScreen(
    targetLabel: String,
    sourceLabel: String,
    suggestionTitle: String?,
    suggestionBucket: String?,
    curfewLabel: String?,
    instagramQuarantineUntilEpochMillis: Long?,
    onOpenKindle: () -> Unit,
    onOpenBacklog: () -> Unit,
    onOpenTaskRatchetSetup: () -> Unit,
    onGoHome: () -> Unit,
    taskRatchetConfiguration: TaskRatchetConfiguration,
    paymentStatus: DoomShieldInterventionActivity.PaymentStatus,
    hasConfirmedRecovery: Boolean,
    unresolvedAttemptMode: TaskRatchetMode?,
    onConfirmPayment: () -> Unit,
) {
    var coolingOffStartedAtMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val remainingMillis = coolingOffStartedAtMillis?.let { startedAt ->
        max(0L, DoomShieldPolicy.PAID_OVERRIDE_COOLING_OFF_MILLIS - (nowMillis - startedAt))
    } ?: DoomShieldPolicy.PAID_OVERRIDE_COOLING_OFF_MILLIS
    val remainingSeconds = (remainingMillis + 999L) / 1_000L
    val canConfirm = coolingOffStartedAtMillis != null && remainingMillis == 0L
    val displayMode = unresolvedAttemptMode ?: taskRatchetConfiguration.mode

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
    LaunchedEffect(instagramQuarantineUntilEpochMillis) {
        val deadline = instagramQuarantineUntilEpochMillis ?: return@LaunchedEffect
        while (System.currentTimeMillis() < deadline) {
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
                text = if (instagramQuarantineUntilEpochMillis != null) {
                    "Instagram quarantine"
                } else {
                    "Doom Shield caught $targetLabel"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (instagramQuarantineUntilEpochMillis != null) {
                Text(
                    text = "Your paid gaming allowance was used. Instagram is blocked in both " +
                        "the app and supported browsers until quarantine ends.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            } else if (curfewLabel != null) {
                Text(
                    text = "🌙 It's within your $curfewLabel night curfew — time to sleep, not scroll.",
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
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenBacklog) {
                Text("Open Backlog in WARVIS")
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (instagramQuarantineUntilEpochMillis != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = if (nowMillis >= instagramQuarantineUntilEpochMillis) {
                            "This block interval ended. Return to Instagram; WARVIS will " +
                                "reconcile whether the paid session resumes or has ended."
                        } else {
                            "Quarantine remaining: ${
                                ((instagramQuarantineUntilEpochMillis - nowMillis)
                                    .coerceAtLeast(0L) + 999L) / 1_000L
                            } seconds. Generic overrides cannot bypass quarantine."
                        },
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (coolingOffStartedAtMillis == null) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        nowMillis = System.currentTimeMillis()
                        coolingOffStartedAtMillis = nowMillis
                    },
                ) {
                    Text(
                        if (displayMode == TaskRatchetMode.Live) {
                            "Start $TASK_RATCHET_AMOUNT_LABEL override"
                        } else {
                            "Start TaskRatchet dry-run override"
                        },
                    )
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
                            text = if (
                                displayMode == TaskRatchetMode.Live
                            ) {
                                "Live TaskRatchet override"
                            } else {
                                "TaskRatchet dry-run override"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Amount: $TASK_RATCHET_AMOUNT_LABEL")
                        Text(
                            "Unlock: ${DoomShieldPolicy.PAID_OVERRIDE_DURATION_MINUTES} " +
                                "minutes for $targetLabel only",
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (
                                displayMode == TaskRatchetMode.Live
                            ) {
                                "The charge is captured immediately after TaskRatchet confirms it."
                            } else {
                                "No money will be charged in dry-run mode."
                            },
                            fontWeight = FontWeight.Bold,
                        )
                        if (hasConfirmedRecovery) {
                            Text(
                                "A confirmed TaskRatchet attempt is ready for local recovery. " +
                                    "Retrying will not create a new charge.",
                                fontWeight = FontWeight.Bold,
                            )
                        } else if (unresolvedAttemptMode != null) {
                            Text(
                                "This retry reuses the original request ID and " +
                                    "${if (displayMode == TaskRatchetMode.Live) "live" else "dry-run"} mode.",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (!taskRatchetConfiguration.canCharge && !hasConfirmedRecovery) {
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
                            enabled = canConfirm &&
                                paymentStatus !is DoomShieldInterventionActivity.PaymentStatus.InProgress &&
                                (taskRatchetConfiguration.canCharge || hasConfirmedRecovery),
                            onClick = onConfirmPayment,
                        ) {
                            Text(
                                if (
                                    paymentStatus is
                                    DoomShieldInterventionActivity.PaymentStatus.InProgress
                                ) {
                                    "Confirming with TaskRatchet…"
                                } else {
                                    "Confirm ${if (displayMode == TaskRatchetMode.Live) "live charge" else "dry run"} — unlock ${DoomShieldPolicy.PAID_OVERRIDE_DURATION_MINUTES} minutes"
                                },
                            )
                        }
                        val error = (
                            paymentStatus as?
                                DoomShieldInterventionActivity.PaymentStatus.Error
                            )?.message
                        error?.let {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
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

internal object NightCurfewLabelPolicy {
    private val intervalPattern = Regex("""(\d{2}):(\d{2})-(\d{2}):(\d{2})""")

    fun isBoundedIntervalLabel(label: String): Boolean {
        if (label.length != 11) return false
        val match = intervalPattern.matchEntire(label) ?: return false
        val (startHour, startMinute, endHour, endMinute) =
            match.destructured.toList().map(String::toInt)
        return startHour in 0..23 &&
            endHour in 0..23 &&
            startMinute in 0..59 &&
            endMinute in 0..59
    }
}
