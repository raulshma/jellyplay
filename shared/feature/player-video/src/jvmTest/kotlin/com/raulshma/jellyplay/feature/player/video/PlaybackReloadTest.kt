package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * The reload family's PURE policy folds — the part of [PlaybackReload] that
 * needs no session graph: the session retry entry points' fold into
 * [ReloadVariant.RetryWithEngine] ([engineRetryVariant]) and the
 * variant→delta predicates ([ReloadVariant.persistEngineChoice] /
 * [ReloadVariant.restoresPausedState]) the coordinator's engine-retry arm
 * branches on. The choreography itself (arm order, the engine gate,
 * capture-before-teardown, the rebind tail) is pinned behaviorally by
 * [PlaybackSessionLifecycleTest] through the session's reload entry points —
 * the recording-arms discipline, not source text.
 */
class PlaybackReloadTest {

    private val selection = MediaStreamSelection(audioStreamIndex = 1, subtitleStreamIndex = 2)

    // ── engineRetryVariant: the session entry points' variant fold ──────────

    @Test
    fun errorRetry_persistsTheNewDefault_andNeverRestoresPause() {
        // The error dialog's engine picker: the user acted on an ERROR, not a
        // pause button — the choice becomes the saved default even when the
        // engine was caught paused.
        val variant = engineRetryVariant(
            playerType = PlayerType.MPV,
            streamingQuality = StreamingQuality.AUTO,
            playbackSpeed = 1.5f,
            sessionOnly = false,
            enginePaused = true,
        )

        val retry = variant as ReloadVariant.RetryWithEngine
        assertEquals(PlayerType.MPV, retry.preferred)
        assertEquals(StreamingQuality.AUTO, retry.streamingQuality)
        assertEquals(1.5f, retry.playbackSpeed)
        assertTrue(retry.persistPreferredPlayer, "an error retry lands the engine as the NEW saved default")
        assertFalse(retry.restorePaused, "an error retry never hands playback back paused")
    }

    @Test
    fun sessionOnlySwitch_preservesTheSavedDefault_andThePauseState() {
        val variant = engineRetryVariant(
            playerType = PlayerType.EXO_PLAYER,
            streamingQuality = StreamingQuality.AUTO,
            playbackSpeed = 1.0f,
            sessionOnly = true,
            enginePaused = true,
        )

        val retry = variant as ReloadVariant.RetryWithEngine
        assertFalse(retry.persistPreferredPlayer, "a live-session switch keeps the saved default")
        assertTrue(retry.restorePaused, "a live-session switch caught mid-pause hands playback back paused")
    }

    @Test
    fun sessionOnlySwitchWhilePlaying_restoresNothing() {
        val variant = engineRetryVariant(
            playerType = PlayerType.EXO_PLAYER,
            streamingQuality = StreamingQuality.AUTO,
            playbackSpeed = 1.0f,
            sessionOnly = true,
            enginePaused = false,
        )

        val retry = variant as ReloadVariant.RetryWithEngine
        assertFalse(retry.persistPreferredPlayer)
        assertFalse(retry.restorePaused, "a playing engine must not be paused by the switch")
    }

    // ── the variant→delta folds ─────────────────────────────────────────────

    @Test
    fun persistEngineChoice_holdsForThePersistingRetryWithEngine_only() {
        assertTrue(
            ReloadVariant.RetryWithEngine(
                PlayerType.MPV, StreamingQuality.AUTO, 1.0f,
                persistPreferredPlayer = true, restorePaused = false,
            ).persistEngineChoice(),
        )
        assertFalse(
            ReloadVariant.RetryWithEngine(
                PlayerType.MPV, StreamingQuality.AUTO, 1.0f,
                persistPreferredPlayer = false, restorePaused = true,
            ).persistEngineChoice(),
            "the session-only switch's delta must not leak into the persist fold",
        )
        assertFalse(
            ReloadVariant.RetrySameEngine(PlayerType.MPV, StreamingQuality.AUTO, 1.0f).persistEngineChoice(),
            "a same-engine retry never rewrites the saved default",
        )
        assertFalse(ReloadVariant.FallbackToTranscode(0L, StreamingQuality.AUTO, null).persistEngineChoice())
        assertFalse(ReloadVariant.StreamChange(selection).persistEngineChoice())
        assertFalse(ReloadVariant.SourceSwitch("ms-1").persistEngineChoice())
    }

    @Test
    fun restoresPausedState_holdsForARestoringRetryWithEngine_only() {
        assertTrue(
            ReloadVariant.RetryWithEngine(
                PlayerType.MPV, StreamingQuality.AUTO, 1.0f,
                persistPreferredPlayer = false, restorePaused = true,
            ).restoresPausedState(),
        )
        assertFalse(
            ReloadVariant.RetryWithEngine(
                PlayerType.MPV, StreamingQuality.AUTO, 1.0f,
                persistPreferredPlayer = true, restorePaused = false,
            ).restoresPausedState(),
        )
        assertFalse(
            ReloadVariant.RetrySameEngine(PlayerType.MPV, StreamingQuality.AUTO, 1.0f).restoresPausedState(),
            "a same-engine retry never touches the pause state",
        )
        assertFalse(ReloadVariant.FallbackToTranscode(0L, StreamingQuality.AUTO, null).restoresPausedState())
        assertFalse(ReloadVariant.StreamChange(selection).restoresPausedState())
        assertFalse(ReloadVariant.SourceSwitch("ms-1").restoresPausedState())
    }
}
