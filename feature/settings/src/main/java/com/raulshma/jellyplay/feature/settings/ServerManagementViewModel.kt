package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ServerManagementViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverIdentityStore: ServerIdentityStore,
) : JellyPlayViewModel() {

    private val _servers = composeState<List<ServerInfo>>(emptyList())
    val servers: List<ServerInfo> get() = _servers.value

    private val _activeServerId = composeState<String?>(null)
    val activeServerId: String? get() = _activeServerId.value

    private val _isSwitching = composeState(false)
    val isSwitching: Boolean get() = _isSwitching.value

    private val _addressOperationMessage = composeState<String?>(null)
    val addressOperationMessage: String? get() = _addressOperationMessage.value

    private val _isAddressOperationInProgress = composeState(false)
    val isAddressOperationInProgress: Boolean get() = _isAddressOperationInProgress.value

    init {
        launch {
            authRepository.servers.collect { serverList ->
                _servers.value = serverList
            }
        }
        launch {
            serverIdentityStore.activeServerId.collect { id ->
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

    fun addServerAddress(serverId: String, address: String) {
        launch {
            _isAddressOperationInProgress.value = true
            _addressOperationMessage.value = null
            authRepository.addServerAddress(serverId, address)
                .onSuccess { _addressOperationMessage.value = "Address added" }
                .onFailure { _addressOperationMessage.value = it.message ?: "Failed to add address" }
            _isAddressOperationInProgress.value = false
        }
    }

    fun removeServerAddress(serverId: String, address: String) {
        launch {
            authRepository.removeServerAddress(serverId, address)
        }
    }

    fun switchServerAddress(serverId: String, address: String) {
        launch {
            _isAddressOperationInProgress.value = true
            _addressOperationMessage.value = null
            authRepository.switchServerAddress(serverId, address)
                .onSuccess { _addressOperationMessage.value = "Switched to $address" }
                .onFailure { _addressOperationMessage.value = it.message ?: "Failed to switch address" }
            _isAddressOperationInProgress.value = false
        }
    }

    fun clearAddressOperationMessage() {
        _addressOperationMessage.value = null
    }
}
