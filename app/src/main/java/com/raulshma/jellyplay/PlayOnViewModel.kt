package com.raulshma.jellyplay

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.cast.CastDevice
import com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State surfaced to the "Play On" sheet + persistent mini bar at the app shell.
 */
@Immutable
data class PlayOnUiState(
    /** Jellyfin-remote sessions only (other JellyPlay / Jellyfin clients). */
    val devices: List<CastDevice> = emptyList(),
    val isDiscovering: Boolean = false,
    val isConnected: Boolean = false,
    /** Display name of the session we are currently controlling, if any. */
    val targetDeviceName: String? = null,
    /** True when there is a local item id we can fling. */
    val canFling: Boolean = false,
    // Transport — fed by the connected Jellyfin session's play state.
    val title: String = "",
    val artist: String = "",
    val artworkUri: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val volume: Float = 1f,
)

/**
 * Backs the global "Play On" entry point (Home FAB).
 *
 * Talks to [JellyfinRemotePlayCastStrategy] **directly** rather than through
 * the shared [com.raulshma.jellyplay.core.data.cast.CastManager]. This is
 * deliberate: [CastManager] holds a single global `activeStrategy` + connection
 * flag that the video player also reads (`VideoPlayerViewModel.isCastConnected`).
 * Routing Play On through it would (a) make the video player think it is
 * casting, hijacking it into companion mode, and (b) break when discovery
 * stops/the active strategy flips. The strategy's own flows
 * ([JellyfinRemotePlayCastStrategy.isConnected], [positionMs], …) are stable,
 * independent references, so Play On stays fully isolated from the player's
 * cast state.
 *
 * The PlayTo call is `JellyfinRemotePlayCastStrategy.loadMedia` →
 * `AdminApiClient.play(sessionId, "PlayNow", [itemId], …)`.
 */
@HiltViewModel
class PlayOnViewModel @Inject constructor(
    private val jellyfinStrategy: JellyfinRemotePlayCastStrategy,
    private val audioPlaybackManager: AudioPlaybackManager,
) : JellyPlayViewModel() {

    /** Shared singleton; exposed so the Home nav graph can short-circuit plays. */
    val strategy: JellyfinRemotePlayCastStrategy get() = jellyfinStrategy

    private val _targetDeviceName = MutableStateFlow<String?>(null)
    val targetDeviceName: StateFlow<String?> = _targetDeviceName.asStateFlow()

    private val canFling: StateFlow<Boolean> = audioPlaybackManager.currentPlayingItemId
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val devices: StateFlow<List<CastDevice>> = jellyfinStrategy.discoveredDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isConnected: StateFlow<Boolean> = jellyfinStrategy.isConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val uiState: StateFlow<PlayOnUiState> = combine(
        // devices + connected + target
        combine(
            devices,
            isConnected,
            targetDeviceName,
        ) { devs, connected, target -> DeviceState(devs, connected, target) },
        // transport + remote now-playing — straight off the strategy's own flows.
        // kotlinx.coroutines `combine` caps at 5 flows, so nest the metadata triple.
        combine(
            jellyfinStrategy.isPlaying,
            jellyfinStrategy.positionMs,
            jellyfinStrategy.durationMs,
            jellyfinStrategy.volume,
            combine(
                jellyfinStrategy.nowPlayingTitle,
                jellyfinStrategy.nowPlayingSubtitle,
                jellyfinStrategy.nowPlayingArtworkUrl,
            ) { t, s, art -> Triple(t, s, art) },
        ) { playing, pos, dur, vol, (title, subtitle, art) ->
            TransportState(playing, pos, dur, vol, title, subtitle, art)
        },
        // local now-playing metadata for the flingable item (fallback display)
        combine(
            audioPlaybackManager.title,
            audioPlaybackManager.artist,
            audioPlaybackManager.albumArtUrl,
        ) { title, artist, art -> NowPlayingState(title, artist, art) },
    ) { device, transport, nowPlaying ->
        // Prefer the remote session's reported now-playing; fall back to local
        // metadata (e.g. right after a fling before the server reflects it).
        val displayTitle = transport.title.ifBlank { nowPlaying.title }
        val displaySubtitle = if (transport.title.isNotBlank()) transport.subtitle else nowPlaying.artist
        // Remote poster takes precedence; only fall back to local art when the
        // session hasn't reported a now-playing item yet.
        val displayArt = transport.artworkUrl.ifBlank { nowPlaying.art }
        PlayOnUiState(
            devices = device.devices,
            isDiscovering = device.devices.isNotEmpty(),
            isConnected = device.connected,
            targetDeviceName = device.target,
            canFling = canFling.value,
            title = displayTitle,
            artist = displaySubtitle,
            artworkUri = displayArt,
            positionMs = transport.positionMs,
            durationMs = transport.durationMs,
            isPlaying = transport.playing,
            volume = transport.volume,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayOnUiState())

    fun startDiscovery(context: Context) {
        jellyfinStrategy.startDiscovery(context)
        startStatusPolling()
    }

    fun stopDiscovery() {
        jellyfinStrategy.stopDiscovery()
    }

    /**
     * Connect to [device] as the active remote player (mirrors jellyfin-web's
     * `trySetActivePlayer`). If audio is currently playing locally, fling it to
     * the remote and pause local. Subsequent local plays are intercepted at
     * [com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager.play] /
     * [com.raulshma.jellyplay.feature.player.video.VideoPlayerViewModel] (the
     * "remote is current player" delegation) — no reactive watcher needed.
     */
    fun connectAndFling(context: Context, device: CastDevice) {
        jellyfinStrategy.connect(context, device)
        _targetDeviceName.value = device.name
        startStatusPolling()
        val itemId = audioPlaybackManager.currentPlayingItemId.value
        if (itemId != null) {
            jellyfinStrategy.loadMedia(
                itemId = itemId,
                startPositionMs = audioPlaybackManager.currentPosition.value,
            )
            audioPlaybackManager.pause()
        }
    }

    fun castPlay() = jellyfinStrategy.play()
    fun castPause() = jellyfinStrategy.pause()
    fun castSeekTo(positionMs: Long) = jellyfinStrategy.seekTo(positionMs)
    fun setCastVolume(volume: Float) = jellyfinStrategy.setRendererVolume(volume)
    fun castNextTrack() = jellyfinStrategy.nextTrack()
    fun castPreviousTrack() = jellyfinStrategy.previousTrack()
    fun castStop(context: Context) {
        jellyfinStrategy.stop(context)
        _targetDeviceName.value = null
        statusPollingJob?.cancel()
        statusPollingJob = null
    }
    fun disconnect(context: Context) {
        jellyfinStrategy.disconnect(context)
        _targetDeviceName.value = null
        statusPollingJob?.cancel()
        statusPollingJob = null
    }

    // ---- internal ----

    private var statusPollingJob: kotlinx.coroutines.Job? = null

    /**
     * Fallback REST poll for the connected session's play state. Primary sync is
     * the WebSocket `Sessions` push handled inside the strategy; this is a
     * reliability net (slow cadence) for when the socket is laggy or the session
     * list changes off-push. Self-cancels on disconnect.
     */
    private fun startStatusPolling() {
        statusPollingJob?.cancel()
        statusPollingJob = launch {
            // Seed immediately, then poll slowly.
            jellyfinStrategy.refreshPlaybackState()
            while (isActive && jellyfinStrategy.isConnected.value) {
                delay(5_000L)
                jellyfinStrategy.refreshPlaybackState()
            }
        }
    }

    private data class DeviceState(
        val devices: List<CastDevice>,
        val connected: Boolean,
        val target: String?,
    )
    private data class TransportState(
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val volume: Float,
        val title: String,
        val subtitle: String,
        val artworkUrl: String,
    )
    private data class NowPlayingState(
        val title: String,
        val artist: String,
        val art: String?,
    )
}
