package com.warvis.android.dating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatingSwipeTrackingTest {
    @Test
    fun classifier_acceptsValidatedPositiveControls() {
        assertEquals(
            DatingApp.Hinge,
            DatingSwipeClassifier.classify(
                observation(
                    packageName = DatingApp.Hinge.packageName,
                    sourceViewIdResourceName = "co.hinge.app:id/like_button",
                    semanticLabel = "Like",
                ),
            ),
        )
        assertEquals(
            DatingApp.Tinder,
            DatingSwipeClassifier.classify(
                observation(
                    packageName = DatingApp.Tinder.packageName,
                    sourceViewIdResourceName = "com.tinder:id/heart_button",
                    semanticLabel = "Heart",
                ),
            ),
        )
        assertEquals(
            DatingApp.Bumble,
            DatingSwipeClassifier.classify(
                observation(
                    packageName = DatingApp.Bumble.packageName,
                    sourceClassName = "com.bumble.app.ui.LikeButton",
                    sourceViewIdResourceName = null,
                    semanticLabel = "Like",
                    sourceIsClickable = false,
                ),
            ),
        )
    }

    @Test
    fun classifier_rejectsGenericLikeButtonsWithoutActionControlIdentity() {
        assertNull(
            DatingSwipeClassifier.classify(
                observation(
                    packageName = DatingApp.Tinder.packageName,
                    sourceViewIdResourceName = "com.tinder:id/profile_button",
                    sourceClassName = "android.widget.Button",
                    semanticLabel = "Like",
                ),
            ),
        )
        assertNull(
            DatingSwipeClassifier.classify(
                observation(
                    packageName = DatingApp.Hinge.packageName,
                    sourceViewIdResourceName = "generic_button",
                    sourceClassName = "android.widget.ImageButton",
                    semanticLabel = "Heart",
                ),
            ),
        )
        assertNull(
            DatingSwipeClassifier.classify(
                observation(
                    packageName = DatingApp.Tinder.packageName,
                    sourceViewIdResourceName = "co.hinge.app:id/like_button",
                    sourceClassName = "android.widget.ImageButton",
                    semanticLabel = "Like",
                ),
            ),
        )
    }

    @Test
    fun classifier_rejectsWindowChangesGenericCardsAndNegativeActions() {
        assertNull(
            DatingSwipeClassifier.classify(
                observation(isClickEvent = false, semanticLabel = "Like"),
            ),
        )
        assertNull(
            DatingSwipeClassifier.classify(
                observation(
                    sourceClassName = "android.view.View",
                    sourceIsClickable = false,
                    semanticLabel = "Profile card",
                ),
            ),
        )
        assertNull(
            DatingSwipeClassifier.classify(
                observation(
                    sourceViewIdResourceName = "profile_card",
                    sourceClassName = "com.example.ProfileCard",
                    semanticLabel = "I like hiking",
                ),
            ),
        )
        assertNull(
            DatingSwipeClassifier.classify(
                observation(semanticLabel = "Dislike"),
            ),
        )
    }

    @Test
    fun deduplicator_suppressesRepeatedSemanticEventsWithinDebounceWindow() {
        val deduplicator = DatingSwipeDeduplicator(debounceWindowMillis = 1_000L)
        val observation = observation(
            packageName = DatingApp.Tinder.packageName,
            semanticLabel = "Like",
        )

        assertEquals(DatingApp.Tinder, deduplicator.accept(observation, 10_000L))
        assertNull(
            deduplicator.accept(
                observation(
                    packageName = DatingApp.Tinder.packageName,
                    semanticLabel = "Like button",
                ),
                10_500L,
            ),
        )
        assertEquals(DatingApp.Tinder, deduplicator.accept(observation, 11_000L))
    }

    private fun observation(
        packageName: String = DatingApp.Hinge.packageName,
        isClickEvent: Boolean = true,
        sourceViewIdResourceName: String? = "like_button",
        sourceClassName: String? = "android.widget.ImageButton",
        semanticLabel: String? = "Like",
        sourceIsClickable: Boolean = true,
        sourceHasClickAction: Boolean = false,
    ): DatingAccessibilityObservation =
        DatingAccessibilityObservation(
            packageName = packageName,
            isClickEvent = isClickEvent,
            sourceViewIdResourceName = sourceViewIdResourceName,
            sourceClassName = sourceClassName,
            semanticLabel = semanticLabel,
            sourceIsClickable = sourceIsClickable,
            sourceHasClickAction = sourceHasClickAction,
        )
}
