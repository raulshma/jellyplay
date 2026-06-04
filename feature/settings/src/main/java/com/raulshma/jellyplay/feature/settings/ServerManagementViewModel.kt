package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ServerManagementViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesStore: UserPreferencesStore,
) : JellyPlayViewModel() {

    private val _servers = composeState<List<ServerInfo>>(emptyList())
    val servers: List<ServerInfo> get() = _servers.value

    private val _activeServerId = composeState<String?>(null)
    val activeServerId: String? get() = _activeServerId.value

    private val _isSwitching = composeState(false)
    val isSwitching: Boolean get() = _isSwitching.value

    init {
        launch {
            authRepository.servers.collect { serverList ->
                _servers.value = serverList
            }
        }
        launch {
            preferencesStore.activeServerId.collect { id ->
                _activeServerId.value = id
            }
        }
    }

    fun switchServer(serverId: String, onSuccess: () -> Unit) {
        launch {
            _isSwitching.value = true
            authRepository.switchServer(serverId)
                .onSuccess { onSuccess() }
            _isSwitching.value = false
        }
    }

    fun removeServer(serverId: String) {
        launch {
            authRepository.removeServer(serverId)
            if (activeServerId == serverId) {
                val remaining = servers.filter { it.id != serverId }
                if (remaining.isNotEmpty()) {
                    switchServer(remaining.first().id) {}
                }
            }
        }
    }
}
