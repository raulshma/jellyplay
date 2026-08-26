package com.raulshma.jellyplay.core.data.remote

import android.util.Log
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import com.raulshma.jellyplay.core.network.websocket.WebSocketEvent
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.remote.GeneralCommand
import com.raulshma.jellyplay.core.model.remote.PlayRequest
import com.raulshma.jellyplay.core.model.remote.PlaybackDomain
import com.raulshma.jellyplay.core.model.remote.PlaystateCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Subscribes to the Jellyfin [JellyfinWebSocketClient] event stream and
 * converts the "Play", "Playstate" and "GeneralCommand" envelopes into
 * strongly-typed [com.raulshma.jellyplay.core.model.remote.*] requests that are
 * dispatched to the appropriate [RemoteControlDispatcher].
 *
 * This is the bridge that makes JellyPlay a 1:1 "Play To" / remote-control
 * receiver compatible with the official Jellyfin web and Android clients.
 */
class RemoteControlReceiver(
    private val webSocketClient: JellyfinWebSocketClient,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val videoDispatcher: VideoRemoteControlDispatcher,
    private val audioDispatcher: AudioRemoteControlDispatcher,
    private val uiDispatcher: UiRemoteControlDispatcher,
    private val activePlayerController: ActivePlayerController,
    private val securityStore: SecurityStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectionJob: Job? = null

    /**
     * Emits a user-visible message for incoming "DisplayMessage" general
     * commands. Observed by [com.raulshma.jellyplay.MainViewModel] to surface
     * a toast.
     */
    private val _displayMessages = MutableSharedFlow<DisplayMessagePayload>(extraBufferCapacity = 4)
    val displayMessages: SharedFlow<DisplayMessagePayload> = _displayMessages.asSharedFlow()

    /**
     * Emits when a remote "Play" has been received — used by the UI to show
     * a "Now playing from another device" banner.
     */
    private val _playEvents = MutableSharedFlow<PlayEventPayload>(extraBufferCapacity = 4)
    val playEvents: SharedFlow<PlayEventPayload> = _playEvents.asSharedFlow()

    /**
     * Start listening on the WebSocket event stream. Safe to call multiple
     * times — only one collection job runs at a time. No-op when the user
     * has disabled remote control via [UserPreferencesStore.remoteControlEnabled].
     */
    fun start() {
        if (collectionJob?.isActive == true) return
        collectionJob = scope.launch {
            // Honour the user's "remote control" preference: if disabled,
            // bail out before collecting any events. Re-checked on every
            // start() so toggling the pref + reconnecting takes effect.
            val enabled = try {
                securityStore.security.first().remoteControlEnabled
            } catch (_: Exception) {
                true
            }
            if (!enabled) {
                Log.d(TAG, "RemoteControlReceiver skipped — disabled in preferences")
                return@launch
            }
            webSocketClient.events.collect { event ->
                if (!isAuthenticatedSync()) return@collect
                handleWebSocketEvent(event)
            }
        }
        Log.d(TAG, "RemoteControlReceiver started")
    }

    /**
     * Stop listening. Used on logout / app teardown.
     */
    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        Log.d(TAG, "RemoteControlReceiver stopped")
    }

    private suspend fun isAuthenticatedSync(): Boolean {
        return try {
            authRepository.isAuthenticated.first()
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun handleWebSocketEvent(event: WebSocketEvent) {
        when (event.type) {
            "Play" -> handlePlayMessage(event.data)
            "Playstate" -> handlePlaystateMessage(event.data)
            "GeneralCommand" -> handleGeneralCommandMessage(event.data)
            else -> Unit
        }
    }

    /**
     * First-item detail with one silent retry: transient fetch failures are
     * not cached, so the retry recovers what the old two-fetch shape gave
     * for free — and serves both the banner title and the playback domain,
     * instead of fixing the domain to VIDEO when only the first attempt
     * failed.
     */
    private suspend fun fetchFirstItemDetail(id: String) =
        mediaRepository.getMediaDetail(id).getOrNull()
            ?: mediaRepository.getMediaDetail(id).getOrNull()

    // ── Play ──────────────────────────────────────────────────────────────

    private suspend fun handlePlayMessage(data: JSONObject) {
        val itemIds = readStringArray(data, "ItemIds")
        if (itemIds.isEmpty()) {
            Log.w(TAG, "Play message with empty ItemIds — ignoring")
            return
        }
        val request = PlayRequest(
            itemIds = itemIds,
            startIndex = data.optInt("StartIndex", 0),
            startPositionTicks = data.optLong("StartPositionTicks", 0L),
            playCommand = data.optString("PlayCommand", "PlayNow"),
            mediaSourceId = data.optString("MediaSourceId", "").takeIf { it.isNotBlank() },
            audioStreamIndex = data.optInt("AudioStreamIndex", -1).takeIf { it >= 0 },
            subtitleStreamIndex = data.optInt("SubtitleStreamIndex", -1).takeIf { it >= 0 },
            controllingUserId = data.optString("ControllingUserId", ""),
        )

        // The dispatcher pick and the banner title both derive from the first
        // item's detail — one getMediaDetail fetch serves both (the old shape
        // issued two sequential calls) on the remote-command latency path.
        val firstItemId = request.itemIds.firstOrNull()
        val firstDetail = firstItemId?.let { fetchFirstItemDetail(it) }
        val bannerTitle = firstDetail?.item?.name.orEmpty()

        val domain = if (firstItemId == null) {
            PlaybackDomain.UNKNOWN
        } else if (firstDetail == null) {
            PlaybackDomain.VIDEO
        } else {
            when (firstDetail.item.mediaType) {
                MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM -> PlaybackDomain.AUDIO
                else -> PlaybackDomain.VIDEO
            }
        }
        val dispatcher = when (domain) {
            PlaybackDomain.VIDEO -> videoDispatcher
            PlaybackDomain.AUDIO -> audioDispatcher
            PlaybackDomain.UNKNOWN -> videoDispatcher
        }

        dispatcher.play(request)
        // Also notify UI to surface a "Now playing" banner.
        _playEvents.tryEmit(
            PlayEventPayload(
                itemId = request.itemIds.first(),
                title = bannerTitle,
                startPositionTicks = request.startPositionTicks,
            )
        )

        // NOTE: We intentionally do NOT start a [RemotePlaybackReporter] session
        // here. The video [VideoPlayerViewModel] and the audio
        // [AudioPlaybackManager] each call `reportPlaybackStart` /
        // `reportPlaybackProgress` / `reportPlaybackStopped` with their own
        // `playSessionId` once the engine is bound. Starting a second session
        // from the receiver would race with the player's reports under a
        // different sessionId, which surfaces as a jittery position ticker on
        // the controlling device (Jellyfin web, Android, etc.).
    }

    // ── Playstate ─────────────────────────────────────────────────────────

    private suspend fun handlePlaystateMessage(data: JSONObject) {
        val commandStr = data.optString("Command", "")
        val command = parsePlaystateCommand(commandStr, data) ?: return
        // Dispatch to both engines. Each no-ops if no engine is bound. The UI
        // dispatcher is not used here — playstate is engine-specific.
        audioDispatcher.handlePlaystate(command)
        videoDispatcher.handlePlaystate(command)
    }

    private fun parsePlaystateCommand(command: String, data: JSONObject): PlaystateCommand? = when (command) {
        "Stop" -> PlaystateCommand.Stop
        "Pause" -> PlaystateCommand.Pause
        "Unpause" -> PlaystateCommand.Unpause
        "NextTrack" -> PlaystateCommand.NextTrack
        "PreviousTrack" -> PlaystateCommand.PreviousTrack
        "Rewind" -> PlaystateCommand.Rewind
        "FastForward" -> PlaystateCommand.FastForward
        "PlayPause" -> PlaystateCommand.PlayPause
        "Seek" -> PlaystateCommand.Seek(data.optLong("SeekPositionTicks", 0L))
        else -> {
            Log.d(TAG, "Unknown playstate command: $command")
            null
        }
    }

    // ── GeneralCommand ────────────────────────────────────────────────────

    private suspend fun handleGeneralCommandMessage(data: JSONObject) {
        val name = data.optString("Name", "")
        if (name.isBlank()) return
        val args = data.optJSONObject("Arguments")
        val command = parseGeneralCommand(name, args) ?: return

        // Volume, mute, repeat, shuffle, stream index → engine-specific dispatch.
        when (command) {
            is GeneralCommand.SetVolume,
            GeneralCommand.VolumeUp,
            GeneralCommand.VolumeDown,
            GeneralCommand.Mute,
            GeneralCommand.Unmute,
            GeneralCommand.ToggleMute,
            is GeneralCommand.SetAudioStreamIndex,
            is GeneralCommand.SetSubtitleStreamIndex,
            is GeneralCommand.SetRepeatMode,
            is GeneralCommand.SetShuffleQueue,
            is GeneralCommand.SetPlaybackOrder -> {
                videoDispatcher.handleGeneral(command)
                audioDispatcher.handleGeneral(command)
            }
            is GeneralCommand.SetMaxStreamingBitrate -> {
                videoDispatcher.handleGeneral(command)
                uiDispatcher.handleGeneral(command)
            }
            GeneralCommand.ToggleFullscreen -> uiDispatcher.handleGeneral(command)
            is GeneralCommand.DisplayMessage -> {
                _displayMessages.tryEmit(
                    DisplayMessagePayload(command.header, command.text, command.timeoutMs)
                )
                uiDispatcher.handleGeneral(command)
            }
            is GeneralCommand.Unknown -> uiDispatcher.handleGeneral(command)
        }
    }

    private fun parseGeneralCommand(name: String, args: JSONObject?): GeneralCommand? = when (name) {
        "SetVolume" -> {
            val volume = args?.optInt("Volume", -1) ?: -1
            if (volume < 0) return null
            val muteStr = args?.optString("Mute", "")
            val mute: Boolean? = when {
                muteStr.isNullOrEmpty() -> null
                muteStr.equals("true", ignoreCase = true) -> true
                muteStr.equals("false", ignoreCase = true) -> false
                else -> null
            }
            GeneralCommand.SetVolume(volume, mute)
        }
        "VolumeUp" -> GeneralCommand.VolumeUp
        "VolumeDown" -> GeneralCommand.VolumeDown
        "Mute" -> GeneralCommand.Mute
        "Unmute" -> GeneralCommand.Unmute
        "ToggleMute" -> GeneralCommand.ToggleMute
        "SetAudioStreamIndex" -> {
            val idx = args?.optInt("Index", -1) ?: -1
            if (idx < 0) return null
            GeneralCommand.SetAudioStreamIndex(idx)
        }
        "SetSubtitleStreamIndex" -> {
            val idx = args?.optInt("Index", -1) ?: -1
            if (idx < 0) return null
            GeneralCommand.SetSubtitleStreamIndex(idx)
        }
        "SetRepeatMode" -> {
            val mode = args?.optString("RepeatMode", "RepeatNone") ?: "RepeatNone"
            GeneralCommand.SetRepeatMode(mode)
        }
        "SetShuffleQueue" -> {
            val shuffle = args?.optBoolean("Shuffle", false) ?: false
            GeneralCommand.SetShuffleQueue(shuffle)
        }
        "SetPlaybackOrder" -> {
            val order = args?.optString("Order", "Default") ?: "Default"
            GeneralCommand.SetPlaybackOrder(order)
        }
        "SetMaxStreamingBitrate" -> {
            val bitrate = args?.optInt("Bitrate", 0) ?: 0
            if (bitrate <= 0) return null
            GeneralCommand.SetMaxStreamingBitrate(bitrate)
        }
        "ToggleFullscreen" -> GeneralCommand.ToggleFullscreen
        "DisplayMessage" -> {
            val header = args?.optString("Header", "") ?: ""
            val text = args?.optString("Text", "") ?: ""
            val timeoutMs = args?.optInt("TimeoutMs", -1)?.takeIf { it >= 0 }
            GeneralCommand.DisplayMessage(header, text, timeoutMs)
        }
        else -> GeneralCommand.Unknown(name)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun readStringArray(json: JSONObject, key: String): List<String> {
        val arr = json.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it, "").takeIf(String::isNotBlank) }
    }

    companion object {
        private const val TAG = "RemoteControlRx"
    }
}

data class DisplayMessagePayload(
    val header: String,
    val text: String,
    val timeoutMs: Int?,
)

data class PlayEventPayload(
    val itemId: String,
    val title: String,
    val startPositionTicks: Long,
)
