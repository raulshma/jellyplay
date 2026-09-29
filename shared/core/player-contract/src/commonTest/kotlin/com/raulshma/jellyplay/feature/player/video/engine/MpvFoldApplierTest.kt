package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the shared fold-application choreography ([MpvFoldApplier]) with
 * recording hooks: the write half both mpv engines run after [MpvEventFold.fold].
 * The engines supply their chassis flows + two sinks (track refresh with a
 * reason label, live-line cue accumulation) — exactly the shape tested here,
 * so the Android `refreshReason` passthrough and the desktop's ignored reason
 * are one parameter, the accumulate-then-mirror live-line order is pinned, and
 * the END_FILE fold-before-apply error ordering is pinned.
 */
class MpvFoldApplierTest {

    /** Recording-harness fixture: real chassis-shaped flows + sink recorders. */
    private class Harness {
        val isPlaying = MutableStateFlow(false)
        val playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
        val currentCues = MutableStateFlow<List<TimedCue>>(emptyList())
        val liveSubtitleCue = MutableStateFlow<CharSequence?>(null)
        val refreshReasons = mutableListOf<String>()
        val liveLines = mutableListOf<String>()
        /** The live flow's value AT the moment each line hook ran. */
        val flowValueAtHook = mutableListOf<CharSequence?>()

        val applier = MpvFoldApplier(
            isPlaying = isPlaying,
            playbackState = playbackState,
            currentCues = currentCues,
            liveSubtitleCue = liveSubtitleCue,
            refreshTracks = { reason -> refreshReasons += reason },
            onLiveSubtitleLine = { text ->
                liveLines += text
                flowValueAtHook += liveSubtitleCue.value
            },
        )
    }

    // ── chassis writes + latch store-back ───────────────────────────────────

    @Test
    fun apply_writesOnlyTheDeclaredLeaves_andStoresLatches() {
        val h = Harness()
        h.applier.apply(MpvPlaybackEvent.PauseChanged(paused = true))
        // No file loaded: the guarded fold declares no isPlaying flip, no
        // refresh, no line.
        assertFalse(h.isPlaying.value)
        assertEquals(EnginePlaybackState.IDLE, h.playbackState.value)
        assertTrue(h.refreshReasons.isEmpty())
        assertTrue(h.liveLines.isEmpty())
        assertTrue(h.applier.latches.paused, "the paused latch must be stored back")
    }

    @Test
    fun apply_fileLoadedSeedsReadyAndPlaying() {
        val h = Harness()
        h.applier.apply(MpvPlaybackEvent.FileLoaded(pausedNow = false))
        assertTrue(h.isPlaying.value)
        assertEquals(EnginePlaybackState.READY, h.playbackState.value)
        assertTrue(h.applier.latches.fileLoaded)
    }

    @Test
    fun apply_cueHistoryClear_wipesTheAccumulatedList() {
        val h = Harness()
        h.currentCues.value = listOf(TimedCue(0L, Long.MAX_VALUE, "stale"))
        h.applier.apply(MpvPlaybackEvent.TrackSwitch(MpvPlaybackEvent.TrackKind.SUBTITLE))
        assertTrue(h.currentCues.value.isEmpty(), "a sid switch clears the cue history")
        assertNull(h.liveSubtitleCue.value, "a sid switch clears the live line")
    }

    // ── the refreshReason parameter (Android's declared extra) ──────────────

    @Test
    fun apply_trackSwitch_refreshHookReceivesTheCallerReason() {
        val h = Harness()
        h.applier.apply(MpvPlaybackEvent.TrackSwitch(MpvPlaybackEvent.TrackKind.SUBTITLE), refreshReason = "property:sid")
        assertEquals(listOf("property:sid"), h.refreshReasons)
    }

    @Test
    fun apply_withoutAReason_usesTheDefaultLabel() {
        val h = Harness()
        h.applier.apply(MpvPlaybackEvent.TrackSwitch(MpvPlaybackEvent.TrackKind.AUDIO))
        assertEquals(listOf(MpvFoldApplier.DEFAULT_REFRESH_REASON), h.refreshReasons)
    }

    @Test
    fun apply_noRefreshDecision_neverTouchesTheRefreshHook() {
        val h = Harness()
        h.applier.apply(MpvPlaybackEvent.PauseChanged(paused = true))
        assertTrue(h.refreshReasons.isEmpty())
    }

    // ── live line: accumulate FIRST, then mirror into the flow ──────────────

    @Test
    fun apply_liveSubtitleLine_accumulatesBeforeMirroring() {
        val h = Harness()
        h.applier.apply(MpvPlaybackEvent.SubTextChanged("Hello"))
        assertEquals(listOf<CharSequence?>(null), h.flowValueAtHook, "the hook runs before the mirror write")
        assertEquals("Hello", h.liveSubtitleCue.value)
        assertEquals(listOf("Hello"), h.liveLines)
    }

    @Test
    fun apply_blankSubText_clearsTheLiveLine_withoutAccumulating() {
        val h = Harness()
        h.liveSubtitleCue.value = "previous"
        h.applier.apply(MpvPlaybackEvent.SubTextChanged(""))
        assertTrue(h.liveLines.isEmpty())
        assertNull(h.liveSubtitleCue.value)
    }

    // ── END_FILE: fold (latch store) BEFORE apply (the error-emission order) ─

    @Test
    fun fold_storesLatchesSoTheHostCanInspectBeforeApplying() {
        val h = Harness()
        h.applier.apply(MpvPlaybackEvent.FileLoaded(pausedNow = false))
        val result = h.applier.fold(MpvPlaybackEvent.EndFile(MpvPlaybackEvent.EndFileReason.ERROR))
        assertTrue(result.emitEndFileError, "the host inspects this before applying")
        assertEquals(EnginePlaybackState.READY, h.playbackState.value, "apply has not run yet")
        assertTrue(h.refreshReasons.isEmpty())
        // The apply half completes the choreography.
        h.applier.applyResult(result, refreshReason = "end-file")
        assertEquals(EnginePlaybackState.ERROR, h.playbackState.value)
    }

    // ── per-item reset ──────────────────────────────────────────────────────

    @Test
    fun resetLatches_startsAFreshFold() {
        val h = Harness()
        h.applier.apply(MpvPlaybackEvent.FileLoaded(pausedNow = false))
        assertTrue(h.applier.latches.fileLoaded)
        h.applier.resetLatches()
        assertEquals(MpvPlaybackLatches(), h.applier.latches)
    }
}
