package com.warvis.android.dating

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

internal enum class DatingApp(
    val packageName: String,
    val displayName: String,
) {
    Hinge("co.hinge.app", "Hinge"),
    Tinder("com.tinder", "Tinder"),
    Bumble("com.bumble.app", "Bumble"),
    ;

    companion object {
        fun fromPackageName(packageName: String?): DatingApp? =
            entries.firstOrNull { it.packageName == packageName }
    }
}

internal data class DatingAccessibilityObservation(
    val packageName: String,
    val isClickEvent: Boolean,
    val sourceViewIdResourceName: String? = null,
    val sourceClassName: String? = null,
    val semanticLabel: String? = null,
    val sourceIsClickable: Boolean = false,
    val sourceHasClickAction: Boolean = false,
) {
    companion object {
        fun from(event: AccessibilityEvent): DatingAccessibilityObservation {
            val source = try {
                event.source
            } catch (_: RuntimeException) {
                null
            }
            return try {
                val sourceClassName = source?.className?.toString() ?: event.className?.toString()
                val sourceLooksLikeButton = isButtonLikeClass(sourceClassName)
                val semanticParts = buildList<String> {
                    event.contentDescription?.toString()?.let(::add)
                    source?.contentDescription?.toString()?.let(::add)
                    if (sourceLooksLikeButton) {
                        event.text.forEach { add(it.toString()) }
                        source?.text?.toString()?.let(::add)
                    }
                }

                DatingAccessibilityObservation(
                    packageName = event.packageName?.toString().orEmpty(),
                    isClickEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED,
                    sourceViewIdResourceName = source?.viewIdResourceName,
                    sourceClassName = sourceClassName,
                    semanticLabel = semanticParts
                        .distinct()
                        .filter(String::isNotBlank)
                        .joinToString(" ")
                        .takeIf(String::isNotBlank),
                    sourceIsClickable = source?.isClickable == true,
                    sourceHasClickAction = source?.let {
                        (it.actions and AccessibilityNodeInfo.ACTION_CLICK) != 0
                    } == true,
                )
            } finally {
                try {
                    source?.recycle()
                } catch (_: RuntimeException) {
                    // The framework may already have recycled the event source.
                }
            }
        }
    }
}

internal object DatingSwipeClassifier {
    private val negativeTokens = setOf(
        "dislike",
        "negative",
        "no",
        "not",
        "nope",
        "pass",
        "reject",
        "skip",
        "unlike",
    )
    private val positiveActionLabels = setOf(
        "heart",
        "heart button",
        "interested",
        "interested button",
        "like",
        "like button",
        "send like",
        "super like",
        "super like button",
    )
    private val packageSpecificPositiveIdentifiers = mapOf(
        DatingApp.Hinge to setOf(
            "heart",
            "heart_button",
            "interested",
            "interested_button",
            "like",
            "like_button",
        ),
        DatingApp.Tinder to setOf(
            "action_like",
            "heart",
            "heart_button",
            "like",
            "like_button",
            "super_like",
            "super_like_button",
        ),
        DatingApp.Bumble to setOf(
            "heart",
            "heart_button",
            "interested",
            "interested_button",
            "like",
            "like_button",
            "send_like",
            "send_like_button",
        ),
    )
    private val genericPositiveIdentifiers = setOf(
        "action_heart",
        "action_like",
        "heart",
        "heart_button",
        "interested",
        "interested_button",
        "like",
        "like_button",
        "send_like",
        "send_like_button",
        "super_like",
        "super_like_button",
    )
    private val positiveActionClassNames = setOf(
        "heartbutton",
        "interestedbutton",
        "likebutton",
        "sendlikebutton",
        "superlikebutton",
    )

    fun classify(observation: DatingAccessibilityObservation): DatingApp? {
        val app = DatingApp.fromPackageName(observation.packageName) ?: return null
        if (!observation.isClickEvent || !observation.hasLikelyActionSource()) return null

        val label = normalizeLabel(observation.semanticLabel)
        val identifierText = listOfNotNull(
            observation.sourceViewIdResourceName,
            observation.sourceClassName,
        ).joinToString(" ")
        val identifierTokens = tokenize(identifierText)
        if (identifierTokens.intersect(negativeTokens).isNotEmpty()) return null
        if (tokenize(label).intersect(negativeTokens).isNotEmpty()) return null
        if (label !in positiveActionLabels) return null
        return app.takeIf { hasPositiveActionControlIdentity(app, observation) }
    }

    private fun hasPositiveActionControlIdentity(
        app: DatingApp,
        observation: DatingAccessibilityObservation,
    ): Boolean {
        val viewId = observation.sourceViewIdResourceName
            ?.lowercase(Locale.ROOT)
            ?.trim()
            ?: return hasPackageSpecificActionClass(app, observation.sourceClassName)
        val qualifiedSeparator = ":id/"
        if (qualifiedSeparator in viewId) {
            val resourcePackage = viewId.substringBefore(qualifiedSeparator)
            val localId = viewId.substringAfter(qualifiedSeparator)
            return resourcePackage == app.packageName &&
                localId in packageSpecificPositiveIdentifiers.getValue(app)
        }

        val localId = viewId.substringAfterLast('/')
        return localId in genericPositiveIdentifiers ||
            hasPackageSpecificActionClass(app, observation.sourceClassName)
    }

    private fun hasPackageSpecificActionClass(
        app: DatingApp,
        className: String?,
    ): Boolean {
        val normalized = className.orEmpty().lowercase(Locale.ROOT)
        if (!normalized.startsWith("${app.packageName}.")) return false
        return normalized.substringAfterLast('.') in positiveActionClassNames
    }

    private fun DatingAccessibilityObservation.hasLikelyActionSource(): Boolean =
        sourceIsClickable ||
            sourceHasClickAction ||
            isButtonLikeClass(sourceClassName)

    private fun tokenize(value: String): Set<String> =
        value.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter(String::isNotBlank)
            .toSet()

    private fun normalizeLabel(value: String?): String =
        value.orEmpty()
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter(String::isNotBlank)
            .joinToString(" ")
}

private fun isButtonLikeClass(className: String?): Boolean =
    className.orEmpty()
        .lowercase(Locale.ROOT)
        .let { normalized ->
            normalized.endsWith("button") ||
                normalized.contains("imagebutton") ||
                normalized.contains("floatingactionbutton")
        }

internal class DatingSwipeDeduplicator(
    private val debounceWindowMillis: Long = DEFAULT_DEBOUNCE_WINDOW_MILLIS,
) {
    private var lastAccepted: AcceptedAction? = null

    @Synchronized
    fun accept(
        observation: DatingAccessibilityObservation,
        nowElapsedMillis: Long,
    ): DatingApp? {
        val app = DatingSwipeClassifier.classify(observation) ?: return null
        val fingerprint = observation.fingerprint(app)
        val previous = lastAccepted
        if (
            previous != null &&
            previous.fingerprint == fingerprint &&
            nowElapsedMillis >= previous.acceptedAtElapsedMillis &&
            nowElapsedMillis - previous.acceptedAtElapsedMillis < debounceWindowMillis
        ) {
            return null
        }

        lastAccepted = AcceptedAction(fingerprint, nowElapsedMillis)
        return app
    }

    private fun DatingAccessibilityObservation.fingerprint(app: DatingApp): String =
        listOf(
            app.packageName,
            sourceViewIdResourceName
                ?.takeIf(String::isNotBlank)
                ?: listOf(sourceClassName.orEmpty(), semanticLabel.orEmpty())
                    .joinToString("|"),
        ).joinToString("|").lowercase(Locale.ROOT)

    private data class AcceptedAction(
        val fingerprint: String,
        val acceptedAtElapsedMillis: Long,
    )

    internal companion object {
        const val DEFAULT_DEBOUNCE_WINDOW_MILLIS = 1_000L
    }
}
