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

/** The server-address operation an [AddressOperationPhase] reports. */
enum class AddressOperationVerb {
    /** [ServerManagementViewModel.addServerAddress] — the Add Address sheet's save. */
    Add,

    /** [ServerManagementViewModel.switchServerAddress] — the address row's "switch to" action. */
    Switch,
}

/**
 * The server-address operation machine ([addServerAddress] /
 * [switchServerAddress]) — one phase where the former
 * `_addressOperationMessage: String?` + `_isAddressOperationInProgress`
 * pair settled by hand. Shape mirrors the landed `SnapshotActionPhase`
 * (Idle/Running(verb) + a terminal face); the verb is carried so a fresh
 * in-flight run's face is distinguishable in principle even though today's
 * screen renders every terminal the same way.
 *
 * Byte-preserving map (the state the pair could hold → the variant):
 *  - no operation / the parked message handed to the bus → [Idle];
 *  - operation in flight, parked message cleared at entry → [Running]
 *    (the entry clear is ATOMIC now — the former two-write window where
 *    `inProgress=true` but the old message still stood is unrepresentable;
 *    it was unobservable in composition, so no behavior moved);
 *  - terminal outcome text parked until the screen posts it to the
 *    UserMessageBus (or the next operation clears it) → [Completed].
 *    [Completed] deliberately carries BOTH faces: the former shape never
 *    distinguished success from failure in state — the screen posted either
 *    text verbatim (`bus.info`) — and the exact strings are preserved
 *    ("Address added" / "Failed to add address" /
 *    "Switched to <address>" / "Failed to switch address", with the
 *    exception-message fallbacks).
 *
 * Preserved verbatim, flagged (the former shape's own semantics, NOT
 * declared fixes): there is NO re-entry guard on the address operations
 * (the switch half's guard is the precedent if one is ever added) — two
 * concurrent operations each set [Running] and each land [Completed], so
 * the first settle already drops `isAddressOperationInProgress` while the
 * second still runs, exactly as the pair's per-op writes did; and there is
 * no `finally` — a cancellation mid-operation strands [Running] the same
 * way it stranded `inProgress=true`.
 */
sealed interface AddressOperationPhase {
    /** No operation in flight and no parked outcome. */
    data object Idle : AddressOperationPhase

    /** An add/switch is in flight; the parked outcome (if any) was cleared at entry. */
    data class Running(val verb: AddressOperationVerb) : AddressOperationPhase

    /** The outcome text parked for the screen's bus post, until handed off or overwritten. */
    data class Completed(val message: String) : AddressOperationPhase
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

    /**
     * The address operation machine ([AddressOperationPhase] — the former
     * `_addressOperationMessage: String?` one-shot + `_isAddressOperationInProgress`
     * boolean folded into one phase; [isAddressOperationInProgress] below is its
     * DERIVED projection, kept so the screen's spinner/guard surface reads
     * unchanged). The screen posts [AddressOperationPhase.Completed]'s text to
     * the UserMessageBus exactly once and hands the phase back to
     * [Idle][AddressOperationPhase.Idle] via [clearAddressOperationMessage] —
     * the same clear-after-handling timing the parked String had.
     */
    private val _addressOperationPhase =
        composeState<AddressOperationPhase>(AddressOperationPhase.Idle)
    val addressOperationPhase: AddressOperationPhase get() = _addressOperationPhase.value

    /**
     * True while an add/switch address operation is in flight — the derived
     * [AddressOperationPhase.Running] projection (the screen's address-row
     * switch guard and the legacy pins read this Boolean unchanged).
     */
    val isAddressOperationInProgress: Boolean
        get() = _addressOperationPhase.value is AddressOperationPhase.Running

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
     * the paths that bypass it. The guard is checked-and-set SYNCHRONOUSLY,
     * before the coroutine is queued — setting it inside the launched
     * coroutine left a window (until the dispatcher ran the coroutine) where
     * a second caller still read `false` and re-entered, so the documented
     * single-flight behavior was unenforced. The coroutine owns every
     * terminal clear (success, failure, cancellation — the `finally`).
     */
    fun switchServer(serverId: String, onSuccess: () -> Unit) {
        if (_isSwitching.value) return
        _isSwitching.value = true
        _switchServerFailure.value = null
        launch {
            try {
                authRepository.switchServer(serverId)
                    .onSuccess { onSuccess() }
                    .onFailure {
                        _switchServerFailure.value =
                            UiMessage.of(it, Res.string.settings_server_switch_failed)
                    }
            } finally {
                _isSwitching.value = false
            }
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
            _addressOperationPhase.value = AddressOperationPhase.Running(AddressOperationVerb.Add)
            authRepository.addServerAddress(serverId, address)
                .onSuccess {
                    _addressOperationPhase.value = AddressOperationPhase.Completed("Address added")
                }
                .onFailure {
                    _addressOperationPhase.value =
                        AddressOperationPhase.Completed(it.message ?: "Failed to add address")
                }
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
            _addressOperationPhase.value = AddressOperationPhase.Running(AddressOperationVerb.Switch)
            authRepository.switchServerAddress(serverId, address)
                .onSuccess {
                    _addressOperationPhase.value =
                        AddressOperationPhase.Completed("Switched to $address")
                }
                .onFailure {
                    _addressOperationPhase.value =
                        AddressOperationPhase.Completed(it.message ?: "Failed to switch address")
                }
        }
    }

    /**
     * Hands the parked outcome back to [AddressOperationPhase.Idle] once the
     * screen has posted it (the former clear-the-parked-String write).
     */
    fun clearAddressOperationMessage() {
        _addressOperationPhase.value = AddressOperationPhase.Idle
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
