package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections

/**
 * Bundles the preference/state stores the detail content core reads into a
 * single constructor parameter for [DetailViewModel], following the
 * [DetailActionFactories] aggregation pattern. Pure DI aggregation — no
 * behaviour.
 */
internal class DetailStores constructor(
    val projections: PreferenceProjections,
    val libraryStore: LibraryStore,
    val homeDiscoveryStore: HomeDiscoveryStore,
    val experimentalStore: ExperimentalStore,
    val engineStore: PlayerEngineStore,
)
