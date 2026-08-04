package com.raulshma.jellyplay.feature.auth

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.DiscoveredServer
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.network.LocalNetworkAccess
import com.raulshma.jellyplay.core.network.ServerDiscoveryService
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import javax.inject.Inject

data class AddServerUiState(
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    val isDiscovering: Boolean = false,
    val discoveryFailed: Boolean = false,
    val isConnecting: Boolean = false,
    val connectError: String? = null,
    val manualAddress: String = "",
)

@HiltViewModel
class AddServerViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val discoveryService: ServerDiscoveryService,
    @ApplicationContext private val appContext: Context,
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
                discoveryService.discoverLocalServers().collect { server ->
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
            _uiState.update { it.copy(connectError = appContext.getString(R.string.auth_error_server_address_required)) }
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
                    it.copy(connectError = getConnectionErrorMessage(appContext, address.trim(), throwable))
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

private fun getConnectionErrorMessage(context: Context, address: String, throwable: Throwable): String {
    val root = getRootCause(throwable)
    // Android 17+: when local network access is denied, attempts to reach a
    // LAN host fail as a timeout / connect error / unresolved host. Surface a
    // single actionable message instead of a cryptic generic failure, but only
    // when the target is actually local (public hosts are unaffected by the
    // permission, so blaming it there would be misleading).
    if (LocalNetworkAccess.enforced &&
        !LocalNetworkAccess.isGranted(context) &&
        LocalNetworkAccess.isLocalAddress(address) &&
        (root is java.net.UnknownHostException ||
            root is java.net.ConnectException ||
            root is java.net.SocketTimeoutException)
    ) {
        return context.getString(R.string.auth_error_local_network_denied)
    }
    return when {
        root is java.net.UnknownHostException -> context.getString(R.string.auth_error_resolve_address)
        root is java.net.ConnectException -> context.getString(R.string.auth_error_could_not_connect)
        root is java.net.SocketTimeoutException -> context.getString(R.string.auth_error_connection_timeout)
        root is javax.net.ssl.SSLException -> context.getString(R.string.auth_error_ssl)
        root.message?.contains("cleartext", ignoreCase = true) == true ->
            context.getString(R.string.auth_error_cleartext)
        root.message?.contains("ssl", ignoreCase = true) == true ->
            context.getString(R.string.auth_error_ssl)
        else -> root.message?.takeIf {
            it.isNotBlank() && !it.startsWith("org.") && it.length < 100
        } ?: context.getString(R.string.auth_error_connection_failed)
    }
}
