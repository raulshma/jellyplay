package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DiscoveredServer
import com.raulshma.jellyplay.core.network.ServerDiscoveryService
import kotlinx.coroutines.flow.Flow

// C4p2 placement note: the impl lives in jvmShared (not commonMain, unlike its
// interface) because its constructor takes [ServerDiscoveryService], which is
// defined in :shared:core:network's jvmShared source set — invisible from
// commonMain.

/** Thin module-boundary view of [ServerDiscoveryService] (core:network stays hidden from feature modules). */
class ServerDiscoveryRepositoryImpl constructor(
    private val discoveryService: ServerDiscoveryService,
) : ServerDiscoveryRepository {
    override fun discoverLocalServers(): Flow<DiscoveredServer> = discoveryService.discoverLocalServers()
}
