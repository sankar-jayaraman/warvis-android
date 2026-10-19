package com.warvis.android.doom

import java.util.UUID

internal const val INSTAGRAM_APP_TARGET_KEY = "app:com.instagram.android"
internal const val INSTAGRAM_DOMAIN_TARGET_KEY = "domain:instagram.com"
internal const val INSTAGRAM_GAMING_PAYMENT_TARGET =
    "instagram-gaming:app:com.instagram.android+domain:instagram.com"
private val INSTAGRAM_GAMING_TARGET_KEYS =
    setOf(INSTAGRAM_APP_TARGET_KEY, INSTAGRAM_DOMAIN_TARGET_KEY)

internal fun isInstagramGamingTarget(targetKey: String?): Boolean =
    targetKey in INSTAGRAM_GAMING_TARGET_KEYS

internal data class InstagramGamingConfig(
    val sessionMinutes: Int = DEFAULT_SESSION_MINUTES,
    val allowanceMinutes: Int = DEFAULT_ALLOWANCE_MINUTES,
    val quarantineMinutes: Int = DEFAULT_QUARANTINE_MINUTES,
) {
    val validationError: String?
        get() = when {
            sessionMinutes !in SESSION_MINUTES_RANGE ->
                "Session duration must be between 15 minutes and 12 hours."
            allowanceMinutes !in ALLOWANCE_MINUTES_RANGE ->
                "Foreground allowance must be between 1 and 30 minutes."
            quarantineMinutes !in QUARANTINE_MINUTES_RANGE ->
                "Quarantine duration must be between 1 and 60 minutes."
            else -> null
        }

    val isValid: Boolean
        get() = validationError == null

    val sessionDurationMillis: Long
        get() = minutesToMillis(sessionMinutes)

    val allowanceDurationMillis: Long
        get() = minutesToMillis(allowanceMinutes)

    val quarantineDurationMillis: Long
        get() = minutesToMillis(quarantineMinutes)

    companion object {
        const val DEFAULT_SESSION_MINUTES = 180
        const val DEFAULT_ALLOWANCE_MINUTES = 3
        const val DEFAULT_QUARANTINE_MINUTES = 5
        val SESSION_MINUTES_RANGE = 15..720
        val ALLOWANCE_MINUTES_RANGE = 1..30
        val QUARANTINE_MINUTES_RANGE = 1..60

        private fun minutesToMillis(minutes: Int): Long =
            Math.multiplyExact(minutes.toLong(), 60_000L)
    }
}

internal data class InstagramGamingPurchaseSnapshot(
    val sessionId: String,
    val config: InstagramGamingConfig,
) {
    fun encode(): String {
        require(config.isValid)
        require(isValidSessionId(sessionId))
        return listOf(
            PURCHASE_SCHEMA_VERSION,
            sessionId,
            config.sessionMinutes,
            config.allowanceMinutes,
            config.quarantineMinutes,
        ).joinToString("|")
    }

    companion object {
        private const val PURCHASE_SCHEMA_VERSION = "1"

        fun create(config: InstagramGamingConfig): InstagramGamingPurchaseSnapshot {
            require(config.isValid)
            return InstagramGamingPurchaseSnapshot(UUID.randomUUID().toString(), config)
        }

        fun decode(raw: String?): InstagramGamingPurchaseSnapshot? {
            val parts = raw?.split('|') ?: return null
            if (parts.size != 5 || parts[0] != PURCHASE_SCHEMA_VERSION) return null
            val config = InstagramGamingConfig(
                sessionMinutes = parts[2].toIntOrNull() ?: return null,
                allowanceMinutes = parts[3].toIntOrNull() ?: return null,
                quarantineMinutes = parts[4].toIntOrNull() ?: return null,
            )
            return InstagramGamingPurchaseSnapshot(
                sessionId = parts[1].takeIf(::isValidSessionId) ?: return null,
                config = config.takeIf { it.isValid } ?: return null,
            )
        }
    }
}

internal data class InstagramGamingSession(
    val purchase: InstagramGamingPurchaseSnapshot,
    val taskRatchetRequestId: String,
    val startedAtEpochMillis: Long,
    val deadlineEpochMillis: Long,
    val quarantineUntilEpochMillis: Long? = null,
    val consumedForegroundMillis: Long = 0L,
    val bootId: Long? = null,
    val sessionDeadlineElapsedMillis: Long? = null,
    val quarantineDeadlineElapsedMillis: Long? = null,
    val lastObservedEpochMillis: Long = startedAtEpochMillis,
    val lastObservedSessionRemainingMillis: Long = purchase.config.sessionDurationMillis,
    val lastObservedQuarantineRemainingMillis: Long? = null,
    val activeSegmentBootId: Long? = null,
    val activeSegmentStartedElapsedMillis: Long? = null,
    val activeSegmentCheckpointElapsedMillis: Long? = null,
    val activeSegmentConsumedAtCheckpointMillis: Long? = null,
    val epochDeadlinesTrusted: Boolean = true,
)

internal data class InstagramGamingClockReading(
    val epochMillis: Long,
    val elapsedRealtimeMillis: Long,
    val bootId: Long?,
)

internal enum class InstagramGamingPhase {
    Inactive,
    Active,
    Quarantine,
}

internal data class InstagramGamingStatus(
    val phase: InstagramGamingPhase,
    val config: InstagramGamingConfig,
    val session: InstagramGamingSession? = null,
    val sessionRemainingMillis: Long = 0L,
    val allowanceRemainingMillis: Long = 0L,
    val quarantineRemainingMillis: Long = 0L,
)

internal object InstagramGamingPolicy {
    fun status(
        configured: InstagramGamingConfig,
        session: InstagramGamingSession?,
        clock: InstagramGamingClockReading,
    ): InstagramGamingStatus {
        val sessionRemaining = session?.let {
            InstagramGamingClockPolicy.sessionRemainingMillis(it, clock)
        } ?: 0L
        if (session == null || sessionRemaining <= 0L) {
            return InstagramGamingStatus(InstagramGamingPhase.Inactive, configured)
        }
        val quarantineUntil = session.quarantineUntilEpochMillis
        val quarantineRemaining = if (quarantineUntil != null) {
            InstagramGamingClockPolicy.quarantineRemainingMillis(session, clock)
        } else {
            0L
        }
        if (quarantineUntil != null && quarantineRemaining > 0L) {
            return InstagramGamingStatus(
                phase = InstagramGamingPhase.Quarantine,
                config = configured,
                session = session,
                sessionRemainingMillis = sessionRemaining,
                quarantineRemainingMillis = quarantineRemaining,
            )
        }
        val consumed = if (quarantineUntil != null) 0L else session.consumedForegroundMillis
        return InstagramGamingStatus(
            phase = InstagramGamingPhase.Active,
            config = configured,
            session = session,
            sessionRemainingMillis = sessionRemaining,
            allowanceRemainingMillis =
                (session.purchase.config.allowanceDurationMillis - consumed).coerceAtLeast(0L),
        )
    }

    fun reconciledSession(
        session: InstagramGamingSession?,
        clock: InstagramGamingClockReading,
    ): InstagramGamingSession? {
        return InstagramGamingClockPolicy.reconcile(session, clock)
    }
}

internal object InstagramGamingClockPolicy {
    const val MAX_PLAUSIBLE_FORWARD_JUMP_MILLIS = 6L * 60L * 60L * 1_000L
    const val TRUSTED_REMAINING_CHECKPOINT_MILLIS = 15_000L

    fun reconcile(
        session: InstagramGamingSession?,
        clock: InstagramGamingClockReading,
    ): InstagramGamingSession? {
        session ?: return null
        val sessionRemaining = sessionRemainingMillis(session, clock)
        if (sessionRemaining <= 0L) return null

        val quarantineRemaining = if (session.quarantineUntilEpochMillis != null) {
            quarantineRemainingMillis(session, clock).coerceAtMost(sessionRemaining)
        } else {
            0L
        }
        val quarantineActive =
            session.quarantineUntilEpochMillis != null && quarantineRemaining > 0L
        val sameBoot = hasValidElapsedAnchors(session, clock)
        if (sameBoot) {
            val trustedCheckpointDue =
                shouldPersistTrustedRemaining(
                    session.lastObservedSessionRemainingMillis,
                    sessionRemaining,
                ) ||
                    (
                        quarantineActive &&
                            shouldPersistTrustedRemaining(
                                session.lastObservedQuarantineRemainingMillis
                                    ?: session.purchase.config.quarantineDurationMillis,
                                quarantineRemaining,
                            )
                        ) ||
                    (session.quarantineUntilEpochMillis != null && !quarantineActive)
            val normalized = session.copy(
                quarantineUntilEpochMillis =
                    session.quarantineUntilEpochMillis.takeIf { quarantineActive },
                consumedForegroundMillis =
                    if (session.quarantineUntilEpochMillis != null && !quarantineActive) {
                        0L
                    } else {
                        session.consumedForegroundMillis
                    },
                quarantineDeadlineElapsedMillis =
                    session.quarantineDeadlineElapsedMillis.takeIf { quarantineActive },
                lastObservedEpochMillis = if (trustedCheckpointDue) {
                    clock.epochMillis
                } else {
                    session.lastObservedEpochMillis
                },
                lastObservedSessionRemainingMillis =
                    if (trustedCheckpointDue) {
                        sessionRemaining
                    } else {
                        session.lastObservedSessionRemainingMillis
                    },
                lastObservedQuarantineRemainingMillis = when {
                    !quarantineActive -> null
                    trustedCheckpointDue -> quarantineRemaining
                    else -> session.lastObservedQuarantineRemainingMillis
                },
            )
            return normalized
        }

        val anchoredSessionDeadline = safeAdd(
            clock.elapsedRealtimeMillis,
            sessionRemaining,
        )
        val anchoredQuarantineDeadline = if (quarantineActive) {
            safeAdd(clock.elapsedRealtimeMillis, quarantineRemaining)
        } else {
            null
        }
        return session.copy(
            quarantineUntilEpochMillis =
                session.quarantineUntilEpochMillis.takeIf { quarantineActive },
            consumedForegroundMillis =
                if (session.quarantineUntilEpochMillis != null && !quarantineActive) {
                    0L
                } else {
                    session.consumedForegroundMillis
                },
            bootId = clock.bootId,
            sessionDeadlineElapsedMillis =
                anchoredSessionDeadline.takeIf { clock.bootId != null },
            quarantineDeadlineElapsedMillis =
                anchoredQuarantineDeadline.takeIf { clock.bootId != null },
            lastObservedEpochMillis = clock.epochMillis,
            lastObservedSessionRemainingMillis = sessionRemaining,
            lastObservedQuarantineRemainingMillis =
                quarantineRemaining.takeIf { quarantineActive },
            epochDeadlinesTrusted =
                session.epochDeadlinesTrusted &&
                    !isImplausibleForwardJump(session, clock),
        )
    }

    fun sessionRemainingMillis(
        session: InstagramGamingSession,
        clock: InstagramGamingClockReading,
    ): Long {
        if (hasValidElapsedAnchors(session, clock)) {
            return elapsedRemainingMillis(
                requireNotNull(session.sessionDeadlineElapsedMillis),
                clock,
            )
        }
        return remainingAfterBootChange(
            persistedRemaining = session.lastObservedSessionRemainingMillis,
            configuredMaximum = session.purchase.config.sessionDurationMillis,
            lastObservedEpochMillis = session.lastObservedEpochMillis,
            currentEpochMillis = clock.epochMillis,
            epochDeadlineMillis =
                session.deadlineEpochMillis.takeIf { session.epochDeadlinesTrusted },
            preserveOnImplausibleForwardJump = true,
        )
    }

    fun quarantineRemainingMillis(
        session: InstagramGamingSession,
        clock: InstagramGamingClockReading,
    ): Long {
        session.quarantineUntilEpochMillis ?: return 0L
        if (hasValidElapsedAnchors(session, clock)) {
            return elapsedRemainingMillis(
                session.quarantineDeadlineElapsedMillis ?: return 0L,
                clock,
            )
        }
        val persistedRemaining = session.lastObservedQuarantineRemainingMillis
            ?: session.purchase.config.quarantineDurationMillis
        return remainingAfterBootChange(
            persistedRemaining = persistedRemaining,
            configuredMaximum = session.purchase.config.quarantineDurationMillis,
            lastObservedEpochMillis = session.lastObservedEpochMillis,
            currentEpochMillis = clock.epochMillis,
            epochDeadlineMillis =
                session.quarantineUntilEpochMillis.takeIf {
                    session.epochDeadlinesTrusted
                },
            preserveOnImplausibleForwardJump = true,
        )
    }

    private fun remainingAfterBootChange(
        persistedRemaining: Long,
        configuredMaximum: Long,
        lastObservedEpochMillis: Long,
        currentEpochMillis: Long,
        epochDeadlineMillis: Long?,
        preserveOnImplausibleForwardJump: Boolean,
    ): Long {
        val trusted = minOf(persistedRemaining, configuredMaximum).coerceAtLeast(0L)
        val epochDelta = currentEpochMillis - lastObservedEpochMillis
        if (epochDelta < 0L) return trusted
        if (
            preserveOnImplausibleForwardJump &&
            epochDelta > MAX_PLAUSIBLE_FORWARD_JUMP_MILLIS
        ) {
            return trusted
        }
        val deltaRemaining = (trusted - epochDelta).coerceAtLeast(0L)
        return epochDeadlineMillis?.let {
            minOf(
                trusted,
                deltaRemaining,
                (it - currentEpochMillis).coerceAtLeast(0L),
            )
        } ?: minOf(trusted, deltaRemaining)
    }

    private fun isImplausibleForwardJump(
        session: InstagramGamingSession,
        clock: InstagramGamingClockReading,
    ): Boolean {
        val delta = clock.epochMillis - session.lastObservedEpochMillis
        return delta > MAX_PLAUSIBLE_FORWARD_JUMP_MILLIS
    }

    private fun shouldPersistTrustedRemaining(
        previous: Long,
        current: Long,
    ): Boolean =
        current == 0L ||
            previous < current ||
            previous - current >= TRUSTED_REMAINING_CHECKPOINT_MILLIS

    private fun hasValidElapsedAnchors(
        session: InstagramGamingSession,
        clock: InstagramGamingClockReading,
    ): Boolean =
        clock.bootId != null &&
            session.bootId == clock.bootId &&
            session.sessionDeadlineElapsedMillis != null

    private fun elapsedRemainingMillis(
        deadlineElapsedMillis: Long,
        clock: InstagramGamingClockReading,
    ): Long =
        (deadlineElapsedMillis - clock.elapsedRealtimeMillis).coerceAtLeast(0L)
}

internal sealed interface InstagramActiveWindow {
    data object Unavailable : InstagramActiveWindow
    data object Other : InstagramActiveWindow
    data class Instagram(
        val targetKey: String,
        val packageName: String,
    ) : InstagramActiveWindow
}

internal enum class InstagramForegroundTransition {
    Ignore,
    Stop,
    Keep,
    StartOrSwitch,
}

internal object InstagramForegroundTransitionPolicy {
    fun decide(
        relevantEvent: Boolean,
        activeWindow: InstagramActiveWindow,
        meteredTargetKey: String?,
        meteredPackageName: String?,
    ): InstagramForegroundTransition = when {
        !relevantEvent -> InstagramForegroundTransition.Ignore
        activeWindow == InstagramActiveWindow.Unavailable ->
            InstagramForegroundTransition.Ignore
        activeWindow == InstagramActiveWindow.Other ->
            InstagramForegroundTransition.Stop
        activeWindow is InstagramActiveWindow.Instagram &&
            activeWindow.targetKey == meteredTargetKey &&
            activeWindow.packageName == meteredPackageName ->
            InstagramForegroundTransition.Keep
        else -> InstagramForegroundTransition.StartOrSwitch
    }
}

internal object InstagramForegroundUsagePolicy {
    fun settledDeltaMillis(
        startedAtElapsedMillis: Long?,
        nowElapsedMillis: Long,
    ): Long =
        startedAtElapsedMillis?.let {
            (nowElapsedMillis - it).coerceAtLeast(0L)
        } ?: 0L
}

internal object InstagramMeterCallbackPolicy {
    fun shouldRun(
        callbackGeneration: Long,
        currentGeneration: Long,
        meterActive: Boolean,
    ): Boolean =
        meterActive && callbackGeneration == currentGeneration
}

internal enum class InstagramAccessDecision {
    NotInstagram,
    AllowPaidGaming,
    AllowGenericGrace,
    BlockQuarantine,
    BlockNormally,
}

internal object InstagramAccessPolicy {
    fun decide(
        targetKey: String?,
        gamingPhase: InstagramGamingPhase,
        genericGraceActive: Boolean,
    ): InstagramAccessDecision {
        if (!isInstagramGamingTarget(targetKey)) {
            return InstagramAccessDecision.NotInstagram
        }
        return when {
            gamingPhase == InstagramGamingPhase.Quarantine ->
                InstagramAccessDecision.BlockQuarantine
            gamingPhase == InstagramGamingPhase.Active ->
                InstagramAccessDecision.AllowPaidGaming
            genericGraceActive -> InstagramAccessDecision.AllowGenericGrace
            else -> InstagramAccessDecision.BlockNormally
        }
    }
}

private fun isValidSessionId(value: String): Boolean =
    try {
        UUID.fromString(value).toString() == value.lowercase()
    } catch (_: IllegalArgumentException) {
        false
    }

internal fun safeAdd(left: Long, right: Long): Long? =
    try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        null
    }
