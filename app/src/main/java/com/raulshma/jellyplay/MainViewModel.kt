package com.raulshma.jellyplay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.deeplink.DeepLinkHandler
import com.raulshma.jellyplay.feature.player.video.VideoMiniPlayerState
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
) : JellyPlayViewModel() {

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
                    }
                } else {
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

    fun consumePendingRoute() {
        _pendingRoute.set(null)
    }

    fun logout() {
        launch {
            remoteControlReceiver.stop()
            authRepository.logout()
        }
    }

    private fun buildDeviceName(userName: String): String {
        val model = Build.MODEL.orEmpty().ifBlank { "Android" }
        val name = userName.take(20)
        val full = if (name.isNotBlank()) "JellyPlay on $model ($name)" else "JellyPlay on $model"
        return if (full.length > 60) full.take(60) else full
    }

    suspend fun launchExternalPlayer(
        route: Route.VideoPlayer,
        context: android.content.Context,
    ) {
        val download = downloadRepository.getDownloadByMediaItemId(route.itemId)
        val localFile = download?.let {
            java.io.File(it.downloadPath).takeIf { f -> f.exists() }
        }

        val url: String
        val title: String

        if (download != null && localFile != null && download.status == DownloadStatus.COMPLETED) {
            url = Uri.fromFile(localFile).toString()
            val offlineItem = offlineRepository.getOfflineItem(route.itemId)
            title = offlineItem?.name ?: download.name
        } else {
            val detail = mediaRepository.getMediaDetail(route.itemId).getOrNull() ?: return
            val source = if (route.mediaSourceId != null) {
                detail.mediaSources.find { it.id == route.mediaSourceId }
            } else {
                detail.mediaSources.firstOrNull()
            }
            url = playbackRepository.getStreamUrl(
                route.itemId,
                source?.id ?: "",
                route.startPositionTicks,
            )
            title = detail.item.name
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
            val startMs = route.startPositionTicks / 10_000
            if (startMs > 0) putExtra("position", startMs)
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Open with…"))
        } catch (_: Exception) {
            Toast.makeText(context, "No video player found", Toast.LENGTH_LONG).show()
        }
    }
}
