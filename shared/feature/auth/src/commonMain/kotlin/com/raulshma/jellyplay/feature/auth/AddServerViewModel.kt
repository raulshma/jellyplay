package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ServerDiscoveryRepository
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
)

class AddServerViewModel(
    private val authRepository: AuthRepository,
    private val serverDiscoveryRepository: ServerDiscoveryRepository,
    private val localNetworkStatus: LocalNetworkStatus,
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
            )
        }
    }

    /**
     * Connect to a server using an address string (either from manual entry or discovered server).
     */
    fun connectToServer(address: String, onResult: (Result<ServerInfo>) -> Unit) {
        if (address.isBlank()) {
            _uiState.update { it.copy(connectError = AuthMessage.Resource(Res.string.auth_error_server_address_required)) }
            return
        }

        _uiState.update { it.copy(isConnecting = true, connectError = null) }

        launch {
            val result = authRepository.addServer(address.trim())
            _uiState.update { it.copy(isConnecting = false) }
            result.onSuccess {
                onResult(result)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(connectError = getConnectionErrorMessage(address.trim(), throwable, localNetworkStatus))
                }
                onResult(result)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(connectError = null) }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryJob?.cancel()
    }
}

private fun getRootCause(throwable: Throwable): Throwable {
    var cause = throwable
    while (cause.cause != null && cause.cause != cause) cause = cause.cause!!
    return cause
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
