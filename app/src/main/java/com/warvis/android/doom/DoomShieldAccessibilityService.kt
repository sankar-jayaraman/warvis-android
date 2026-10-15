package com.warvis.android.doom

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Calendar

class DoomShieldAccessibilityService : AccessibilityService() {
    private val nightExemptionProvider by lazy { NightExemptionProvider(this) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = DoomShieldPolicy.normalizedForegroundPackage(
            event.packageName?.toString(),
        ) ?: return
        if (packageName == this.packageName) return

        val localTime = Calendar.getInstance()
        val isNightWindow = DoomShieldPolicy.isNightWindow(
            hour = localTime.get(Calendar.HOUR_OF_DAY),
            minute = localTime.get(Calendar.MINUTE),
        )
        val existingTarget = DoomShieldPolicy.nativeAppTarget(packageName)
            ?: browserTarget(packageName)
        val nowMillis = System.currentTimeMillis()
        val target = DoomShieldPolicy.classifyForegroundPackage(
            packageName = packageName,
            insideNightWindow = isNightWindow,
            existingTarget = existingTarget,
            exemptionPackages = if (isNightWindow) {
                nightExemptionProvider.effectivePackages(nowMillis)
            } else {
                emptySet()
            },
        ) ?: return
        val labeledTarget = if (existingTarget == null) {
            target.withApplicationLabel(packageName)
        } else {
            target
        }

        if (DoomShieldGracePeriod.isActive(this, labeledTarget.key, nowMillis)) return
        if (!DoomShieldInterventionState.tryBeginLaunch(nowMillis)) return

        showIntervention(labeledTarget, packageName, isNightWindow)
    }

    override fun onInterrupt() = Unit

    private fun browserTarget(packageName: String): DoomShieldTarget? {
        if (packageName !in DoomShieldPolicy.supportedBrowserPackages) return null

        val root = rootInActiveWindow ?: return null
        val detectedDomain = findBlockedDomain(root) ?: return null
        return DoomShieldPolicy.domainTarget(detectedDomain, packageName)
    }

    private fun showIntervention(
        target: DoomShieldTarget,
        sourcePackage: String,
        isNightWindow: Boolean,
    ) {
        try {
            startActivity(
                Intent(this, DoomShieldInterventionActivity::class.java)
                    .putExtra(DoomShieldInterventionActivity.EXTRA_TARGET_KEY, target.key)
                    .putExtra(DoomShieldInterventionActivity.EXTRA_TARGET_LABEL, target.displayName)
                    .putExtra(DoomShieldInterventionActivity.EXTRA_SOURCE_LABEL, target.sourceLabel)
                    .putExtra(DoomShieldInterventionActivity.EXTRA_SOURCE_PACKAGE, sourcePackage)
                    .putExtra(DoomShieldInterventionActivity.EXTRA_IS_BEDTIME, isNightWindow)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (exception: ActivityNotFoundException) {
            DoomShieldInterventionState.cancelLaunch()
            Log.e(TAG, "Doom Shield intervention activity was not found", exception)
        } catch (exception: SecurityException) {
            DoomShieldInterventionState.cancelLaunch()
            Log.e(TAG, "Doom Shield intervention launch was blocked", exception)
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

    private fun DoomShieldTarget.withApplicationLabel(packageName: String): DoomShieldTarget {
        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        } catch (_: Exception) {
            packageName
        }
        return copy(displayName = label, sourceLabel = label)
    }

    private companion object {
        const val TAG = "DoomShield"
    }
}

internal object DoomShieldInterventionState {
    private var visibleActivityCount = 0
    private var launchInProgress = false
    private var launchStartedAtMillis = 0L

    @Synchronized
    fun tryBeginLaunch(nowMillis: Long): Boolean {
        if (visibleActivityCount > 0) return false
        if (
            launchInProgress &&
            nowMillis - launchStartedAtMillis < DoomShieldPolicy.INTERVENTION_LAUNCH_TIMEOUT_MILLIS
        ) {
            return false
        }

        launchInProgress = true
        launchStartedAtMillis = nowMillis
        return true
    }

    @Synchronized
    fun markVisible() {
        launchInProgress = false
        launchStartedAtMillis = 0L
        visibleActivityCount += 1
    }

    @Synchronized
    fun markHidden() {
        visibleActivityCount = (visibleActivityCount - 1).coerceAtLeast(0)
    }

    @Synchronized
    fun cancelLaunch() {
        launchInProgress = false
        launchStartedAtMillis = 0L
    }
}
