package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.state.AutoplayState
import com.raulshma.jellyplay.feature.player.video.state.GesturePrefsState
import com.raulshma.jellyplay.feature.player.video.state.PlayerUiPrefsState
import com.raulshma.jellyplay.feature.player.video.state.VideoFxState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The session-owned pref write choreography (the [TransportRouter] shape):
 * the ViewModel's former private pref setters — the mirror-write-then-persist
 * funs whose choreography ORDER is load-bearing — moved beside the
 * collaborators they drive and constructed inside [PlaybackSession]. The
 * class is named for what its members have in common: none of them persist an
 * aspect ratio ([setAspectRatio] is a pure mirror write), everything else
 * writes a datastore slice after (or beside) the uiState mirror.
 *
 * The state bag never crosses (the migrated-controller guard): every
 * uiState read/write arrives as a slice-scoped lens lambda — the
 * [PlayerUiPrefsState]/[VideoFxState]/[GesturePrefsState]/[AutoplayState]
 * reads + transforms the session derives over its [PlayerStateHandles] — the
 * same narrow-lambda discipline every migrated controller has.
 *
 * Preserved choreography (the former per-setter bodies, verbatim):
 *  - the mirror write precedes the persist launch;
 *  - the sibling quality is captured from the SAME pre-write snapshot the
 *    guard read, never read back after the mirror write;
 *  - an ABR change resolves the reload against the pre-change snapshot
 *    (it changes neither mode nor quality tier);
 *  - [toggleMute] writes the engine BEFORE the mirror (the engine's mute
 *    state is the truth; the mirror only projects it);
 *  - the brightness persist is gated on the remember-brightness pref read
 *    AFTER the mirror write;
 *  - [setAspectRatio] persists nothing.
 */
internal class PlayerUiCommands(
    private val scope: CoroutineScope,

    // ── The uiState mirror lenses (slice-scoped read + write) ────────────────

    /** The ui-prefs slice (streaming quality / playback mode / stats toggle). */
    private val uiPrefs: () -> PlayerUiPrefsState,
    private val updateUiPrefs: ((PlayerUiPrefsState) -> PlayerUiPrefsState) -> Unit,
    /** The videoFx slice (aspect ratio + video effects). */
    private val updateVideoFx: ((VideoFxState) -> VideoFxState) -> Unit,
    /** The gestures slice (brightness level + its remember flag). */
    private val updateGestures: ((GesturePrefsState) -> GesturePrefsState) -> Unit,
    private val rememberBrightness: () -> Boolean,
    /** The mute mirror (the engine write precedes this write in [toggleMute]). */
    private val isMuted: () -> Boolean,
    private val setMuted: (Boolean) -> Unit,
    /** The autoplay slice (the in-player Up Next card's preference mirror). */
    private val updateAutoplay: ((AutoplayState) -> AutoplayState) -> Unit,
    /** The media streams, for the AUTO aspect-ratio detection. */
    private val getMediaStreams: () -> List<MediaStream>,

    // ── Stores + collaborators ───────────────────────────────────────────────

    private val playbackStore: PlaybackStore,
    private val networkOfflineStore: NetworkOfflineStore,
    private val videoPlayerStore: VideoPlayerStore,
    private val engineStore: PlayerEngineStore,
    /** The remember-muted gate for [toggleMute]'s persist (the aggregate slice). */
    private val rememberMutedEnabled: () -> Boolean,
    private val getCurrentItemId: () -> String?,
    /** Re-read per use, so engine swaps between calls are observed. */
    private val getEngine: () -> MediaEngine?,
    /** The direct-play fallback latch reset ([setPlaybackMode]'s explicit change). */
    private val onPlaybackModeChanged: () -> Unit,
    /** The session's reload choreography (the [PlaybackSession.reloadForMode] seam). */
    private val reloadForMode: suspend (mode: PlaybackMode, quality: StreamingQuality, selection: MediaStreamSelection?) -> Unit,
    private val markEngineConfigDirtyDebounced: () -> Unit,
    /** The autoplay controller's preference flip ([setVideoAutoplayNext]). */
    private val setAutoplayEnabled: (Boolean) -> Unit,
    /** The effects controller's settle-gated per-item persist ([setVideoEffectsSettled]). */
    private val persistVideoEffects: (VideoEffectsConfig) -> Unit,
) {

    /**
     * Serializes the mode/quality persist-then-reload sequence: the options
     * sheet stays open across picks, and without the mutex a quick second tap
     * could let the first change's reload resolve against the stale mirror.
     */
    private val playbackPreferenceMutex = Mutex()

    fun setAspectRatio(ratio: AspectRatio) {
        updateVideoFx { it.copy(aspectRatio = ratio) }
        if (ratio == AspectRatio.AUTO) {
            val detected = detectAspectRatio(getMediaStreams())
            updateVideoFx { it.copy(detectedAspectRatio = detected) }
        }
        // The PiP aspect ratio always tracks the underlying media, independent
        // of the in-app resize mode, so it does not need re-deriving here.
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        val prefs = uiPrefs()
        if (prefs.playbackMode == mode) return
        // User explicitly changed the mode — re-arm the direct-play fallback so
        // a future FORCE_DIRECT_PLAY attempt can fail-and-retry again.
        onPlaybackModeChanged()
        // The sibling quality the reload must still resolve against, captured
        // from the SAME pre-write snapshot the guard read (this setter does not
        // change it) — never read back after the mirror write.
        val quality = prefs.streamingQuality
        updateUiPrefs { it.copy(playbackMode = mode) }
        applyPlaybackPrefChange(
            mode = mode,
            quality = quality,
            persist = { playbackStore.setPlaybackMode(mode) },
        )
    }

    fun setStreamingQuality(quality: StreamingQuality) {
        val prefs = uiPrefs()
        if (prefs.streamingQuality == quality) return
        val mode = prefs.playbackMode
        updateUiPrefs { it.copy(streamingQuality = quality) }
        applyPlaybackPrefChange(
            mode = mode,
            quality = quality,
            persist = { playbackStore.setStreamingQuality(quality) },
        )
    }

    /**
     * Toggles adaptive bitrate (the AUTO-mode network cap). Persisted and
     * re-resolved immediately so the cap change takes effect for the running
     * stream: disabling it drops the cap so the server direct-plays instead of
     * transcoding high-bitrate media. ABR changes NEITHER the mode nor the
     * quality tier — the reload resolves against the pre-change mirror
     * snapshot (captured synchronously, before any suspension can interleave
     * a projection); it re-resolves at all because the resolved cap feeds the
     * server's PlaybackInfo decision.
     */
    fun setAdaptiveBitrateEnabled(enabled: Boolean) {
        val prefs = uiPrefs()
        if (prefs.adaptiveBitrateEnabled == enabled) return
        val mode = prefs.playbackMode
        val quality = prefs.streamingQuality
        updateUiPrefs { it.copy(adaptiveBitrateEnabled = enabled) }
        applyPlaybackPrefChange(
            mode = mode,
            quality = quality,
            persist = { networkOfflineStore.setAdaptiveBitrateEnabled(enabled) },
        )
    }

    /**
     * The playback-pref write choreography shared by [setPlaybackMode],
     * [setStreamingQuality] and [setAdaptiveBitrateEnabled]: launch →
     * persist the caller-specific store write → reload the running session
     * with EXPLICIT [mode]/[quality] values. The reload never reads the
     * ui-prefs mirror back — the setters' mirror writes stay a pure UI
     * projection, and each caller passes exactly the post-change values
     * (its own new argument for the pref it changed; the pre-write snapshot
     * for the siblings it did not). The relative order mirror-write → launch
     * is unchanged from the former per-setter bodies.
     */
    private fun applyPlaybackPrefChange(
        mode: PlaybackMode,
        quality: StreamingQuality,
        persist: suspend () -> Unit,
    ) {
        scope.launch {
            // The options sheet stays open across picks. Keep mode and quality
            // reloads ordered so an older server response cannot win the race.
            playbackPreferenceMutex.withLock {
                persist()
                reloadPlaybackForMode(mode = mode, quality = quality)
            }
        }
    }

    /**
     * Thin delegate to [PlaybackSession.reloadForMode]: supplies the caller's
     * post-change mode + quality (passed in explicitly — this never
     * reads the ui-prefs mirror) and the stored per-item stream selection
     * from the engine store (the session never reads either); the session
     * owns the stop-report / reload / selection re-arm / media-session-rebuild
     * choreography and surfaces its transcode notices as
     * [SessionEvent.InformUser]s.
     */
    private suspend fun reloadPlaybackForMode(mode: PlaybackMode, quality: StreamingQuality) {
        // Carry the stored per-item stream selection into the re-POST: the
        // server bakes one audio track into a transcoded manifest and burns in
        // image subs, so dropping the indices would reset those choices. The
        // client-side selection is re-armed separately (setPendingStreams inside
        // PlaybackSession.reloadForMode).
        val itemId = getCurrentItemId()
        val selection = itemId?.let { engineStore.playerEngine.value.mediaStreamSelections[it] }
        // Positional args — the reload seam is a function-typed property,
        // named args are prohibited.
        reloadForMode(mode, quality, selection)
    }

    fun setVideoEffects(effects: VideoEffectsConfig) {
        updateVideoFx { it.copy(videoEffects = effects) }
        markEngineConfigDirtyDebounced()
    }

    fun setVideoEffectsSettled(effects: VideoEffectsConfig) {
        setVideoEffects(effects)
        // The settle-gated per-item persist lives on the effects
        // controller (item + cinema gates inside) — this stays a thin caller.
        persistVideoEffects(effects)
    }

    fun saveBrightness(level: Float) {
        updateGestures { it.copy(brightnessLevel = level) }
        if (rememberBrightness()) {
            scope.launch {
                videoPlayerStore.setVideoBrightnessLevel(level)
            }
        }
    }

    fun toggleVideoStats() {
        val newValue = !uiPrefs().showVideoStats
        updateUiPrefs { it.copy(showVideoStats = newValue) }
        getEngine()?.setVideoStatsEnabled(newValue)
    }

    fun toggleMute() {
        val engine = getEngine() ?: return
        val currentlyMuted = isMuted()
        val nowMuted = !currentlyMuted
        engine.setMuted(nowMuted)
        setMuted(nowMuted)
        if (rememberMutedEnabled()) {
            scope.launch { videoPlayerStore.setVideoMuted(nowMuted) }
        }
    }

    /** Toggle the autoplay-next-episode preference from the in-player Up Next card. */
    fun setVideoAutoplayNext(enabled: Boolean) {
        updateAutoplay { it.copy(videoAutoplayNext = enabled) }
        setAutoplayEnabled(enabled)
        scope.launch { videoPlayerStore.setVideoAutoplayNext(enabled) }
    }
}

/**
 * The composition wiring for [PlaybackSession.uiCommands] — moved out of the
 * session body (the composition root's line guard) into the module being
 * wired, the [transportRouter] move's twin. Reads the session's internal
 * collaborators through the receiver (lazily — the lambdas defer every read
 * of the later-declared autoplay/effects controllers, the usual
 * construction-order rule) and the owner-private handles/stores through the
 * parameters; the uiState never crosses by name (the narrow-lens discipline —
 * the bag stays inference-only here too).
 */
internal fun PlaybackSession.playerUiCommands(
    handles: PlayerStateHandles,
    stores: PlayerStores,
    playbackStore: PlaybackStore,
): PlayerUiCommands {
    val uiState = handles.uiState
    return PlayerUiCommands(
        scope = scope,
        uiPrefs = { uiState.value.uiPrefs },
        updateUiPrefs = { transform -> uiState.update { it.copy(uiPrefs = transform(it.uiPrefs)) } },
        updateVideoFx = { transform -> uiState.update { it.copy(videoFx = transform(it.videoFx)) } },
        updateGestures = { transform -> uiState.update { it.copy(gestures = transform(it.gestures)) } },
        rememberBrightness = { uiState.value.gestures.rememberBrightness },
        isMuted = { uiState.value.isMuted },
        setMuted = { muted -> uiState.update { it.copy(isMuted = muted) } },
        updateAutoplay = { transform -> uiState.update { it.copy(autoplay = transform(it.autoplay)) } },
        getMediaStreams = { uiState.value.media.mediaStreams },
        playbackStore = playbackStore,
        networkOfflineStore = stores.networkOffline,
        videoPlayerStore = stores.videoPlayer,
        engineStore = stores.engine,
        rememberMutedEnabled = { stores.aggregateStore.aggregate.value.videoPlayer.videoRememberMuted },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getEngine = { playerSessionManager.engine },
        onPlaybackModeChanged = { engineEventCoordinator.onPlaybackModeChanged() },
        reloadForMode = { mode, quality, selection -> reloadForMode(mode, quality, selection) },
        markEngineConfigDirtyDebounced = { markEngineConfigDirtyDebounced() },
        setAutoplayEnabled = { enabled -> autoplayController.setEnabled(enabled) },
        // Lazy lambda, not a bound reference — the effects controller is
        // declared later in the composition root (the usual lazy-read rule).
        persistVideoEffects = { fx -> effects.persistVideoEffects(fx) },
    )
}
