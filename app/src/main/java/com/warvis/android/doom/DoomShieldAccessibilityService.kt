package com.warvis.android.doom

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.warvis.android.bedtime.BedtimeManager
import com.warvis.android.data.BacklogRepository

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
            launchIntervention(target)
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

    private fun launchIntervention(target: DoomShieldTarget) {
        val suggestion = BacklogRepository(applicationContext).pickSuggestion()
        val isBedtime = BedtimeManager.isAfterBedtime() && BedtimeManager.isEnabledRaw(applicationContext)
        val intent = Intent(this, DoomShieldInterventionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(DoomShieldInterventionActivity.EXTRA_TARGET_KEY, target.key)
            .putExtra(DoomShieldInterventionActivity.EXTRA_TARGET_LABEL, target.displayName)
            .putExtra(DoomShieldInterventionActivity.EXTRA_SOURCE_LABEL, target.sourceLabel)
            .putExtra(DoomShieldInterventionActivity.EXTRA_IS_BEDTIME, isBedtime)
        if (suggestion != null) {
            intent.putExtra(DoomShieldInterventionActivity.EXTRA_SUGGESTION_TITLE, suggestion.title)
            intent.putExtra(DoomShieldInterventionActivity.EXTRA_SUGGESTION_BUCKET, suggestion.bucketId.displayName)
        }
        startActivity(intent)
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
