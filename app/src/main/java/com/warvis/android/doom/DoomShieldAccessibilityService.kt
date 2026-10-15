package com.warvis.android.doom

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class DoomShieldAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        val target = DoomShieldPolicy.nativeAppTarget(packageName)
            ?: browserTarget(packageName)
            ?: return

        if (shouldIntervene(target)) {
            showIntervention(target, packageName)
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

        return DoomShieldInterventionState.tryBeginLaunch(System.currentTimeMillis())
    }

    private fun showIntervention(target: DoomShieldTarget, sourcePackage: String) {
        startActivity(
            Intent(this, DoomShieldInterventionActivity::class.java)
                .putExtra(DoomShieldInterventionActivity.EXTRA_TARGET_KEY, target.key)
                .putExtra(DoomShieldInterventionActivity.EXTRA_TARGET_LABEL, target.displayName)
                .putExtra(DoomShieldInterventionActivity.EXTRA_SOURCE_LABEL, target.sourceLabel)
                .putExtra(DoomShieldInterventionActivity.EXTRA_SOURCE_PACKAGE, sourcePackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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
}

internal object DoomShieldInterventionState {
    private var visibleActivityCount = 0
    private var lastLaunchAtMillis = 0L

    @Synchronized
    fun tryBeginLaunch(nowMillis: Long): Boolean {
        if (visibleActivityCount > 0) return false
        if (
            lastLaunchAtMillis > 0L &&
            nowMillis - lastLaunchAtMillis < DoomShieldPolicy.INTERVENTION_LAUNCH_DEBOUNCE_MILLIS
        ) {
            return false
        }

        lastLaunchAtMillis = nowMillis
        return true
    }

    @Synchronized
    fun markVisible() {
        visibleActivityCount += 1
    }

    @Synchronized
    fun markHidden() {
        visibleActivityCount = (visibleActivityCount - 1).coerceAtLeast(0)
    }
}
