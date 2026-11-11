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
import com.raulshma.jellyplay.core.ui.message.UiMessage

/**
 * The discovery scan's phase. Orthogonal to [AddServerConnectPhase] BY
 * INTENT: today's connect never stops a running scan (a user can tap a
 * discovered server while the scan keeps spinning — the server-count row
 * shows "found · scanning" during a connect), so the two ladders advance
 * independently and each stays a single value. Single-valuedness is the
 * point: the impossible boolean combinations of the two former flags
 * (`isDiscovering && discoveryFailed` — a scan both running and failed) are
 * no longer representable.
 *
 * Pinned by AddServerViewModelPhaseTest (a Running scan survives a connect)
 * and the discovery cases in AddServerViewModelTest.
 */
sealed class AddServerDiscoveryPhase {
    /** No scan in flight: none ever ran, the last one completed, or it was stopped early. */
    data object Idle : AddServerDiscoveryPhase()

    /** A scan is collecting from [ServerDiscoveryRepository.discoverLocalServers]. */
    data object Running : AddServerDiscoveryPhase()

    /** The last scan threw; the screen offers the retry row (already-found servers are kept). */
    data object Failed : AddServerDiscoveryPhase()
}

/**
 * The connect + TLS-trust machine's whole phase — ONE value replacing the
 * former `isConnecting` / `connectError` / `tlsTrustPromptAddress` flag
 * triple AND the out-of-state `pendingTrustRetry` closure. That closure (a
 * hidden ViewModel field capturing the retry address + the caller's
 * callback) was the implicit "trust dialog armed" flag living OUTSIDE the ui
 * state, which is what let `clearError` produce the incoherent state
 * "trust dialog up, explanatory error gone, retry still armed". Folding the
 * closure into [TlsTrustPending] makes the armed dialog part of the state:
 * clearing the error is a phase transition, and prompt-without-error is no
 * longer representable.
 *
 * Equality note: [TlsTrustPending] carries a lambda, so instances compare by
 * reference — deliberate and safe. The screen only `when`-branches on the
 * phase (never `==`), and the StateFlow's equality dedupe can never wrongly
 * collapse two distinct retry closures (they are distinct objects exactly
 * when they are distinct attempts).
 *
 * Pinned by AddServerViewModelPhaseTest.
 */
sealed class AddServerConnectPhase {
    /** No connect attempt in flight or resolved on screen. */
    data object Idle : AddServerConnectPhase()

    /** A connect attempt is in flight (every spinner/enable condition derives from this). */
    data object Connecting : AddServerConnectPhase()

    /**
     * The last connect failed with the classifier's user-facing [error]. The
     * screen renders it as the address field's error/supporting text;
     * [AddServerViewModel.clearError] is the way back to [Idle].
     */
    data class Failed(val error: AuthMessage) : AddServerConnectPhase()

    /**
     * The last connect failed on TLS trust against an https address:
     * [address] is the canonical `scheme://host[:port]` to grant self-signed
     * trust for (the screen renders the trust-and-retry dialog from it),
     * [error] is the SAME failure's explanatory message — still rendered
     * under the field while the dialog is up, because the former machine set
     * the prompt and the error together and the screen shows both — and
     * [retry] re-runs the exact failed attempt (same address + caller
     * callback) once the grant is persisted.
     *
     * [retry] is null once [AddServerViewModel.confirmTrustServer] has
     * CONSUMED it: the dialog stays up during the grant-propagation await,
     * and a second confirm (double-tap) must not re-invoke the retry — the
     * former code's synchronous `pendingTrustRetry = null` guard, now a
     * visible state field instead of a hidden one.
     */
    data class TlsTrustPending(
        val address: String,
        val error: AuthMessage,
        val retry: (() -> Unit)? = null,
    ) : AddServerConnectPhase()
}

data class AddServerUiState(
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    val discoveryPhase: AddServerDiscoveryPhase = AddServerDiscoveryPhase.Idle,
    val connectPhase: AddServerConnectPhase = AddServerConnectPhase.Idle,
    val manualAddress: String = "",
    /**
     * The Android 17+ local-network rationale banner flag: the platform
     * enforces the permission AND the grant is absent. Folded here by
     * [AddServerViewModel.onLocalNetworkAccessSynced] from the screen's
     * composition-side platform seam (`rememberLocalNetworkAccess()`); a
     * non-enforcing platform (desktop) can never set it. Deliberately NOT
     * part of either phase ladder: it tracks the platform permission sync,
     * not the discovery/connect machines.
     */
    val localNetworkRationale: Boolean = false,
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
     * The screen's local-network permission sync — the composition-side
     * platform seam (`rememberLocalNetworkAccess()`) reports its current
     * state on screen entry and on every grant change, and this method owns
     * the two choreography decisions behind the screen's
     * `LaunchedEffect(localNetwork.isGranted)`:
     *
     * 1. The rationale-banner predicate (`enforced && !granted`) folds into
     *    [AddServerUiState.localNetworkRationale] — the screen renders the
     *    banner from state instead of re-deriving the permission algebra in
     *    composition.
     * 2. Discovery auto-start: a granted report (initial or post-grant
     *    transition) starts a scan — the [startDiscovery] Running-phase
     *    guard dedupes. A denied report never starts one: a scan without
     *    the permission silently finds nothing, which is exactly the trap
     *    the banner replaces.
     */
    fun onLocalNetworkAccessSynced(enforced: Boolean, granted: Boolean) {
        _uiState.update { it.copy(localNetworkRationale = enforced && !granted) }
        if (granted) startDiscovery()
    }

    /**
     * Start discovering local Jellyfin servers (UDP discovery broadcast on port 7359).
     * Automatically acquires/releases the Wi-Fi multicast lock.
     *
     * The former `isDiscovering` guard is now the single-valued
     * [AddServerDiscoveryPhase.Running] check — same dedupe semantics (a
     * second call while a scan runs is a no-op; already-known servers are
     * kept), pinned by AddServerViewModelGapsTest. Completion and failure
     * each land in exactly one phase value, so Running-and-Failed is no
     * longer representable.
     */
    fun startDiscovery() {
        if (_uiState.value.discoveryPhase is AddServerDiscoveryPhase.Running) return

        discoveryJob?.cancel()
        _uiState.update {
            it.copy(
                discoveryPhase = AddServerDiscoveryPhase.Running,
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
                _uiState.update { it.copy(discoveryPhase = AddServerDiscoveryPhase.Idle) }
            } catch (e: Exception) {
                _uiState.update { it.copy(discoveryPhase = AddServerDiscoveryPhase.Failed) }
            }
        }
    }

    /**
     * Stop the discovery scan early.
     */
    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _uiState.update { it.copy(discoveryPhase = AddServerDiscoveryPhase.Idle) }
    }

    /**
     * Update the manual address text field.
     *
     * ONE atomic phase write: the address text and the connect-phase reset to
     * [AddServerConnectPhase.Idle] land in a single `_uiState.update`. The
     * former machine cleared `connectError`/`tlsTrustPromptAddress` in one
     * update and the armed `pendingTrustRetry` closure in a SECOND write,
     * leaving a window where the prompt was visually gone but the retry was
     * still armed. The retry now lives IN the phase, so the single write
     * drops it with everything else — there is no second step to race. The
     * unconditional Idle cannot clobber an in-flight connect: the field is
     * disabled while [AddServerConnectPhase.Connecting] (AddServerScreen's
     * ManualEntrySection), so edits only ever arrive from Idle, Failed or
     * TlsTrustPending.
     */
    fun updateManualAddress(address: String) {
        _uiState.update {
            it.copy(
                manualAddress = address,
                connectPhase = AddServerConnectPhase.Idle,
            )
        }
    }

    /**
     * Connect to a server using an address string (either from manual entry or discovered server).
     *
     * The whole connect+trust machine advances through single-valued phases:
     * entry lands in [AddServerConnectPhase.Connecting] (one write that also
     * drops any previous Failed/TlsTrustPending — including the previous
     * attempt's armed retry, which used to need a separate
     * `tlsTrustPromptAddress = null` clear); the result lands in exactly one
     * terminal phase — [AddServerConnectPhase.Idle], [AddServerConnectPhase.Failed]
     * or [AddServerConnectPhase.TlsTrustPending] — before the caller's
     * callback fires, same ordering as before but with the former two-step
     * `isConnecting=false` then `connectError=…` updates collapsed into one.
     * A TLS-trust failure on an https address arms [AddServerConnectPhase.TlsTrustPending.retry]
     * with a closure redoing THIS attempt (address + caller callback) — the
     * former out-of-state `pendingTrustRetry`, now part of the ui state.
     * Pinned by AddServerViewModelPhaseTest.
     */
    fun connectToServer(address: String, onResult: (Result<ServerInfo>) -> Unit) {
        if (address.isBlank()) {
            // Validation rejection, not an attempt: no Connecting ever shows.
            // Landing in Failed (instead of the former flag-set that left the
            // prompt/retry untouched) is safe: the trust dialog is modal and
            // blocks every connectToServer entry point, so this can never
            // clobber a live TlsTrustPending in practice.
            _uiState.update {
                it.copy(
                    connectPhase = AddServerConnectPhase.Failed(
                        UiMessage.Resource(Res.string.auth_error_server_address_required),
                    )
                )
            }
            return
        }

        _uiState.update { it.copy(connectPhase = AddServerConnectPhase.Connecting) }

        launch {
            val trimmed = address.trim()
            val result = authRepository.addServer(trimmed)
            result.onSuccess {
                // Idle subsumes the former explicit `pendingTrustRetry = null`:
                // the retry lived in the phase, and the transition drops it.
                _uiState.update { it.copy(connectPhase = AddServerConnectPhase.Idle) }
                onResult(result)
            }.onFailure { throwable ->
                val tlsPrompt = tlsTrustPromptFor(trimmed, throwable)
                val error = getConnectionErrorMessage(trimmed, throwable, localNetworkStatus)
                _uiState.update {
                    if (tlsPrompt != null) {
                        it.copy(
                            connectPhase = AddServerConnectPhase.TlsTrustPending(
                                address = tlsPrompt,
                                error = error,
                                retry = { connectToServer(address, onResult) },
                            )
                        )
                    } else {
                        it.copy(connectPhase = AddServerConnectPhase.Failed(error))
                    }
                }
                onResult(result)
            }
        }
    }

    /**
     * User declined the trust dialog: drop back to
     * [AddServerConnectPhase.Failed] carrying the SAME failure's error — the
     * former machine kept `connectError` visible while nulling
     * `tlsTrustPromptAddress`, and the phase encoding preserves that by
     * keeping the error in the destination phase. The armed retry dies with
     * the phase (it only ever existed inside TlsTrustPending).
     */
    fun dismissTrustPrompt() {
        _uiState.update {
            val phase = it.connectPhase
            if (phase is AddServerConnectPhase.TlsTrustPending) {
                it.copy(connectPhase = AddServerConnectPhase.Failed(phase.error))
            } else {
                it
            }
        }
    }

    /**
     * User accepted the trust dialog: persist the host grant, then retry the
     * failed connect. `addSelfSignedTrustHost` returning only means the
     * DataStore EDIT completed — the derived `networkOffline` StateFlow the
     * network layer reads at handshake time republishes asynchronously, so we
     * AWAIT the entry appearing in it before retrying (the retry's very first
     * TLS handshake must already honor the grant; racing it fails the
     * retry on the same certificate). The wait is bounded: on timeout the
     * retry proceeds anyway — a stuck propagation must not trap the dialog
     * (the retry then just fails like an ungranted host and re-prompts). No
     * client rebuild anywhere (the network layer reads the set at handshake
     * time).
     *
     * The retry is consumed SYNCHRONOUSLY (nulled inside the phase) before
     * launching: the dialog stays up during the propagation await, so a
     * double-tap on confirm reaches this method twice — the second read must
     * see the retry already gone (it still persists the grant and re-awaits,
     * exactly as the former machine's second confirm did after its
     * synchronous `pendingTrustRetry = null`). Pinned by
     * AddServerViewModelPhaseTest.
     */
    fun confirmTrustServer() {
        val pending = _uiState.value.connectPhase as? AddServerConnectPhase.TlsTrustPending ?: return
        val entry = pending.address
        val retry = pending.retry
        _uiState.update {
            val phase = it.connectPhase
            if (phase is AddServerConnectPhase.TlsTrustPending) {
                it.copy(connectPhase = phase.copy(retry = null))
            } else {
                it
            }
        }
        launch {
            networkOfflineStore.addSelfSignedTrustHost(entry)
            withTimeoutOrNull(TRUST_PROPAGATION_TIMEOUT_MS) {
                networkOfflineStore.networkOffline.first { entry in it.selfSignedTrustHosts }
            }
            _uiState.update { it.copy(connectPhase = AddServerConnectPhase.Idle) }
            retry?.invoke()
        }
    }

    /**
     * Clear the standalone connect-error banner: only
     * [AddServerConnectPhase.Failed] offers a clearable standalone error, so
     * this is a Failed → Idle phase transition. During
     * [AddServerConnectPhase.TlsTrustPending] the error is PART of the dialog
     * phase — clearing it alone would recreate the former machine's
     * incoherent "trust dialog up, explanatory error gone, retry armed"
     * state, so the phase stands (dismissing the dialog is the way out). The
     * screen itself never calls this today; it exists for the banner-clear
     * contract pinned by AddServerViewModelGapsTest and
     * AddServerViewModelPhaseTest.
     */
    fun clearError() {
        _uiState.update {
            when (it.connectPhase) {
                is AddServerConnectPhase.Failed -> it.copy(connectPhase = AddServerConnectPhase.Idle)
                is AddServerConnectPhase.TlsTrustPending -> it
                AddServerConnectPhase.Connecting, AddServerConnectPhase.Idle -> it
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryJob?.cancel()
    }

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
 * because the platform actuals of [tlsTrustPromptFor] and
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
 * javax-typed.
 */
internal expect fun tlsTrustPromptFor(address: String, throwable: Throwable): String?

/**
 * User-facing connect-failure message for the add-server screen, classified
 * from the failure's root cause plus the [LocalNetworkStatus] blame for the
 * Android 17+ local-network permission. Platform seam: the JVM/android
 * actual keys on `java.net` / `javax.net.ssl` exception types, with the
 * same resources and the same raw-message fallback for untyped failures.
 */
internal expect fun getConnectionErrorMessage(
    address: String,
    throwable: Throwable,
    localNetworkStatus: LocalNetworkStatus,
): AuthMessage
