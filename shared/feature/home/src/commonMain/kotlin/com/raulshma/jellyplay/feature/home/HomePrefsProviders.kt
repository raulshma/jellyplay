package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore

/**
 * Construction-time bundle of the four datastore stores the home feature
 * reads and writes — [HomeViewModel]'s constructor takes this one aggregate
 * instead of four store parameters (the same construction seam as
 * [HomeRefresherFactory]: a new store dependency widens this bundle and the
 * DI definition, not the VM's interface and the test harness with it).
 *
 * NOT a read-only narrowing: the VM keeps using the stores exactly as before,
 * including the [HomeDiscoveryStore] command writes from the section-config
 * sheet ([HomeDiscoveryStore.setSectionVisible] and friends).
 */
internal class HomePrefsProviders(
    val homeDiscovery: HomeDiscoveryStore,
    val appearance: AppearanceStore,
    val experimental: ExperimentalStore,
    val playback: PlaybackStore,
)
