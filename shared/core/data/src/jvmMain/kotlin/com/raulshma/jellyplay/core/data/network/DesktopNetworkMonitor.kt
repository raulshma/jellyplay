package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.model.NetworkStatus
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

/**
 * Desktop implementation of the [NetworkMonitor] seam: JVM
 * [NetworkInterface]-based reachability (wave 17C — replaces the
 * always-Online stub whose deferral note outlived its phase). The JVM has
 * no connectivity callback, so the upstream re-probes the interface table
 * on a timer; the wiring mirrors [AndroidNetworkMonitor]
 * (distinctUntilChanged + stateIn over WhileSubscribed) with one
 * wave-xB-review lesson applied: the initial value is a SYNCHRONOUS seed
 * probe, not a hardcoded constant, so cold `.value` readers —
 * [com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl],
 * [com.raulshma.jellyplay.core.data.worker.DesktopDownloadManager] — see
 * real state before the first subscription.
 *
 * Semantics (desktop previously reported Online unconditionally; anything
 * reported now must not break that contract's consumers):
 *  - Online iff any interface is up, non-loopback, and has at least one
 *    bound address — [networkStatusFromInterfaces]; Offline otherwise.
 *    [NetworkStatus.Local] is never reported: interface presence says
 *    nothing about gateway/portal validation, and consumers branch on Local
 *    (LAN-favoured URL selection, offline search) that desktop cannot
 *    honor. Reachability-style false-Online remains possible (dead gateway
 *    or an up virtual bridge such as WSL/Hyper-V vEthernet) — same optimism
 *    class as the stub, so no consumer regresses.
 *  - A probe FAILURE (SocketException mid-enumeration) keeps the last
 *    reported state (Online before the first successful probe): a
 *    transient syscall error must not flip
 *    [com.raulshma.jellyplay.core.data.worker.DesktopDownloadManager]'s
 *    offline→online reconnect-resume edge, and distinctUntilChanged keeps
 *    unchanged probes from re-firing its combine at all.
 *  - `isMetered` stays constant false (unmetered): the JVM exposes no
 *    meteredness signal, matching the desktop contract
 *    [com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager]
 *    and [com.raulshma.jellyplay.core.data.playback.AudioCachePolicyGuard]
 *    were written against.
 *
 * Runtime honesty: only [networkStatusFromInterfaces] — the pure decision
 * over an interface snapshot — is unit-tested (DesktopNetworkMonitorTest);
 * the NetworkInterface statics and the polling plumbing are covered by
 * compilation and the synchronous-seed read only.
 */
class DesktopNetworkMonitor : NetworkMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val networkStatus: StateFlow<NetworkStatus> = flow {
        var last: NetworkStatus? = null
        while (currentCoroutineContext().isActive) {
            last = runCatching { probeNetworkStatus() }
                .getOrDefault(last ?: NetworkStatus.Online)
            emit(last)
            delay(PROBE_INTERVAL_MS)
        }
    }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            // Seeded with a one-shot synchronous probe (not a hardcoded
            // Online): WhileSubscribed means the polling upstream only runs
            // while a collector is active, so an un-subscribed `.value`
            // read would otherwise always see the initial constant. An
            // unreadable interface table falls back to Online — the
            // pre-17C observable behavior.
            initialValue = runCatching { probeNetworkStatus() }
                .getOrDefault(NetworkStatus.Online),
        )

    override val isMetered: StateFlow<Boolean> =
        MutableStateFlow(false).asStateFlow()

    // ── probing ──────────────────────────────────────────────

    private fun probeNetworkStatus(): NetworkStatus =
        networkStatusFromInterfaces(interfaceSnapshots())

    private fun interfaceSnapshots(): List<NetworkInterfaceSnapshot> =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { nif ->
            NetworkInterfaceSnapshot(
                isUp = nif.isUp,
                isLoopback = nif.isLoopback,
                hasAddress = nif.inetAddresses.hasMoreElements(),
            )
        }

    private companion object {
        /**
         * NIC enumeration is a local syscall (no network round-trip), so a
         * 15 s re-probe is effectively free while bounding detection lag
         * for the download reconnect edge and the prefetch gate.
         */
        const val PROBE_INTERVAL_MS = 15_000L
    }
}

/**
 * Per-interface facts the desktop probe reduces each [NetworkInterface] to
 * — the shape that keeps [networkStatusFromInterfaces] unit-testable
 * without mocking the NetworkInterface statics.
 */
internal data class NetworkInterfaceSnapshot(
    val isUp: Boolean,
    val isLoopback: Boolean,
    val hasAddress: Boolean,
)

/**
 * Pure decision function over an interface-table snapshot: Online iff ANY
 * interface is up, non-loopback, and has at least one bound address;
 * Offline otherwise — including the empty snapshot (no interfaces
 * enumerated). All three facts are required: loopback alone is not
 * connectivity, an up address-less tunnel/bridge is not connectivity, and
 * a down interface's stale addresses are not connectivity.
 */
internal fun networkStatusFromInterfaces(
    interfaces: List<NetworkInterfaceSnapshot>,
): NetworkStatus =
    if (interfaces.any { it.isUp && !it.isLoopback && it.hasAddress }) {
        NetworkStatus.Online
    } else {
        NetworkStatus.Offline
    }
