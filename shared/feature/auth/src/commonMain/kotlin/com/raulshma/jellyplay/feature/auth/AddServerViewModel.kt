package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ServerDiscoveryRepository
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.DiscoveredServer
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_server_address_required
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
     * failed connect. `addSelfSignedTrustHost` returning only means the
     * DataStore EDIT completed — the derived `networkOffline` StateFlow the
     * network layer reads at handshake time republishes asynchronously, so we
     * AWAIT the entry appearing in it before retrying (the retry's very first
     * TLS handshake must already honor the grant; racing it used to fail the
     * retry on the same certificate). The wait is bounded: on timeout the
     * retry proceeds anyway — a stuck propagation must not trap the dialog
     * (the retry then just fails like an ungranted host and re-prompts). No
     * client rebuild anywhere (the network layer reads the set at handshake
     * time).
     */
    fun confirmTrustServer() {
        val entry = _uiState.value.tlsTrustPromptAddress ?: return
        val retry = pendingTrustRetry
        pendingTrustRetry = null
        launch {
            networkOfflineStore.addSelfSignedTrustHost(entry)
            withTimeoutOrNull(TRUST_PROPAGATION_TIMEOUT_MS) {
                networkOfflineStore.networkOffline.first { entry in it.selfSignedTrustHosts }
            }
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

    private companion object {
        /**
         * Upper bound for awaiting the trust grant's propagation into the
         * `networkOffline` StateFlow before the post-grant retry (see
         * [confirmTrustServer]). Generous vs. a DataStore round-trip; on
         * expiry the retry proceeds anyway rather than trapping the dialog.
         */
        const val TRUST_PROPAGATION_TIMEOUT_MS = 5_000L
    }
}

/**
 * Walks to the root of a failure chain (loop-guarded). Internal (not private)
 * because the jvmShared/wasmJsMain actuals of [tlsTrustPromptFor] and
 * [getConnectionErrorMessage] share it.
 */
internal fun getRootCause(throwable: Throwable): Throwable {
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
 *
 * Deliberately narrower than the public classifier
 * (`core.network.config.isTlsTrustFailure`, behind `ApiException.isTlsTrustError`
 * on the JVM): the JVM/android actual requires the ROOT cause to be a
 * `javax.net.ssl.SSLException` and ignores bare `CertificateException`s
 * anywhere in the chain. Two reasons the seam can't just reuse the public
 * flag: (a) it is jvmShared/JVM-only (javax types), invisible to this
 * commonMain classifier; (b) the failure crossing this seam is the raw
 * probe/transport chain — never an `ApiException` — and this predicate gates
 * a *security consent dialog*, so it stays conservative: only a root-cause
 * SSL handshake failure (the exact thing a trust grant fixes) may prompt.
 * Declared an expect/actual seam for the same reason
 * [getConnectionErrorMessage] classifies per-platform: the JVM taxonomy is
 * javax-typed, while the web fetch stack surfaces TLS refusals only as
 * opaque transport errors (the wasmJs actual therefore never prompts).
 */
internal expect fun tlsTrustPromptFor(address: String, throwable: Throwable): String?

/**
 * User-facing connect-failure message for the add-server screen, classified
 * from the failure's root cause plus the [LocalNetworkStatus] blame for the
 * Android 17+ local-network permission. Platform seam: the JVM/android
 * actual keys on `java.net` / `javax.net.ssl` exception types; the wasmJs
 * actual keys on the ktor/fetch taxonomy (HttpRequestTimeoutException,
 * ktor-io IOException, browser refusal strings) with the same resources and
 * the same raw-message fallback for untyped failures.
 */
internal expect fun getConnectionErrorMessage(
    address: String,
    throwable: Throwable,
    localNetworkStatus: LocalNetworkStatus,
): AuthMessage
