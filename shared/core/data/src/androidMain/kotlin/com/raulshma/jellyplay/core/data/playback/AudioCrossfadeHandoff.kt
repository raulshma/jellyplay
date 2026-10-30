package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The crossfade execution half — the "Engine event verdict"'s decision/
 * execution split applied to the audio crossfade: [AudioCrossfader] stays the
 * decision half (the trigger gates, the volume ramp, the teardown), while this
 * module owns what the manager used to inline beside it:
 *
 *  - the [AudioCrossfader] construction — every manager-cell provider lambda
 *    (the former ctor wiring), so the cells are read at their source;
 *  - [onCrossfadeTransition] — the primary→secondary swap tail: the
 *    SYNCHRONOUS stop report, the track-handoff commit, the player swap, the
 *    MediaLibrarySession rebuild (the service-cast fix — a plain MediaSession
 *    rebuild made JellyPlayPlaybackService's cast return null, killing the
 *    notification + headset buttons), the effects re-attach, the windowed
 *    pre-warm and the start report;
 *  - [onCrossfadeFailed] — the reconciliation advance when a crossfade setup
 *    failed and the primary reaches STATE_ENDED with no auto-advance under
 *    REPEAT_MODE_OFF.
 *
 * The two blocks both transition sites share stay manager-owned and arrive as
 * ports: [commitTransition] (the ONE track-handoff reconcile block, also read
 * by the natural-transition path) and [startReport] (the start-report shape).
 * The manager keeps only the pass-through call sites — its `crossfader` reads
 * [crossfader], `isCrossfading` reads [isCrossfading].
 *
 * internal (the manager and the mirror it wires are): constructed only by
 * [AudioPlaybackManager] and the androidHostTest handoff suite.
 */
internal class AudioCrossfadeHandoff(
    private val scope: CoroutineScope,
    private val context: Context,
    private val effectsProcessor: AudioEffectsProcessor,
    /** The detail-read family seam (the crossfader's next-item resolve). */
    private val detailFeed: DetailFeed,
    /** Artwork URL for the incoming crossfade row (the ImageUrlProvider seam). */
    private val imageUrlProvider: ImageUrlProvider,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val playbackRepository: PlaybackRepository,
    /** The stop report at the swap (synchronously, BEFORE the writes below). */
    private val progressReporter: AudioProgressReporter,
    /** The shared audio-session builder — the ONE MediaLibrarySession construction path. */
    private val libraryBrowser: AudioLibraryBrowser,
    private val sessionManager: PlaybackSessionManager,
    /** The windowed pre-warm of the queue mirror around the promoted row. */
    private val queueMirror: QueuePlaylistMirror,
    /** The queue-state chassis — the cells the wiring reads and the tail writes. */
    private val state: AudioQueueStateCore,
    /** The primary's listener — detached from the dying primary, added to the promoted secondary. */
    private val playerListener: Player.Listener,
    /** The live-engine read (the crossfader's primary + the failed-path advance). */
    private val exoPlayerProvider: () -> ExoPlayer?,
    /** The swap write — the promoted secondary BECOMES the live engine. */
    private val exoPlayerSetter: (ExoPlayer) -> Unit,
    /** The manager's current MediaSession (released at the rebuild), null when none. */
    private val mediaSessionProvider: () -> MediaSession?,
    private val mediaSessionSetter: (MediaSession?) -> Unit,
    /** The manager's audio preload buffer pair (the crossfade player's load control). */
    private val audioBufferProvider: () -> Pair<Int, Int>,
    /** The byte-cache-wrapped data source factory (the crossfade player's transport). */
    private val dataSourceFactoryProvider: () -> androidx.media3.datasource.DataSource.Factory,
    /** The shared track-handoff reconcile block (manager-owned, both transition sites). */
    private val commitTransition: (nextIndex: Int, nextItem: AudioQueueItem, reapplyReplayGain: Boolean) -> Unit,
    /** The start-report shape both transition sites send after the handoff. */
    private val startReport: (item: AudioQueueItem) -> PlaybackStartInfo,
    /** Test seam: the crossfade secondary factory (defaults to the production build). */
    crossfadePlayerFactory: (() -> ExoPlayer)? = null,
) {

    private val _isCrossfading = MutableStateFlow(false)

    /** The in-flight crossfade flag — the manager re-exposes it by reference. */
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    /**
     * The decision half, wired to this manager's cells. The manager's
     * `crossfader` delegates here — cancel/pause/setVolume/setPlaybackSpeed/
     * maybeStart keep their existing call sites.
     */
    val crossfader = AudioCrossfader(
        scope = scope,
        context = context,
        effectsProcessor = effectsProcessor,
        detailFeed = detailFeed,
        imageUrlProvider = imageUrlProvider,
        playbackSourceResolver = playbackSourceResolver,
        repeatModeProvider = { state.repeatMode.value },
        crossfadeDurationMsProvider = { state.crossfadeDurationMs.value },
        isCrossfadingProvider = { _isCrossfading.value },
        isCrossfadingSetter = { _isCrossfading.value = it },
        exoPlayerProvider = exoPlayerProvider,
        queueSizeProvider = { state.queue.value.size },
        onGetNextItem = { idx -> state.queue.value.getOrNull(idx) },
        speedProvider = { state.speed.value },
        audioBufferProvider = audioBufferProvider,
        onCrossfadeTransition = { secondary, nextIndex, nextItem ->
            onCrossfadeTransition(secondary, nextIndex, nextItem)
        },
        detachPrimaryListener = { primary -> primary.removeListener(playerListener) },
        onCrossfadeError = { error -> playerListener.onPlayerError(error) },
        onCrossfadeFailed = { nextIndex -> onCrossfadeFailed(nextIndex) },
        dataSourceFactoryProvider = dataSourceFactoryProvider,
        crossfadePlayerFactory = crossfadePlayerFactory,
    )

    /**
     * The crossfade swap tail ([AudioCrossfader]'s `onCrossfadeTransition`).
     *
     * The crossfade site's load-bearing ordering: the stop report runs
     * SYNCHRONOUSLY before the swap/writes below (see the manager's
     * commitTrackTransition KDoc for why this is not folded in — the natural-
     * transition site launches stop+start AFTER the writes, and no single
     * placement is faithful to both).
     *
     * internal: the androidHostTest handoff suite drives the tail directly
     * (the chassis's internal-for-test-seams idiom) — on the production path
     * only the [crossfader] wiring below calls it.
     */
    internal suspend fun onCrossfadeTransition(secondary: ExoPlayer, nextIndex: Int, nextItem: AudioQueueItem) {
        val prevItemId = state.currentItemId
        val prevSessionId = state.playSessionId
        val prevPosTicks = AudioQueuePolicy.finalStopPositionTicks(
            positionMs = state.currentPosition.value,
            durationMs = state.duration.value,
        )

        progressReporter.reportStopped(
            itemId = prevItemId,
            sessionId = prevSessionId,
            positionTicks = prevPosTicks,
        )

        // reapplyReplayGain = false: the wholesale effects re-attach below
        // stands in for the single-row ReplayGain re-apply the
        // natural-transition site does.
        commitTransition(nextIndex, nextItem, false)

        exoPlayerSetter(secondary)

        mediaSessionProvider()?.release()
        // Rebuild via the shared audio-session builder (AudioLibraryBrowser is
        // the single construction path for both the initial session and the
        // crossfade rebuild). JellyPlayPlaybackService.onGetSession casts the
        // active session to MediaLibrarySession; a plain MediaSession rebuild
        // — the pre-fix behaviour — cast to null, so the service rejected
        // controller connections, killing the now-playing notification and
        // headset buttons until app restart. Mirrors the video fix
        // (MediaSessionController).
        val newSession = libraryBrowser.buildMediaSession(context, secondary, state.playSessionId)
        mediaSessionSetter(newSession)
        sessionManager.setActiveSession(newSession)

        secondary.addListener(playerListener)
        effectsProcessor.applyNightMode()
        effectsProcessor.applyDialogueBoost()
        effectsProcessor.applyEqualizer()
        effectsProcessor.applyBassBoost()
        effectsProcessor.applyVirtualizer()
        effectsProcessor.reattachForCrossfade(secondary.audioSessionId)

        _isCrossfading.value = false

        // Same windowed mirror as the play-path pre-warm: full prefix
        // below the crossfaded row, bounded lookahead ahead of it.
        queueMirror.prewarmAround(secondary, nextIndex)

        playbackRepository.reportPlaybackStart(startReport(nextItem))
    }

    /**
     * Invoked by [AudioCrossfader] when a crossfade setup fails (e.g. a
     * network error fetching the next item's detail). In that case the primary
     * ExoPlayer keeps playing the current track to its end and reaches
     * `STATE_ENDED`; under `REPEAT_MODE_OFF` ExoPlayer neither auto-advances
     * nor fires `onMediaItemTransition`, so `_currentIndex` would otherwise
     * stay stuck on the ended item and desync from the queue/UI highlight.
     *
     * We proactively advance the primary player to [nextIndex], which fires
     * `onMediaItemTransition` → the manager's `onTrackTransitioned` for full
     * reconciliation (title/artist/lyrics/replayGain/index). Mirrors the
     * manual skipToNext advance path.
     *
     * internal: the androidHostTest handoff suite drives it directly — on the
     * production path only the [crossfader] wiring below calls it.
     */
    internal fun onCrossfadeFailed(nextIndex: Int) {
        scope.launch(Dispatchers.Main) {
            val player = exoPlayerProvider() ?: return@launch
            val q = state.queue.value
            if (nextIndex !in q.indices) return@launch
            state._currentIndex.value = nextIndex
            player.seekTo(nextIndex, 0L)
            player.prepare()
            player.playWhenReady = true
        }
    }
}
