package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.network.config.SelfSignedTrustMatcher
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
            pruneOrphanedTrustGrants()
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
            pruneOrphanedTrustGrants()
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
     *
     * Delegates to the SAME pure matcher the handshake-time trust layer uses
     * ([SelfSignedTrustMatcher], reached through the jvmShared facade): the
     * layer honors a portless grant on ANY port of the host, so exact string
     * membership here would show the toggle OFF for a grant every handshake
     * honors (wave-21 review finding — display drift).
     */
    fun isSelfSignedTrustGranted(server: ServerInfo): Boolean =
        SelfSignedTrustMatcher.isAddressGranted(selfSignedTrustHosts, normalizeAddress(server.address))

    /**
     * Grants or revokes this server's self-signed-certificate trust. Grant
     * writes the primary address; revoke removes EVERY granted entry that
     * covers any of this server's addresses (primary + alternates, matched
     * with [SelfSignedTrustMatcher] — so a portless grant covering a ported
     * address is revoked too, mirroring what the toggle displays; revoking
     * one address of a server while leaving its siblings trusted would be
     * surprising). Note (documented limitation, mirrored in the network
     * layer's KDoc): already pooled TLS connections stay trusted until they
     * idle out of OkHttp's connection pool or the process restarts; only NEW
     * handshakes are gated.
     */
    fun setSelfSignedTrust(server: ServerInfo, granted: Boolean) {
        launch {
            if (granted) {
                networkOfflineStore.addSelfSignedTrustHost(normalizeAddress(server.address))
            } else {
                val serverAddresses = (listOf(server.address) + server.alternateAddresses)
                    .map { normalizeAddress(it) }
                selfSignedTrustHosts.forEach { grant ->
                    val coversThisServer = serverAddresses.any { address ->
                        SelfSignedTrustMatcher.isAddressGranted(setOf(grant), address)
                    }
                    if (coversThisServer) {
                        networkOfflineStore.removeSelfSignedTrustHost(grant)
                    }
                }
            }
        }
    }

    private fun normalizeAddress(address: String): String =
        address.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }

    /**
     * Orphan-grant cleanup, run after an address or whole server is removed:
     * drops every granted trust entry that no longer covers ANY known server
     * address (the primary or an alternate of ANY server, normalized, matched
     * with the same [SelfSignedTrustMatcher] the toggle and the handshake
     * layer use — so a portless grant survives while any port of its host is
     * still known, and vice versa). A grant still covering another address
     * survives; a grant whose last covering address is gone would otherwise
     * linger forever as an invisible trust hole with no UI left to revoke it.
     *
     * Best-effort by design: the server list is re-read from the repository
     * AFTER the removal, and any race (a removal not yet visible to the
     * servers flow) simply defers the prune to the next removal — never drops
     * a covering grant.
     */
    private suspend fun pruneOrphanedTrustGrants() {
        val knownAddresses = authRepository.servers.first()
            .flatMap { listOf(it.address) + it.alternateAddresses }
            .map { normalizeAddress(it) }
        val granted = networkOfflineStore.networkOffline.value.selfSignedTrustHosts
        granted.forEach { grant ->
            val stillCoversSomeAddress = knownAddresses.any { address ->
                SelfSignedTrustMatcher.isAddressGranted(setOf(grant), address)
            }
            if (!stillCoversSomeAddress) {
                networkOfflineStore.removeSelfSignedTrustHost(grant)
            }
        }
    }
}
