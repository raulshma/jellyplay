package com.raulshma.jellyplay.core.data.cast.remote

import android.content.Context
import android.util.Log
import com.raulshma.jellyplay.core.data.cast.CastDevice
import com.raulshma.jellyplay.core.data.cast.CastStrategy
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.network.api.AdminApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinRemotePlayCastStrategy @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val adminApiClient: AdminApiClient,
    private val preferencesStore: UserPreferencesStore,
) : CastStrategy {

    companion object {
        private const val TAG = "JellyfinRemotePlayCastStrategy"
        private const val STRATEGY_NAME = "jellyfin"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    @Volatile
    private var connectedSessionId: String? = null

    private var discoveryJob: Job? = null
    private var statusPollingJob: Job? = null

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
                    val ownDeviceId = preferencesStore.ensureDeviceId()
                    val sessionsResult = adminApiClient.getSessions()
                    if (sessionsResult.isSuccess) {
                        val sessions = sessionsResult.getOrThrow()
                        val controllableSessions = sessions.filter {
                            it.supportsRemoteControl && it.deviceId.isNotBlank() && !it.deviceId.equals(ownDeviceId, ignoreCase = true)
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
        _isConnected.value = true
        _isConnecting.value = false

        startStatusPollingLoop()
        Log.i(TAG, "Connected to Jellyfin remote session: ${session.userName} (${session.client})")
    }

    override fun disconnect(context: Context) {
        statusPollingJob?.cancel()
        statusPollingJob = null
        connectedSessionId = null
        _isConnected.value = false
        _isConnecting.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
        Log.i(TAG, "Disconnected from Jellyfin remote session")
    }

    private fun startStatusPollingLoop() {
        statusPollingJob?.cancel()
        statusPollingJob = scope.launch {
            while (isConnected.value) {
                refreshPlaybackState()
                delay(2000L)
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
                    val playState = currentSession.playState
                    val nowPlaying = currentSession.nowPlayingItem
                    
                    _positionMs.value = (playState?.positionTicks ?: 0L) / 10000L
                    _durationMs.value = (nowPlaying?.runTimeTicks ?: 0L) / 10000L
                    _isPlaying.value = if (nowPlaying != null) !(playState?.isPaused ?: true) else false
                    _volume.value = (playState?.volumeLevel ?: 100) / 100f
                } else {
                    disconnect(appContext)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing playback state", e)
        }
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

    fun loadMedia(itemId: String, startPositionMs: Long = 0) {
        val sessionId = connectedSessionId ?: return
        scope.launch {
            val startTicks = startPositionMs * 10000L
            adminApiClient.play(
                sessionId = sessionId,
                playCommand = "PlayNow",
                itemIds = listOf(itemId),
                startPositionTicks = startTicks
            )
            _isPlaying.value = true
        }
    }
}
