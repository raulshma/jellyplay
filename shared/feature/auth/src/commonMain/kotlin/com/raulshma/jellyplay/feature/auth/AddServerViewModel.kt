package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ServerDiscoveryRepository
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.DiscoveredServer
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_cleartext
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_could_not_connect
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_connection_failed
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_connection_timeout
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_local_network_denied
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_resolve_address
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_server_address_required
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_ssl
import kotlinx.coroutines.Job

data class AddServerUiState(
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    val isDiscovering: Boolean = false,
    val discoveryFailed: Boolean = false,
    val isConnecting: Boolean = false,
    val connectError: AuthMessage? = null,
    val manualAddress: String = "",
    /**
     * Non-null when the last connect attempt failed on TLS trust AND the
     * address is https: the canonical `scheme://host[:port]` the user would
     * grant self-signed-certificate trust for. The screen renders the
     * trust-and-retry dialog while set; [AddServerViewModel.dismissTrustPrompt]
     * / [AddServerViewModel.confirmTrustServer] clear it.
     */
    val tlsTrustPromptAddress: String? = null,
)

class AddServerViewModel(
    private val authRepository: AuthRepository,
    private val serverDiscoveryRepository: ServerDiscoveryRepository,
    private val localNetworkStatus: LocalNetworkStatus,
    private val networkOfflineStore: NetworkOfflineStore,
) : JellyPlayViewModel() {

    private val _uiState: StateFlowHandle<AddServerUiState> = stateFlow(AddServerUiState())
    val uiState = _uiState.flow

    private var discoveryJob: Job? = null

    /**
     * Start discovering local Jellyfin servers via SSDP.
     * Automatically acquires/releases the Wi-Fi multicast lock.
     */
    fun startDiscovery() {
        if (_uiState.value.isDiscovering) return

        discoveryJob?.cancel()
        _uiState.update {
            it.copy(
                isDiscovering = true,
                discoveryFailed = false,
                discoveredServers = emptyList(),
            )
        }

        discoveryJob = launch {
            try {
                serverDiscoveryRepository.discoverLocalServers().collect { server ->
                    val current = _uiState.value.discoveredServers
                    if (current.none { it.id == server.id || it.address == server.address }) {
                        _uiState.update {
                            it.copy(discoveredServers = current + server)
                        }
                    }
                }
                _uiState.update { it.copy(isDiscovering = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDiscovering = false,
                        discoveryFailed = true,
                    )
                }
            }
        }
    }

    /**
     * Stop the discovery scan early.
     */
    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _uiState.update { it.copy(isDiscovering = false) }
    }

    /**
     * Update the manual address text field.
     */
    fun updateManualAddress(address: String) {
        _uiState.update {
            it.copy(
                manualAddress = address,
                connectError = null,
                tlsTrustPromptAddress = null,
            )
        }
        pendingTrustRetry = null
    }

    /**
     * Connect to a server using an address string (either from manual entry or discovered server).
     */
    fun connectToServer(address: String, onResult: (Result<ServerInfo>) -> Unit) {
        if (address.isBlank()) {
            _uiState.update { it.copy(connectError = AuthMessage.Resource(Res.string.auth_error_server_address_required)) }
            return
        }

        _uiState.update { it.copy(isConnecting = true, connectError = null, tlsTrustPromptAddress = null) }

        launch {
            val trimmed = address.trim()
            val result = authRepository.addServer(trimmed)
            _uiState.update { it.copy(isConnecting = false) }
            result.onSuccess {
                pendingTrustRetry = null
                onResult(result)
            }.onFailure { throwable ->
                val tlsPrompt = tlsTrustPromptFor(trimmed, throwable)
                _uiState.update {
                    it.copy(
                        connectError = getConnectionErrorMessage(trimmed, throwable, localNetworkStatus),
                        tlsTrustPromptAddress = tlsPrompt,
                    )
                }
                if (tlsPrompt != null) {
                    // Remember how to redo THIS attempt so the trust dialog's
                    // confirm can retry the exact same connect (address +
                    // caller callback) once the grant is persisted.
                    pendingTrustRetry = { connectToServer(address, onResult) }
                } else {
                    pendingTrustRetry = null
                }
                onResult(result)
            }
        }
    }

    /**
     * User declined the trust dialog: drop the prompt (the connect error
     * message stays visible) and forget the pending retry.
     */
    fun dismissTrustPrompt() {
        pendingTrustRetry = null
        _uiState.update { it.copy(tlsTrustPromptAddress = null) }
    }

    /**
     * User accepted the trust dialog: persist the host grant, then retry the
     * failed connect. The grant lands in the OkHttp config StateFlow before
     * the retry's probe runs, so the very next TLS handshake honors it — no
     * client rebuild anywhere (the network layer reads the set at handshake
     * time).
     */
    fun confirmTrustServer() {
        val entry = _uiState.value.tlsTrustPromptAddress ?: return
        val retry = pendingTrustRetry
        pendingTrustRetry = null
        launch {
            networkOfflineStore.addSelfSignedTrustHost(entry)
            _uiState.update { it.copy(tlsTrustPromptAddress = null) }
            retry?.invoke()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(connectError = null) }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryJob?.cancel()
    }

    /** Pending retry for the shown TLS-trust dialog, if any. */
    private var pendingTrustRetry: (() -> Unit)? = null
}

private fun getRootCause(throwable: Throwable): Throwable {
    var cause = throwable
    while (cause.cause != null && cause.cause != cause) cause = cause.cause!!
    return cause
}

/**
 * The canonical address to offer self-signed trust for, or null when the
 * failure isn't a TLS-trust failure or the address isn't https (a cleartext
 * address can never present a certificate). Mirrors the normalization
 * `connectToServer` applies before probing, so the stored entry is exactly
 * the endpoint that failed the handshake.
 */
internal fun tlsTrustPromptFor(address: String, throwable: Throwable): String? {
    val normalized = address.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
    }
    if (!normalized.startsWith("https://")) return null
    // SSLHandshakeException / SSLPeerUnverifiedException are both SSLException
    // subclasses — the file-local twin of the jvmShared ApiException
    // classifier's predicate (kept local for the same reason
    // getConnectionErrorMessage classifies inline).
    return if (getRootCause(throwable) is javax.net.ssl.SSLException) normalized else null
}

internal fun getConnectionErrorMessage(
    address: String,
    throwable: Throwable,
    localNetworkStatus: LocalNetworkStatus,
): AuthMessage {
    val root = getRootCause(throwable)
    // Android 17+: when local network access is denied, attempts to reach a
    // LAN host fail as a timeout / connect error / unresolved host. Surface a
    // single actionable message instead of a cryptic generic failure, but only
    // when the target is actually local (public hosts are unaffected by the
    // permission, so blaming it there would be misleading).
    if (localNetworkStatus.blamesFailureOnPermission(address) &&
        (root is java.net.UnknownHostException ||
            root is java.net.ConnectException ||
            root is java.net.SocketTimeoutException)
    ) {
        return AuthMessage.Resource(Res.string.auth_error_local_network_denied)
    }
    return when {
        root is java.net.UnknownHostException -> AuthMessage.Resource(Res.string.auth_error_resolve_address)
        root is java.net.ConnectException -> AuthMessage.Resource(Res.string.auth_error_could_not_connect)
        root is java.net.SocketTimeoutException -> AuthMessage.Resource(Res.string.auth_error_connection_timeout)
        root is javax.net.ssl.SSLException -> AuthMessage.Resource(Res.string.auth_error_ssl)
        root.message?.contains("cleartext", ignoreCase = true) == true ->
            AuthMessage.Resource(Res.string.auth_error_cleartext)
        root.message?.contains("ssl", ignoreCase = true) == true ->
            AuthMessage.Resource(Res.string.auth_error_ssl)
        else -> root.message?.takeIf {
            it.isNotBlank() && !it.startsWith("org.") && it.length < 100
        }?.let { AuthMessage.Raw(it) } ?: AuthMessage.Resource(Res.string.auth_error_connection_failed)
    }
}
