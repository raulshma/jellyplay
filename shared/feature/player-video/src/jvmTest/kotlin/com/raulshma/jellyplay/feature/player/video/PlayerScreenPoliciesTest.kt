package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.FakeMediaEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the PRODUCTION player-screen policies ([PlayerScreenPolicies.kt]) —
 * the seek-clamp math, orientation fold, aspect-ratio ladder, skip-button
 * precedence, auto-hide gate, and font gate that used to live inline in
 * [VideoPlayerScreen] composition where no test could reach them.
 */
class StepSeekTargetTest {

    @Test
    fun seekBack_subtractsStep() {
        assertEquals(40_000L, seekBackTargetMs(currentPositionMs = 50_000L, stepMs = 10_000L))
    }

    @Test
    fun seekBack_floorsAtZero_neverNegative() {
        assertEquals(0L, seekBackTargetMs(currentPositionMs = 5_000L, stepMs = 10_000L))
        assertEquals(0L, seekBackTargetMs(currentPositionMs = 0L, stepMs = 10_000L))
    }

    @Test
    fun seekBack_hasNoUpperClamp() {
        // Position beyond duration (pathological engine report) is passed through
        // — the back path never clamps to duration.
        val pos = 120_000L
        assertEquals(pos - 10_000L, seekBackTargetMs(currentPositionMs = pos, stepMs = 10_000L))
    }

    @Test
    fun seekForward_vod_capsAtDuration() {
        assertEquals(
            60_000L,
            seekForwardTargetMs(currentPositionMs = 55_000L, stepMs = 10_000L, durationMs = 60_000L),
        )
        assertEquals(
            20_000L,
            seekForwardTargetMs(currentPositionMs = 10_000L, stepMs = 10_000L, durationMs = 100_000L),
        )
    }

    @Test
    fun seekForward_live_noDuration_neverPinsToZero() {
        // dur == 0 used to pin every forward seek to 0 via the upper clamp.
        assertEquals(
            40_000L,
            seekForwardTargetMs(currentPositionMs = 30_000L, stepMs = 10_000L, durationMs = 0L),
        )
        assertEquals(
            10_000L,
            seekForwardTargetMs(currentPositionMs = 0L, stepMs = 10_000L, durationMs = 0L),
        )
    }

    @Test
    fun seekForward_negativeDuration_treatedAsLive() {
        assertEquals(
            35_000L,
            seekForwardTargetMs(currentPositionMs = 30_000L, stepMs = 5_000L, durationMs = -1L),
        )
    }

    // ── Funnel (C3) ──────────────────────────────────────────────────────────
    // stepSeekTargetMs is the reduction VideoPlayerViewModel.seekByStep makes
    // over the engine's live reads; the FakeMediaEngine drives those reads the
    // same way the funnel does (advanceTo / durationValue).

    @Test
    fun funnel_negativeDirection_stepsBackAndFloorsAtZero() {
        val engine = FakeMediaEngine()
        engine.advanceTo(50_000L)
        assertEquals(
            40_000L,
            stepSeekTargetMs(
                direction = -1,
                currentPositionMs = engine.currentPositionMs,
                stepMs = 10_000L,
                durationMs = engine.durationMs,
            ),
        )
        engine.advanceTo(3_000L)
        assertEquals(
            0L,
            stepSeekTargetMs(
                direction = -1,
                currentPositionMs = engine.currentPositionMs,
                stepMs = 10_000L,
                durationMs = engine.durationMs,
            ),
        )
    }

    @Test
    fun funnel_forwardDirection_capsAtEngineDuration() {
        val engine = FakeMediaEngine()
        engine.advanceTo(55_000L)
        engine.durationValue = 60_000L
        assertEquals(
            60_000L,
            stepSeekTargetMs(
                direction = +1,
                currentPositionMs = engine.currentPositionMs,
                stepMs = 10_000L,
                durationMs = engine.durationMs,
            ),
        )
    }

    @Test
    fun funnel_forwardWithoutResolvedDuration_neverPinsToZero() {
        val engine = FakeMediaEngine()
        engine.advanceTo(30_000L)
        assertEquals(
            40_000L,
            stepSeekTargetMs(
                direction = +1,
                currentPositionMs = engine.currentPositionMs,
                stepMs = 10_000L,
                durationMs = engine.durationMs,
            ),
        )
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

class ControlsAutoHidePolicyTest {

    @Test
    fun visibleIdleControls_scheduleAutoHide() {
        assertTrue(
            shouldScheduleControlsAutoHide(
                showControls = true,
                isSeeking = false,
                isSheetOpen = false,
                isOverflowMenuOpen = false,
                isTv = false,
                controlsHasFocus = false,
            ),
        )
    }

    @Test
    fun hiddenControls_seeking_sheet_and_overflow_suppressTheTimer() {
        fun gate(
            showControls: Boolean = true,
            isSeeking: Boolean = false,
            isSheetOpen: Boolean = false,
            isOverflowMenuOpen: Boolean = false,
        ) = shouldScheduleControlsAutoHide(
            showControls = showControls,
            isSeeking = isSeeking,
            isSheetOpen = isSheetOpen,
            isOverflowMenuOpen = isOverflowMenuOpen,
            isTv = false,
            controlsHasFocus = false,
        )

        assertTrue(gate()) // idle gate itself is true
        assertFalse(gate(showControls = false))
        assertFalse(gate(isSeeking = true))
        assertFalse(gate(isSheetOpen = true))
        assertFalse(gate(isOverflowMenuOpen = true))
    }

    @Test
    fun nonTv_controlsFocus_suppressesTheTimer() {
        assertFalse(
            shouldScheduleControlsAutoHide(
                showControls = true,
                isSeeking = false,
                isSheetOpen = false,
                isOverflowMenuOpen = false,
                isTv = false,
                controlsHasFocus = true,
            ),
        )
    }

    @Test
    fun tv_ignoresControlsFocus() {
        assertTrue(
            shouldScheduleControlsAutoHide(
                showControls = true,
                isSeeking = false,
                isSheetOpen = false,
                isOverflowMenuOpen = false,
                isTv = true,
                controlsHasFocus = true,
            ),
        )
    }

    @Test
    fun timeout_tvIsDoubleTheBase() {
        assertEquals(5_000L, controlsAutoHideTimeoutMs(baseTimeoutMs = 5_000L, isTv = false))
        assertEquals(10_000L, controlsAutoHideTimeoutMs(baseTimeoutMs = 5_000L, isTv = true))
        assertEquals(0L, controlsAutoHideTimeoutMs(baseTimeoutMs = 0L, isTv = true))
    }
}

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
