package com.raulshma.jellyplay.feature.player.video.chrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/**
 * Pins the shared player-chrome policies both player screens consume
 * (the VOD `VideoPlayerScreen` and the live `LivePlayerScreen` cite this ONE
 * implementation — moved verbatim from player-video's `PlayerScreenPolicies`
 * with the funnel dedup; the play-state mirror is the hosts' shared
 * `isPlaying` collector with its same-value guard and sink fan-out).
 */
class PlayerChromePoliciesTest {

    @Test
    fun controlsAutoHideTimeout_touchFormsUseTheBaseTimeout() {
        assertEquals(5_000L, controlsAutoHideTimeoutMs(baseTimeoutMs = 5_000L, isTv = false))
    }

    @Test
    fun controlsAutoHideTimeout_tvDoublesTheBaseTimeout() {
        assertEquals(10_000L, controlsAutoHideTimeoutMs(baseTimeoutMs = 5_000L, isTv = true))
    }

    @Test
    fun controlsAutoHideTimeout_zeroStaysZeroOnTv() {
        assertEquals(0L, controlsAutoHideTimeoutMs(baseTimeoutMs = 0L, isTv = true))
    }

    @Test
    fun liveWindowRefreshLoop_ticksAtTheSharedCadenceWhileActive() = runTest {
        val ticks = mutableListOf<Int>()
        var active = true
        launch {
            liveWindowRefreshLoop(active = { active }, onTick = { ticks += ticks.size })
        }
        // Ticks land at the 500ms boundaries: 0/500/1000/1500 within 1600ms.
        testScheduler.advanceTimeBy(1_600L)
        testScheduler.runCurrent()
        assertEquals(listOf(0, 1, 2, 3), ticks)

        // Deactivate: the loop exits at the next gate check — no further ticks.
        active = false
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertEquals(listOf(0, 1, 2, 3), ticks)
    }

    @Test
    fun liveWindowRefreshLoop_neverTicksWhenInactiveAtLaunch() = runTest {
        val ticks = mutableListOf<Int>()
        launch {
            liveWindowRefreshLoop(active = { false }, onTick = { ticks += 1 })
        }
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertEquals(emptyList(), ticks)
    }

    @Test
    fun mirrorPlaying_fansOutEachDistinctValueToEverySink() = runTest {
        val sinkA = mutableListOf<Boolean>()
        val sinkB = mutableListOf<Boolean>()
        val job = mirrorPlaying(
            listOf(true, false, true).asFlow(),
            { sinkA += it },
            { sinkB += it },
        )
        job.join()
        assertEquals(listOf(true, false, true), sinkA)
        assertEquals(listOf(true, false, true), sinkB)
    }

    @Test
    fun mirrorPlaying_swallowsSameValueRepeatsBeforeAnySinkRuns() = runTest {
        val sinkA = mutableListOf<Boolean>()
        val sinkB = mutableListOf<Boolean>()
        // asFlow replays EVERY emission — including the consecutive same-values
        // a StateFlow source dedupes upstream — exactly the redundant
        // emissions the guard exists to swallow.
        val job = mirrorPlaying(
            listOf(true, true, false, false, true).asFlow(),
            { sinkA += it },
            { sinkB += it },
        )
        job.join()
        assertEquals(listOf(true, false, true), sinkA)
        assertEquals(listOf(true, false, true), sinkB)
    }

    @Test
    fun mirrorPlaying_freshMirrorAlwaysDeliversItsFirstValue() = runTest {
        val sink = mutableListOf<Boolean>()
        // A re-armed mirror has no prior value: its first delivery fans out
        // even when the sinks already hold the same state — the VOD host's
        // uiState sink keeps its own same-value check for exactly that replay.
        val job = mirrorPlaying(
            flowOf(false),
            { sink += it },
        )
        job.join()
        assertEquals(listOf(false), sink)
    }

    @Test
    fun mirrorPlaying_cancellingTheJobStopsTheFanOut() = runTest {
        val sink = mutableListOf<Boolean>()
        val job = mirrorPlaying(
            flow {
                emit(true)
                awaitCancellation()
            },
            { sink += it },
        )
        testScheduler.runCurrent()
        assertEquals(listOf(true), sink)
        job.cancel()
        testScheduler.runCurrent()
        assertFalse(job.isActive)
        assertEquals(listOf(true), sink)
    }
}

/**
 * The step-seek family's pins, moved verbatim from player-video's
 * `PlayerScreenPoliciesTest` with the policies themselves (the live player's
 * screen now cites the same math). The funnel cases inline the engine reads
 * the old pins drove through `FakeMediaEngine` — the fixture lives in
 * player-video's test-fixtures and the contract's tests pull no feature-module
 * edge — so the same position/duration values feed the fold directly.
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

    // ── Funnel ──────────────────────────────────────────────────────────
    // stepSeekTargetMs is the reduction VideoPlayerViewModel.seekByStep makes
    // over the engine's live reads; the pins feed the same position/duration
    // values the old FakeMediaEngine-driven reads produced (advanceTo /
    // durationValue), inlined now that the fold lives in the contract.

    @Test
    fun funnel_negativeDirection_stepsBackAndFloorsAtZero() {
        // Engine reads: advanceTo(50_000), duration unresolved (0)...
        assertEquals(
            40_000L,
            stepSeekTargetMs(
                direction = -1,
                currentPositionMs = 50_000L,
                stepMs = 10_000L,
                durationMs = 0L,
            ),
        )
        // ...and advanceTo(3_000).
        assertEquals(
            0L,
            stepSeekTargetMs(
                direction = -1,
                currentPositionMs = 3_000L,
                stepMs = 10_000L,
                durationMs = 0L,
            ),
        )
    }

    @Test
    fun funnel_forwardDirection_capsAtEngineDuration() {
        // Engine reads: advanceTo(55_000), durationValue = 60_000.
        assertEquals(
            60_000L,
            stepSeekTargetMs(
                direction = +1,
                currentPositionMs = 55_000L,
                stepMs = 10_000L,
                durationMs = 60_000L,
            ),
        )
    }

    @Test
    fun funnel_forwardWithoutResolvedDuration_neverPinsToZero() {
        // Engine reads: advanceTo(30_000), duration unresolved (0).
        assertEquals(
            40_000L,
            stepSeekTargetMs(
                direction = +1,
                currentPositionMs = 30_000L,
                stepMs = 10_000L,
                durationMs = 0L,
            ),
        )
    }
}

/**
 * The auto-hide gate's pins, moved verbatim from player-video's
 * `PlayerScreenPoliciesTest` with the predicate (both screens' auto-hide
 * effects now cite the same gate).
 */
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
    fun liveScreen_shape_overlayVisibleWithNoSheet_schedulesHide_evenWithFocusedControls() {
        // The live screen's gate maps onto the shared predicate with NO new
        // parameter: `overlayVisible` as showControls, `activeSheet != null`
        // as isSheetOpen, and constant false for the concepts the live chrome
        // lacks (seek-gesture suppression, overflow menu) — including
        // controlsHasFocus: a focused live control never suppresses the hide,
        // TV included (the pre-adoption inline `overlayVisible && activeSheet
        // == null` gate, pinned here so a predicate change cannot silently
        // alter the live behavior).
        assertTrue(
            shouldScheduleControlsAutoHide(
                showControls = true,
                isSeeking = false,
                isSheetOpen = false,
                isOverflowMenuOpen = false,
                isTv = true,
                controlsHasFocus = false,
            ),
        )
        assertFalse(
            shouldScheduleControlsAutoHide(
                showControls = true,
                isSeeking = false,
                isSheetOpen = true,
                isOverflowMenuOpen = false,
                isTv = true,
                controlsHasFocus = false,
            ),
        )
    }
}

/**
 * The summons gate's pins (jellyfin-androidtv #3924: pausing should not
 * summon the control overlay). The VOD screen gates BOTH pause-summons arms
 * (the keyboard media-key toggle and the TV D-pad space) through this one
 * policy; the live screen has no pause-summons arm at all, so it cites the
 * policy nowhere — the rows below pin the shape both arms must follow.
 */
class ControlsSummonOnPausePolicyTest {

    @Test
    fun pause_withPrefOff_summonsToday() {
        // Default OFF — today's behavior is untouched.
        assertTrue(shouldSummonControlsOnPause(isPause = true, hideOsdOnPause = false))
    }

    @Test
    fun pause_withPrefOn_suppressesTheSummon() {
        assertFalse(shouldSummonControlsOnPause(isPause = true, hideOsdOnPause = true))
    }

    @Test
    fun play_keepsTheSummon_evenWithPrefOn() {
        // The option is "don't summon on pause", not "never show": resuming
        // still summons exactly as before.
        assertTrue(shouldSummonControlsOnPause(isPause = false, hideOsdOnPause = true))
        assertTrue(shouldSummonControlsOnPause(isPause = false, hideOsdOnPause = false))
    }
}

/**
 * The modifier-stepped keyboard seek table ([keyboardSeekStepMs]) — one test
 * per row of the modifier fold (VLC's arrow map) plus the constant pins. The
 * key→row mapping that FEEDS this fold lives in player-video's
 * `mediaKeySeek` (pinned there in MediaKeySeekTest); the values are this
 * file's.
 */
class KeyboardSeekStepTest {

    @Test
    fun plainPress_fallsThroughToTheConfiguredStep() {
        // The configured preference step passes through untouched — no
        // hardcoded default on the plain row.
        assertEquals(33_000L, keyboardSeekStepMs(false, false, false, configuredStepMs = 33_000L))
        assertEquals(10_000L, keyboardSeekStepMs(false, false, false, configuredStepMs = 10_000L))
    }

    @Test
    fun shift_takesTheFineStep() {
        assertEquals(KEY_SEEK_STEP_FINE_MS, keyboardSeekStepMs(true, false, false, configuredStepMs = 10_000L))
    }

    @Test
    fun ctrl_takesTheCoarseStep() {
        assertEquals(KEY_SEEK_STEP_CTRL_MS, keyboardSeekStepMs(false, true, false, configuredStepMs = 10_000L))
    }

    @Test
    fun shiftCtrl_takesTheMidStep_beatingTheSingleModifierRows() {
        // Row precedence: Shift+Ctrl is 30s, never the 5s or 60s a naive
        // single-modifier first match would yield.
        assertEquals(
            KEY_SEEK_STEP_SHIFT_CTRL_MS,
            keyboardSeekStepMs(true, true, false, configuredStepMs = 10_000L),
        )
    }

    @Test
    fun altCtrl_takesTheVeryCoarseStep() {
        assertEquals(KEY_SEEK_STEP_ALT_CTRL_MS, keyboardSeekStepMs(false, true, true, configuredStepMs = 10_000L))
    }

    @Test
    fun homeEnd_step_isTenSeconds() {
        assertEquals(10_000L, KEY_SEEK_STEP_HOME_END_MS)
    }

    @Test
    fun page_step_isFiveMinutes_matchingAltCtrl() {
        assertEquals(300_000L, KEY_SEEK_STEP_PAGE_MS)
        assertEquals(KEY_SEEK_STEP_ALT_CTRL_MS, KEY_SEEK_STEP_PAGE_MS)
    }

    @Test
    fun keyboardSeekCommitDelay_isHalfASecond() {
        // The debounce: the keyboard seek chip commits this long after the
        // LAST key-down (must stay under the screen's 800ms chip linger so
        // the commit lands before the chip resets out).
        assertEquals(500L, KEYBOARD_SEEK_COMMIT_DELAY_MS)
    }
}
