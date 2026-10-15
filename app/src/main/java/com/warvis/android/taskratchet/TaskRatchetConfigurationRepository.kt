package com.warvis.android.taskratchet

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal class TaskRatchetConfigurationRepository(
    context: Context,
    private val credentialStore: TaskRatchetCredentialStore =
        TaskRatchetCredentialStore(context),
    private val attemptRepository: TaskRatchetAttemptRepository =
        TaskRatchetAttemptRepository(context),
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(TaskRatchetConfiguration())

    val state: StateFlow<TaskRatchetConfiguration> = mutableState.asStateFlow()

    init {
        synchronized(TaskRatchetProcessLock.monitor) {
            mutableState.value = readConfiguration()
            if (
                mutableState.value.connected &&
                credentialStore.load() !is TaskRatchetCredentialState.Available
            ) {
                preferences.edit().clear().commit()
                mutableState.value = TaskRatchetConfiguration()
            }
        }
    }

    fun saveValidatedToken(
        token: String,
        account: TaskRatchetAccount,
    ): TaskRatchetConfigurationMutationResult =
        synchronized(TaskRatchetProcessLock.monitor) {
            val mutationResult = attemptRepository.credentialMutationResult()
            if (mutationResult != TaskRatchetConfigurationMutationResult.Success) {
                return mutationResult
            }
            if (!attemptRepository.discardPendingDryRunAttempts()) {
                return TaskRatchetConfigurationMutationResult.Failure
            }
            if (!credentialStore.save(token)) {
                return TaskRatchetConfigurationMutationResult.Failure
            }
            val credentialGenerationId = UUID.randomUUID().toString()
            val persisted = preferences.edit()
                .putBoolean(KEY_CONNECTED, true)
                .putString(KEY_ACCOUNT_USER_ID, account.userId)
                .putString(KEY_CREDENTIAL_GENERATION_ID, credentialGenerationId)
                .putBoolean(KEY_HAS_PAYMENT_METHOD, account.hasPaymentMethod)
                .putInt(KEY_MONTHLY_LIMIT_CENTS, account.monthlyChargeLimitCents ?: NO_LIMIT_VALUE)
                .putBoolean(KEY_LIVE_ENABLED, false)
                .commit()
            if (!persisted) {
                credentialStore.clear()
                return TaskRatchetConfigurationMutationResult.Failure
            }
            mutableState.value = readConfiguration()
            TaskRatchetConfigurationMutationResult.Success
        }

    fun enableLive(confirmationAccepted: Boolean): Boolean =
        synchronized(TaskRatchetProcessLock.monitor) {
            val current = readConfiguration()
            if (
                !TaskRatchetConfigurationPolicy.canEnableLive(
                    connected = current.connected,
                    hasPaymentMethod = current.account?.hasPaymentMethod == true,
                    confirmationAccepted = confirmationAccepted,
                )
            ) {
                return false
            }
            val persisted = preferences.edit().putBoolean(KEY_LIVE_ENABLED, true).commit()
            if (persisted) mutableState.value = readConfiguration()
            persisted
        }

    fun useDryRun(): TaskRatchetConfigurationMutationResult =
        synchronized(TaskRatchetProcessLock.monitor) {
            val mutationResult = attemptRepository.credentialMutationResult()
            if (mutationResult != TaskRatchetConfigurationMutationResult.Success) {
                return mutationResult
            }
            val persisted = preferences.edit().putBoolean(KEY_LIVE_ENABLED, false).commit()
            if (!persisted) return TaskRatchetConfigurationMutationResult.Failure
            mutableState.value = readConfiguration()
            TaskRatchetConfigurationMutationResult.Success
        }

    fun disconnect(): TaskRatchetConfigurationMutationResult =
        synchronized(TaskRatchetProcessLock.monitor) {
            val mutationResult = attemptRepository.credentialMutationResult()
            if (mutationResult != TaskRatchetConfigurationMutationResult.Success) {
                return mutationResult
            }
            if (!attemptRepository.discardPendingDryRunAttempts()) {
                return TaskRatchetConfigurationMutationResult.Failure
            }
            val credentialCleared = credentialStore.clear()
            val configurationCleared = preferences.edit().clear().commit()
            mutableState.value = TaskRatchetConfiguration()
            if (credentialCleared && configurationCleared) {
                TaskRatchetConfigurationMutationResult.Success
            } else {
                TaskRatchetConfigurationMutationResult.Failure
            }
        }

    fun credential(): TaskRatchetCredentialState =
        synchronized(TaskRatchetProcessLock.monitor) {
            val state = credentialStore.load()
            if (
                state is TaskRatchetCredentialState.Unusable ||
                state is TaskRatchetCredentialState.Missing
            ) {
                preferences.edit().clear().commit()
                mutableState.value = TaskRatchetConfiguration()
            }
            state
        }

    fun snapshot(): TaskRatchetConfiguration =
        synchronized(TaskRatchetProcessLock.monitor) { readConfiguration() }

    fun credentialMutationResult(): TaskRatchetConfigurationMutationResult =
        synchronized(TaskRatchetProcessLock.monitor) {
            attemptRepository.credentialMutationResult()
        }

    private fun readConfiguration(): TaskRatchetConfiguration {
        return try {
            if (!preferences.getBoolean(KEY_CONNECTED, false)) {
                TaskRatchetConfiguration()
            } else {
                val monthlyLimit =
                    preferences.getInt(KEY_MONTHLY_LIMIT_CENTS, NO_LIMIT_VALUE)
                        .takeUnless { it == NO_LIMIT_VALUE }
                val account = TaskRatchetAccount(
                    userId = preferences.getString(KEY_ACCOUNT_USER_ID, null)
                        ?.takeIf { it.isNotBlank() }
                        ?: return TaskRatchetConfiguration(),
                    hasPaymentMethod =
                        preferences.getBoolean(KEY_HAS_PAYMENT_METHOD, false),
                    monthlyChargeLimitCents = monthlyLimit,
                )
                TaskRatchetConfiguration(
                    connected = true,
                    account = account,
                    credentialGenerationId =
                        preferences.getString(KEY_CREDENTIAL_GENERATION_ID, null)
                            ?.takeIf { it.isNotBlank() }
                            ?: return TaskRatchetConfiguration(),
                    mode = if (preferences.getBoolean(KEY_LIVE_ENABLED, false)) {
                        TaskRatchetMode.Live
                    } else {
                        TaskRatchetMode.DryRun
                    },
                )
            }
        } catch (_: RuntimeException) {
            preferences.edit().clear().commit()
            TaskRatchetConfiguration()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "task_ratchet_configuration"
        const val KEY_CONNECTED = "connected"
        const val KEY_ACCOUNT_USER_ID = "account_user_id"
        const val KEY_CREDENTIAL_GENERATION_ID = "credential_generation_id"
        const val KEY_HAS_PAYMENT_METHOD = "has_payment_method"
        const val KEY_MONTHLY_LIMIT_CENTS = "monthly_limit_cents"
        const val KEY_LIVE_ENABLED = "live_enabled"
        const val NO_LIMIT_VALUE = -1
    }
}
