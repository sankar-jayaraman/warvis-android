package com.warvis.android.doom

import android.content.Context

internal data class DoomShieldTarget(
    val key: String,
    val displayName: String,
    val sourceLabel: String,
)

internal object DoomShieldPolicy {
    const val PAID_OVERRIDE_PRICE_KR = 25
    const val PAID_OVERRIDE_DURATION_MINUTES = 15
    const val PAID_OVERRIDE_COOLING_OFF_MILLIS = 10_000L
    const val GRACE_PERIOD_MILLIS = PAID_OVERRIDE_DURATION_MINUTES * 60 * 1000L
    const val INTERVENTION_LAUNCH_DEBOUNCE_MILLIS = 1_000L
    const val MAX_NODES_TO_SCAN = 250
    const val MAX_TEXT_TO_SCAN = 500
    private const val X_TARGET_KEY = "domain:x.com"

    val supportedBrowserPackages = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android",
    )

    private val blockedApps = mapOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.twitter.android" to "X",
        "com.quora.android" to "Quora",
    )

    private val blockedDomains = mapOf(
        "facebook.com" to "facebook.com",
        "m.facebook.com" to "facebook.com",
        "fb.com" to "facebook.com",
        "instagram.com" to "instagram.com",
        "www.instagram.com" to "instagram.com",
        "youtube.com" to "youtube.com",
        "www.youtube.com" to "youtube.com",
        "m.youtube.com" to "youtube.com",
        "youtu.be" to "youtube.com",
        "x.com" to "x.com",
        "www.x.com" to "x.com",
        "mobile.x.com" to "x.com",
        "twitter.com" to "x.com",
        "www.twitter.com" to "x.com",
        "mobile.twitter.com" to "x.com",
        "quora.com" to "quora.com",
        "www.quora.com" to "quora.com",
        "m.quora.com" to "quora.com",
    )

    fun nativeAppTarget(packageName: String): DoomShieldTarget? {
        val displayName = blockedApps[packageName] ?: return null
        return DoomShieldTarget(
            key = if (packageName == "com.twitter.android") X_TARGET_KEY else "app:$packageName",
            displayName = displayName,
            sourceLabel = displayName,
        )
    }

    fun domainTarget(domain: String, browserPackage: String): DoomShieldTarget? {
        val canonicalDomain = blockedDomains[domain] ?: return null
        return DoomShieldTarget(
            key = "domain:$canonicalDomain",
            displayName = canonicalDomain,
            sourceLabel = browserLabel(browserPackage),
        )
    }

    fun findBlockedDomainInText(rawText: String?): String? {
        val text = rawText
            ?.trim()
            ?.lowercase()
            ?: return null

        if (text.length > MAX_TEXT_TO_SCAN) return null

        return blockedDomains.keys.firstOrNull { domain -> text.containsDomain(domain) }
    }

    private fun String.containsDomain(domain: String): Boolean {
        var index = indexOf(domain)
        while (index >= 0) {
            val beforeIsBoundary = index == 0 || this[index - 1].isDomainBoundary()
            val afterIndex = index + domain.length
            val afterIsBoundary = afterIndex == length || this[afterIndex].isDomainBoundary()
            if (beforeIsBoundary && afterIsBoundary) return true
            index = indexOf(domain, startIndex = index + 1)
        }
        return false
    }

    private fun Char.isDomainBoundary(): Boolean =
        !isLetterOrDigit() && this != '-' && this != '_' && this != '.'

    private fun browserLabel(packageName: String): String = when (packageName) {
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev" -> "Chrome"
        "com.sec.android.app.sbrowser" -> "Samsung Internet"
        "org.mozilla.firefox", "org.mozilla.firefox_beta" -> "Firefox"
        "com.microsoft.emmx" -> "Edge"
        "com.brave.browser" -> "Brave"
        "com.opera.browser", "com.opera.mini.native" -> "Opera"
        "com.duckduckgo.mobile.android" -> "DuckDuckGo"
        else -> packageName
    }
}

internal data class DoomShieldEntitlementState(
    private val deadlinesByTarget: Map<String, Long> = emptyMap(),
) {
    fun confirm(targetKey: String, nowMillis: Long): DoomShieldEntitlementState {
        if (isActive(targetKey, nowMillis)) return this

        return copy(
            deadlinesByTarget = deadlinesByTarget + (
                targetKey to nowMillis + DoomShieldPolicy.GRACE_PERIOD_MILLIS
            ),
        )
    }

    fun isActive(targetKey: String, nowMillis: Long): Boolean =
        deadlineFor(targetKey) > nowMillis

    fun deadlineFor(targetKey: String): Long = deadlinesByTarget[targetKey] ?: 0L
}

internal object DoomShieldGracePeriod {
    private const val PREFERENCES_NAME = "doom_shield_grace_periods"
    private const val GRACE_UNTIL_PREFIX = "grace_until:"

    @Synchronized
    fun startIfInactive(
        context: Context,
        targetKey: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val preferences = preferences(context)
        val preferenceKey = GRACE_UNTIL_PREFIX + targetKey
        val currentDeadlineMillis = preferences.getLong(preferenceKey, 0L)
        val currentState = DoomShieldEntitlementState(
            deadlinesByTarget = mapOf(targetKey to currentDeadlineMillis),
        )
        val updatedState = currentState.confirm(targetKey, nowMillis)
        val updatedDeadlineMillis = updatedState.deadlineFor(targetKey)
        if (updatedDeadlineMillis == currentDeadlineMillis) return false

        return preferences
            .edit()
            .putLong(preferenceKey, updatedDeadlineMillis)
            .commit()
    }

    fun isActive(context: Context, targetKey: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val preferences = preferences(context)
        val preferenceKey = GRACE_UNTIL_PREFIX + targetKey
        val graceUntilMillis = preferences.getLong(preferenceKey, 0L)
        if (graceUntilMillis <= nowMillis) {
            if (graceUntilMillis > 0L) {
                preferences.edit().remove(preferenceKey).apply()
            }
            return false
        }

        return true
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
