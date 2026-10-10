package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.concurrency.withDeadlineMs
import com.raulshma.jellyplay.core.model.DiscoveredServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import java.net.URI

/**
 * Guards the platform's multicast reception requirement around SSDP scans.
 * Android needs a `WifiManager.MulticastLock` held for the duration of the
 * discovery or the Wi-Fi stack filters the UDP multicast responses out; the
 * legacy Android shim binds [AndroidMulticastLockGuard]. Desktop platforms
 * need nothing — [NoopDiscoveryMulticastGuard] is the default there.
 */
interface DiscoveryMulticastGuard {
    fun acquire()
    fun release()
}

/** Desktop / no-op implementation of [DiscoveryMulticastGuard]. */
class NoopDiscoveryMulticastGuard : DiscoveryMulticastGuard {
    override fun acquire() {}
    override fun release() {}
}

/**
 * Service for discovering Jellyfin servers on the local network via the
 * Jellyfin UDP discovery protocol (broadcast "who is JellyfinServer?" to
 * port 7359, via the Jellyfin SDK).
 *
 * Docker note: behind a bridge network the discovery payload's `Address`
 * names a container-internal interface (172.x), so the SDK-reported address
 * is rewritten to the host the datagram actually came from — see
 * [connectableAddress]. Servers in Docker still need UDP 7359 published to
 * be reachable at all; discovery can also fail on mesh Wi-Fi with IGMP
 * snooping, or VPNs.
 *
 * Handles the platform multicast-reception requirement automatically via the
 * injected [DiscoveryMulticastGuard]:
 * - Acquires the guard before scanning
 * - Releases the guard when scanning completes or is cancelled
 */
class ServerDiscoveryService(
    private val jellyfin: Jellyfin,
    private val multicastGuard: DiscoveryMulticastGuard,
) {

    /**
     * Discover Jellyfin servers on the local network.
     * Returns a flow that emits discovered servers one by one.
     *
     * The multicast guard is acquired for the duration of the scan and released when complete.
     *
     * @param timeoutMs Scan duration in milliseconds (default 3000ms)
     * @param maxServers Maximum number of servers to discover (default 16)
     */
    fun discoverLocalServers(
        timeoutMs: Long = 3_000,
        maxServers: Int = 16,
    ): Flow<DiscoveredServer> = flow {
        multicastGuard.acquire()
        try {
            // The SDK's `timeout` is a per-receive socket timeout, not a scan
            // window — a silent LAN would otherwise spin maxServers+1 receive
            // timeouts before the flow completes. The whole collection runs
            // under the governor's deadline envelope instead (withTimeoutOrNull
            // semantics; the whole-scan window 28e954187 bounded by hand).
            withDeadlineMs(timeoutMs) {
                jellyfin.discovery.discoverLocalServers(
                    timeout = timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    maxServers = maxServers,
                ).collect { server ->
                    emit(
                        DiscoveredServer(
                            id = server.id.toString(),
                            name = server.name,
                            address = server.connectableAddress(),
                        )
                    )
                }
            }
        } finally {
            multicastGuard.release()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get recommended server candidates for a given address input.
     * Validates the address by actually connecting and checks the server score.
     *
     * @param address Raw server address input from the user
     * @param minimumScore Minimum acceptable server score (default GOOD)
     * @return List of recommended servers sorted by quality
     */
    suspend fun getRecommendedServers(
        address: String,
        minimumScore: RecommendedServerInfoScore = RecommendedServerInfoScore.GOOD,
    ): List<RecommendedServerInfo> = withContext(Dispatchers.IO) {
        jellyfin.discovery.getRecommendedServers(
            input = address,
            minimumScore = minimumScore,
        ).toList()
    }
}

/**
 * Connectable base URL for a discovered server.
 *
 * Behind a Docker bridge (or any NAT) the discovery payload's `Address` names
 * the interface the SERVER sees — a container-internal 172.x address no
 * client can reach — while the datagram itself arrives from the bridge host's
 * LAN IP. The SDK surfaces that datagram source in
 * [org.jellyfin.sdk.model.api.ServerDiscoveryInfo.endpointAddress] as a bare
 * host (no scheme, no port): re-apply the payload's scheme and port onto it.
 *
 * When the payload already names the datagram source (bare-metal or
 * `--network=host` deployments) the payload address is kept verbatim, so any
 * server-side port/scheme detail survives untouched. Unparseable payloads and
 * missing endpoint addresses also fall back to the payload address.
 */
internal fun ServerDiscoveryInfo.connectableAddress(): String {
    val endpointHost = endpointAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return address
    val payloadUri = try {
        URI(address)
    } catch (_: Exception) {
        return address
    }
    val payloadHost = payloadUri.host ?: return address
    if (payloadHost.equals(endpointHost, ignoreCase = true)) return address

    return buildString {
        append(payloadUri.scheme?.takeIf { it.isNotEmpty() } ?: "http")
        append("://")
        // getHostAddress() emits bare IPv6 (no brackets); a URI authority needs them
        if (endpointHost.contains(':') && !endpointHost.startsWith("[")) {
            append('[').append(endpointHost).append(']')
        } else {
            append(endpointHost)
        }
        if (payloadUri.port > 0) {
            append(':').append(payloadUri.port)
        }
    }
}
