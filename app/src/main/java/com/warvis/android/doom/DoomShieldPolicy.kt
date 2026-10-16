package com.warvis.android.doom

import android.content.Context

internal data class DoomShieldTarget(
    val key: String,
    val displayName: String,
    val sourceLabel: String,
)

internal data class NightExemptionSelection(
    val automaticPackages: Set<String>,
    val userPackages: Set<String>,
) {
    val effectivePackages: Set<String>
        get() = automaticPackages + userPackages

    fun withUserExemption(packageName: String, exempt: Boolean): NightExemptionSelection {
        if (packageName in automaticPackages) return this

        return copy(
            userPackages = if (exempt) {
                userPackages + packageName
            } else {
                userPackages - packageName
            },
        )
    }
}

internal object DoomShieldPolicy {
    const val PAID_OVERRIDE_DURATION_MINUTES = 15
    const val PAID_OVERRIDE_COOLING_OFF_MILLIS = 10_000L
    const val GRACE_PERIOD_MILLIS = PAID_OVERRIDE_DURATION_MINUTES * 60 * 1000L
    const val INTERVENTION_LAUNCH_TIMEOUT_MILLIS = 1_000L
    const val MAX_NODES_TO_SCAN = 250
    const val MAX_TEXT_TO_SCAN = 500
    private const val X_TARGET_KEY = "domain:x.com"

    val explicitNightExemptions = setOf(
        // Email
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.samsung.android.email.provider",
        "com.android.email",
        "com.google.android.email",
        // SMS/messages
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        // WhatsApp
        "com.whatsapp",
        "com.whatsapp.w4b",
        // YouTube Music (not the regular YouTube app)
        "com.google.android.apps.youtube.music",
    )

    val mandatorySafetyNightExemptions = setOf(
        "com.amazon.kindle",
        "com.android.vending",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.samsung.android.permissioncontroller",
        "com.miui.securitycenter",
        "com.android.settings",
        "com.samsung.android.settings",
        "com.android.systemui",
        "com.android.phone",
        "com.android.emergency",
        "com.google.android.emergency",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.incallui",
        "com.google.android.incallui",
        "com.samsung.android.incallui",
        "com.oneplus.incallui",
        "com.miui.incallui",
        "com.samsung.android.app.telephonyui",
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.android.nfc",
        "com.google.android.apps.walletnfcrel",
        "com.samsung.android.spay",
        "com.google.android.gms",
    )

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

    fun classifyForegroundPackage(
        packageName: String?,
        insideNightWindow: Boolean,
        existingTarget: DoomShieldTarget?,
        exemptionPackages: Set<String>,
    ): DoomShieldTarget? {
        val normalizedPackage = normalizedForegroundPackage(packageName) ?: return null

        if (existingTarget != null) return existingTarget
        if (!insideNightWindow || normalizedPackage in exemptionPackages) return null

        return DoomShieldTarget(
            key = "app:$normalizedPackage",
            displayName = normalizedPackage,
            sourceLabel = normalizedPackage,
        )
    }

    fun normalizedForegroundPackage(packageName: String?): String? {
        val value = packageName?.trim().orEmpty()
        if (value.isEmpty() || value.length > 255 || value == "android") return null
        if (value != packageName || '.' !in value) return null
        if (value.any { character ->
                !character.isLetterOrDigit() && character != '.' && character != '_'
            }
        ) {
            return null
        }
        if (value.startsWith('.') || value.endsWith('.') || ".." in value) return null
        return value
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

internal enum class DoomShieldGracePeriodStartResult {
    AlreadyActive,
    Persisted,
    PersistenceFailed,
}

internal interface DoomShieldGracePeriodPersistence {
    fun readDeadline(targetKey: String): Long
    fun persistDeadline(targetKey: String, deadlineMillis: Long): Boolean
    fun restoreDeadline(targetKey: String, deadlineMillis: Long)
    fun removeDeadline(targetKey: String)
}

internal object DoomShieldGracePeriod {
    private const val PREFERENCES_NAME = "doom_shield_grace_periods"
    private const val GRACE_UNTIL_PREFIX = "grace_until:"

    @Synchronized
    fun startIfInactive(
        context: Context,
        targetKey: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): DoomShieldGracePeriodStartResult =
        startIfInactive(
            persistence = SharedPreferencesGracePeriodPersistence(context),
            targetKey = targetKey,
            nowMillis = nowMillis,
        )

    internal fun startIfInactive(
        persistence: DoomShieldGracePeriodPersistence,
        targetKey: String,
        nowMillis: Long,
    ): DoomShieldGracePeriodStartResult {
        val currentDeadlineMillis = persistence.readDeadline(targetKey)
        if (currentDeadlineMillis > nowMillis) {
            return DoomShieldGracePeriodStartResult.AlreadyActive
        }

        val updatedDeadlineMillis = nowMillis + DoomShieldPolicy.GRACE_PERIOD_MILLIS
        if (persistence.persistDeadline(targetKey, updatedDeadlineMillis)) {
            return DoomShieldGracePeriodStartResult.Persisted
        }

        persistence.restoreDeadline(targetKey, currentDeadlineMillis)
        return DoomShieldGracePeriodStartResult.PersistenceFailed
    }

    fun isActive(
        context: Context,
        targetKey: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean =
        isActive(
            persistence = SharedPreferencesGracePeriodPersistence(context),
            targetKey = targetKey,
            nowMillis = nowMillis,
        )

    internal fun isActive(
        persistence: DoomShieldGracePeriodPersistence,
        targetKey: String,
        nowMillis: Long,
    ): Boolean {
        val graceUntilMillis = persistence.readDeadline(targetKey)
        if (graceUntilMillis <= nowMillis) {
            if (graceUntilMillis > 0L) {
                persistence.removeDeadline(targetKey)
            }
            return false
        }

        return true
    }

    private class SharedPreferencesGracePeriodPersistence(context: Context) :
        DoomShieldGracePeriodPersistence {
        private val preferences =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

        override fun readDeadline(targetKey: String): Long =
            preferences.getLong(preferenceKey(targetKey), 0L)

        override fun persistDeadline(targetKey: String, deadlineMillis: Long): Boolean =
            preferences
                .edit()
                .putLong(preferenceKey(targetKey), deadlineMillis)
                .commit()

        override fun restoreDeadline(targetKey: String, deadlineMillis: Long) {
            val editor = preferences.edit()
            if (deadlineMillis > 0L) {
                editor.putLong(preferenceKey(targetKey), deadlineMillis)
            } else {
                editor.remove(preferenceKey(targetKey))
            }
            editor.apply()
        }

        override fun removeDeadline(targetKey: String) {
            preferences.edit().remove(preferenceKey(targetKey)).apply()
        }

        private fun preferenceKey(targetKey: String): String =
            GRACE_UNTIL_PREFIX + targetKey
    }
}
