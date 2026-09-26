package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.network.DesktopNetworkMonitor
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.DesktopOfflineModeManager
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Connectivity family of the desktopDataModule split: the always-connected
 * [NetworkMonitor] pick and the [OfflineModeManager] built over it (the
 * same adjacency androidDataModule uses for its connectivity seams).
 * Binding bodies moved verbatim from the pre-split single-module layout —
 * see [desktopDataModule] for the aggregate and the family map.
 */
internal val desktopConnectivityModule: Module = module {
    single<NetworkMonitor> { DesktopNetworkMonitor() }

    single<OfflineModeManager> {
        DesktopOfflineModeManager(
            networkMonitor = get(),
            networkOfflineStore = get(),
        )
    }
}
