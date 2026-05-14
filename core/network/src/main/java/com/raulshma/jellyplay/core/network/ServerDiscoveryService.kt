package com.raulshma.jellyplay.core.network

import android.content.Context
import android.net.wifi.WifiManager
import com.raulshma.jellyplay.core.model.DiscoveredServer
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Service for discovering Jellyfin servers on the local network using SSDP (via Jellyfin SDK).
 *
 * Handles the Android Wi-Fi multicast lock automatically:
 * - Acquires the lock before scanning
 * - Releases the lock when scanning completes or is cancelled
 *
 * Best practices:
 * - Always provide a manual entry fallback alongside automatic discovery
 * - Discovery can fail on Docker bridge networks, mesh Wi-Fi with IGMP snooping, or VPNs
 */
@Singleton
class ServerDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jellyfin: Jellyfin,
) {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    /**
     * Discover Jellyfin servers on the local network using SSDP.
     * Returns a flow that emits discovered servers one by one.
     *
     * The multicast lock is acquired for the duration of the scan and released when complete.
     *
     * @param timeoutMs Scan duration in milliseconds (default 3000ms)
     * @param maxServers Maximum number of servers to discover (default 16)
     */
    fun discoverLocalServers(
        timeoutMs: Long = 3_000,
        maxServers: Int = 16,
    ): Flow<DiscoveredServer> = flow {
        val multicastLock = acquireMulticastLock()
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
            releaseMulticastLock(multicastLock)
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

    private fun acquireMulticastLock(): WifiManager.MulticastLock {
        val lock = wifiManager.createMulticastLock("JellyPlayDiscovery")
        lock.setReferenceCounted(true)
        lock.acquire()
        return lock
    }

    private fun releaseMulticastLock(lock: WifiManager.MulticastLock) {
        try {
            if (lock.isHeld) {
                lock.release()
            }
        } catch (_: Exception) {
            // Lock may already be released
        }
    }
}
