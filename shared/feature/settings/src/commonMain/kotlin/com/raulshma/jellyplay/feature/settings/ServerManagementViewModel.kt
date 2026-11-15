package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ClientCertificateRepository
import com.raulshma.jellyplay.core.data.repository.SelfSignedTrustRepository
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.ClientCertificateImport
import com.raulshma.jellyplay.core.model.ClientCertificateStatus
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.normalizeServerAddress
import com.raulshma.jellyplay.core.ui.message.UiMessage
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_switch_failed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * One-shot certificate-operation outcome emitted by
 * [ServerManagementViewModel] and rendered by [ServerManagementScreen],
 * which resolves the resource text (compose-resources) and posts it to the
 * shared UserMessageBus — the [PrivacyUserMessage] pattern, so no
 * hardcoded English leaks into shared code.
 */
@Immutable
sealed interface CertificateUserMessage {
    /** Import succeeded — `settings_client_certificate_imported[_expires]`. */
    data class Imported(val notValidAfterMs: Long?) : CertificateUserMessage

    /** The certificate was removed — `settings_client_certificate_removed`. */
    data object Removed : CertificateUserMessage

    /** Import failed — the facade's user-presentable text, or the generic
     * `settings_client_certificate_import_failed` fallback when null. */
    data class ImportFailed(val text: String?) : CertificateUserMessage
}

class ServerManagementViewModel(
    private val authRepository: AuthRepository,
    private val serverIdentityStore: ServerIdentityStore,
    private val networkOfflineStore: NetworkOfflineStore,
    private val selfSignedTrustRepository: SelfSignedTrustRepository,
    private val clientCertificate: ClientCertificateRepository,
) : JellyPlayViewModel() {

    private val _servers = composeState<List<ServerInfo>>(emptyList())
    val servers: List<ServerInfo> get() = _servers.value

    private val _activeServerId = composeState<String?>(null)
    val activeServerId: String? get() = _activeServerId.value

    private val _isSwitching = composeState(false)
    val isSwitching: Boolean get() = _isSwitching.value

    /**
     * One-shot switch-server failure outcome (the
     * [CertificateUserMessage] pattern: the screen resolves the resource
     * fallback and posts to the shared UserMessageBus). DECLARED FIX: a
     * failed switch previously settled the spinner with no feedback — the
     * cards just re-enabled and nothing told the user the active server had
     * NOT changed. Cleared at the start of the next switch attempt.
     */
    private val _switchServerFailure = composeState<UiMessage?>(null)
    val switchServerFailure: UiMessage? get() = _switchServerFailure.value

    private val _addressOperationMessage = composeState<String?>(null)
    val addressOperationMessage: String? get() = _addressOperationMessage.value

    private val _isAddressOperationInProgress = composeState(false)
    val isAddressOperationInProgress: Boolean get() = _isAddressOperationInProgress.value

    /** Addresses the user granted self-signed-certificate trust for. */
    private val _selfSignedTrustHosts = composeState<Set<String>>(emptySet())
    val selfSignedTrustHosts: Set<String> get() = _selfSignedTrustHosts.value

    /** Live status of the app-level client certificate (mTLS). */
    private val _clientCertificateStatus = composeState(ClientCertificateStatus())
    val clientCertificateStatus: ClientCertificateStatus get() = _clientCertificateStatus.value

    /** Ephemeral outcome of a certificate operation (surfaced via the message bus). */
    private val _certificateMessage = composeState<CertificateUserMessage?>(null)
    val certificateMessage: CertificateUserMessage? get() = _certificateMessage.value

    private val _isCertificateOperationInProgress = composeState(false)
    val isCertificateOperationInProgress: Boolean get() = _isCertificateOperationInProgress.value

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
        launch {
            clientCertificate.status.collect { status ->
                _clientCertificateStatus.value = status
            }
        }
    }

    /**
     * Switches the active server. Failure parks [switchServerFailure] (the
     * declared fix above) and settles the spinner exactly as before; success
     * still fires [onSuccess] for the caller's navigation. A switch attempt
     * clears any parked failure first.
     *
     * Re-entry guard: a second switch while one is in flight is dropped. The
     * screen disables the whole server card while switching, so this guards
     * the paths that bypass it.
     */
    fun switchServer(serverId: String, onSuccess: () -> Unit) {
        if (_isSwitching.value) return
        launch {
            _isSwitching.value = true
            _switchServerFailure.value = null
            authRepository.switchServer(serverId)
                .onSuccess { onSuccess() }
                .onFailure {
                    _switchServerFailure.value =
                        UiMessage.of(it, Res.string.settings_server_switch_failed)
                }
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

    /** Dismisses a parked switch failure once the screen has surfaced it. */
    fun clearSwitchServerFailure() {
        _switchServerFailure.value = null
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
     * writes), normalized inside the repository seam the same way
     * `connectToServer` normalizes before probing so the stored entry and
     * this read agree byte-for-byte.
     *
     * Delegates through [SelfSignedTrustRepository] — core:data's view of the
     * SAME pure matcher the handshake-time trust layer uses: the layer honors
     * a portless grant on ANY port of the host, so exact string membership
     * here would show the toggle OFF for a grant every handshake honors
     * (review finding — display drift).
     */
    fun isSelfSignedTrustGranted(server: ServerInfo): Boolean =
        selfSignedTrustRepository.isSelfSignedTrustGranted(selfSignedTrustHosts, server.address)

    /**
     * Grants or revokes this server's self-signed-certificate trust. Grant
     * writes the primary address; revoke removes EVERY granted entry that
     * covers any of this server's addresses (primary + alternates, matched
     * through [SelfSignedTrustRepository] — so a portless grant covering a
     * ported address is revoked too, mirroring what the toggle displays;
     * revoking one address of a server while leaving its siblings trusted
     * would be surprising). Note (documented limitation, mirrored in the
     * network layer's KDoc): already pooled TLS connections stay trusted
     * until they idle out of OkHttp's connection pool or the process
     * restarts; only NEW handshakes are gated.
     */
    fun setSelfSignedTrust(server: ServerInfo, granted: Boolean) {
        launch {
            if (granted) {
                networkOfflineStore.addSelfSignedTrustHost(normalizeServerAddress(server.address))
            } else {
                val serverAddresses = listOf(server.address) + server.alternateAddresses
                selfSignedTrustRepository
                    .selfSignedTrustGrantsCovering(selfSignedTrustHosts, serverAddresses)
                    .forEach { grant -> networkOfflineStore.removeSelfSignedTrustHost(grant) }
            }
        }
    }

    /**
     * Orphan-grant cleanup, run after an address or whole server is removed:
     * drops every granted trust entry that no longer covers ANY known server
     * address (the primary or an alternate of ANY server, normalized inside
     * [SelfSignedTrustRepository] — the same matcher the toggle and the
     * handshake layer use, so a portless grant survives while any port of its
     * host is still known, and vice versa). A grant still covering another
     * address survives; a grant whose last covering address is gone would
     * otherwise linger forever as an invisible trust hole with no UI left to
     * revoke it.
     *
     * Best-effort by design: the server list is re-read from the repository
     * AFTER the removal, and any race (a removal not yet visible to the
     * servers flow) simply defers the prune to the next removal — never drops
     * a covering grant.
     */
    private suspend fun pruneOrphanedTrustGrants() {
        val knownAddresses = authRepository.servers.first()
            .flatMap { listOf(it.address) + it.alternateAddresses }
        val granted = networkOfflineStore.networkOffline.value.selfSignedTrustHosts
        val stillCovering = selfSignedTrustRepository.selfSignedTrustGrantsCovering(granted, knownAddresses)
        (granted - stillCovering).forEach { grant ->
            networkOfflineStore.removeSelfSignedTrustHost(grant)
        }
    }

    // ------------------------------------------------- client certificate (mTLS)

    /**
     * Imports + enables a client certificate. The screen assembles
     * the [ClientCertificateImport] from its picks; the parse/normalize
     * failures come back as user-presentable messages through
     * [certificateMessage], successes confirm with subject + expiry.
     */
    fun importClientCertificate(input: ClientCertificateImport) {
        launch {
            _isCertificateOperationInProgress.value = true
            _certificateMessage.value = null
            clientCertificate.import(input)
                .onSuccess { status -> _certificateMessage.value = CertificateUserMessage.Imported(status.notValidAfterMs) }
                .onFailure { _certificateMessage.value = CertificateUserMessage.ImportFailed(it.message) }
            _isCertificateOperationInProgress.value = false
        }
    }

    fun setClientCertificateEnabled(enabled: Boolean) {
        clientCertificate.setEnabled(enabled)
    }

    fun removeClientCertificate() {
        clientCertificate.remove()
        _certificateMessage.value = CertificateUserMessage.Removed
    }

    fun clearCertificateMessage() {
        _certificateMessage.value = null
    }
}
