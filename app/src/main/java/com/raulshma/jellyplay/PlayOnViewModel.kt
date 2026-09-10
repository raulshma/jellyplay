package com.raulshma.jellyplay

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.cast.CastDevice
import com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * State surfaced to the "Play On" sheet + persistent mini bar at the app shell.
 *
 * Declared delta: the `canFling` field is deleted. It was a
 * `WhileSubscribed` stateIn over the audio manager's current item id whose
 * value the fold read via `.value` — and because nothing ever collected that
 * flow, it sat at its initial `false` forever while the fold kept re-stamping
 * the dead value into every emission. Deleted rather than wired live: no
 * surface ever rendered it (the fold was its only reader), and both real
 * fling decisions ([connectAndFling], [flingIfConnected]) read the item id /
 * the connection directly, so a live projection would still have no consumer.
 *
 * Declared delta: the `positionMs` / `durationMs` / `volume`
 * fields are deleted. During a cast session the strategy updates `positionMs`
 * ~1 Hz off WebSocket session pushes, and folding it here re-emitted uiState
 * on every tick — recomposing the whole app-shell scope (PhoneContent:
 * MainNavDisplay + nav bar + mini player) for the entire session. The per-tick
 * fields now live as narrow [PlayOnViewModel.positionMsFlow] /
 * [PlayOnViewModel.durationMsFlow] / [PlayOnViewModel.volumeFlow] StateFlows
 * that only the leaf sliders rendering them collect — the same
 * leaf-collection rule the video player pins for its 4 Hz position stream
 * (VideoPlayerScreen's ChapterPickerBinder / TvControllableSeekBar). Nothing
 * left in this fold changes per tick: connection, metadata and play/pause
 * events only.
 */
@Immutable
data class PlayOnUiState(
    /** Jellyfin-remote sessions only (other JellyPlay / Jellyfin clients). */
    val devices: List<CastDevice> = emptyList(),
    val isDiscovering: Boolean = false,
    val isConnected: Boolean = false,
    /** Display name of the session we are currently controlling, if any. */
    val targetDeviceName: String? = null,
    // Transport — fed by the connected Jellyfin session's play state.
    val title: String = "",
    val artist: String = "",
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
)

/**
 * The "Play On" controller — the one home for the whole Play On surface
 * family (the persistent mini bar, the device sheet and the full-screen
 * companion): device discovery, connect + fling, the 5 s status poll and the
 * [uiState] metadata-precedence fold all live here. It is constructed ONCE
 * at the shell (`MainContent` in JellyPlayApp, above the TV / phone /
 * full-screen fork) and threaded to every surface as an explicit parameter;
 * no surface resolves it itself. The companion screen used to self-resolve
 * through `koinViewModel()` — an identity that held only because both call
 * sites happened to sit under MainActivity's ViewModelStoreOwner, and would
 * have silently forked state the moment the screen moved to another host.
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
 * cast state. The strategy is PRIVATE to this controller: the transport
 * commands below are the narrow surface the screens drive, and the Home nav
 * graph's probe + fling rides [flingIfConnected] (adapted into its
 * [com.raulshma.jellyplay.feature.home.navigation.HomePlayOnRedirect] seam
 * at `MainNavDisplay`) — nothing outside reaches the strategy anymore.
 *
 * The PlayTo call is `JellyfinRemotePlayCastStrategy.loadMedia` →
 * `AdminApiClient.play(sessionId, "PlayNow", [itemId], …)`.
 */
class PlayOnViewModel(
    private val jellyfinStrategy: JellyfinRemotePlayCastStrategy,
    private val audioPlaybackManager: AudioPlaybackManager,
) : JellyPlayViewModel() {

    private val _targetDeviceName = MutableStateFlow<String?>(null)
    val targetDeviceName: StateFlow<String?> = _targetDeviceName.asStateFlow()

    val devices: StateFlow<List<CastDevice>> = jellyfinStrategy.discoveredDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isConnected: StateFlow<Boolean> = jellyfinStrategy.isConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Per-tick transport streams, deliberately OUTSIDE [uiState]: during a
    // connected session the strategy updates `positionMs` ~1 Hz off WebSocket
    // session pushes, and the former uiState fold included position/duration/
    // volume — so every tick re-emitted uiState and recomposed the entire app
    // shell that collects it. Exposed as narrow StateFlows so only the leaf
    // sliders that render them (the mini bar's + companion's seek/volume
    // rows) subscribe and recompose. Initials mirror the strategy's own
    // StateFlow seeds (0L / 0L / 1f), which are also what the former uiState
    // defaults carried.
    val positionMsFlow: StateFlow<Long> = jellyfinStrategy.positionMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val durationMsFlow: StateFlow<Long> = jellyfinStrategy.durationMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val volumeFlow: StateFlow<Float> = jellyfinStrategy.volume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    val uiState: StateFlow<PlayOnUiState> = combine(
        // devices + connected + target
        combine(
            devices,
            isConnected,
            targetDeviceName,
        ) { devs, connected, target -> DeviceState(devs, connected, target) },
        // transport + remote now-playing — straight off the strategy's own
        // flows, minus the per-tick position/duration/volume (narrow flows
        // above): only play/pause flips and metadata changes re-emit.
        combine(
            jellyfinStrategy.isPlaying,
            jellyfinStrategy.nowPlayingTitle,
            jellyfinStrategy.nowPlayingSubtitle,
            jellyfinStrategy.nowPlayingArtworkUrl,
        ) { playing, title, subtitle, art ->
            TransportState(playing, title, subtitle, art)
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
            title = displayTitle,
            artist = displaySubtitle,
            artworkUri = displayArt,
            isPlaying = transport.playing,
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

    /**
     * The Home nav graph's probe + fling — the body behind its
     * [com.raulshma.jellyplay.feature.home.navigation.HomePlayOnRedirect]
     * seam: when a remote session is connected, fling [itemId] to it and
     * return `true` (the caller skips local playback routing); otherwise
     * return `false` having issued nothing. `loadMedia` itself no-ops when
     * the session died between the probe and the call, so collapsing
     * probe + call into one member changes nothing observable.
     */
    fun flingIfConnected(itemId: String, startPositionMs: Long): Boolean =
        jellyfinStrategy.isConnected.value.also { connected ->
            if (connected) {
                jellyfinStrategy.loadMedia(itemId = itemId, startPositionMs = startPositionMs)
            }
        }

    // ---- transport ----
    // The narrow command surface every Play On surface drives (mini bar,
    // companion). Each is a declared one-line pass-through onto the strategy —
    // the strategy remains the transport home (it owns the session-id guards
    // and the admin-API play/pause/seek commands); what is gone is the
    // facade-era wide escape hatch: the strategy itself is no longer exposed,
    // so this list is the ONLY way in.

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
    // Position/duration/volume ride their own narrow flows — see the
    // positionMsFlow block above for why they left this fold.
    private data class TransportState(
        val playing: Boolean,
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
