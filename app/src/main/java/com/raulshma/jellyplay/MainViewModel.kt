package com.raulshma.jellyplay

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.shortcuts.AppShortcutManager
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.player.video.VideoMiniPlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val _isRestoring = MutableStateFlow(true)
    val isRestoring = _isRestoring.asStateFlow()

    val isAuthenticated = authRepository.isAuthenticated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isAdmin = authRepository.currentUser
        .map { it?.isAdmin == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val preferences = preferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _navigationRequest = MutableSharedFlow<Route>(extraBufferCapacity = 1)
    val navigationRequest = _navigationRequest.asSharedFlow()

    private val _globalMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val globalMessage = _globalMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.restoreSession()
            // Wait for isAuthenticated to reflect the restored session state
            // before hiding the splash screen, preventing the auth screen flash
            isAuthenticated.first()
            preferences.first()
            _isRestoring.value = false
        }
        
        viewModelScope.launch {
            isAuthenticated.collect { isAuth ->
                if (isAuth) {
                    val server = authRepository.currentServer.first()
                    val user = authRepository.currentUser.first()
                    if (server != null && user != null) {
                        webSocketClient.connect(
                            serverAddress = server.address,
                            accessToken = user.accessToken,
                            device = "JellyPlay-${user.id.take(8)}",
                            deviceName = "JellyPlay",
                            client = "JellyPlay",
                        )
                        try {
                            apiClient.postCapabilities()
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }
                } else {
                    webSocketClient.disconnect()
                }
            }
        }

        viewModelScope.launch {
            webSocketClient.events.collect { event ->
                if (event.type == "GeneralCommand") {
                    val name = event.data.optString("Name")
                    if (name == "DisplayMessage") {
                        val args = event.data.optJSONObject("Arguments")
                        val header = args?.optString("Header") ?: ""
                        val text = args?.optString("Text") ?: ""
                        val msg = if (header.isNotBlank()) "$header\n$text" else text
                        if (msg.isNotBlank()) {
                            _globalMessage.tryEmit(msg)
                        }
                    }
                }
            }
        }

        appShortcutManager.observePlaybackForDynamicShortcuts()
    }

    fun handleShortcutIntent(intent: Intent) {
        val route = when (intent.action) {
            AppShortcutManager.ACTION_CONTINUE_WATCHING -> Route.Home
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
            _navigationRequest.tryEmit(route)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
