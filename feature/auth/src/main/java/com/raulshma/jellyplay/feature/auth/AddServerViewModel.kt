package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.DiscoveredServer
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.network.ServerDiscoveryService
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import dagger.hilt.android.lifecycle.HiltViewModel
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
            _uiState.update { it.copy(connectError = "Please enter a server address") }
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
                    it.copy(connectError = getConnectionErrorMessage(throwable))
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

private fun getConnectionErrorMessage(throwable: Throwable): String {
    val root = getRootCause(throwable)
    return when {
        root is java.net.UnknownHostException -> "Unable to resolve server address"
        root is java.net.ConnectException -> "Could not connect to server"
        root is java.net.SocketTimeoutException -> "Connection timed out"
        root is javax.net.ssl.SSLException -> "SSL/TLS error - check server certificate"
        root.message?.contains("cleartext", ignoreCase = true) == true ->
            "HTTP connections are not allowed. Use HTTPS."
        root.message?.contains("ssl", ignoreCase = true) == true ->
            "SSL/TLS error - check server certificate"
        else -> root.message?.takeIf {
            it.isNotBlank() && !it.startsWith("org.") && it.length < 100
        } ?: "Failed to connect to server"
    }
}
