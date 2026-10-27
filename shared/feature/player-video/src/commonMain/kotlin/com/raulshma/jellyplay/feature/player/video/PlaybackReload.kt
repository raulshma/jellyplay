package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One reload's variant — the five reload shapes' deltas as data, so the
 * [PlaybackReload] coordinator carries the choreography ONCE and each
 * variant's divergence stays constructor arguments, not a bespoke body.
 * Variant names are the domain vocabulary:
 *
 *  - [RetryWithEngine]: the error dialog's engine picker (or its
 *    session-only live switch — [RetryWithEngine.persistPreferredPlayer] /
 *    [RetryWithEngine.restorePaused] carry that delta);
 *  - [RetrySameEngine]: the recoverable-error retry (Network, Render, the
 *    buffering watchdog timeout);
 *  - [FallbackToTranscode]: the forced-direct-play fallback — the position
 *    is caller-supplied because the reload that discovered the failure
 *    already swapped the engine once;
 *  - [StreamChange]: the server-origin audio/subtitle pick that a
 *    transcoding server must re-issue the stream for;
 *  - [SourceSwitch]: the Version sheet's media-source pick.
 */
internal sealed interface ReloadVariant {

    /**
     * Retry on a different engine. [persistPreferredPlayer] is TRUE for the
     * error-dialog retry (the choice becomes the saved default) and FALSE for
     * the session-only live switch; [restorePaused] is TRUE only for a
     * session-only switch that caught the engine paused — the swap must hand
     * playback back paused.
     */
    data class RetryWithEngine(
        val preferred: PlayerType,
        val streamingQuality: StreamingQuality,
        val playbackSpeed: Float,
        val persistPreferredPlayer: Boolean,
        val restorePaused: Boolean,
    ) : ReloadVariant

    /**
     * Same-engine retry for a recoverable error: never persists the engine
     * choice and never touches the pause state (the fold below pins both).
     */
    data class RetrySameEngine(
        val preferred: PlayerType,
        val streamingQuality: StreamingQuality,
        val playbackSpeed: Float,
    ) : ReloadVariant

    /**
     * The forced-direct-play fallback: persist FORCE_TRANSCODE, stop-report
     * the failed session, then re-resolve at [fromPositionMs] (the caller
     * captured it — reading it here would race the engine that the triggering
     * reload just swapped).
     */
    data class FallbackToTranscode(
        val fromPositionMs: Long,
        val quality: StreamingQuality,
        val selection: MediaStreamSelection?,
    ) : ReloadVariant

    /** Re-issues the stream for a new audio/subtitle [selection]. */
    data class StreamChange(val selection: MediaStreamSelection) : ReloadVariant

    /** Switches the playing version (media source) of the current item. */
    data class SourceSwitch(val mediaSourceId: String) : ReloadVariant
}

/**
 * The session retry entry points' fold into [ReloadVariant.RetryWithEngine]:
 * a plain error retry always lands the chosen engine as the new saved default
 * and never restores pause (the user acted on an error, not a pause button);
 * a session-only switch preserves BOTH the saved default and the pre-switch
 * pause state. Pure — the caller supplies the engine read.
 */
internal fun engineRetryVariant(
    playerType: PlayerType,
    streamingQuality: StreamingQuality,
    playbackSpeed: Float,
    sessionOnly: Boolean,
    enginePaused: Boolean,
): ReloadVariant =
    if (sessionOnly) {
        ReloadVariant.RetryWithEngine(
            preferred = playerType,
            streamingQuality = streamingQuality,
            playbackSpeed = playbackSpeed,
            persistPreferredPlayer = false,
            restorePaused = enginePaused,
        )
    } else {
        ReloadVariant.RetryWithEngine(
            preferred = playerType,
            streamingQuality = streamingQuality,
            playbackSpeed = playbackSpeed,
            persistPreferredPlayer = true,
            restorePaused = false,
        )
    }

/**
 * Whether [PlaybackReload.reload] persists the variant's engine choice as the
 * saved default. The retry pair's delta, isolated as a pure fold — the
 * coordinator branches on this, and PlaybackReloadTest pins it for every
 * variant.
 */
internal fun ReloadVariant.persistEngineChoice(): Boolean = when (this) {
    is ReloadVariant.RetryWithEngine -> persistPreferredPlayer
    else -> false
}

/**
 * Whether [PlaybackReload.reload] hands playback back paused after the swap.
 * Same-shaped fold as [persistEngineChoice]; only the session-only engine
 * switch carries it.
 */
internal fun ReloadVariant.restoresPausedState(): Boolean = when (this) {
    is ReloadVariant.RetryWithEngine -> restorePaused
    else -> false
}

/**
 * The reload family's module — the five reload shapes' shared swap
 * choreography (capture the position → cancel the reporter jobs →
 * release the media session → seed/clear the pending streams → swap through
 * the session manager → rebind tracking) behind ONE verb, [reload]. The
 * ordering rules are private implementation: the position is captured before
 * any teardown statement runs (a released engine reports nothing), and the
 * reporter jobs are cancelled before the media session is released (a dying
 * reporter must not re-publish against a session being torn down). The
 * variants carry only what genuinely differs between the shapes.
 *
 * Session-internal like its siblings ([SessionPositionLedger],
 * [PlayerUiCommands]): constructed by the session root, never injected. The
 * rebind tail stays session-owned ([PlaybackSession.rebindSessionTracking],
 * wired in as a lambda) because the non-reload adopt sites — the cinema
 * pre-rolls and the mini-player reclaim — share it without going through
 * [reload].
 */
internal class PlaybackReload(
    private val scope: CoroutineScope,
    private val playerSessionManager: PlayerSessionManager,
    private val positionLedger: SessionPositionLedger,
    private val progressReporter: PlaybackProgressReporter,
    private val mediaSessionController: MediaSessionController,
    private val playbackStore: PlaybackStore,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val setPendingStreams: (MediaStreamSelection?) -> Unit,
    private val setUiPlaybackMode: (PlaybackMode) -> Unit,
    private val rebindSessionTracking: (itemId: String) -> Unit,
) {

    /** The completed no-op job the engine-gated arms yield (the session's noLoadJob pattern). */
    private val noReloadJob: Job = Job().also { it.complete() }

    /**
     * Runs one reload variant. Returns the reload's job — the arms that
     * launch (everything but the gated no-op) run their choreography on
     * [scope].
     */
    fun reload(variant: ReloadVariant): Job = when (variant) {
        is ReloadVariant.RetryWithEngine -> retryWithEngine(variant)
        is ReloadVariant.RetrySameEngine -> retrySameEngine(variant)
        is ReloadVariant.FallbackToTranscode -> fallbackToTranscode(variant)
        is ReloadVariant.StreamChange -> streamChange(variant.selection)
        is ReloadVariant.SourceSwitch -> sourceSwitch(variant.mediaSourceId)
    }

    /**
     * The engine-retry pair's shared body — the difference between the
     * error-dialog retry and the same-engine retry is purely the variant's
     * deltas ([persistEngineChoice] / [restoresPausedState]).
     */
    private fun retryWithEngine(variant: ReloadVariant.RetryWithEngine): Job {
        // Position captured from the engine BEFORE any teardown statement —
        // the released engine would report nothing.
        val currentPos = playerSessionManager.engine?.currentPositionMs ?: 0L
        val maxBitrate = adaptiveBitrateManager.resolveMaxBitrate(variant.streamingQuality)?.toInt()
        progressReporter.cancelJobs()
        mediaSessionController.release()
        return scope.launch {
            if (variant.persistEngineChoice()) playbackStore.setPreferredPlayer(variant.preferred)
            playerSessionManager.reloadWithEngine(variant.preferred, currentPos, variant.playbackSpeed, maxBitrate)
            if (variant.restoresPausedState()) playerSessionManager.engine?.pause()
            rebindSessionTracking(playerSessionManager.sessionState.value.currentItemId ?: "")
        }
    }

    private fun retrySameEngine(variant: ReloadVariant.RetrySameEngine): Job =
        retryWithEngine(
            ReloadVariant.RetryWithEngine(
                preferred = variant.preferred,
                streamingQuality = variant.streamingQuality,
                playbackSpeed = variant.playbackSpeed,
                persistPreferredPlayer = false,
                restorePaused = false,
            ),
        )

    private fun fallbackToTranscode(variant: ReloadVariant.FallbackToTranscode): Job {
        // The mode mirror write is synchronous (the in-memory flip the user
        // already saw); the persisted write rides the reload launch.
        setUiPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
        return scope.launch {
            playbackStore.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
            positionLedger.reportCurrentPlaybackStopped()
            progressReporter.cancelJobs()
            setPendingStreams(variant.selection)
            playerSessionManager.reloadPlayback(
                PlaybackMode.FORCE_TRANSCODE,
                variant.quality,
                variant.fromPositionMs,
                variant.selection,
            )
            rebindSessionTracking(playerSessionManager.sessionState.value.currentItemId ?: "")
        }
    }

    /**
     * The two stream re-issue arms' shared gate: with no live engine there is
     * nothing to re-issue a stream for — the funnel short-circuits before the
     * load coroutine even launches.
     */
    private fun streamChange(selection: MediaStreamSelection): Job {
        if (playerSessionManager.engine == null) return noReloadJob
        val positionMs = positionLedger.getReportPositionMs()
        return scope.launch {
            setPendingStreams(selection)
            playerSessionManager.reloadForStreamChange(selection, positionMs)
        }
    }

    private fun sourceSwitch(mediaSourceId: String): Job {
        if (playerSessionManager.engine == null) return noReloadJob
        val positionMs = positionLedger.getReportPositionMs()
        return scope.launch {
            setPendingStreams(null)
            playerSessionManager.switchMediaSource(mediaSourceId, positionMs)
        }
    }
}
