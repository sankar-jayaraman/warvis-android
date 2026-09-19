package com.warvis.android.doom

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class DoomShieldAccessibilityService : AccessibilityService() {
    private val recentInterventions = mutableMapOf<String, Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        val target = DoomShieldPolicy.nativeAppTarget(packageName)
            ?: browserTarget(packageName)
            ?: return

        if (shouldIntervene(target)) {
            redirectToKindle(target)
        }
    }

    override fun onInterrupt() = Unit

    private fun browserTarget(packageName: String): DoomShieldTarget? {
        if (packageName !in DoomShieldPolicy.supportedBrowserPackages) return null

        val root = rootInActiveWindow ?: return null
        val detectedDomain = findBlockedDomain(root) ?: return null
        return DoomShieldPolicy.domainTarget(detectedDomain, packageName)
    }

    private fun shouldIntervene(target: DoomShieldTarget): Boolean {
        if (DoomShieldGracePeriod.isActive(this, target.key)) return false

        val now = System.currentTimeMillis()
        val lastInterventionAtMillis = recentInterventions[target.key] ?: 0L
        val targetWithinCooldown = now - lastInterventionAtMillis < DoomShieldPolicy.INTERVENTION_COOLDOWN_MILLIS

        if (targetWithinCooldown) return false

        recentInterventions[target.key] = now
        return true
    }

    private fun redirectToKindle(target: DoomShieldTarget) {
        val kindleIntent = packageManager.getLaunchIntentForPackage(KINDLE_PACKAGE_NAME)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (kindleIntent == null) {
            openKindleStore()
            return
        }

        try {
            startActivity(kindleIntent)
            DoomShieldSwitchLog.recordSwitch(applicationContext, target)
        } catch (exception: ActivityNotFoundException) {
            Log.e(TAG, "Kindle launch activity was not found", exception)
            openKindleStore()
        } catch (exception: SecurityException) {
            Log.e(TAG, "Kindle launch was blocked", exception)
            openKindleStore()
        }
    }

    private fun openKindleStore() {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$KINDLE_PACKAGE_NAME"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            startActivity(marketIntent)
        } catch (exception: ActivityNotFoundException) {
            Log.e(TAG, "Play Store is unavailable; opening the Kindle web listing", exception)
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$KINDLE_PACKAGE_NAME"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun findBlockedDomain(root: AccessibilityNodeInfo): String? {
        val nodesToVisit = ArrayDeque<AccessibilityNodeInfo>()
        nodesToVisit.add(root)
        var visited = 0

        while (nodesToVisit.isNotEmpty() && visited < DoomShieldPolicy.MAX_NODES_TO_SCAN) {
            val node = nodesToVisit.removeFirst()
            visited += 1

            DoomShieldPolicy.findBlockedDomainInText(node.text?.toString())?.let { return it }
            DoomShieldPolicy.findBlockedDomainInText(node.contentDescription?.toString())?.let { return it }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(nodesToVisit::add)
            }
        }

        return null
    }

    companion object {
        private const val TAG = "DoomShield"
        private const val KINDLE_PACKAGE_NAME = "com.amazon.kindle"
    }
}
