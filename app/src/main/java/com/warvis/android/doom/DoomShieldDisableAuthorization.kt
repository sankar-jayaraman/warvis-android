package com.warvis.android.doom

import java.util.UUID

internal enum class DoomShieldControlAction {
    OpenAccessibilitySettings,
    RequireDisableCommitment,
}

internal data class DoomShieldControlDecision(
    val enabled: Boolean,
    val action: DoomShieldControlAction,
)

internal object DoomShieldControlPolicy {
    fun decide(enabled: Boolean): DoomShieldControlDecision =
        DoomShieldControlDecision(
            enabled = enabled,
            action = if (enabled) {
                DoomShieldControlAction.RequireDisableCommitment
            } else {
                DoomShieldControlAction.OpenAccessibilitySettings
            },
        )
}

internal data class DisableAuthorization(
    val token: String,
    val purpose: String,
    val expiresAtMillis: Long,
)

internal data class DisableAuthorizationIssueResult(
    val state: DisableAuthorizationState,
    val authorization: DisableAuthorization,
)

internal sealed interface DisableAuthorizationConsumeResult {
    val state: DisableAuthorizationState

    data class Consumed(
        override val state: DisableAuthorizationState,
    ) : DisableAuthorizationConsumeResult

    data class Expired(
        override val state: DisableAuthorizationState,
    ) : DisableAuthorizationConsumeResult

    data class AlreadyConsumed(
        override val state: DisableAuthorizationState,
    ) : DisableAuthorizationConsumeResult

    data class WrongPurpose(
        override val state: DisableAuthorizationState,
    ) : DisableAuthorizationConsumeResult

    data class NotFound(
        override val state: DisableAuthorizationState,
    ) : DisableAuthorizationConsumeResult
}

internal data class DisableAuthorizationState(
    private val activeByToken: Map<String, DisableAuthorization> = emptyMap(),
    private val consumedTokens: Set<String> = emptySet(),
) {
    fun issue(
        token: String,
        purpose: String,
        nowMillis: Long,
        lifetimeMillis: Long = DoomShieldDisableAuthorization.AUTHORIZATION_LIFETIME_MILLIS,
    ): DisableAuthorizationIssueResult {
        val authorization = DisableAuthorization(
            token = token,
            purpose = purpose,
            expiresAtMillis = nowMillis + lifetimeMillis,
        )
        return DisableAuthorizationIssueResult(
            state = copy(activeByToken = activeByToken + (token to authorization)),
            authorization = authorization,
        )
    }

    fun consume(
        token: String,
        expectedPurpose: String,
        nowMillis: Long,
    ): DisableAuthorizationConsumeResult {
        if (token in consumedTokens) {
            return DisableAuthorizationConsumeResult.AlreadyConsumed(this)
        }

        val authorization = activeByToken[token]
            ?: return DisableAuthorizationConsumeResult.NotFound(this)
        if (authorization.purpose != expectedPurpose) {
            return DisableAuthorizationConsumeResult.WrongPurpose(this)
        }

        val consumedState = copy(
            activeByToken = activeByToken - token,
            consumedTokens = consumedTokens + token,
        )
        return if (authorization.expiresAtMillis <= nowMillis) {
            DisableAuthorizationConsumeResult.Expired(consumedState)
        } else {
            DisableAuthorizationConsumeResult.Consumed(consumedState)
        }
    }
}

internal object DoomShieldDisableAuthorization {
    const val PURPOSE = "doom-shield:disable"
    const val AUTHORIZATION_LIFETIME_MILLIS = 30_000L

    private var state = DisableAuthorizationState()

    @Synchronized
    fun issue(nowMillis: Long = System.currentTimeMillis()): DisableAuthorization {
        val result = state.issue(
            token = UUID.randomUUID().toString(),
            purpose = PURPOSE,
            nowMillis = nowMillis,
        )
        state = result.state
        return result.authorization
    }

    @Synchronized
    fun consume(
        token: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): DisableAuthorizationConsumeResult {
        val result = state.consume(
            token = token,
            expectedPurpose = PURPOSE,
            nowMillis = nowMillis,
        )
        state = result.state
        return result
    }
}
