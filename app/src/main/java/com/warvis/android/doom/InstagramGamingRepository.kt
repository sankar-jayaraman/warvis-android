package com.warvis.android.doom

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings

internal sealed interface InstagramGamingSaveResult {
    data object Persisted : InstagramGamingSaveResult
    data class Invalid(val message: String) : InstagramGamingSaveResult
    data object PersistenceFailed : InstagramGamingSaveResult
}

internal enum class InstagramGamingGrantResult {
    Granted,
    AlreadyGranted,
    ActiveSessionConflict,
    InvalidBinding,
    PersistenceFailed,
}

internal data class InstagramGamingStoredState(
    val config: InstagramGamingConfig = InstagramGamingConfig(),
    val session: InstagramGamingSession? = null,
    val lastGrantedTaskRatchetRequestId: String? = null,
)

internal interface InstagramGamingPersistence {
    fun read(): String?
    fun commit(raw: String): Boolean
}

internal class InstagramGamingRepository(
    private val persistence: InstagramGamingPersistence,
    private val clockProvider: () -> InstagramGamingClockReading = {
        InstagramGamingClockReading(
            epochMillis = System.currentTimeMillis(),
            elapsedRealtimeMillis = System.nanoTime() / 1_000_000L,
            bootId = null,
        )
    },
) {
    constructor(context: Context) : this(
        SharedPreferencesInstagramGamingPersistence(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
        {
            InstagramGamingClockReading(
                epochMillis = System.currentTimeMillis(),
                elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                bootId = try {
                    Settings.Global.getInt(
                        context.contentResolver,
                        Settings.Global.BOOT_COUNT,
                    ).toLong()
                } catch (_: Settings.SettingNotFoundException) {
                    null
                } catch (_: SecurityException) {
                    null
                },
            )
        },
    )

    fun status(): InstagramGamingStatus = status(clockProvider())

    fun status(clock: InstagramGamingClockReading): InstagramGamingStatus =
        synchronized(MONITOR) {
            val reconciled = reconcile(readValidatedState(), clock)
            InstagramGamingPolicy.status(
                configured = reconciled.config,
                session = reconciled.session,
                clock = clock,
            )
        }

    fun status(nowEpochMillis: Long): InstagramGamingStatus =
        synchronized(MONITOR) {
            val state = readValidatedState()
            InstagramGamingPolicy.status(
                configured = state.config,
                session = state.session,
                clock = legacyClock(nowEpochMillis),
            )
        }

    fun saveConfig(config: InstagramGamingConfig): InstagramGamingSaveResult =
        synchronized(MONITOR) {
            config.validationError?.let { return InstagramGamingSaveResult.Invalid(it) }
            val current = readValidatedState()
            if (persist(current.copy(config = config))) {
                InstagramGamingSaveResult.Persisted
            } else {
                InstagramGamingSaveResult.PersistenceFailed
            }
        }

    fun grant(
        taskRatchetRequestId: String,
        encodedPurchase: String?,
    ): InstagramGamingGrantResult =
        grant(taskRatchetRequestId, encodedPurchase, clockProvider())

    fun grant(
        taskRatchetRequestId: String,
        encodedPurchase: String?,
        nowEpochMillis: Long,
    ): InstagramGamingGrantResult =
        grant(taskRatchetRequestId, encodedPurchase, legacyClock(nowEpochMillis))

    fun grant(
        taskRatchetRequestId: String,
        encodedPurchase: String?,
        clock: InstagramGamingClockReading,
    ): InstagramGamingGrantResult = synchronized(MONITOR) {
        val purchase = InstagramGamingPurchaseSnapshot.decode(encodedPurchase)
            ?: return InstagramGamingGrantResult.InvalidBinding
        if (!isValidRequestId(taskRatchetRequestId)) {
            return InstagramGamingGrantResult.InvalidBinding
        }
        val current = reconcile(readValidatedState(), clock)
        if (current.lastGrantedTaskRatchetRequestId == taskRatchetRequestId) {
            return InstagramGamingGrantResult.AlreadyGranted
        }
        if (current.session != null) {
            return InstagramGamingGrantResult.ActiveSessionConflict
        }
        val deadlineEpoch = safeAdd(clock.epochMillis, purchase.config.sessionDurationMillis)
            ?: return InstagramGamingGrantResult.InvalidBinding
        val deadlineElapsed = safeAdd(
            clock.elapsedRealtimeMillis,
            purchase.config.sessionDurationMillis,
        )
        val updated = current.copy(
            session = InstagramGamingSession(
                purchase = purchase,
                taskRatchetRequestId = taskRatchetRequestId,
                startedAtEpochMillis = clock.epochMillis,
                deadlineEpochMillis = deadlineEpoch,
                bootId = clock.bootId,
                sessionDeadlineElapsedMillis = deadlineElapsed.takeIf { clock.bootId != null },
                lastObservedEpochMillis = clock.epochMillis,
                lastObservedSessionRemainingMillis = purchase.config.sessionDurationMillis,
            ),
            lastGrantedTaskRatchetRequestId = taskRatchetRequestId,
        )
        if (persist(updated)) {
            InstagramGamingGrantResult.Granted
        } else {
            InstagramGamingGrantResult.PersistenceFailed
        }
    }

    fun addForegroundUsage(deltaElapsedMillis: Long): InstagramGamingStatus =
        addForegroundUsage(deltaElapsedMillis, clockProvider())

    fun addForegroundUsage(
        deltaElapsedMillis: Long,
        nowEpochMillis: Long,
    ): InstagramGamingStatus =
        addForegroundUsage(deltaElapsedMillis, legacyClock(nowEpochMillis))

    fun addForegroundUsage(
        deltaElapsedMillis: Long,
        clock: InstagramGamingClockReading,
    ): InstagramGamingStatus = synchronized(MONITOR) {
        val current = reconcile(readValidatedState(), clock)
        val session = current.session
        val currentStatus = InstagramGamingPolicy.status(current.config, session, clock)
        if (
            session == null ||
            deltaElapsedMillis <= 0L ||
            currentStatus.phase != InstagramGamingPhase.Active
        ) {
            return currentStatus
        }
        val allowance = session.purchase.config.allowanceDurationMillis
        val updatedConsumed = safeAdd(session.consumedForegroundMillis, deltaElapsedMillis)
            ?.coerceAtMost(allowance)
            ?: allowance
        val updatedSession = session.copy(
            consumedForegroundMillis = updatedConsumed,
            lastObservedEpochMillis = maxOf(
                session.lastObservedEpochMillis,
                clock.epochMillis,
            ),
            lastObservedSessionRemainingMillis = currentStatus.sessionRemainingMillis,
        )
        val updated = current.copy(session = updatedSession)
        if (!persist(updated)) {
            return InstagramGamingStatus(InstagramGamingPhase.Inactive, current.config)
        }
        InstagramGamingPolicy.status(updated.config, updated.session, clock)
    }

    fun enterQuarantine(): InstagramGamingStatus = enterQuarantine(clockProvider())

    fun enterQuarantine(nowEpochMillis: Long): InstagramGamingStatus =
        enterQuarantine(legacyClock(nowEpochMillis))

    fun enterQuarantine(clock: InstagramGamingClockReading): InstagramGamingStatus =
        synchronized(MONITOR) {
            val current = reconcile(readValidatedState(), clock)
            val session = current.session
                ?: return InstagramGamingStatus(InstagramGamingPhase.Inactive, current.config)
            val currentStatus = InstagramGamingPolicy.status(current.config, session, clock)
            if (
                currentStatus.phase != InstagramGamingPhase.Active ||
                session.consumedForegroundMillis <
                session.purchase.config.allowanceDurationMillis
            ) {
                return currentStatus
            }
            val quarantineRemaining = minOf(
                session.purchase.config.quarantineDurationMillis,
                currentStatus.sessionRemainingMillis,
            )
            val quarantineUntilEpoch = minOf(
                safeAdd(
                    maxOf(
                        clock.epochMillis,
                        session.startedAtEpochMillis,
                        session.lastObservedEpochMillis,
                    ),
                    quarantineRemaining,
                ) ?: return InstagramGamingStatus(
                    InstagramGamingPhase.Inactive,
                    current.config,
                ),
                session.deadlineEpochMillis,
            )
            val quarantineUntilElapsed = safeAdd(
                clock.elapsedRealtimeMillis,
                quarantineRemaining,
            )
            val updated = current.copy(
                session = session.copy(
                    quarantineUntilEpochMillis = quarantineUntilEpoch,
                    quarantineDeadlineElapsedMillis =
                        quarantineUntilElapsed.takeIf { clock.bootId != null },
                    lastObservedEpochMillis = maxOf(
                        session.lastObservedEpochMillis,
                        clock.epochMillis,
                    ),
                    lastObservedSessionRemainingMillis = currentStatus.sessionRemainingMillis,
                    lastObservedQuarantineRemainingMillis = quarantineRemaining,
                ).withoutActiveSegment(),
            )
            if (!persist(updated)) {
                return InstagramGamingStatus(InstagramGamingPhase.Inactive, current.config)
            }
            InstagramGamingPolicy.status(updated.config, updated.session, clock)
        }

    fun startForegroundSegment(clock: InstagramGamingClockReading): InstagramGamingStatus =
        synchronized(MONITOR) {
            val current = reconcile(readValidatedState(), clock)
            val session = current.session
            val currentStatus = InstagramGamingPolicy.status(current.config, session, clock)
            if (
                session == null ||
                currentStatus.phase != InstagramGamingPhase.Active ||
                clock.bootId == null
            ) {
                return currentStatus
            }
            val updated = current.copy(
                session = session.copy(
                    activeSegmentBootId = clock.bootId,
                    activeSegmentStartedElapsedMillis = clock.elapsedRealtimeMillis,
                    activeSegmentCheckpointElapsedMillis = clock.elapsedRealtimeMillis,
                    activeSegmentConsumedAtCheckpointMillis =
                        session.consumedForegroundMillis,
                ),
            )
            if (!persist(updated)) {
                return InstagramGamingStatus(InstagramGamingPhase.Inactive, current.config)
            }
            InstagramGamingPolicy.status(updated.config, updated.session, clock)
        }

    fun checkpointForegroundSegment(
        clock: InstagramGamingClockReading,
        endSegment: Boolean = false,
    ): InstagramGamingStatus = synchronized(MONITOR) {
        val current = reconcile(readValidatedState(), clock)
        val session = current.session
            ?: return InstagramGamingStatus(InstagramGamingPhase.Inactive, current.config)
        val currentStatus = InstagramGamingPolicy.status(current.config, session, clock)
        val checkpoint = session.activeSegmentCheckpointElapsedMillis
        val validSegment =
            currentStatus.phase == InstagramGamingPhase.Active &&
                clock.bootId != null &&
                session.activeSegmentBootId == clock.bootId &&
                checkpoint != null &&
                session.activeSegmentConsumedAtCheckpointMillis ==
                session.consumedForegroundMillis
        if (!validSegment) {
            if (session.activeSegmentBootId == null) return currentStatus
            return persistClearedSegment(current, session, clock)
        }
        val delta = (clock.elapsedRealtimeMillis - checkpoint).coerceAtLeast(0L)
        return persistSegmentUsage(current, session, currentStatus, clock, delta, endSegment)
    }

    fun recoverInterruptedForegroundSegment(
        clock: InstagramGamingClockReading,
        recoverBoundedTail: Boolean = true,
    ): InstagramGamingStatus = synchronized(MONITOR) {
        val current = reconcile(readValidatedState(), clock)
        val session = current.session
            ?: return InstagramGamingStatus(InstagramGamingPhase.Inactive, current.config)
        val currentStatus = InstagramGamingPolicy.status(current.config, session, clock)
        val checkpoint = session.activeSegmentCheckpointElapsedMillis
        val validSameBootCheckpoint =
            currentStatus.phase == InstagramGamingPhase.Active &&
                clock.bootId != null &&
                session.activeSegmentBootId == clock.bootId &&
                checkpoint != null &&
                session.activeSegmentConsumedAtCheckpointMillis ==
                session.consumedForegroundMillis
        if (!validSameBootCheckpoint) {
            return persistClearedSegment(current, session, clock)
        }
        val recoverableDelta = if (recoverBoundedTail) {
            (clock.elapsedRealtimeMillis - checkpoint)
                .coerceIn(0L, MAX_INTERRUPTED_SEGMENT_RECOVERY_MILLIS)
        } else {
            0L
        }
        return persistSegmentUsage(
            current = current,
            session = session,
            currentStatus = currentStatus,
            clock = clock,
            deltaElapsedMillis = recoverableDelta,
            endSegment = true,
        )
    }

    fun endSession(sessionId: String): Boolean = synchronized(MONITOR) {
        val current = readValidatedState()
        val session = current.session ?: return true
        if (session.purchase.sessionId != sessionId) return false
        persist(current.copy(session = null))
    }

    fun reconcile(): InstagramGamingStatus = reconcile(clockProvider())

    fun reconcile(nowEpochMillis: Long): InstagramGamingStatus =
        reconcile(legacyClock(nowEpochMillis))

    fun reconcile(clock: InstagramGamingClockReading): InstagramGamingStatus =
        synchronized(MONITOR) {
            val reconciled = reconcile(readValidatedState(), clock)
            InstagramGamingPolicy.status(
                reconciled.config,
                reconciled.session,
                clock,
            )
        }

    private fun reconcile(
        state: InstagramGamingStoredState,
        clock: InstagramGamingClockReading,
    ): InstagramGamingStoredState {
        val reconciledSession = InstagramGamingPolicy.reconciledSession(state.session, clock)
        if (reconciledSession == state.session) return state
        val updated = state.copy(session = reconciledSession)
        return if (persist(updated)) updated else state.copy(session = null)
    }

    private fun persistSegmentUsage(
        current: InstagramGamingStoredState,
        session: InstagramGamingSession,
        currentStatus: InstagramGamingStatus,
        clock: InstagramGamingClockReading,
        deltaElapsedMillis: Long,
        endSegment: Boolean,
    ): InstagramGamingStatus {
        val allowance = session.purchase.config.allowanceDurationMillis
        val updatedConsumed = safeAdd(
            session.consumedForegroundMillis,
            deltaElapsedMillis,
        )?.coerceAtMost(allowance) ?: allowance
        val updatedSession = session.copy(
            consumedForegroundMillis = updatedConsumed,
            activeSegmentBootId = session.activeSegmentBootId.takeUnless { endSegment },
            activeSegmentStartedElapsedMillis =
                session.activeSegmentStartedElapsedMillis.takeUnless { endSegment },
            activeSegmentCheckpointElapsedMillis =
                clock.elapsedRealtimeMillis.takeUnless { endSegment },
            activeSegmentConsumedAtCheckpointMillis =
                updatedConsumed.takeUnless { endSegment },
            lastObservedEpochMillis = clock.epochMillis,
            lastObservedSessionRemainingMillis = currentStatus.sessionRemainingMillis,
            lastObservedQuarantineRemainingMillis =
                currentStatus.quarantineRemainingMillis.takeIf {
                    currentStatus.phase == InstagramGamingPhase.Quarantine
                },
        )
        val updated = current.copy(session = updatedSession)
        if (!persist(updated)) {
            return InstagramGamingStatus(InstagramGamingPhase.Inactive, current.config)
        }
        return InstagramGamingPolicy.status(updated.config, updated.session, clock)
    }

    private fun persistClearedSegment(
        current: InstagramGamingStoredState,
        session: InstagramGamingSession,
        clock: InstagramGamingClockReading,
    ): InstagramGamingStatus {
        val cleared = current.copy(session = session.withoutActiveSegment())
        if (cleared != current && !persist(cleared)) {
            return InstagramGamingStatus(InstagramGamingPhase.Inactive, current.config)
        }
        return InstagramGamingPolicy.status(cleared.config, cleared.session, clock)
    }

    private fun readValidatedState(): InstagramGamingStoredState {
        val raw = try {
            persistence.read()
        } catch (_: RuntimeException) {
            return resetToDefaultState()
        } ?: return InstagramGamingStoredState()
        return InstagramGamingStateCodec.decode(raw) ?: resetToDefaultState()
    }

    private fun resetToDefaultState(): InstagramGamingStoredState {
        val fallback = InstagramGamingStoredState()
        persist(fallback)
        return fallback
    }

    private fun persist(state: InstagramGamingStoredState): Boolean =
        try {
            persistence.commit(InstagramGamingStateCodec.encode(state))
        } catch (_: RuntimeException) {
            false
        }

    private class SharedPreferencesInstagramGamingPersistence(
        private val preferences: SharedPreferences,
    ) : InstagramGamingPersistence {
        override fun read(): String? = preferences.getString(KEY_STATE, null)

        override fun commit(raw: String): Boolean =
            preferences.edit().putString(KEY_STATE, raw).commit()
    }

    internal companion object {
        const val PREFERENCES_NAME = "instagram_gaming"
        const val KEY_STATE = "state"
        const val FOREGROUND_CHECKPOINT_INTERVAL_MILLIS = 15_000L
        const val MAX_INTERRUPTED_SEGMENT_RECOVERY_MILLIS =
            FOREGROUND_CHECKPOINT_INTERVAL_MILLIS
        private val MONITOR = Any()
    }
}

internal object InstagramGamingStateCodec {
    private const val SCHEMA_VERSION = "3"
    private const val V2_SCHEMA_VERSION = "2"
    private const val LEGACY_SCHEMA_VERSION = "1"

    fun encode(state: InstagramGamingStoredState): String {
        val session = state.session
        return listOf(
            SCHEMA_VERSION,
            state.config.sessionMinutes,
            state.config.allowanceMinutes,
            state.config.quarantineMinutes,
            state.lastGrantedTaskRatchetRequestId.orEmpty(),
            session?.purchase?.encode().orEmpty(),
            session?.taskRatchetRequestId.orEmpty(),
            session?.startedAtEpochMillis?.toString().orEmpty(),
            session?.deadlineEpochMillis?.toString().orEmpty(),
            session?.quarantineUntilEpochMillis?.toString().orEmpty(),
            session?.consumedForegroundMillis?.toString().orEmpty(),
            session?.bootId?.toString().orEmpty(),
            session?.sessionDeadlineElapsedMillis?.toString().orEmpty(),
            session?.quarantineDeadlineElapsedMillis?.toString().orEmpty(),
            session?.lastObservedEpochMillis?.toString().orEmpty(),
            session?.lastObservedSessionRemainingMillis?.toString().orEmpty(),
            session?.lastObservedQuarantineRemainingMillis?.toString().orEmpty(),
            session?.activeSegmentBootId?.toString().orEmpty(),
            session?.activeSegmentStartedElapsedMillis?.toString().orEmpty(),
            session?.activeSegmentCheckpointElapsedMillis?.toString().orEmpty(),
            session?.activeSegmentConsumedAtCheckpointMillis?.toString().orEmpty(),
            session?.epochDeadlinesTrusted?.let { if (it) "1" else "0" }.orEmpty(),
        ).joinToString("\t")
    }

    fun decode(raw: String): InstagramGamingStoredState? {
        val parts = raw.split('\t')
        return when (parts.firstOrNull()) {
            SCHEMA_VERSION -> decodeV3(parts)
            V2_SCHEMA_VERSION -> decodeV2(parts)
            LEGACY_SCHEMA_VERSION -> decodeV1(parts)
            else -> null
        }
    }

    private fun decodeV3(parts: List<String>): InstagramGamingStoredState? {
        if (parts.size != 22) return null
        val base = decodeV2(parts.take(17).toMutableList().also {
            it[0] = V2_SCHEMA_VERSION
        }) ?: return null
        val session = base.session ?: return base.takeIf {
            parts.drop(17).all(String::isEmpty)
        }
        val activeBoot = parts[17].optionalNonNegativeLong()
            ?: if (parts[17].isEmpty()) null else return null
        val activeStart = parts[18].optionalNonNegativeLong()
            ?: if (parts[18].isEmpty()) null else return null
        val activeCheckpoint = parts[19].optionalNonNegativeLong()
            ?: if (parts[19].isEmpty()) null else return null
        val activeConsumed = parts[20].optionalNonNegativeLong()
            ?: if (parts[20].isEmpty()) null else return null
        val epochDeadlinesTrusted = when (parts[21]) {
            "1" -> true
            "0" -> false
            else -> return null
        }
        val allNull = activeBoot == null && activeStart == null &&
            activeCheckpoint == null && activeConsumed == null
        val allPresent = activeBoot != null && activeStart != null &&
            activeCheckpoint != null && activeConsumed != null
        if (!allNull && !allPresent) return null
        if (
            allPresent &&
            (activeBoot != session.bootId ||
                activeCheckpoint!! < activeStart!! ||
                activeConsumed != session.consumedForegroundMillis)
        ) {
            return null
        }
        return base.copy(
            session = session.copy(
                activeSegmentBootId = activeBoot,
                activeSegmentStartedElapsedMillis = activeStart,
                activeSegmentCheckpointElapsedMillis = activeCheckpoint,
                activeSegmentConsumedAtCheckpointMillis = activeConsumed,
                epochDeadlinesTrusted = epochDeadlinesTrusted,
            ),
        )
    }

    private fun decodeV2(parts: List<String>): InstagramGamingStoredState? {
        if (parts.size != 17) return null
        val base = decodeBase(parts) ?: return null
        val session = base.session ?: return base.takeIf {
            parts.drop(11).all(String::isEmpty)
        }
        val bootId = parts[11].optionalNonNegativeLong()
            ?: if (parts[11].isEmpty()) null else return null
        val sessionElapsed = parts[12].optionalNonNegativeLong()
            ?: if (parts[12].isEmpty()) null else return null
        val quarantineElapsed = parts[13].optionalNonNegativeLong()
            ?: if (parts[13].isEmpty()) null else return null
        if ((bootId == null) != (sessionElapsed == null)) return null
        val lastObserved = parts[14].toLongOrNull()?.takeIf { it >= 0L }
            ?: return null
        val lastSessionRemaining = parts[15].toLongOrNull()?.takeIf {
            it in 0L..session.purchase.config.sessionDurationMillis
        } ?: return null
        val lastQuarantineRemaining = parts[16].optionalNonNegativeLong()
            ?: if (parts[16].isEmpty()) null else return null
        if (
            lastQuarantineRemaining != null &&
            lastQuarantineRemaining > session.purchase.config.quarantineDurationMillis
        ) {
            return null
        }
        if ((session.quarantineUntilEpochMillis == null) != (lastQuarantineRemaining == null)) {
            return null
        }
        if (session.quarantineUntilEpochMillis == null && quarantineElapsed != null) return null
        if (
            session.quarantineUntilEpochMillis != null &&
            bootId != null &&
            quarantineElapsed == null
        ) {
            return null
        }
        return base.copy(
            session = session.copy(
                bootId = bootId,
                sessionDeadlineElapsedMillis = sessionElapsed,
                quarantineDeadlineElapsedMillis = quarantineElapsed,
                lastObservedEpochMillis = lastObserved,
                lastObservedSessionRemainingMillis = lastSessionRemaining,
                lastObservedQuarantineRemainingMillis = lastQuarantineRemaining,
            ),
        )
    }

    private fun decodeV1(parts: List<String>): InstagramGamingStoredState? {
        if (parts.size != 11) return null
        return decodeBase(parts)
    }

    private fun decodeBase(parts: List<String>): InstagramGamingStoredState? {
        if (parts.size < 11) return null
        val config = InstagramGamingConfig(
            sessionMinutes = parts[1].toIntOrNull() ?: return null,
            allowanceMinutes = parts[2].toIntOrNull() ?: return null,
            quarantineMinutes = parts[3].toIntOrNull() ?: return null,
        )
        if (!config.isValid) return null
        val lastGranted = parts[4].takeIf(String::isNotEmpty)
        if (lastGranted != null && !isValidRequestId(lastGranted)) return null
        if (parts[5].isEmpty()) {
            if (parts.subList(6, 11).any(String::isNotEmpty)) return null
            return InstagramGamingStoredState(config, null, lastGranted)
        }
        val purchase = InstagramGamingPurchaseSnapshot.decode(parts[5]) ?: return null
        val requestId = parts[6].takeIf(::isValidRequestId) ?: return null
        val started = parts[7].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val deadline = parts[8].toLongOrNull()?.takeIf { it > started } ?: return null
        if (
            deadline != safeAdd(started, purchase.config.sessionDurationMillis) ||
            lastGranted != requestId
        ) {
            return null
        }
        val quarantine = parts[9].takeIf(String::isNotEmpty)?.toLongOrNull()
        if (quarantine != null && quarantine <= started) return null
        val consumed = parts[10].toLongOrNull()?.takeIf {
            it in 0L..purchase.config.allowanceDurationMillis
        } ?: return null
        return InstagramGamingStoredState(
            config = config,
            session = InstagramGamingSession(
                purchase = purchase,
                taskRatchetRequestId = requestId,
                startedAtEpochMillis = started,
                deadlineEpochMillis = deadline,
                quarantineUntilEpochMillis = quarantine,
                consumedForegroundMillis = consumed,
                lastObservedEpochMillis = started,
                lastObservedSessionRemainingMillis = purchase.config.sessionDurationMillis,
                lastObservedQuarantineRemainingMillis =
                    purchase.config.quarantineDurationMillis.takeIf { quarantine != null },
            ),
            lastGrantedTaskRatchetRequestId = lastGranted,
        )
    }
}

private fun String.optionalNonNegativeLong(): Long? =
    takeIf(String::isNotEmpty)?.toLongOrNull()?.takeIf { it >= 0L }

private fun InstagramGamingSession.withoutActiveSegment(): InstagramGamingSession =
    copy(
        activeSegmentBootId = null,
        activeSegmentStartedElapsedMillis = null,
        activeSegmentCheckpointElapsedMillis = null,
        activeSegmentConsumedAtCheckpointMillis = null,
    )

private fun isValidRequestId(value: String): Boolean =
    value.length in 1..128 &&
        value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' || it == ':' }

private fun legacyClock(nowEpochMillis: Long): InstagramGamingClockReading =
    InstagramGamingClockReading(
        epochMillis = nowEpochMillis,
        elapsedRealtimeMillis = 0L,
        bootId = null,
    )
