package com.warvis.android.doom

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

internal object DoomShieldServiceStatusReader {
    fun isEnabled(context: Context): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            ) == 1
        } catch (_: SecurityException) {
            false
        }
        if (!accessibilityEnabled) return false

        val serviceComponent = ComponentName(
            context,
            DoomShieldAccessibilityService::class.java,
        )
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
                .split(':')
                .mapNotNull(ComponentName::unflattenFromString)
                .any { component -> component == serviceComponent }
        } catch (_: SecurityException) {
            false
        }
    }
}

internal enum class DoomShieldSettingsLaunchResult {
    Opened,
    AuthorizationExpired,
    AuthorizationAlreadyConsumed,
    AuthorizationWrongPurpose,
    AuthorizationNotFound,
    LaunchFailed,
}

internal class DoomShieldSettingsGateway(private val context: Context) {
    fun openForEnable(): DoomShieldSettingsLaunchResult = openAccessibilitySettings()

    fun openForAuthorizedDisable(
        authorizationToken: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): DoomShieldSettingsLaunchResult =
        when (DoomShieldDisableAuthorization.consume(authorizationToken, nowMillis)) {
            is DisableAuthorizationConsumeResult.Consumed -> openAccessibilitySettings()
            is DisableAuthorizationConsumeResult.Expired ->
                DoomShieldSettingsLaunchResult.AuthorizationExpired
            is DisableAuthorizationConsumeResult.AlreadyConsumed ->
                DoomShieldSettingsLaunchResult.AuthorizationAlreadyConsumed
            is DisableAuthorizationConsumeResult.WrongPurpose ->
                DoomShieldSettingsLaunchResult.AuthorizationWrongPurpose
            is DisableAuthorizationConsumeResult.NotFound ->
                DoomShieldSettingsLaunchResult.AuthorizationNotFound
        }

    private fun openAccessibilitySettings(): DoomShieldSettingsLaunchResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val detailsIntent = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                putExtra(
                    EXTRA_COMPONENT_NAME,
                    ComponentName(context, DoomShieldAccessibilityService::class.java),
                )
            }
            if (tryStart(detailsIntent)) return DoomShieldSettingsLaunchResult.Opened
        }

        return if (tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) {
            DoomShieldSettingsLaunchResult.Opened
        } else {
            DoomShieldSettingsLaunchResult.LaunchFailed
        }
    }

    private fun tryStart(intent: Intent): Boolean {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
            "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
        const val EXTRA_COMPONENT_NAME = "android.intent.extra.COMPONENT_NAME"
    }
}
