package com.raulshma.jellyplay.core.data.remote

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.remote.GeneralCommand
import com.raulshma.jellyplay.core.model.remote.NavigationTarget
import com.raulshma.jellyplay.core.model.remote.PlayRequest
import com.raulshma.jellyplay.core.model.remote.PlaybackDomain
import com.raulshma.jellyplay.core.model.remote.PlaystateCommand
import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection
import com.raulshma.jellyplay.core.model.remote.RemoteTopLevelDestination
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import com.raulshma.jellyplay.core.network.websocket.WebSocketEvent
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
 *
 * Promoted from androidMain to jvmShared with the desktop receiver port:
 * every dependency is commonMain/jvmShared now (the platform dispatchers are
 * bound through the [VideoRemoteControlDispatcher] /
 * [AudioRemoteControlDispatcher] interfaces, the engine registry through the
 * commonMain [ActivePlayerController], android.util.Log through the
 * core.data.log facade, org.json through jvmMain's desktop artifact /
 * android.jar). The Android Koin home stays
 * `AndroidCoreDataKoinModule`; desktop binds it in its composition root over
 * the desktop dispatcher twins.
 *
 * Adds the navigation-ladder family (Back, Select, the four Move
 * directions, the three Go destinations, ToggleContextMenu) — routed to
 * [RemoteNavigationBridge], never to a playback dispatcher — plus
 * [GeneralCommand.DisplayContent] (idle-gated detail navigation) and
 * [GeneralCommand.TakeScreenshot] (the bound engine's frame-capture flow).
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
    private val remoteNavigationBridge: RemoteNavigationBridge,
    private val audioQueueManager: AudioQueueManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectionJob: Job? = null

    /**
     * Emits a user-visible message for incoming "DisplayMessage" general
     * commands. Observed by the Android app's MainViewModel (and the desktop
     * shell's message host) to surface a toast/snackbar.
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
     * has disabled remote control via [SecurityStore]'s
     * `remoteControlEnabled` slice field.
     */
    fun start() {
        if (collectionJob?.isActive == true) return
        collectionJob = scope.launch {
            // Honour the user's "remote control" preference: if disabled,
            // bail out before collecting any events. Re-checked on every
            // start() so toggling the pref + reconnecting takes effect.
            // firstPersistedSecurity: `.first()` on the seeded StateFlow
            // would return the cold-process seed (remote control ON) before
            // the file read lands.
            val enabled = try {
                securityStore.firstPersistedSecurity().remoteControlEnabled
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
            // Older/alternative wire shape: DisplayContent as its own message
            // type with the item payload directly in Data (the modern shape is
            // a GeneralCommand with Name=DisplayContent). Both parse through
            // the same argument reader.
            "DisplayContent" -> handleGeneralCommandMessage(
                JSONObject().put("Name", "DisplayContent").put("Arguments", event.data),
            )
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

        // Navigation ladder + DisplayContent + TakeScreenshot are UI-level
        // commands: they go to the navigation bridge / the screenshot
        // flow BEFORE any playback dispatcher is consulted.
        when (command) {
            GeneralCommand.Back -> remoteNavigationBridge.request(NavigationTarget.GoBack)
            GeneralCommand.Select -> remoteNavigationBridge.request(NavigationTarget.InvokeSelect)
            GeneralCommand.MoveUp -> remoteNavigationBridge.request(NavigationTarget.MoveFocus(RemoteFocusDirection.UP))
            GeneralCommand.MoveDown -> remoteNavigationBridge.request(NavigationTarget.MoveFocus(RemoteFocusDirection.DOWN))
            GeneralCommand.MoveLeft -> remoteNavigationBridge.request(NavigationTarget.MoveFocus(RemoteFocusDirection.LEFT))
            GeneralCommand.MoveRight -> remoteNavigationBridge.request(NavigationTarget.MoveFocus(RemoteFocusDirection.RIGHT))
            GeneralCommand.GoHome -> remoteNavigationBridge.request(
                NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.HOME),
            )
            GeneralCommand.GoToSettings -> remoteNavigationBridge.request(
                NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SETTINGS),
            )
            GeneralCommand.GoToSearch -> remoteNavigationBridge.request(
                NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SEARCH),
            )
            GeneralCommand.ToggleContextMenu -> remoteNavigationBridge.request(NavigationTarget.OpenContextMenu)
            is GeneralCommand.DisplayContent -> handleDisplayContent(command)
            GeneralCommand.TakeScreenshot -> handleTakeScreenshot()
            // Volume, mute, repeat, shuffle, stream index → engine-specific dispatch.
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

    /**
     * Remote "show this item": navigate to the item's detail page, but ONLY
     * when this device is idle (no bound video engine AND nothing playing in
     * the audio queue) AND the user opted in via the security-slice toggle
     * `remoteDisplayContentEnabled` (default off — a remote flipping the UI
     * while a user browses is hostile without consent). During active
     * playback the request is dropped with a log line (v1; surfacing it over
     * the playing media would fight the player).
     */
    private suspend fun handleDisplayContent(command: GeneralCommand.DisplayContent) {
        // firstPersistedSecurity, not the seeded StateFlow's `.value`: on a
        // cold process `.value` is still the default seed, which would
        // silently drop the command for an opted-in user until the first
        // DataStore emission lands (the store's own documented race).
        val enabled = try {
            securityStore.firstPersistedSecurity().remoteDisplayContentEnabled
        } catch (_: Exception) {
            false
        }
        if (!enabled) {
            Log.d(TAG, "DisplayContent dropped — remoteDisplayContentEnabled is off")
            return
        }
        if (!isIdleForDisplayContent()) {
            Log.d(
                TAG,
                "DisplayContent dropped — playback active (item=${command.itemId} name=${command.itemName})",
            )
            return
        }
        remoteNavigationBridge.request(NavigationTarget.OpenMediaDetail(command.itemId))
    }

    /** Idle = no bound video engine and nothing playing in the audio queue. */
    private fun isIdleForDisplayContent(): Boolean =
        activePlayerController.engine == null && audioQueueManager.currentPlayingItemId.value == null

    /**
     * Remote "TakeScreenshot": forwarded to the mounted player screen
     * through the engine registry's screenshot flow (the same capture path
     * as the overflow-menu action). With no player mounted there is nothing
     * to capture — surface the standard DisplayMessage-style notice instead.
     */
    private fun handleTakeScreenshot() {
        if (activePlayerController.engine != null) {
            activePlayerController.requestScreenshot()
        } else {
            Log.d(TAG, "TakeScreenshot with no bound engine — surfacing notice")
            _displayMessages.tryEmit(
                DisplayMessagePayload(header = "", text = "Nothing playing to capture", timeoutMs = null),
            )
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
        // Navigation ladder — no arguments on the wire.
        "Back" -> GeneralCommand.Back
        "Select" -> GeneralCommand.Select
        "MoveUp" -> GeneralCommand.MoveUp
        "MoveDown" -> GeneralCommand.MoveDown
        "MoveLeft" -> GeneralCommand.MoveLeft
        "MoveRight" -> GeneralCommand.MoveRight
        "GoHome" -> GeneralCommand.GoHome
        "GoToSettings" -> GeneralCommand.GoToSettings
        "GoToSearch" -> GeneralCommand.GoToSearch
        "ToggleContextMenu" -> GeneralCommand.ToggleContextMenu
        "TakeScreenshot" -> GeneralCommand.TakeScreenshot
        "DisplayContent" -> {
            val itemId = args?.optString("ItemId", "")?.takeIf { it.isNotBlank() } ?: return null
            GeneralCommand.DisplayContent(
                itemId = itemId,
                itemName = args.optString("ItemName", "").takeIf { it.isNotBlank() },
                itemType = args.optString("ItemType", "").takeIf { it.isNotBlank() },
            )
        }
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
