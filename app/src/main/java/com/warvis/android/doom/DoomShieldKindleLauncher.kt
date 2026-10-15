package com.warvis.android.doom

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

internal object DoomShieldKindleLauncher {
    private const val TAG = "DoomShield"
    private const val KINDLE_PACKAGE_NAME = "com.amazon.kindle"

    fun open(context: Context, target: DoomShieldTarget) {
        val kindleIntent = context.packageManager.getLaunchIntentForPackage(KINDLE_PACKAGE_NAME)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (kindleIntent == null) {
            openStore(context)
            return
        }

        try {
            context.startActivity(kindleIntent)
            DoomShieldSwitchLog.recordSwitch(context.applicationContext, target)
        } catch (exception: ActivityNotFoundException) {
            Log.e(TAG, "Kindle launch activity was not found", exception)
            openStore(context)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Kindle launch was blocked", exception)
            openStore(context)
        }
    }

    private fun openStore(context: Context) {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$KINDLE_PACKAGE_NAME"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(marketIntent)
        } catch (exception: ActivityNotFoundException) {
            Log.e(TAG, "Play Store is unavailable; opening the Kindle web listing", exception)
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$KINDLE_PACKAGE_NAME"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
