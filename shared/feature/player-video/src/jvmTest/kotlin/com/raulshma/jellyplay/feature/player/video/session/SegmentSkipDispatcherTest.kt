package com.raulshma.jellyplay.feature.player.video.session

import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import com.raulshma.jellyplay.feature.player.video.CinemaIntroUiState
import com.raulshma.jellyplay.feature.player.video.VideoPlayerUiState
import com.raulshma.jellyplay.feature.player.video.state.SegmentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the segment-skip family's composition — the module the session root
 * delegates its skip arms to (session/SegmentSkipDispatcher.kt):
 *
 *  1. the dispatch ladder over a REAL projection (the
 *     [com.raulshma.jellyplay.feature.player.video.SegmentDispatchFacts]
 *     pairing rule: the active segment and its end ticks resolved from ONE
 *     position/duration read against the uiState slices),
 *  2. the effect execution with its userInitiated propagation (the local-seek
 *     clamp semantics — user-initiated seeks clamp, auto-skips don't),
 *  3. the "Skipped …" notice lifecycle (the auto-clear window, the
 *     re-show cancellation) under virtual time.
 *
 * The pure policy's precedence ladder is SegmentSkipPolicyTest's job (including
 * the CREDITS rungs, which no production arm dispatches — the overlay's
 * [SegmentSkipDispatcher.skipSegment] handles segment presses directly); here
 * the arms drive the whole fold — projection, policy, effects, notice — through
 * the module's interface, with recording doubles for the effects.
 */
class SegmentSkipDispatcherTest {

    private class Harness {
        val uiState = StateFlowHandle(
            MutableStateFlow(
                VideoPlayerUiState(
                    segmentState = SegmentState(
                        segments = listOf(
                            MediaSegment(
                                id = "intro-1",
                                itemId = "item-1",
                                type = MediaSegmentType.INTRO,
                                startTicks = 0L,
                                endTicks = 500_000_000L, // 50 s
                            ),
                            MediaSegment(
                                id = "outro-1",
                                itemId = "item-1",
                                type = MediaSegmentType.OUTRO,
                                startTicks = 5_800_000_000L, // 9:40
                                endTicks = 6_000_000_000L, // 10:00
                            ),
                        ),
                    ),
                ),
            ),
        )
        val positionMs = MutableStateFlow(10_000L)
        val durationMs = MutableStateFlow(600_000L)

        val seeks = mutableListOf<Pair<Long, Boolean>>()
        var playNextCalls = 0
        var cinemaAdvances = 0

        fun build(scope: CoroutineScope) = SegmentSkipDispatcher(
            scope = scope,
            uiState = uiState,
            positionMs = { positionMs.value },
            durationMs = { durationMs.value },
            canSkipToNext = { _ -> false },
            seekLocal = { position, userInitiated -> seeks.add(position to userInitiated) },
            playNextManually = { playNextCalls++ },
            advanceCinemaIntro = { cinemaAdvances++ },
        )
    }

    // ── The dispatch ladder over a real projection ───────────────────────────

    @Test
    fun skipIntroSeeksToTheActiveIntroEnd_userInitiated() = runTest {
        val h = Harness()
        val d = h.build(backgroundScope)

        d.skipIntro()

        assertEquals(listOf(50_000L to true), h.seeks, "the active intro's end ticks seek, user-initiated")
        assertEquals(0, h.playNextCalls)
        assertEquals(0, h.cinemaAdvances)
    }

    @Test
    fun introPressWithCinemaPrerollActiveAdvancesTheCinemaChain_insteadOfSeeking() = runTest {
        val h = Harness()
        h.uiState.update {
            it.copy(cinemaIntroState = CinemaIntroUiState(title = "intro", currentIndex = 0, totalCount = 2))
        }
        val d = h.build(backgroundScope)

        d.skipIntro()

        assertEquals(1, h.cinemaAdvances, "the pre-roll advance wins over touching playback")
        assertTrue(h.seeks.isEmpty(), "no seek may fire while the cinema chain owns the press")
    }

    @Test
    fun introPressWithNothingActiveIsASilentNoOp() = runTest {
        val h = Harness()
        h.positionMs.value = 300_000L // outside both segments
        val d = h.build(backgroundScope)

        d.skipIntro()

        assertTrue(h.seeks.isEmpty())
        assertEquals(0, h.playNextCalls)
        assertEquals(0, h.cinemaAdvances)
    }

    // ── The per-segment arms ─────────────────────────────────────────────────

    @Test
    fun skipSegmentSeeksToThePressedSegmentEnd_userInitiated() = runTest {
        val h = Harness()
        val d = h.build(backgroundScope)
        val outro = h.uiState.value.segmentState.segments.first { it.type == MediaSegmentType.OUTRO }

        d.skipSegment(outro)

        assertEquals(listOf(600_000L to true), h.seeks)
    }

    @Test
    fun autoSkipSeeksNotUserInitiatedAndRaisesTheNotice() = runTest {
        val h = Harness()
        val d = h.build(backgroundScope)
        val intro = h.uiState.value.segmentState.segments.first { it.type == MediaSegmentType.INTRO }

        d.autoSkip(intro)

        assertEquals(listOf(50_000L to false), h.seeks, "the auto-skip never rides the user-initiated clamp")
        val notice = h.uiState.value.skippedSegmentNotice
        assertEquals(MediaSegmentType.INTRO, notice?.segmentType)
        assertEquals(1L, notice?.seq)
    }

    @Test
    fun transportClampArmRaisesTheNoticeOnly() = runTest {
        val h = Harness()
        val d = h.build(backgroundScope)

        d.onClampedSeek(MediaSegmentType.OUTRO)

        assertTrue(h.seeks.isEmpty(), "the router already adjusted its target; the dispatcher only notices")
        assertEquals(MediaSegmentType.OUTRO, h.uiState.value.skippedSegmentNotice?.segmentType)
    }

    // ── The notice lifecycle (virtual time) ──────────────────────────────────

    @Test
    fun noticeAutoClearsAfterTheWindow() = runTest {
        val h = Harness()
        val d = h.build(backgroundScope)

        d.autoSkip(h.uiState.value.segmentState.segments.first { it.type == MediaSegmentType.INTRO })
        assertEquals(MediaSegmentType.INTRO, h.uiState.value.skippedSegmentNotice?.segmentType)

        advanceTimeBy(2_499L)
        assertEquals(
            MediaSegmentType.INTRO,
            h.uiState.value.skippedSegmentNotice?.segmentType,
            "still up just before the window closes",
        )

        advanceTimeBy(1L)
        runCurrent()
        assertNull(h.uiState.value.skippedSegmentNotice, "auto-cleared exactly at the window's end")
    }

    @Test
    fun aReplacedNoticeDoesNotClearItsSuccessor_andTheSuccessorClearsOnItsOwnWindow() = runTest {
        val h = Harness()
        val d = h.build(backgroundScope)

        d.onClampedSeek(MediaSegmentType.INTRO)
        advanceTimeBy(1_500L)
        d.onClampedSeek(MediaSegmentType.OUTRO) // replaces the live notice — its auto-clear job is cancelled

        advanceTimeBy(1_001L) // past the FIRST notice's would-be deadline
        val current = h.uiState.value.skippedSegmentNotice
        assertEquals(MediaSegmentType.OUTRO, current?.segmentType, "the replaced notice's clear must not fire")
        assertEquals(2L, current?.seq)

        advanceTimeBy(1_499L) // 2_500 after the SECOND notice
        runCurrent()
        assertNull(h.uiState.value.skippedSegmentNotice, "the second window clears on its own deadline")
    }

    @Test
    fun sameTypeConsecutiveNoticesBumpTheSeq_soTheCollectorSeesTheRetrigger() = runTest {
        val h = Harness()
        val d = h.build(backgroundScope)

        d.onClampedSeek(MediaSegmentType.INTRO)
        val first = h.uiState.value.skippedSegmentNotice
        d.onClampedSeek(MediaSegmentType.INTRO)
        val second = h.uiState.value.skippedSegmentNotice

        assertEquals(1L, first?.seq)
        assertEquals(2L, second?.seq)
        assertFalse(first === second, "a fresh notice instance must replace the live one")
    }
}
