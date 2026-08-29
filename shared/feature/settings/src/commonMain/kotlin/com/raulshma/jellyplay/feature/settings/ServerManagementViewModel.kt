package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ServerManagementViewModel(
    private val authRepository: AuthRepository,
    private val serverIdentityStore: ServerIdentityStore,
    private val networkOfflineStore: NetworkOfflineStore,
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

    /** Addresses the user granted self-signed-certificate trust for. */
    private val _selfSignedTrustHosts = composeState<Set<String>>(emptySet())
    val selfSignedTrustHosts: Set<String> get() = _selfSignedTrustHosts.value

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
        launch {
            networkOfflineStore.networkOffline
                .map { it.selfSignedTrustHosts }
                .distinctUntilChanged()
                .collect { hosts ->
                    _selfSignedTrustHosts.value = hosts
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

    // ------------------------------------------------- self-signed trust

    /**
     * Whether this server's granted self-signed trust entry is currently on.
     * Keyed on the PRIMARY address (the canonical grant the Add Server dialog
     * writes), normalized the same way `connectToServer` normalizes before
     * probing so the stored entry and this read agree byte-for-byte.
     */
    fun isSelfSignedTrustGranted(server: ServerInfo): Boolean =
        normalizeAddress(server.address) in selfSignedTrustHosts

    /**
     * Grants or revokes this server's self-signed-certificate trust. Grant
     * writes the primary address; revoke removes EVERY granted entry that
     * belongs to this server (primary + alternates — revoking one address of
     * a server while leaving its siblings trusted would be surprising). Note
     * (documented limitation, mirrored in the network layer's KDoc): already
     * pooled TLS connections stay trusted until they idle out of OkHttp's
     * connection pool or the process restarts; only NEW handshakes are gated.
     */
    fun setSelfSignedTrust(server: ServerInfo, granted: Boolean) {
        launch {
            if (granted) {
                networkOfflineStore.addSelfSignedTrustHost(normalizeAddress(server.address))
            } else {
                val grantedAddresses = (listOf(server.address) + server.alternateAddresses)
                    .map { normalizeAddress(it) }
                    .filter { it in selfSignedTrustHosts }
                grantedAddresses.forEach { networkOfflineStore.removeSelfSignedTrustHost(it) }
            }
        }
    }

    private fun normalizeAddress(address: String): String =
        address.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
}
