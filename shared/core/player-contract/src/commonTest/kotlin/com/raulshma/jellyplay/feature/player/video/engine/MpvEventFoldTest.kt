package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.feature.player.video.engine.MpvPlaybackEvent.EndFileReason
import com.raulshma.jellyplay.feature.player.video.engine.MpvPlaybackEvent.TrackKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the shared mpv event → state machine ([MpvEventFold]) both mpv hosts
 * fold their raw events through: FILE_LOADED seeding, keep-open EOF, the
 * guarded pause/paused-for-cache mirrors, END_FILE reasons and the sid-switch
 * cue clears. These are exactly the semantics the desktop engine carried and
 * Android converged onto (see the fold's KDoc for the three drifts).
 */
class MpvEventFoldTest {

    private val latches = MpvPlaybackLatches()

    // ─── START_FILE / FILE_LOADED ─────────────────────────────────────────────

    @Test
    fun startFile_buffersAndClearsStaleLoadLatches() {
        val loaded = latches.copy(fileLoaded = true, eofReached = true)
        val result = MpvEventFold.fold(loaded, MpvPlaybackEvent.StartFile)
        assertEquals(EnginePlaybackState.BUFFERING, result.playbackState)
        assertFalse(result.latches.fileLoaded)
        assertFalse(result.latches.eofReached)
        assertNull(result.isPlaying)
    }

    @Test
    fun fileLoaded_autoPlayingCore_seedsIsPlayingTrueAndReady() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.FileLoaded(pausedNow = false))
        assertTrue(result.isPlaying!!)
        assertEquals(EnginePlaybackState.READY, result.playbackState)
        assertTrue(result.latches.fileLoaded)
        assertFalse(result.latches.eofReached)
    }

    @Test
    fun fileLoaded_pausedCore_seedsIsPlayingFalseAndStillReady() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.FileLoaded(pausedNow = true))
        assertFalse(result.isPlaying!!)
        assertEquals(EnginePlaybackState.READY, result.playbackState)
    }

    @Test
    fun fileLoaded_resetsStaleEofLatchFromPreviousKeepOpenPark() {
        val parked = latches.copy(fileLoaded = true, eofReached = true)
        val result = MpvEventFold.fold(parked, MpvPlaybackEvent.FileLoaded(pausedNow = false))
        assertTrue(result.isPlaying!!)
        assertEquals(EnginePlaybackState.READY, result.playbackState)
    }

    // ─── pause / paused-for-cache mirroring (the guarded semantics) ──────────

    @Test
    fun pauseChanged_beforeFileLoaded_cannotPublishPlaying() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.PauseChanged(paused = false))
        assertFalse(result.isPlaying!!)
        assertNull(result.playbackState)
    }

    @Test
    fun pauseChanged_mirrorAfterLoad() {
        val loaded = MpvEventFold.fold(latches, MpvPlaybackEvent.FileLoaded(pausedNow = false)).latches
        val paused = MpvEventFold.fold(loaded, MpvPlaybackEvent.PauseChanged(paused = true))
        assertFalse(paused.isPlaying!!)
        val resumed = MpvEventFold.fold(paused.latches, MpvPlaybackEvent.PauseChanged(paused = false))
        assertTrue(resumed.isPlaying!!)
    }

    @Test
    fun pauseChanged_atEof_cannotPublishPlaying() {
        val parked = MpvEventFold.fold(latches, MpvPlaybackEvent.FileLoaded(pausedNow = false)).latches
            .let { MpvEventFold.fold(it, MpvPlaybackEvent.EofReachedChanged(eof = true, pausedNow = false)).latches }
        val result = MpvEventFold.fold(parked, MpvPlaybackEvent.PauseChanged(paused = false))
        assertFalse(result.isPlaying!!)
    }

    @Test
    fun pausedForCache_mirrorsBufferingOnlyWhenLoadedAndNotEof() {
        // Before FILE_LOADED: no decision.
        assertNull(
            MpvEventFold.fold(latches, MpvPlaybackEvent.PausedForCacheChanged(buffering = true)).playbackState,
        )
        // After FILE_LOADED: BUFFERING ↔ READY.
        val loaded = MpvEventFold.fold(latches, MpvPlaybackEvent.FileLoaded(pausedNow = false)).latches
        assertEquals(
            EnginePlaybackState.BUFFERING,
            MpvEventFold.fold(loaded, MpvPlaybackEvent.PausedForCacheChanged(buffering = true)).playbackState,
        )
        assertEquals(
            EnginePlaybackState.READY,
            MpvEventFold.fold(loaded, MpvPlaybackEvent.PausedForCacheChanged(buffering = false)).playbackState,
        )
        // Parked at EOF: no READY flip back.
        val parked = MpvEventFold.fold(loaded, MpvPlaybackEvent.EofReachedChanged(eof = true, pausedNow = true)).latches
        assertNull(
            MpvEventFold.fold(parked, MpvPlaybackEvent.PausedForCacheChanged(buffering = false)).playbackState,
        )
    }

    // ─── keep-open EOF ────────────────────────────────────────────────────────

    @Test
    fun eofReached_true_endsStopsPlayingAndClearsLiveCue() {
        val loaded = MpvEventFold.fold(latches, MpvPlaybackEvent.FileLoaded(pausedNow = false)).latches
        val result = MpvEventFold.fold(loaded, MpvPlaybackEvent.EofReachedChanged(eof = true, pausedNow = false))
        assertEquals(EnginePlaybackState.ENDED, result.playbackState)
        assertFalse(result.isPlaying!!)
        assertTrue(result.clearLiveCue)
        assertTrue(result.latches.eofReached)
    }

    @Test
    fun eofReached_false_replaySeekBack_rederivesFromLivePause() {
        val parked = MpvEventFold.fold(latches, MpvPlaybackEvent.FileLoaded(pausedNow = false)).latches
            .let { MpvEventFold.fold(it, MpvPlaybackEvent.EofReachedChanged(eof = true, pausedNow = true)).latches }
        val result = MpvEventFold.fold(parked, MpvPlaybackEvent.EofReachedChanged(eof = false, pausedNow = false))
        assertEquals(EnginePlaybackState.READY, result.playbackState)
        assertTrue(result.isPlaying!!)
        assertFalse(result.latches.eofReached)
    }

    @Test
    fun eofReached_false_beforeFileLoaded_isIgnored() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.EofReachedChanged(eof = false, pausedNow = false))
        assertNull(result.isPlaying)
        assertNull(result.playbackState)
    }

    // ─── END_FILE reasons ─────────────────────────────────────────────────────

    @Test
    fun endFile_eof_isCompletion() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.EndFile(EndFileReason.EOF))
        assertEquals(EnginePlaybackState.ENDED, result.playbackState)
        assertFalse(result.isPlaying!!)
        assertFalse(result.emitEndFileError)
    }

    @Test
    fun endFile_redirect_isTransientBuffering() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.EndFile(EndFileReason.REDIRECT))
        assertEquals(EnginePlaybackState.BUFFERING, result.playbackState)
        assertNull(result.isPlaying)
    }

    @Test
    fun endFile_error_signalsErrorEmission() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.EndFile(EndFileReason.ERROR))
        assertEquals(EnginePlaybackState.ERROR, result.playbackState)
        assertTrue(result.emitEndFileError)
    }

    @Test
    fun endFile_stopAndQuit_parkAtIdle() {
        for (reason in listOf(EndFileReason.STOP, EndFileReason.QUIT)) {
            val result = MpvEventFold.fold(latches, MpvPlaybackEvent.EndFile(reason))
            assertEquals(EnginePlaybackState.IDLE, result.playbackState)
        }
    }

    @Test
    fun endFile_unreadablePayload_isNotEndOfContent() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.EndFile(reason = null))
        assertNull(result.playbackState)
        assertNull(result.isPlaying)
        assertFalse(result.emitEndFileError)
    }

    @Test
    fun endFileReason_fromCodeMapsTheWireInts() {
        assertEquals(EndFileReason.EOF, EndFileReason.fromCode(0))
        assertEquals(EndFileReason.STOP, EndFileReason.fromCode(1))
        assertEquals(EndFileReason.QUIT, EndFileReason.fromCode(2))
        assertEquals(EndFileReason.ERROR, EndFileReason.fromCode(3))
        assertEquals(EndFileReason.REDIRECT, EndFileReason.fromCode(4))
        assertNull(EndFileReason.fromCode(5))
    }

    // ─── sid/aid switches ─────────────────────────────────────────────────────

    @Test
    fun subtitleSwitch_clearsCueHistoryAndLiveCueAndRefreshesTracks() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.TrackSwitch(TrackKind.SUBTITLE))
        assertTrue(result.clearCueHistory)
        assertTrue(result.clearLiveCue)
        assertTrue(result.refreshTracks)
        assertNull(result.isPlaying)
        assertNull(result.playbackState)
    }

    @Test
    fun audioSwitch_refreshesTracksWithoutTouchingCues() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.TrackSwitch(TrackKind.AUDIO))
        assertTrue(result.refreshTracks)
        assertFalse(result.clearCueHistory)
        assertFalse(result.clearLiveCue)
    }

    // ─── sub-text ─────────────────────────────────────────────────────────────

    @Test
    fun subText_lineChange_carriesTheText() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.SubTextChanged("Hello"))
        assertEquals("Hello", result.liveSubtitleText)
        assertFalse(result.clearLiveCue)
    }

    @Test
    fun subText_blank_clearsTheLiveLine() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.SubTextChanged(""))
        assertNull(result.liveSubtitleText)
        assertTrue(result.clearLiveCue)
    }

    // ─── core idle (desktop-only event) ──────────────────────────────────────

    @Test
    fun coreIdle_beforeFirstLoad_parksAtIdle() {
        val result = MpvEventFold.fold(latches, MpvPlaybackEvent.CoreIdle)
        assertEquals(EnginePlaybackState.IDLE, result.playbackState)
    }

    @Test
    fun coreIdle_afterLoad_leavesStateAlone() {
        val loaded = MpvEventFold.fold(latches, MpvPlaybackEvent.FileLoaded(pausedNow = false)).latches
        assertNull(MpvEventFold.fold(loaded, MpvPlaybackEvent.CoreIdle).playbackState)
    }
}
