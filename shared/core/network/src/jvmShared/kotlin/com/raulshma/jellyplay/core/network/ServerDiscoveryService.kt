package com.raulshma.jellyplay.core.network

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
import javax.inject.Inject
import javax.inject.Singleton

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
 * Service for discovering Jellyfin servers on the local network using SSDP (via Jellyfin SDK).
 *
 * Handles the platform multicast-reception requirement automatically via the
 * injected [DiscoveryMulticastGuard]:
 * - Acquires the guard before scanning
 * - Releases the guard when scanning completes or is cancelled
 *
 * Best practices:
 * - Always provide a manual entry fallback alongside automatic discovery
 * - Discovery can fail on Docker bridge networks, mesh Wi-Fi with IGMP snooping, or VPNs
 */
@Singleton
class ServerDiscoveryService @Inject constructor(
    private val jellyfin: Jellyfin,
    private val multicastGuard: DiscoveryMulticastGuard,
) {

    /**
     * Discover Jellyfin servers on the local network using SSDP.
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
            jellyfin.discovery.discoverLocalServers(
                timeout = timeoutMs.toInt(),
                maxServers = maxServers,
            ).collect { server ->
                emit(
                    DiscoveredServer(
                        id = server.id.toString(),
                        name = server.name,
                        address = server.address,
                    )
                )
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
