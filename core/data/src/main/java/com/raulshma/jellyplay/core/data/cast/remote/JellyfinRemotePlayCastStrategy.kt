package com.raulshma.jellyplay.core.data.cast.remote

import android.content.Context
import androidx.compose.runtime.Stable
import android.util.Log
import com.raulshma.jellyplay.core.data.cast.CastDevice
import com.raulshma.jellyplay.core.data.cast.CastStrategy
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.network.api.AdminApiClient
import com.raulshma.jellyplay.core.network.websocket.parseSessionsMessage
import com.raulshma.jellyplay.core.network.websocket.toSessionInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Stable
class JellyfinRemotePlayCastStrategy(
    private val appContext: Context,
    private val adminApiClient: AdminApiClient,
    private val serverIdentityStore: ServerIdentityStore,
    private val webSocketClient: com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient,
    private val imageUrlProvider: ImageUrlProvider,
) : CastStrategy {

    companion object {
        private const val TAG = "JellyfinRemotePlayCastStrategy"
        private const val STRATEGY_NAME = "jellyfin"
    }

    /**
     * Settings.Secure.ANDROID_ID — the device id the Jellyfin SDK defaulted to before
     * [com.raulshma.jellyplay.core.network.di.NetworkModule.provideJellyfin] pinned the SDK
     * id to the DataStore UUID. Read to match a possibly still-live server session.
     */
    @android.annotation.SuppressLint("HardwareIds")
    private fun legacyAndroidId(): String =
        android.provider.Settings.Secure.getString(
            appContext.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        ) ?: ""

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Lenient decoder for the WebSocket `Sessions` push payloads. */
    private val sessionsJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    override val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<CastDevice>> = _discoveredDevices.asStateFlow()

    // Playback state flows
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    // Now-playing metadata from the connected remote session (filled by
    // [refreshPlaybackState]). Empty until the remote session reports something.
    private val _nowPlayingTitle = MutableStateFlow("")
    val nowPlayingTitle: StateFlow<String> = _nowPlayingTitle.asStateFlow()

    private val _nowPlayingSubtitle = MutableStateFlow("")
    val nowPlayingSubtitle: StateFlow<String> = _nowPlayingSubtitle.asStateFlow()

    // Item id of the remote session's now-playing item. Drives the artwork URL
    // exposed below — the remote's poster is distinct from the local now-playing
    // art, so we must resolve it from the session, not the local player.
    private val _nowPlayingItemId = MutableStateFlow("")
    val nowPlayingItemId: StateFlow<String> = _nowPlayingItemId.asStateFlow()

    // Poster URL derived from [nowPlayingItemId]. Blank when the remote reports
    // no now-playing item; consumers fall back to local now-playing art.
    val nowPlayingArtworkUrl: StateFlow<String> = _nowPlayingItemId
        .map { id -> if (id.isNotBlank()) imageUrlProvider.getImageUrl(id) else "" }
        .stateIn(scope, SharingStarted.Eagerly, "")

    /** Display name of the session we are currently connected to (for UI). */
    private val _targetName = MutableStateFlow<String?>(null)
    val targetName: StateFlow<String?> = _targetName.asStateFlow()

    @Volatile
    private var connectedSessionId: String? = null

    private var discoveryJob: Job? = null
    private var statusPollingJob: Job? = null

    /**
     * Subscribes to the shared [webSocketClient]'s `Sessions` push (server emits
     * session state changes) and mirrors them onto our transport flows. This is
     * jellyfin-web's SessionPlayer transport — real-time, no REST polling. Also
     * auto-disconnects when the connected session disappears from the list.
     */
    private var sessionsObserverJob: Job? = null

    @Volatile
    private var discoveryActive = false

    override fun startDiscovery(context: Context) {
        if (discoveryActive) return
        discoveryActive = true
        startDiscoveryLoop()
    }

    override fun stopDiscovery() {
        discoveryActive = false
        discoveryJob?.cancel()
        discoveryJob = null
        _discoveredDevices.value = emptyList()
        _isAvailable.value = false
    }

    private fun startDiscoveryLoop() {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            while (discoveryActive) {
                try {
                        val ownDeviceId = serverIdentityStore.ensureDeviceId()
                        val sessionsResult = adminApiClient.getSessions()
                        if (sessionsResult.isSuccess) {
                            val sessions = sessionsResult.getOrThrow()
                            // The legacy device id is Settings.Secure.ANDROID_ID, which the
                            // Jellyfin SDK used as its default device id before we pinned it to
                            // the DataStore UUID. Sessions registered under it may still be live
                            // server-side and must be recognized as self too, or the app would
                            // list its own (stale) session as a cast target.
                            val legacyDeviceId = legacyAndroidId()
                            val controllableSessions = sessions.filter {
                                it.supportsRemoteControl &&
                                    it.deviceId.isNotBlank() &&
                                    !it.deviceId.equals(ownDeviceId, ignoreCase = true) &&
                                    !it.deviceId.equals(legacyDeviceId, ignoreCase = true)
                            }
                        _discoveredDevices.value = controllableSessions.map { session ->
                            val displayName = buildString {
                                val devName = session.deviceName.ifBlank { session.client }
                                append(devName)
                                if (session.deviceId.isNotBlank()) {
                                    append(" (${session.deviceId.take(8)})")
                                }
                                if (session.userName.isNotBlank()) {
                                    append(" - ${session.userName}")
                                }
                            }
                            CastDevice(
                                id = session.id,
                                name = displayName,
                                type = "jellyfin",
                                tag = session,
                                strategyName = STRATEGY_NAME
                            )
                        }
                        _isAvailable.value = controllableSessions.isNotEmpty()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error discovering controllable sessions", e)
                }
                delay(5000L)
            }
        }
    }

    override fun connect(context: Context, device: CastDevice) {
        if (_isConnected.value) {
            disconnect(context)
        }

        val session = device.tag as? SessionInfo ?: return
        _isConnecting.value = true
        connectedSessionId = session.id
        _targetName.value = device.name
        _isConnected.value = true
        _isConnecting.value = false

        // Real-time sync: subscribe to the server's `Sessions` WebSocket push so
        // transport state (position / play-pause / now-playing) reflects the
        // remote client without REST polling. Mirrors jellyfin-web SessionPlayer.
        startSessionsObserver()

        Log.i(TAG, "Connected to Jellyfin remote session: ${session.userName} (${session.client})")
    }

    override fun disconnect(context: Context) {
        statusPollingJob?.cancel()
        statusPollingJob = null
        sessionsObserverJob?.cancel()
        sessionsObserverJob = null
        connectedSessionId = null
        _targetName.value = null
        _isConnected.value = false
        _isConnecting.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
        _nowPlayingTitle.value = ""
        _nowPlayingSubtitle.value = ""
        _nowPlayingItemId.value = ""
        Log.i(TAG, "Disconnected from Jellyfin remote session")
    }

    /**
     * Subscribes to [webSocketClient] `Sessions` events and applies the matching
     * session's state to the transport flows. Auto-disconnects if the connected
     * session vanishes (mirrors jellyfin-web SessionPlayer's auto-default-to-
     * local behaviour).
     */
    private fun startSessionsObserver() {
        sessionsObserverJob?.cancel()
        sessionsObserverJob = scope.launch {
            webSocketClient.events.collect { event ->
                if (event.type != "Sessions") return@collect
                val sessionId = connectedSessionId ?: return@collect
                // `Data` is a PascalCase SessionInfo[] array — decode the raw
                // envelope text via the WS DTO (the shared SessionInfo model is
                // camelCase without @SerialName and cannot decode the wire
                // format directly), then filter to the connected session before
                // paying for full mapping.
                try {
                    val current = parseSessionsMessage(sessionsJson, event.rawText)
                        .firstOrNull { it.id == sessionId }
                        ?.toSessionInfo()
                    if (current != null) {
                        applySessionState(current)
                    } else {
                        // Session gone (other client closed / device offline).
                        disconnect(appContext)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse Sessions push", e)
                }
            }
        }
    }

    suspend fun refreshPlaybackState() {
        val sessionId = connectedSessionId ?: return
        try {
            val sessionsResult = adminApiClient.getSessions()
            if (sessionsResult.isSuccess) {
                val sessions = sessionsResult.getOrThrow()
                val currentSession = sessions.find { it.id == sessionId }
                if (currentSession != null) {
                    applySessionState(currentSession)
                } else {
                    disconnect(appContext)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing playback state", e)
        }
    }

    /**
     * Applies a [SessionInfo]'s play state + now-playing metadata to the
     * transport flows. Shared by the WebSocket push ([startSessionsObserver])
     * and the REST fallback ([refreshPlaybackState]).
     */
    private fun applySessionState(session: SessionInfo) {
        val playState = session.playState
        val nowPlaying = session.nowPlayingItem
        _positionMs.value = (playState?.positionTicks ?: 0L) / 10000L
        _durationMs.value = (nowPlaying?.runTimeTicks ?: 0L) / 10000L
        _isPlaying.value = if (nowPlaying != null) !(playState?.isPaused ?: true) else false
        _volume.value = (playState?.volumeLevel ?: 100) / 100f
        _nowPlayingTitle.value = nowPlaying?.name.orEmpty()
        _nowPlayingSubtitle.value = nowPlaying?.seriesName.orEmpty()
        _nowPlayingItemId.value = nowPlaying?.id.orEmpty()
    }

    fun play() {
        val sessionId = connectedSessionId ?: return
        scope.launch {
            adminApiClient.sendPlaystateCommand(sessionId, "Unpause")
            _isPlaying.value = true
        }
    }

    fun pause() {
        val sessionId = connectedSessionId ?: return
        scope.launch {
            adminApiClient.sendPlaystateCommand(sessionId, "Pause")
            _isPlaying.value = false
        }
    }

    fun seekTo(positionMs: Long) {
        val sessionId = connectedSessionId ?: return
        scope.launch {
            val ticks = positionMs * 10000L
            adminApiClient.sendPlaystateCommand(sessionId, "Seek", seekPositionTicks = ticks)
            _positionMs.value = positionMs
        }
    }

    fun setRendererVolume(volume: Float) {
        val sessionId = connectedSessionId ?: return
        scope.launch {
            val volumePercent = (volume * 100f).coerceIn(0f, 100f).toInt().toString()
            adminApiClient.sendGeneralCommand(
                sessionId = sessionId,
                commandName = "SetVolume",
                arguments = mapOf("Volume" to volumePercent)
            )
            _volume.value = volume
        }
    }

    /**
     * Jump to the next item in the connected session's play queue. Mapped by
     * serial name inside [AdminApiClient.sendPlaystateCommand] (same enum path
     * the local remote-control dispatchers use for PlaystateCommand.NextTrack).
     */
    fun nextTrack() {
        val sessionId = connectedSessionId ?: return
        scope.launch {
            adminApiClient.sendPlaystateCommand(sessionId, "NextTrack")
        }
    }

    /**
     * Jump to the previous item in the connected session's play queue. See
     * [nextTrack] — same playstate-command plumbing.
     */
    fun previousTrack() {
        val sessionId = connectedSessionId ?: return
        scope.launch {
            adminApiClient.sendPlaystateCommand(sessionId, "PreviousTrack")
        }
    }

    /**
     * Send a `Stop` playstate command then disconnect locally. Jellyfin's Stop
     * ends playback on the controlling client; mirroring the local dispatchers
     * (see [com.raulshma.jellyplay.core.data.remote.AudioRemoteControlDispatcher]
     * / VideoRemoteControlDispatcher) we treat Stop as terminal and clear our
     * remote-session bookkeeping so local playback delegation resumes.
     */
    fun stop(context: Context) {
        val sessionId = connectedSessionId ?: return
        scope.launch {
            adminApiClient.sendPlaystateCommand(sessionId, "Stop")
        }
        disconnect(context)
    }

    fun loadMedia(
        itemId: String,
        startPositionMs: Long = 0,
        mediaSourceId: String? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ) {
        val sessionId = connectedSessionId ?: return
        scope.launch {
            val startTicks = startPositionMs * 10000L
            adminApiClient.play(
                sessionId = sessionId,
                playCommand = "PlayNow",
                itemIds = listOf(itemId),
                startPositionTicks = startTicks,
                // Carry the user's active audio/subtitle selection and the
                // targeted media source into the remote session so casting
                // does not reset tracks to the server default.
                mediaSourceId = mediaSourceId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
            )
            _isPlaying.value = true
        }
    }
}
