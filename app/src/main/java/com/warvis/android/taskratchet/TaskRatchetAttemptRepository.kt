package com.warvis.android.taskratchet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal enum class TaskRatchetPurpose {
    BlockedTargetOverride,
    DoomShieldDisable,
    InstagramGamingSession,
}

internal enum class TaskRatchetAttemptStatus {
    Pending,
    RemoteConfirmed,
}

internal data class TaskRatchetAttempt(
    val requestId: String,
    val purpose: TaskRatchetPurpose,
    val targetKey: String?,
    val dryRun: Boolean,
    val accountUserId: String,
    val credentialGenerationId: String,
    val note: String,
    val status: TaskRatchetAttemptStatus,
    val remoteChargeId: String? = null,
    val benefitBinding: String? = null,
)

internal data class TaskRatchetAttemptState(
    val attempts: List<TaskRatchetAttempt> = emptyList(),
) {
    fun unresolved(
        purpose: TaskRatchetPurpose,
        targetKey: String?,
    ): TaskRatchetAttempt? =
        attempts.firstOrNull { it.purpose == purpose && it.targetKey == targetKey }

    fun withAttempt(attempt: TaskRatchetAttempt): TaskRatchetAttemptState =
        copy(
            attempts = attempts
                .filterNot {
                    it.purpose == attempt.purpose && it.targetKey == attempt.targetKey
                } + attempt,
        )

    fun getOrCreate(
        purpose: TaskRatchetPurpose,
        targetKey: String?,
        dryRun: Boolean,
        accountUserId: String,
        credentialGenerationId: String,
        note: String,
        benefitBinding: String?,
        newRequestId: () -> String,
    ): Pair<TaskRatchetAttemptState, TaskRatchetAttempt> {
        unresolved(purpose, targetKey)?.let { return this to it }
        val attempt = TaskRatchetAttempt(
            requestId = newRequestId(),
            purpose = purpose,
            targetKey = targetKey,
            dryRun = dryRun,
            accountUserId = accountUserId,
            credentialGenerationId = credentialGenerationId,
            note = note.take(TaskRatchetAttemptRepository.MAX_NOTE_LENGTH),
            status = TaskRatchetAttemptStatus.Pending,
            benefitBinding = benefitBinding?.take(TaskRatchetAttemptRepository.MAX_BINDING_LENGTH),
        )
        return withAttempt(attempt) to attempt
    }

    fun hasPendingLiveAttempt(): Boolean =
        attempts.any {
            !it.dryRun && it.status == TaskRatchetAttemptStatus.Pending
        }

    fun withoutPendingDryRunAttempts(): TaskRatchetAttemptState =
        copy(
            attempts = attempts.filterNot {
                it.dryRun && it.status == TaskRatchetAttemptStatus.Pending
            },
        )

    fun confirm(
        purpose: TaskRatchetPurpose,
        targetKey: String?,
        requestId: String,
        chargeId: String,
    ): TaskRatchetAttemptState {
        val existing = unresolved(purpose, targetKey)
        if (existing == null || existing.requestId != requestId) return this
        return withAttempt(
            existing.copy(
                status = TaskRatchetAttemptStatus.RemoteConfirmed,
                remoteChargeId = chargeId,
            ),
        )
    }

    fun complete(
        purpose: TaskRatchetPurpose,
        targetKey: String?,
        requestId: String,
    ): TaskRatchetAttemptState =
        copy(
            attempts = attempts.filterNot {
                it.purpose == purpose &&
                    it.targetKey == targetKey &&
                    it.requestId == requestId
            },
        )
}

internal sealed interface TaskRatchetAttemptWriteResult {
    data class Success(val attempt: TaskRatchetAttempt) : TaskRatchetAttemptWriteResult
    data object Failure : TaskRatchetAttemptWriteResult
}

internal object TaskRatchetProcessLock {
    val monitor = Any()
}

internal object TaskRatchetActiveSubmissionRegistry {
    private val requestIds = mutableSetOf<String>()

    fun tryAcquire(requestId: String): TaskRatchetExecutionLease? {
        check(Thread.holdsLock(TaskRatchetProcessLock.monitor))
        return if (requestIds.add(requestId)) {
            TaskRatchetExecutionLease(requestId)
        } else {
            null
        }
    }

    internal fun release(requestId: String) {
        check(Thread.holdsLock(TaskRatchetProcessLock.monitor))
        requestIds.remove(requestId)
    }

    fun hasActiveSubmission(): Boolean {
        check(Thread.holdsLock(TaskRatchetProcessLock.monitor))
        return requestIds.isNotEmpty()
    }
}

internal class TaskRatchetExecutionLease internal constructor(
    private val requestId: String,
) {
    private var released = false

    fun release() = synchronized(TaskRatchetProcessLock.monitor) {
        if (!released) {
            released = true
            TaskRatchetActiveSubmissionRegistry.release(requestId)
        }
    }
}

internal object TaskRatchetSubmissionPolicy {
    fun canSubmit(
        attempt: TaskRatchetAttempt,
        configuration: TaskRatchetConfiguration,
    ): Boolean {
        if (attempt.status != TaskRatchetAttemptStatus.Pending) return false
        val account = configuration.account ?: return false
        if (!configuration.canCharge) return false
        if (account.userId != attempt.accountUserId) return false
        if (configuration.credentialGenerationId != attempt.credentialGenerationId) return false
        return attempt.dryRun || configuration.mode == TaskRatchetMode.Live
    }
}

internal object TaskRatchetCredentialMutationPolicy {
    fun evaluate(state: TaskRatchetAttemptState): TaskRatchetConfigurationMutationResult =
        if (state.hasPendingLiveAttempt()) {
            TaskRatchetConfigurationMutationResult.PendingLiveAttempt
        } else {
            TaskRatchetConfigurationMutationResult.Success
        }
}

internal class TaskRatchetAttemptRepository(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getOrCreate(
        purpose: TaskRatchetPurpose,
        targetKey: String?,
        dryRun: Boolean,
        accountUserId: String,
        credentialGenerationId: String,
        note: String,
        benefitBinding: String? = null,
    ): TaskRatchetAttemptWriteResult = synchronized(TaskRatchetProcessLock.monitor) {
        if (!isValidBenefitBinding(purpose, benefitBinding)) {
            return TaskRatchetAttemptWriteResult.Failure
        }
        val current = readState() ?: return TaskRatchetAttemptWriteResult.Failure
        val (updated, attempt) = current.getOrCreate(
            purpose = purpose,
            targetKey = targetKey,
            dryRun = dryRun,
            accountUserId = accountUserId,
            credentialGenerationId = credentialGenerationId,
            note = note.take(MAX_NOTE_LENGTH),
            benefitBinding = benefitBinding,
            newRequestId = { UUID.randomUUID().toString() },
        )
        if (updated === current) {
            TaskRatchetAttemptWriteResult.Success(attempt)
        } else if (persist(updated)) {
            TaskRatchetAttemptWriteResult.Success(attempt)
        } else {
            TaskRatchetAttemptWriteResult.Failure
        }
    }

    fun markRemoteConfirmed(attempt: TaskRatchetAttempt, chargeId: String): Boolean =
        synchronized(TaskRatchetProcessLock.monitor) {
            val current = readState() ?: return false
            val updated = current.confirm(
                purpose = attempt.purpose,
                targetKey = attempt.targetKey,
                requestId = attempt.requestId,
                chargeId = chargeId,
            )
            val confirmed = updated.unresolved(attempt.purpose, attempt.targetKey)
            if (
                confirmed?.requestId != attempt.requestId ||
                confirmed.status != TaskRatchetAttemptStatus.RemoteConfirmed
            ) {
                return false
            }
            persist(updated)
        }

    fun complete(attempt: TaskRatchetAttempt): Boolean =
        synchronized(TaskRatchetProcessLock.monitor) {
            val current = readState() ?: return false
            persist(
                current.complete(
                    purpose = attempt.purpose,
                    targetKey = attempt.targetKey,
                    requestId = attempt.requestId,
                ),
            )
        }

    fun unresolved(
        purpose: TaskRatchetPurpose,
        targetKey: String?,
    ): TaskRatchetAttempt? = synchronized(TaskRatchetProcessLock.monitor) {
        readState()?.unresolved(purpose, targetKey)
    }

    fun credentialMutationResult(): TaskRatchetConfigurationMutationResult =
        synchronized(TaskRatchetProcessLock.monitor) {
            if (TaskRatchetActiveSubmissionRegistry.hasActiveSubmission()) {
                return TaskRatchetConfigurationMutationResult.AttemptInProgress
            }
            val current = readState()
                ?: return TaskRatchetConfigurationMutationResult.Failure
            TaskRatchetCredentialMutationPolicy.evaluate(current)
        }

    fun discardPendingDryRunAttempts(): Boolean =
        synchronized(TaskRatchetProcessLock.monitor) {
            val current = readState() ?: return false
            val updated = current.withoutPendingDryRunAttempts()
            updated == current || persist(updated)
        }

    private fun readState(): TaskRatchetAttemptState? {
        return try {
            val raw =
                preferences.getString(KEY_ATTEMPTS, null) ?: return TaskRatchetAttemptState()
            val array = JSONArray(raw)
            val attempts = mutableListOf<TaskRatchetAttempt>()
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                val schemaVersion = json.optInt("schemaVersion", 1)
                if (schemaVersion !in 1..ATTEMPT_SCHEMA_VERSION) return null
                val purpose = TaskRatchetPurpose.valueOf(json.getString("purpose"))
                val benefitBinding = json.optionalString("benefitBinding")
                if (!isValidBenefitBinding(purpose, benefitBinding)) return null
                attempts += TaskRatchetAttempt(
                    requestId = json.getString("requestId"),
                    purpose = purpose,
                    targetKey = json.optionalString("targetKey"),
                    dryRun = json.getBoolean("dryRun"),
                    accountUserId = json.getString("accountUserId"),
                    credentialGenerationId =
                        json.getString("credentialGenerationId"),
                    note = json.getString("note"),
                    status = TaskRatchetAttemptStatus.valueOf(json.getString("status")),
                    remoteChargeId = json.optionalString("remoteChargeId"),
                    benefitBinding = benefitBinding,
                )
            }
            TaskRatchetAttemptState(attempts)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun isValidBenefitBinding(
        purpose: TaskRatchetPurpose,
        benefitBinding: String?,
    ): Boolean =
        (benefitBinding == null ||
            benefitBinding.length <= TaskRatchetAttemptRepository.MAX_BINDING_LENGTH) &&
            (purpose != TaskRatchetPurpose.InstagramGamingSession ||
                !benefitBinding.isNullOrBlank())

    private fun JSONObject.optionalString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun persist(state: TaskRatchetAttemptState): Boolean {
        val array = JSONArray()
        state.attempts.forEach { attempt ->
            array.put(
                JSONObject()
                    .put("requestId", attempt.requestId)
                    .put("purpose", attempt.purpose.name)
                    .put("targetKey", attempt.targetKey)
                    .put("dryRun", attempt.dryRun)
                    .put("accountUserId", attempt.accountUserId)
                    .put("credentialGenerationId", attempt.credentialGenerationId)
                    .put("note", attempt.note)
                    .put("status", attempt.status.name)
                    .put("remoteChargeId", attempt.remoteChargeId)
                    .put("benefitBinding", attempt.benefitBinding)
                    .put("schemaVersion", ATTEMPT_SCHEMA_VERSION),
            )
        }
        return preferences.edit().putString(KEY_ATTEMPTS, array.toString()).commit()
    }

    internal companion object {
        const val PREFERENCES_NAME = "task_ratchet_attempts"
        const val KEY_ATTEMPTS = "unresolved_attempts"
        const val MAX_NOTE_LENGTH = 160
        const val MAX_BINDING_LENGTH = 512
        const val ATTEMPT_SCHEMA_VERSION = 2
    }
}
