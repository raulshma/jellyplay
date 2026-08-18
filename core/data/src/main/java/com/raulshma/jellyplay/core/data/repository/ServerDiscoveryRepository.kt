package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DiscoveredServer
import com.raulshma.jellyplay.core.network.ServerDiscoveryService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Discovers Jellyfin servers on the local network via SSDP, emitting each
 * server as it is found. Split out of [AuthRepository] so the auth seam
 * changes only for auth concerns — same rationale as the
 * [RealtimeConnection] split for the socket transport. The multicast lock
 * is managed for the duration of the scan. Backs the add-server flow's
 * automatic discovery.
 */
interface ServerDiscoveryRepository {
    fun discoverLocalServers(): Flow<DiscoveredServer>
}

/** Thin module-boundary view of [ServerDiscoveryService] (core:network stays hidden from feature modules). */
@Singleton
class ServerDiscoveryRepositoryImpl @Inject constructor(
    private val discoveryService: ServerDiscoveryService,
) : ServerDiscoveryRepository {
    override fun discoverLocalServers(): Flow<DiscoveredServer> = discoveryService.discoverLocalServers()
}
