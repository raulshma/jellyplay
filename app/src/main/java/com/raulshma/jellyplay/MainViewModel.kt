package com.raulshma.jellyplay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.shortcuts.AppShortcutManager
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.deeplink.DeepLinkHandler
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    val preferencesStore: UserPreferencesStore,
    val networkMonitor: NetworkMonitor,
    val syncPlayManager: com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager,
    val webSocketClient: com.raulshma.jellyplay.core.data.syncplay.JellyfinWebSocketClient,
    private val apiClient: com.raulshma.jellyplay.core.network.JellyfinApiClient,
    val audioPlaybackManager: AudioPlaybackManager,
    val videoMiniPlayerState: VideoMiniPlayerState,
    val appShortcutManager: AppShortcutManager,
    val remoteControlReceiver: RemoteControlReceiver,
    val remoteNavigationBridge: RemoteNavigationBridge,
    private val deepLinkHandler: DeepLinkHandler,
    private val downloadRepository: DownloadRepository,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val offlineRepository: OfflineRepository,
    val userMessageBus: UserMessageBus,
    private val serverHealthMonitor: com.raulshma.jellyplay.core.data.network.ServerHealthMonitor,
) : JellyPlayViewModel() {

    val serverHealth = serverHealthMonitor.serverHealth

    private val _isRestoring = stateFlow(true)
    val isRestoring = _isRestoring.flow

    val isAuthenticated = authRepository.isAuthenticated
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val isAdmin = authRepository.currentUser
        .map { it?.isAdmin == true }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val preferences = preferencesStore.preferences
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _pendingRoute = stateFlow<Route?>(null)
    val pendingRoute = _pendingRoute.flow

    private val _pendingSearchQuery = stateFlow<String?>(null)
    val pendingSearchQuery = _pendingSearchQuery.flow

    private val _globalMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val globalMessage = _globalMessage.asSharedFlow()

    init {
        launch {
            coroutineScope {
                val authDeferred = async { authRepository.restoreSession() }
                val prefsDeferred = async { preferences.first() }
                val result = authDeferred.await()
                prefsDeferred.await()
                if (result.isSuccess) {
                    val server = authRepository.currentServer.first()
                    val user = authRepository.currentUser.first()
                    if (server != null && user != null) {
                        isAuthenticated.first { it }
                    }
                }
            }
            _isRestoring.set(false)
        }

        launch {
            isAuthenticated.collect { isAuth ->
                if (isAuth) {
                    val server = authRepository.currentServer.first()
                    val user = authRepository.currentUser.first()
                    if (server != null && user != null) {
                        serverHealthMonitor.startMonitoring(server.address)
                        val deviceId = preferencesStore.ensureDeviceId()
                        val deviceName = buildDeviceName(user.name)
                        webSocketClient.connect(
                            serverAddress = server.address,
                            accessToken = user.accessToken,
                            device = deviceId,
                            deviceName = deviceName,
                            client = "JellyPlay",
                        )
                        try {
                            apiClient.postCapabilities()
                        } catch (_: Exception) {
                            // Ignored
                        }
                        remoteControlReceiver.start()
                        launch {
                            com.raulshma.jellyplay.widget.WidgetWorkScheduler.refreshLibraryNow(context)
                        }
                        launch {
                            com.raulshma.jellyplay.widget.WidgetWorkScheduler.refreshSeerrNow(context)
                        }
                        // Force every placed widget to re-read its cached data
                        // from the store. The Continue Watching widget has no
                        // worker of its own (data is pushed by HomeViewModel),
                        // so this is what makes it pick up freshly restored
                        // state on cold start; for Library/Seerr it surfaces
                        // any cached items while the worker run completes.
                        com.raulshma.jellyplay.widget.ContinueWatchingWidget.triggerUpdate(context)
                    }
                } else {
                    serverHealthMonitor.stopMonitoring()
                    webSocketClient.disconnect()
                    remoteControlReceiver.stop()
                }
            }
        }

        launch {
            remoteControlReceiver.displayMessages.collect { msg ->
                val text = if (msg.header.isNotBlank()) "${msg.header}\n${msg.text}" else msg.text
                if (text.isNotBlank()) {
                    _globalMessage.tryEmit(text)
                }
            }
        }

        appShortcutManager.observePlaybackForDynamicShortcuts()
    }

    fun handleShortcutIntent(intent: Intent) {
        val route = when (intent.action) {
            AppShortcutManager.ACTION_CONTINUE_WATCHING ->
                Route.NewsletterSectionList("CONTINUE_WATCHING")
            AppShortcutManager.ACTION_SEARCH -> Route.Search
            AppShortcutManager.ACTION_PLAY_MUSIC -> Route.MusicBrowse
            AppShortcutManager.ACTION_DOWNLOADS -> Route.Downloads
            AppShortcutManager.ACTION_PLAY_AUDIO -> {
                val itemId = intent.getStringExtra(AppShortcutManager.EXTRA_ITEM_ID)
                if (!itemId.isNullOrBlank()) Route.AudioPlayer(itemId) else null
            }
            else -> null
        }
        if (route != null) {
            _pendingRoute.set(route)
        }
    }

    fun handleDeepLink(intent: Intent) {
        val route = deepLinkHandler.parse(intent) ?: return
        _pendingRoute.set(route)
    }

    fun handleSharedText(sharedText: String) {
        launch {
            when (val target = parseSharedText(sharedText)) {
                SharedTextTarget.Empty -> {
                    _globalMessage.emit("No searchable content found in shared text")
                }
                is SharedTextTarget.Search -> {
                    _pendingSearchQuery.set(target.query)
                    _pendingRoute.set(Route.Search)
                }
                is SharedTextTarget.MediaDetail -> {
                    _pendingRoute.set(Route.MediaDetail(target.mediaId))
                }
            }
        }
    }

    private fun parseSharedText(text: String): SharedTextTarget {
        val jellyfinUrlMatch = Regex("""jellyfin://media/([a-f0-9-]+)""").find(text)
        if (jellyfinUrlMatch != null) {
            return SharedTextTarget.MediaDetail(jellyfinUrlMatch.groupValues[1])
        }
        val urlMatch = Regex("""https?://[^\s]+""").find(text)
        if (urlMatch != null) {
            return SharedTextTarget.Search(urlMatch.value)
        }
        return text.takeIf { it.isNotBlank() }
            ?.let(SharedTextTarget::Search)
            ?: SharedTextTarget.Empty
    }

    private sealed class SharedTextTarget {
        data object Empty : SharedTextTarget()
        data class Search(val query: String) : SharedTextTarget()
        data class MediaDetail(val mediaId: String) : SharedTextTarget()
    }

    fun consumePendingRoute() {
        _pendingRoute.set(null)
    }

    fun consumePendingSearchQuery() {
        _pendingSearchQuery.set(null)
    }

    fun handleSearchQuery(query: String) {
        _pendingSearchQuery.set(query)
    }

    fun logout() {
        launch {
            remoteControlReceiver.stop()
            authRepository.logout()
        }
    }

    fun revokeServerSession() {
        launch {
            remoteControlReceiver.stop()
            authRepository.revokeServerSession()
        }
    }

    private fun buildDeviceName(userName: String): String {
        val model = Build.MODEL.orEmpty().ifBlank { "Android" }
        val name = userName.take(20)
        val full = if (name.isNotBlank()) "JellyPlay on $model ($name)" else "JellyPlay on $model"
        return if (full.length > 60) full.take(60) else full
    }

    /**
     * Builds an [ExternalPlayerLaunch] for the given item, resolving either a
     * completed local download or the server stream URL. Works for both regular
     * videos ([Route.VideoPlayer]) and Live TV channels
     * ([Route.LiveTvChannelPlayer]) since [mediaRepository.getMediaDetail] /
     * [playbackRepository.getStreamUrl] handle channel ids identically to the
     * internal-engine path.
     *
     * The returned intent advertises `return_result`, so the app-level
     * `ActivityResultLauncher` in [com.raulshma.jellyplay.navigation.JellyPlayApp]
     * can read the external player's final position and credit watched progress
     * via [reportExternalPlaybackStopped] (see enhancements §4.3).
     */
    suspend fun buildExternalPlayerLaunch(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ): ExternalPlayerLaunch? {
        val download = downloadRepository.getDownloadByMediaItemId(itemId)
        val localFile = download?.let {
            java.io.File(it.downloadPath).takeIf { f -> f.exists() }
        }

        val url: String
        val title: String

        if (download != null && localFile != null && download.status == DownloadStatus.COMPLETED) {
            url = Uri.fromFile(localFile).toString()
            val offlineItem = offlineRepository.getOfflineItem(itemId)
            title = offlineItem?.name ?: download.name
        } else {
            val detail = mediaRepository.getMediaDetail(itemId).getOrNull() ?: return null
            val source = if (mediaSourceId != null) {
                detail.mediaSources.find { it.id == mediaSourceId }
            } else {
                detail.mediaSources.firstOrNull()
            }
            url = playbackRepository.getStreamUrl(
                itemId,
                source?.id ?: "",
                startPositionTicks,
            )
            title = detail.item.name
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
            putExtra("return_result", true)
            val startMs = startPositionTicks / 10_000
            if (startMs > 0) putExtra("position", startMs)
        }

        return ExternalPlayerLaunch(
            intent = intent,
            itemId = itemId,
            startPositionTicks = startPositionTicks,
            playSessionId = java.util.UUID.randomUUID().toString(),
        )
    }

    fun reportExternalPlaybackStart(playerLaunch: ExternalPlayerLaunch) {
        launch {
            runCatching {
                playbackRepository.reportPlaybackStart(
                    com.raulshma.jellyplay.core.model.PlaybackStartInfo(
                        itemId = playerLaunch.itemId,
                        sessionId = playerLaunch.playSessionId,
                        startPositionTicks = playerLaunch.startPositionTicks,
                    )
                )
            }
        }
    }

    fun reportExternalPlaybackStopped(playerLaunch: ExternalPlayerLaunch, finalPositionTicks: Long) {
        val positionTicks = if (finalPositionTicks > 0) finalPositionTicks else playerLaunch.startPositionTicks
        launch {
            runCatching {
                kotlinx.coroutines.withTimeout(5_000) {
                    playbackRepository.reportPlaybackStopped(
                        itemId = playerLaunch.itemId,
                        sessionId = playerLaunch.playSessionId,
                        positionTicks = positionTicks,
                    )
                }
            }
        }
    }
}

data class ExternalPlayerLaunch(
    val intent: Intent,
    val itemId: String,
    val startPositionTicks: Long,
    val playSessionId: String,
)
