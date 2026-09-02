package com.raulshma.jellyplay.floating

import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Singleton bridge that exposes just enough playback state for the
 * [FloatingPlayerService]'s overlay UI — without giving the service a direct
 * dependency on the video player ViewModel or the media engine internals.
 *
 * **State sources:**
 * - Title / subtitle / itemId come from [VideoMiniPlayerState] (video) — set
 *   when the user enters floating mode from the video player.
 * - `isPlaying` is derived from [ActivePlayerController.activeEngine], which
 *   the video ViewModel binds on engine creation.
 * - `isActive` tracks whether the floating overlay is currently shown.
 *
 * The overlay UI collects these flows and renders a compact media controller.
 */
class FloatingPlayerState(
    private val activePlayerController: ActivePlayerController,
    private val miniPlayerState: VideoMiniPlayerState,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Whether the floating overlay window is currently visible. */
    private val _isActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    /** Media title (e.g. "Inception"). */
    val title: StateFlow<String> = miniPlayerState.title

    /** Media subtitle (e.g. "Continue watching" or artist name). */
    val subtitle: StateFlow<String> = miniPlayerState.subtitle

    /** Artwork image URL (null when no artwork is available). */
    private val _artworkUrl = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val artworkUrl: StateFlow<String?> = _artworkUrl

    /** Whether playback is currently active (playing, not paused). */
    val isPlaying: StateFlow<Boolean> = activePlayerController.activeEngine
        .map { it?.isPlaying?.value ?: false }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    /** The current item being played, if any. */
    val itemId: StateFlow<String?> = miniPlayerState.itemId

    /**
     * Called by [FloatingPlayerService.onCreate] to signal the overlay is
     * active. The video player can observe this to suppress its own UI.
     */
    fun onOverlayShown() {
        _isActive.value = true
    }

    /**
     * Called by [FloatingPlayerService.onDestroy] to signal the overlay has
     * been dismissed.
     */
    fun onOverlayHidden() {
        _isActive.value = false
    }

    /**
     * Updates the media metadata shown in the overlay.
     */
    fun updateMetadata(artworkUrl: String?) {
        _artworkUrl.value = artworkUrl
    }

    /**
     * Toggles play/pause on the currently active engine. No-op when no engine
     * is bound.
     */
    fun togglePlayPause() {
        val engine = activePlayerController.engine ?: return
        if (engine.isPlaying.value) engine.pause() else engine.play()
    }

    /**
     * Seeks forward by [deltaMs] milliseconds.
     */
    fun seekBy(deltaMs: Long) {
        val engine = activePlayerController.engine ?: return
        val newPos = (engine.currentPositionMs + deltaMs).coerceAtLeast(0)
        engine.seekTo(newPos)
    }
}
