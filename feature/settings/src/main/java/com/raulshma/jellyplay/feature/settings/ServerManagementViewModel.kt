package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.ServerInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerManagementViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesStore: UserPreferencesStore,
) : ViewModel() {

    var servers by mutableStateOf<List<ServerInfo>>(emptyList())
        private set

    var activeServerId by mutableStateOf<String?>(null)
        private set

    var isSwitching by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            authRepository.servers.collect { serverList ->
                servers = serverList
            }
        }
        viewModelScope.launch {
            preferencesStore.activeServerId.collect { id ->
                activeServerId = id
            }
        }
    }

    fun switchServer(serverId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isSwitching = true
            authRepository.switchServer(serverId)
                .onSuccess { onSuccess() }
            isSwitching = false
        }
    }

    fun removeServer(serverId: String) {
        viewModelScope.launch {
            authRepository.removeServer(serverId)
            if (activeServerId == serverId) {
                val remaining = servers.first { it.id != serverId }
                if (remaining.isNotEmpty()) {
                    switchServer(remaining.first().id) {}
                }
            }
        }
    }
}
