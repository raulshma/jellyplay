package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the PRODUCTION player-screen policies ([PlayerScreenPolicies.kt]) —
 * the orientation fold, aspect-ratio ladder, skip-button precedence, and font
 * gate that used to live inline in [VideoPlayerScreen] composition where no
 * test could reach them. The step-seek family and the auto-hide gate are now
 * shared with the live player's screen and live in the player-contract
 * (`PlayerChromePolicies`) — their pins moved with them to
 * `PlayerChromePoliciesTest` at that home.
 */
class ResumeSkipTargetTest {

    @Test
    fun activeSkip_rewindsBySkipMs() {
        assertEquals(40_000L, resumeSkipTargetMs(currentPositionMs = 50_000L, skipMs = 10_000L))
    }

    @Test
    fun floorsAtZero_neverNegative() {
        assertEquals(0L, resumeSkipTargetMs(currentPositionMs = 5_000L, skipMs = 10_000L))
        assertEquals(0L, resumeSkipTargetMs(currentPositionMs = 0L, skipMs = 10_000L))
    }

    @Test
    fun disabledSkip_leavesPositionUnchanged() {
        // skipMs <= 0 means the videoSkipBackOnResumeMs preference is off —
        // the position must pass through untouched (no seek, no floor).
        assertEquals(50_000L, resumeSkipTargetMs(currentPositionMs = 50_000L, skipMs = 0L))
        assertEquals(50_000L, resumeSkipTargetMs(currentPositionMs = 50_000L, skipMs = -1L))
    }

    @Test
    fun activeSkip_atOrNearZero_landsAtZero() {
        assertEquals(0L, resumeSkipTargetMs(currentPositionMs = 10_000L, skipMs = 10_000L))
    }
}

class OrientationLockDecisionTest {

    @Test
    fun tv_winsOverEverything_locksTvLandscapeImmediately() {
        assertEquals(
            OrientationLockDecision.Immediate(PlayerOrientationLock.TV_LANDSCAPE),
            orientationLockDecision(
                isTv = true,
                isCastConnected = true,
                preference = OrientationMode.LOCKED_PORTRAIT,
            ),
        )
    }

    @Test
    fun cast_followsTheUser_immediately() {
        assertEquals(
            OrientationLockDecision.Immediate(PlayerOrientationLock.USER),
            orientationLockDecision(
                isTv = false,
                isCastConnected = true,
                preference = OrientationMode.SENSOR_PORTRAIT,
            ),
        )
    }

    @Test
    fun everyPreferenceMapsToItsLock_andWaitsForSettle() {
        val expected = mapOf(
            OrientationMode.SENSOR_LANDSCAPE to PlayerOrientationLock.SENSOR_LANDSCAPE,
            OrientationMode.SENSOR_PORTRAIT to PlayerOrientationLock.SENSOR_PORTRAIT,
            OrientationMode.SENSOR to PlayerOrientationLock.SENSOR,
            OrientationMode.LOCKED_LANDSCAPE to PlayerOrientationLock.LOCKED_LANDSCAPE,
            OrientationMode.LOCKED_PORTRAIT to PlayerOrientationLock.LOCKED_PORTRAIT,
        )
        for ((preference, lock) in expected) {
            assertEquals(
                OrientationLockDecision.SettleFirst(lock),
                orientationLockDecision(isTv = false, isCastConnected = false, preference = preference),
                "preference $preference",
            )
        }
    }
}

class EffectiveAspectRatioTest {

    @Test
    fun auto_resolvesToDetectedRatio() {
        assertEquals(
            AspectRatio.RATIO_21_9,
            effectiveAspectRatio(selected = AspectRatio.AUTO, detected = AspectRatio.RATIO_21_9),
        )
    }

    @Test
    fun auto_withoutDetection_fallsBackToFit() {
        assertEquals(
            AspectRatio.FIT,
            effectiveAspectRatio(selected = AspectRatio.AUTO, detected = null),
        )
    }

    @Test
    fun explicitSelection_winsOverDetection() {
        assertEquals(
            AspectRatio.RATIO_4_3,
            effectiveAspectRatio(selected = AspectRatio.RATIO_4_3, detected = AspectRatio.RATIO_16_9),
        )
        assertEquals(
            AspectRatio.FILL,
            effectiveAspectRatio(selected = AspectRatio.FILL, detected = null),
        )
    }
}

class SkipSegmentButtonVisibilityTest {

    private fun segment(type: MediaSegmentType) = MediaSegment(
        id = "s1",
        itemId = "i1",
        type = type,
        startTicks = 0L,
        endTicks = 10_000_000L,
    )

    @Test
    fun showButtonSegment_withNoOverlays_isVisible() {
        assertTrue(
            isSkipSegmentButtonVisible(
                activeSegment = segment(MediaSegmentType.INTRO),
                segmentBehavior = SegmentBehavior.SHOW_BUTTON,
                isInPipMode = false,
                isCinemaIntroVisible = false,
                shouldShowUpNext = false,
            ),
        )
    }

    @Test
    fun noSegment_neverVisible_evenWithShowButtonBehavior() {
        assertFalse(
            isSkipSegmentButtonVisible(
                activeSegment = null,
                segmentBehavior = SegmentBehavior.SHOW_BUTTON,
                isInPipMode = false,
                isCinemaIntroVisible = false,
                shouldShowUpNext = false,
            ),
        )
    }

    @Test
    fun nonButtonBehaviors_areNeverVisible() {
        for (behavior in listOf(SegmentBehavior.AUTO_SKIP, SegmentBehavior.IGNORE)) {
            assertFalse(
                isSkipSegmentButtonVisible(
                    activeSegment = segment(MediaSegmentType.INTRO),
                    segmentBehavior = behavior,
                    isInPipMode = false,
                    isCinemaIntroVisible = false,
                    shouldShowUpNext = false,
                ),
                "behavior $behavior",
            )
        }
    }

    @Test
    fun pip_and_cinemaIntro_suppressTheButton() {
        for (type in listOf(MediaSegmentType.INTRO, MediaSegmentType.OUTRO)) {
            assertFalse(
                isSkipSegmentButtonVisible(
                    activeSegment = segment(type),
                    segmentBehavior = SegmentBehavior.SHOW_BUTTON,
                    isInPipMode = true,
                    isCinemaIntroVisible = false,
                    shouldShowUpNext = false,
                ),
                "pip suppresses $type",
            )
            assertFalse(
                isSkipSegmentButtonVisible(
                    activeSegment = segment(type),
                    segmentBehavior = SegmentBehavior.SHOW_BUTTON,
                    isInPipMode = false,
                    isCinemaIntroVisible = true,
                    shouldShowUpNext = false,
                ),
                "cinema intro suppresses $type",
            )
        }
    }

    @Test
    fun upNext_suppressesOnlyOutro() {
        assertFalse(
            isSkipSegmentButtonVisible(
                activeSegment = segment(MediaSegmentType.OUTRO),
                segmentBehavior = SegmentBehavior.SHOW_BUTTON,
                isInPipMode = false,
                isCinemaIntroVisible = false,
                shouldShowUpNext = true,
            ),
        )
        assertTrue(
            isSkipSegmentButtonVisible(
                activeSegment = segment(MediaSegmentType.INTRO),
                segmentBehavior = SegmentBehavior.SHOW_BUTTON,
                isInPipMode = false,
                isCinemaIntroVisible = false,
                shouldShowUpNext = true,
            ),
        )
    }
}

// The auto-hide gate's pins (`ControlsAutoHidePolicyTest`) moved with the
// predicate itself to player-contract's PlayerChromePoliciesTest — the gate is
// now shared with the live player's screen and pinned at its new home (the
// `timeout_tvIsDoubleTheBase` pin moved there earlier with the fold).

class UserFontGateTest {

    @Test
    fun ttf_and_otf_pass_caseInsensitively() {
        assertTrue(isSupportedUserFontFile("MyFont.ttf"))
        assertTrue(isSupportedUserFontFile("MyFont.TTF"))
        assertTrue(isSupportedUserFontFile("custom.Otf"))
        assertTrue(isSupportedUserFontFile("font.otf"))
    }

    @Test
    fun otherExtensions_andNull_fail() {
        assertFalse(isSupportedUserFontFile("subtitle.srt"))
        assertFalse(isSupportedUserFontFile("archive.zip"))
        assertFalse(isSupportedUserFontFile("font.ttf.bak"))
        assertFalse(isSupportedUserFontFile(null))
        assertFalse(isSupportedUserFontFile(""))
    }
}
