package com.warvis.android.taskratchet

internal const val TASK_RATCHET_CHARGE_CENTS = 400
internal const val TASK_RATCHET_AMOUNT_LABEL =
    "US$4.00 (roughly 25-28 DKK; bank conversion and FX fees may vary)"

internal enum class TaskRatchetMode {
    DryRun,
    Live,
}

internal data class TaskRatchetAccount(
    val userId: String,
    val hasPaymentMethod: Boolean,
    val monthlyChargeLimitCents: Int?,
)

internal data class TaskRatchetConfiguration(
    val connected: Boolean = false,
    val account: TaskRatchetAccount? = null,
    val credentialGenerationId: String? = null,
    val mode: TaskRatchetMode = TaskRatchetMode.DryRun,
) {
    val canCharge: Boolean
        get() = connected && account?.hasPaymentMethod == true
}

internal object TaskRatchetConfigurationPolicy {
    private const val MAX_TOKEN_LENGTH = 4_096

    fun isTokenCandidateAllowed(token: String): Boolean =
        token.isNotBlank() && token.length <= MAX_TOKEN_LENGTH

    fun canEnableLive(
        connected: Boolean,
        hasPaymentMethod: Boolean,
        confirmationAccepted: Boolean,
    ): Boolean = connected && hasPaymentMethod && confirmationAccepted

    fun actionLabel(mode: TaskRatchetMode): String = when (mode) {
        TaskRatchetMode.DryRun -> "TaskRatchet dry run"
        TaskRatchetMode.Live -> "TaskRatchet live charge"
    }
}

internal enum class TaskRatchetFailureKind {
    NoPaymentMethodOrBadRequest,
    Authentication,
    PaymentFailed,
    ChargeInProgress,
    InvalidOrMonthlyLimit,
    RateLimited,
    Network,
    MalformedResponse,
    LocalPersistence,
    NotConfigured,
    CredentialOrModeChanged,
}

internal sealed interface TaskRatchetConfigurationMutationResult {
    data object Success : TaskRatchetConfigurationMutationResult
    data object PendingLiveAttempt : TaskRatchetConfigurationMutationResult
    data object AttemptInProgress : TaskRatchetConfigurationMutationResult
    data object Failure : TaskRatchetConfigurationMutationResult
}

internal sealed interface TaskRatchetAccountResult {
    data class Success(val account: TaskRatchetAccount) : TaskRatchetAccountResult
    data class Failure(val kind: TaskRatchetFailureKind) : TaskRatchetAccountResult
}

internal sealed interface TaskRatchetChargeResult {
    data class Success(
        val chargeId: String,
        val dryRun: Boolean,
    ) : TaskRatchetChargeResult

    data class Failure(val kind: TaskRatchetFailureKind) : TaskRatchetChargeResult
}

internal object TaskRatchetResultMapper {
    fun account(
        statusCode: Int,
        parsedAccount: TaskRatchetAccount?,
    ): TaskRatchetAccountResult =
        if (statusCode == 200 && parsedAccount != null) {
            TaskRatchetAccountResult.Success(parsedAccount)
        } else {
            TaskRatchetAccountResult.Failure(
                failureFor(
                    statusCode = statusCode,
                    malformedSuccess = statusCode == 200 && parsedAccount == null,
                ),
            )
        }

    fun charge(
        statusCode: Int,
        chargeId: String?,
        remoteStatus: String?,
        remoteCents: Int?,
        dryRun: Boolean,
    ): TaskRatchetChargeResult =
        if (
            statusCode == 200 &&
            !chargeId.isNullOrBlank() &&
            remoteStatus == "succeeded" &&
            remoteCents == TASK_RATCHET_CHARGE_CENTS
        ) {
            TaskRatchetChargeResult.Success(chargeId, dryRun)
        } else if (statusCode == 200 && remoteStatus == "failed") {
            TaskRatchetChargeResult.Failure(TaskRatchetFailureKind.PaymentFailed)
        } else {
            TaskRatchetChargeResult.Failure(
                failureFor(
                    statusCode = statusCode,
                    malformedSuccess = statusCode == 200,
                ),
            )
        }

    fun networkFailure(): TaskRatchetFailureKind = TaskRatchetFailureKind.Network

    private fun failureFor(statusCode: Int, malformedSuccess: Boolean): TaskRatchetFailureKind {
        if (malformedSuccess) return TaskRatchetFailureKind.MalformedResponse
        return when (statusCode) {
            400 -> TaskRatchetFailureKind.NoPaymentMethodOrBadRequest
            401, 403 -> TaskRatchetFailureKind.Authentication
            402 -> TaskRatchetFailureKind.PaymentFailed
            409 -> TaskRatchetFailureKind.ChargeInProgress
            422 -> TaskRatchetFailureKind.InvalidOrMonthlyLimit
            429 -> TaskRatchetFailureKind.RateLimited
            else -> TaskRatchetFailureKind.Network
        }
    }
}

internal fun TaskRatchetFailureKind.userMessage(): String = when (this) {
    TaskRatchetFailureKind.NoPaymentMethodOrBadRequest ->
        "TaskRatchet rejected the request. Confirm a payment method in TaskRatchet."
    TaskRatchetFailureKind.Authentication ->
        "TaskRatchet authentication failed. Replace and validate the API key in setup."
    TaskRatchetFailureKind.PaymentFailed ->
        "TaskRatchet could not complete the charge. No access was granted."
    TaskRatchetFailureKind.ChargeInProgress ->
        "TaskRatchet is still processing this request. Retry to check the same attempt."
    TaskRatchetFailureKind.InvalidOrMonthlyLimit ->
        "TaskRatchet rejected the amount or monthly charge limit. No access was granted."
    TaskRatchetFailureKind.RateLimited ->
        "TaskRatchet rate-limited the request. Retry later; WARVIS will reuse the same attempt."
    TaskRatchetFailureKind.Network ->
        "WARVIS could not confirm the TaskRatchet result. Retry to check the same attempt."
    TaskRatchetFailureKind.MalformedResponse ->
        "TaskRatchet returned an unexpected response. No access was granted."
    TaskRatchetFailureKind.LocalPersistence ->
        "WARVIS could not safely save payment state. No access was granted. Retry will reuse any persisted attempt."
    TaskRatchetFailureKind.NotConfigured ->
        "Set up TaskRatchet and confirm a payment method before using paid actions."
    TaskRatchetFailureKind.CredentialOrModeChanged ->
        "This payment attempt belongs to a different TaskRatchet connection or is no longer permitted. No request was sent."
}

internal fun TaskRatchetConfigurationMutationResult.userMessage(
    successMessage: String,
): String = when (this) {
    TaskRatchetConfigurationMutationResult.Success -> successMessage
    TaskRatchetConfigurationMutationResult.PendingLiveAttempt ->
        "Resolve the pending live TaskRatchet payment before replacing the API key or disconnecting."
    TaskRatchetConfigurationMutationResult.AttemptInProgress ->
        "Wait for the current TaskRatchet request to finish before changing the connection."
    TaskRatchetConfigurationMutationResult.Failure ->
        "WARVIS could not safely update the TaskRatchet connection."
}
