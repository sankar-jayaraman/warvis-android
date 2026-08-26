package com.warvis.android.doom

import android.content.Context

internal data class DoomShieldTarget(
    val key: String,
    val displayName: String,
    val sourceLabel: String,
)

internal object DoomShieldPolicy {
    const val GRACE_PERIOD_MILLIS = 5 * 60 * 1000L
    const val INTERVENTION_COOLDOWN_MILLIS = 30_000L
    const val MAX_NODES_TO_SCAN = 250
    const val MAX_TEXT_TO_SCAN = 500

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
    )

    fun nativeAppTarget(packageName: String): DoomShieldTarget? {
        val displayName = blockedApps[packageName] ?: return null
        return DoomShieldTarget(
            key = "app:$packageName",
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

        return blockedDomains.keys.firstOrNull { domain ->
            text == domain ||
                text.contains("://$domain") ||
                text.contains("www.$domain") ||
                text.contains(" $domain") ||
                text.contains("$domain/") ||
                text.contains("$domain?") ||
                text.contains("$domain#")
        }
    }

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

internal object DoomShieldGracePeriod {
    private const val PREFERENCES_NAME = "doom_shield_grace_periods"
    private const val GRACE_UNTIL_PREFIX = "grace_until:"

    fun start(context: Context, targetKey: String, nowMillis: Long = System.currentTimeMillis()) {
        preferences(context)
            .edit()
            .putLong(GRACE_UNTIL_PREFIX + targetKey, nowMillis + DoomShieldPolicy.GRACE_PERIOD_MILLIS)
            .apply()
    }

    fun isActive(context: Context, targetKey: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val preferenceKey = GRACE_UNTIL_PREFIX + targetKey
        val graceUntilMillis = preferences(context).getLong(preferenceKey, 0L)
        if (graceUntilMillis <= nowMillis) {
            if (graceUntilMillis > 0L) {
                preferences(context).edit().remove(preferenceKey).apply()
            }
            return false
        }

        return true
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
