package com.warvis.android.doom

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager

internal data class LaunchableNightApp(
    val packageName: String,
    val label: String,
)

internal enum class NightExemptionCapability {
    AppEmail,
    Mailto,
    ShowAlarms,
    SetAlarm,
}

internal data class NightExemptionCapabilityHandler(
    val packageName: String?,
    val capability: NightExemptionCapability,
)

internal object NightAutomaticExemptionResolution {
    fun resolvedPackages(
        handlers: Iterable<NightExemptionCapabilityHandler>,
    ): Set<String> =
        handlers.mapNotNull { handler ->
            if (handler.capability == NightExemptionCapability.Mailto) {
                null
            } else {
                DoomShieldPolicy.normalizedForegroundPackage(handler.packageName)
            }
        }.toSet()
}

internal class NightExemptionRepository(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun userPackages(): Set<String> =
        preferences.getStringSet(KEY_USER_PACKAGES, emptySet())
            .orEmpty()
            .mapNotNull(DoomShieldPolicy::normalizedForegroundPackage)
            .toSet()

    fun setUserExempt(packageName: String, exempt: Boolean) {
        val normalizedPackage =
            DoomShieldPolicy.normalizedForegroundPackage(packageName) ?: return
        val updatedPackages = if (exempt) {
            userPackages() + normalizedPackage
        } else {
            userPackages() - normalizedPackage
        }
        preferences.edit().putStringSet(KEY_USER_PACKAGES, updatedPackages).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "doom_shield_night_exemptions"
        const val KEY_USER_PACKAGES = "user_packages"
    }
}

internal class NightExemptionResolver(private val context: Context) {
    private val packageManager = context.packageManager

    fun automaticPackages(): Set<String> {
        return buildSet {
            addAll(DoomShieldPolicy.explicitNightExemptions)
            add(context.packageName)
            addAll(DoomShieldPolicy.mandatorySafetyNightExemptions)
            addAll(homePackages())
            defaultDialerPackage()?.let(::add)
            defaultSmsPackage()?.let(::add)
            addAll(enabledComponentPackages(Settings.Secure.DEFAULT_INPUT_METHOD))
            addAll(enabledComponentPackages(Settings.Secure.ENABLED_INPUT_METHODS))
            addAll(enabledComponentPackages(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
            defaultPaymentPackage()?.let(::add)
            addAll(resolvedCapabilityPackages())
        }
    }

    fun launchableApps(): List<LaunchableNightApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            packageManager.queryIntentActivities(launcherIntent, 0)
        } catch (_: RuntimeException) {
            emptyList()
        }
            .mapNotNull { info ->
                val packageName = DoomShieldPolicy.normalizedForegroundPackage(
                    info.activityInfo?.packageName,
                ) ?: return@mapNotNull null
                LaunchableNightApp(
                    packageName = packageName,
                    label = info.loadLabel(packageManager)?.toString()?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?: packageName,
                )
            }
            .distinctBy(LaunchableNightApp::packageName)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, LaunchableNightApp::label))
    }

    private fun homePackages(): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return try {
            buildSet {
                packageManager.resolveActivity(homeIntent, 0)
                    ?.activityInfo
                    ?.packageName
                    ?.let(::add)
                packageManager.queryIntentActivities(homeIntent, 0)
                    .mapNotNullTo(this) { it.activityInfo?.packageName }
            }
        } catch (_: RuntimeException) {
            emptySet()
        }
    }

    private fun defaultDialerPackage(): String? =
        try {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        } catch (_: RuntimeException) {
            null
        }

    private fun defaultSmsPackage(): String? =
        try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (_: RuntimeException) {
            null
        }

    private fun enabledComponentPackages(settingName: String): Set<String> {
        return try {
            val flattenedComponents =
                Settings.Secure.getString(context.contentResolver, settingName).orEmpty()
            flattenedComponents
                .split(':')
                .mapNotNull(ComponentName::unflattenFromString)
                .map { it.packageName }
                .toSet()
        } catch (_: RuntimeException) {
            emptySet()
        }
    }

    private fun resolvedCapabilityPackages(): Set<String> =
        NightAutomaticExemptionResolution.resolvedPackages(
            queryHandlers(
                intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL),
                capability = NightExemptionCapability.AppEmail,
            ) + queryHandlers(
                intent = Intent(AlarmClock.ACTION_SHOW_ALARMS),
                capability = NightExemptionCapability.ShowAlarms,
            ) + queryHandlers(
                intent = Intent(AlarmClock.ACTION_SET_ALARM),
                capability = NightExemptionCapability.SetAlarm,
            ),
        )

    private fun queryHandlers(
        intent: Intent,
        capability: NightExemptionCapability,
    ): List<NightExemptionCapabilityHandler> =
        try {
            packageManager.queryIntentActivities(intent, 0)
                .map { info ->
                    NightExemptionCapabilityHandler(
                        packageName = info.activityInfo?.packageName,
                        capability = capability,
                    )
                }
        } catch (_: RuntimeException) {
            emptyList()
        }

    private fun defaultPaymentPackage(): String? =
        try {
            Settings.Secure.getString(context.contentResolver, NFC_PAYMENT_DEFAULT_COMPONENT)
                ?.let(ComponentName::unflattenFromString)
                ?.packageName
        } catch (_: RuntimeException) {
            null
        }

    private companion object {
        const val NFC_PAYMENT_DEFAULT_COMPONENT = "nfc_payment_default_component"
    }
}

internal class NightExemptionProvider(context: Context) {
    private val repository = NightExemptionRepository(context)
    private val resolver = NightExemptionResolver(context)
    private var automaticPackages = emptySet<String>()
    private var automaticPackagesResolvedAtMillis = 0L

    fun effectivePackages(nowMillis: Long): Set<String> {
        if (
            automaticPackages.isEmpty() ||
            nowMillis - automaticPackagesResolvedAtMillis >= AUTOMATIC_REFRESH_MILLIS
        ) {
            automaticPackages = resolver.automaticPackages()
            automaticPackagesResolvedAtMillis = nowMillis
        }
        return automaticPackages + repository.userPackages()
    }

    private companion object {
        const val AUTOMATIC_REFRESH_MILLIS = 60_000L
    }
}
