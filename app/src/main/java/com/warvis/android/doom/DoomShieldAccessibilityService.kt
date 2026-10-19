package com.warvis.android.doom

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.Calendar

class DoomShieldAccessibilityService : AccessibilityService() {
    private val nightExemptionProvider by lazy { NightExemptionProvider(this) }
    private val nightCurfewRepository by lazy { NightCurfewRepository(this) }
    private val instagramGamingRepository by lazy { InstagramGamingRepository(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var instagramForegroundStartedAtElapsedMillis: Long? = null
    private var instagramForegroundTarget: DoomShieldTarget? = null
    private var instagramForegroundPackage: String? = null
    private var allowanceBoundary: Runnable? = null
    private var meterGeneration = 0L
    private var screenReceiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                Intent.ACTION_USER_PRESENT -> handleUserPresent()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerScreenReceiver()
        clearInstagramForegroundMeter()
        val clock = currentClock()
        val activeWindow = currentActiveWindow()
        val canResumeForeground =
            isScreenInteractive() && activeWindow?.instagramTarget != null
        val status = instagramGamingRepository.recoverInterruptedForegroundSegment(
            clock = clock,
            recoverBoundedTail = canResumeForeground,
        )
        if (canResumeForeground) {
            handleInstagramWindow(requireNotNull(activeWindow), status, clock)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isRelevantWindowEvent(event.eventType)) return
        val activeWindow = currentActiveWindow()
        when (
            InstagramForegroundTransitionPolicy.decide(
                relevantEvent = true,
                activeWindow = activeWindow.toInstagramObservation(),
                meteredTargetKey = instagramForegroundTarget?.key,
                meteredPackageName = instagramForegroundPackage,
            )
        ) {
            InstagramForegroundTransition.Ignore -> return
            InstagramForegroundTransition.Keep -> return
            InstagramForegroundTransition.Stop -> {
                val clock = currentClock()
                settleAndClearInstagramForegroundMeter(clock)
                handleNonInstagramWindow(requireNotNull(activeWindow), clock)
            }
            InstagramForegroundTransition.StartOrSwitch -> {
                val clock = currentClock()
                settleInstagramForegroundUsage(clock, endSegment = true)
                val status = instagramGamingRepository.reconcile(clock)
                handleInstagramWindow(requireNotNull(activeWindow), status, clock)
            }
        }
    }

    override fun onInterrupt() {
        settleAndClearInstagramForegroundMeter(currentClock())
    }

    override fun onDestroy() {
        settleAndClearInstagramForegroundMeter(currentClock())
        unregisterScreenReceiver()
        super.onDestroy()
    }

    private fun handleInstagramWindow(
        window: ActiveWindowSnapshot,
        initialStatus: InstagramGamingStatus,
        clock: InstagramGamingClockReading,
    ) {
        val target = window.instagramTarget ?: return
        when (
            InstagramAccessPolicy.decide(
                targetKey = target.key,
                gamingPhase = initialStatus.phase,
                genericGraceActive =
                    DoomShieldGracePeriod.isActive(this, target.key, clock.epochMillis),
            )
        ) {
            InstagramAccessDecision.AllowPaidGaming -> {
                if (initialStatus.allowanceRemainingMillis <= 0L) {
                    val quarantine = instagramGamingRepository.enterQuarantine(clock)
                    clearInstagramForegroundMeter()
                    showInstagramQuarantine(target, window.packageName, quarantine)
                } else {
                    startInstagramForegroundMeter(
                        target = target,
                        packageName = window.packageName,
                        clock = clock,
                    )
                }
            }
            InstagramAccessDecision.AllowGenericGrace -> clearInstagramForegroundMeter()
            InstagramAccessDecision.BlockQuarantine -> {
                clearInstagramForegroundMeter()
                showInstagramQuarantine(target, window.packageName, initialStatus)
            }
            InstagramAccessDecision.BlockNormally -> {
                clearInstagramForegroundMeter()
                if (
                    DoomShieldInterventionState.tryBeginLaunch(clock.epochMillis)
                ) {
                    showIntervention(target, window.packageName, curfewLabel = null)
                }
            }
            InstagramAccessDecision.NotInstagram -> clearInstagramForegroundMeter()
        }
    }

    private fun handleNonInstagramWindow(
        window: ActiveWindowSnapshot,
        clock: InstagramGamingClockReading,
    ) {
        if (window.packageName == packageName) return
        val schedule = nightCurfewRepository.readSnapshot()
        val localTime = Calendar.getInstance()
        val minuteOfDay = MinuteOfDay.fromHourMinute(
            hour = localTime.get(Calendar.HOUR_OF_DAY),
            minute = localTime.get(Calendar.MINUTE),
        ) ?: return
        val isNightWindow = NightCurfewPolicy.isActive(schedule, minuteOfDay)
        val existingTarget = window.existingTarget
        val target = DoomShieldPolicy.classifyForegroundPackage(
            packageName = window.packageName,
            insideNightWindow = isNightWindow,
            existingTarget = existingTarget,
            exemptionPackages = if (isNightWindow) {
                nightExemptionProvider.effectivePackages(clock.epochMillis)
            } else {
                emptySet()
            },
        ) ?: return
        val labeledTarget = if (existingTarget == null) {
            target.withApplicationLabel(window.packageName)
        } else {
            target
        }
        if (
            DoomShieldGracePeriod.isActive(this, labeledTarget.key, clock.epochMillis) ||
            !DoomShieldInterventionState.tryBeginLaunch(clock.epochMillis)
        ) {
            return
        }
        showIntervention(
            target = labeledTarget,
            sourcePackage = window.packageName,
            curfewLabel = schedule.intervalLabel.takeIf {
                existingTarget == null && isNightWindow
            },
        )
    }

    private fun currentActiveWindow(): ActiveWindowSnapshot? {
        val root = rootInActiveWindow ?: return null
        val windowType = root.window?.type
        if (
            windowType == AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
            windowType == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
            windowType == AccessibilityWindowInfo.TYPE_SYSTEM
        ) {
            return null
        }
        val packageName = DoomShieldPolicy.normalizedForegroundPackage(
            root.packageName?.toString(),
        ) ?: return null
        val existingTarget = DoomShieldPolicy.nativeAppTarget(packageName)
            ?: browserTarget(packageName, root)
        val instagramTarget = existingTarget?.takeIf { isInstagramGamingTarget(it.key) }
        return ActiveWindowSnapshot(packageName, existingTarget, instagramTarget)
    }

    private fun browserTarget(
        packageName: String,
        root: AccessibilityNodeInfo,
    ): DoomShieldTarget? {
        if (packageName !in DoomShieldPolicy.supportedBrowserPackages) return null
        val detectedDomain = findBlockedDomain(root) ?: return null
        return DoomShieldPolicy.domainTarget(detectedDomain, packageName)
    }

    private fun showIntervention(
        target: DoomShieldTarget,
        sourcePackage: String,
        curfewLabel: String?,
        instagramQuarantineUntilEpochMillis: Long? = null,
    ) {
        try {
            startActivity(
                Intent(this, DoomShieldInterventionActivity::class.java)
                    .putExtra(DoomShieldInterventionActivity.EXTRA_TARGET_KEY, target.key)
                    .putExtra(DoomShieldInterventionActivity.EXTRA_TARGET_LABEL, target.displayName)
                    .putExtra(DoomShieldInterventionActivity.EXTRA_SOURCE_LABEL, target.sourceLabel)
                    .putExtra(DoomShieldInterventionActivity.EXTRA_SOURCE_PACKAGE, sourcePackage)
                    .putExtra(DoomShieldInterventionActivity.EXTRA_CURFEW_LABEL, curfewLabel)
                    .putExtra(
                        DoomShieldInterventionActivity.EXTRA_INSTAGRAM_QUARANTINE_UNTIL,
                        instagramQuarantineUntilEpochMillis ?: 0L,
                    )
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
            DoomShieldPolicy.findBlockedDomainInText(
                node.contentDescription?.toString(),
            )?.let { return it }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(nodesToVisit::add)
            }
        }
        return null
    }

    private fun settleInstagramForegroundUsage(
        clock: InstagramGamingClockReading,
        endSegment: Boolean,
    ): InstagramGamingStatus {
        instagramForegroundStartedAtElapsedMillis
            ?: return instagramGamingRepository.status(clock)
        instagramForegroundStartedAtElapsedMillis = clock.elapsedRealtimeMillis
        var status = instagramGamingRepository.checkpointForegroundSegment(
            clock = clock,
            endSegment = endSegment,
        )
        if (
            status.phase == InstagramGamingPhase.Active &&
            status.allowanceRemainingMillis <= 0L
        ) {
            status = instagramGamingRepository.enterQuarantine(clock)
        }
        return status
    }

    private fun settleAndClearInstagramForegroundMeter(
        clock: InstagramGamingClockReading,
    ) {
        if (instagramForegroundStartedAtElapsedMillis != null) {
            settleInstagramForegroundUsage(clock, endSegment = true)
        }
        clearInstagramForegroundMeter()
    }

    private fun startInstagramForegroundMeter(
        target: DoomShieldTarget,
        packageName: String,
        clock: InstagramGamingClockReading,
    ) {
        clearInstagramForegroundMeter()
        val startedStatus = instagramGamingRepository.startForegroundSegment(clock)
        if (startedStatus.phase != InstagramGamingPhase.Active) return
        instagramForegroundTarget = target
        instagramForegroundPackage = packageName
        instagramForegroundStartedAtElapsedMillis = clock.elapsedRealtimeMillis
        val generation = meterGeneration
        val delayMillis = minOf(
            startedStatus.allowanceRemainingMillis,
            startedStatus.sessionRemainingMillis,
            InstagramGamingRepository.FOREGROUND_CHECKPOINT_INTERVAL_MILLIS,
        ).coerceAtLeast(1L)
        val callback = Runnable { handleAllowanceBoundary(generation) }
        allowanceBoundary = callback
        handler.postDelayed(callback, delayMillis)
    }

    private fun handleAllowanceBoundary(generation: Long) {
        if (!InstagramMeterCallbackPolicy.shouldRun(
                callbackGeneration = generation,
                currentGeneration = meterGeneration,
                meterActive = instagramForegroundTarget != null &&
                    instagramForegroundPackage != null,
            )
        ) {
            return
        }
        if (!isScreenInteractive()) {
            instagramGamingRepository.recoverInterruptedForegroundSegment(
                clock = currentClock(),
                recoverBoundedTail = false,
            )
            clearInstagramForegroundMeter()
            return
        }
        val activeWindow = currentActiveWindow()
        if (activeWindow?.instagramTarget == null) {
            clearInstagramForegroundMeter()
            return
        }
        val clock = currentClock()
        val status = settleInstagramForegroundUsage(clock, endSegment = false)
        handleInstagramWindow(activeWindow, status, clock)
    }

    private fun handleScreenOff() {
        settleAndClearInstagramForegroundMeter(currentClock())
    }

    private fun handleUserPresent() {
        if (!isScreenInteractive()) return
        val activeWindow = currentActiveWindow() ?: return
        if (activeWindow.instagramTarget == null) return
        val clock = currentClock()
        handleInstagramWindow(
            activeWindow,
            instagramGamingRepository.reconcile(clock),
            clock,
        )
    }

    private fun showInstagramQuarantine(
        target: DoomShieldTarget,
        sourcePackage: String,
        status: InstagramGamingStatus,
    ) {
        val session = status.session ?: return
        val nowEpochMillis = System.currentTimeMillis()
        val quarantineUntil = minOf(
            safeAdd(nowEpochMillis, status.quarantineRemainingMillis)
                ?: session.quarantineUntilEpochMillis
                ?: return,
            safeAdd(nowEpochMillis, status.sessionRemainingMillis)
                ?: session.deadlineEpochMillis,
        )
        if (!DoomShieldInterventionState.tryBeginLaunch(nowEpochMillis)) return
        showIntervention(
            target = target,
            sourcePackage = sourcePackage,
            curfewLabel = null,
            instagramQuarantineUntilEpochMillis = quarantineUntil,
        )
    }

    private fun clearInstagramForegroundMeter() {
        allowanceBoundary?.let(handler::removeCallbacks)
        allowanceBoundary = null
        meterGeneration += 1L
        instagramForegroundStartedAtElapsedMillis = null
        instagramForegroundTarget = null
        instagramForegroundPackage = null
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: IllegalArgumentException) {
            // The framework may have already detached the service receiver.
        } finally {
            screenReceiverRegistered = false
        }
    }

    private fun currentClock(): InstagramGamingClockReading =
        InstagramGamingClockReading(
            epochMillis = System.currentTimeMillis(),
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            bootId = try {
                Settings.Global.getInt(
                    contentResolver,
                    Settings.Global.BOOT_COUNT,
                ).toLong()
            } catch (_: Settings.SettingNotFoundException) {
                null
            } catch (_: SecurityException) {
                null
            },
        )

    private fun isRelevantWindowEvent(eventType: Int): Boolean =
        eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED

    private fun isScreenInteractive(): Boolean =
        (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive == true

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

    private data class ActiveWindowSnapshot(
        val packageName: String,
        val existingTarget: DoomShieldTarget?,
        val instagramTarget: DoomShieldTarget?,
    )

    private fun ActiveWindowSnapshot?.toInstagramObservation(): InstagramActiveWindow =
        when {
            this == null -> InstagramActiveWindow.Unavailable
            instagramTarget == null -> InstagramActiveWindow.Other
            else -> InstagramActiveWindow.Instagram(
                targetKey = instagramTarget.key,
                packageName = packageName,
            )
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
            nowMillis - launchStartedAtMillis <
            DoomShieldPolicy.INTERVENTION_LAUNCH_TIMEOUT_MILLIS
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
