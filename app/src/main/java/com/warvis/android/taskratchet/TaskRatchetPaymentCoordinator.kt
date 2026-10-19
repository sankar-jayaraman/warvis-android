package com.warvis.android.taskratchet

import android.content.Context

internal sealed interface TaskRatchetPaymentResult {
    data class Granted(val dryRun: Boolean, val recovered: Boolean) : TaskRatchetPaymentResult
    data class Failure(val kind: TaskRatchetFailureKind) : TaskRatchetPaymentResult
    data class BenefitPending(val dryRun: Boolean) : TaskRatchetPaymentResult
    data object AlreadyInProgress : TaskRatchetPaymentResult
}

internal class TaskRatchetPaymentCoordinator(
    context: Context,
    private val configurationRepository: TaskRatchetConfigurationRepository =
        TaskRatchetConfigurationRepository(context),
    private val attemptRepository: TaskRatchetAttemptRepository =
        TaskRatchetAttemptRepository(context),
    private val client: TaskRatchetClient = TaskRatchetClient(),
) {
    fun hasUnresolvedAttempt(
        purpose: TaskRatchetPurpose,
        targetKey: String?,
    ): Boolean = attemptRepository.unresolved(purpose, targetKey) != null

    fun hasConfirmedAttempt(
        purpose: TaskRatchetPurpose,
        targetKey: String?,
    ): Boolean =
        attemptRepository.unresolved(purpose, targetKey)?.status ==
            TaskRatchetAttemptStatus.RemoteConfirmed

    fun unresolvedAttemptMode(
        purpose: TaskRatchetPurpose,
        targetKey: String?,
    ): TaskRatchetMode? {
        val attempt = attemptRepository.unresolved(purpose, targetKey) ?: return null
        return if (attempt.dryRun) TaskRatchetMode.DryRun else TaskRatchetMode.Live
    }

    fun unresolvedAttempt(
        purpose: TaskRatchetPurpose,
        targetKey: String?,
    ): TaskRatchetAttempt? = attemptRepository.unresolved(purpose, targetKey)

    suspend fun execute(
        purpose: TaskRatchetPurpose,
        targetKey: String?,
        note: String,
        benefitBinding: String? = null,
        grantBenefit: (TaskRatchetAttempt) -> Boolean,
    ): TaskRatchetPaymentResult {
        val prepared = synchronized(TaskRatchetProcessLock.monitor) {
            val existing = attemptRepository.unresolved(purpose, targetKey)
            if (existing?.status == TaskRatchetAttemptStatus.RemoteConfirmed) {
                val lease = TaskRatchetActiveSubmissionRegistry.tryAcquire(existing.requestId)
                    ?: return TaskRatchetPaymentResult.AlreadyInProgress
                PreparedExecution(
                    credential = null,
                    attempt = existing,
                    recovered = true,
                    lease = lease,
                )
            } else {
                val configuration = configurationRepository.snapshot()
                if (!configuration.canCharge) {
                    return TaskRatchetPaymentResult.Failure(
                        TaskRatchetFailureKind.NotConfigured,
                    )
                }
                val credential = configurationRepository.credential()
                    as? TaskRatchetCredentialState.Available
                    ?: return TaskRatchetPaymentResult.Failure(
                        TaskRatchetFailureKind.NotConfigured,
                    )
                val attempt = when (
                    val result = attemptRepository.getOrCreate(
                        purpose = purpose,
                        targetKey = targetKey,
                        dryRun = configuration.mode == TaskRatchetMode.DryRun,
                        accountUserId = requireNotNull(configuration.account).userId,
                        credentialGenerationId =
                            requireNotNull(configuration.credentialGenerationId),
                        note = note,
                        benefitBinding = benefitBinding,
                    )
                ) {
                    TaskRatchetAttemptWriteResult.Failure ->
                        return TaskRatchetPaymentResult.Failure(
                            TaskRatchetFailureKind.LocalPersistence,
                        )
                    is TaskRatchetAttemptWriteResult.Success -> result.attempt
                }
                if (!TaskRatchetSubmissionPolicy.canSubmit(attempt, configuration)) {
                    return TaskRatchetPaymentResult.Failure(
                        TaskRatchetFailureKind.CredentialOrModeChanged,
                    )
                }
                val lease = TaskRatchetActiveSubmissionRegistry.tryAcquire(attempt.requestId)
                    ?: return TaskRatchetPaymentResult.AlreadyInProgress
                PreparedExecution(
                    credential = credential,
                    attempt = attempt,
                    recovered = false,
                    lease = lease,
                )
            }
        }

        try {
            if (!prepared.recovered) {
                when (
                    val charge = client.charge(
                        token = requireNotNull(prepared.credential).token,
                        requestId = prepared.attempt.requestId,
                        note = prepared.attempt.note,
                        dryRun = prepared.attempt.dryRun,
                    )
                ) {
                    is TaskRatchetChargeResult.Failure ->
                        return TaskRatchetPaymentResult.Failure(charge.kind)
                    is TaskRatchetChargeResult.Success -> {
                        if (
                            !attemptRepository.markRemoteConfirmed(
                                prepared.attempt,
                                charge.chargeId,
                            )
                        ) {
                            return TaskRatchetPaymentResult.Failure(
                                TaskRatchetFailureKind.LocalPersistence,
                            )
                        }
                    }
                }
            }

            if (!grantBenefit(prepared.attempt)) {
                return TaskRatchetPaymentResult.BenefitPending(prepared.attempt.dryRun)
            }
            if (!attemptRepository.complete(prepared.attempt)) {
                return TaskRatchetPaymentResult.BenefitPending(prepared.attempt.dryRun)
            }
            return TaskRatchetPaymentResult.Granted(
                dryRun = prepared.attempt.dryRun,
                recovered = prepared.recovered,
            )
        } finally {
            prepared.lease.release()
        }
    }

    private data class PreparedExecution(
        val credential: TaskRatchetCredentialState.Available?,
        val attempt: TaskRatchetAttempt,
        val recovered: Boolean,
        val lease: TaskRatchetExecutionLease,
    )
}

internal fun taskRatchetNote(
    purpose: TaskRatchetPurpose,
    targetKey: String?,
): String = when (purpose) {
    TaskRatchetPurpose.DoomShieldDisable -> "WARVIS Doom Shield disable"
    TaskRatchetPurpose.InstagramGamingSession -> "WARVIS Instagram gaming session"
    TaskRatchetPurpose.BlockedTargetOverride -> {
        val safeTarget = targetKey
            ?.removePrefix("domain:")
            ?.removePrefix("app:")
            ?.take(100)
            .orEmpty()
        "WARVIS blocked-app override: $safeTarget".take(160)
    }
}
