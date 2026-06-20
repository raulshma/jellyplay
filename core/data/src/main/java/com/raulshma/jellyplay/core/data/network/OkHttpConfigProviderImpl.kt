package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import com.raulshma.jellyplay.core.network.config.OkHttpConfig
import com.raulshma.jellyplay.core.network.config.OkHttpConfigProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapts [UserPreferencesStore] (in `core:datastore`) into the
 * [OkHttpConfigProvider] contract owned by `core:network`.
 *
 * This is the bridge that lets `core:network` consume cache-size / timeout /
 * verbose-logging settings without taking a direct dependency on
 * `core:datastore` (which would invert the layered dependency rule).
 *
 * Eager sharing starts collection immediately so the [config] StateFlow has a
 * current value available as soon as Hilt materialises the singleton (the
 * underlying `UserPreferencesStore.preferences` is itself a StateFlow backed by
 * DataStore, so the first emission arrives quickly on a background thread).
 */
@Singleton
class OkHttpConfigProviderImpl @Inject constructor(
    userPreferencesStore: UserPreferencesStore,
) : OkHttpConfigProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val config: StateFlow<OkHttpConfig> =
        userPreferencesStore.preferences
            .map { prefs ->
                OkHttpConfig(
                    maxCacheSizeMb = prefs.maxCacheSizeMb,
                    networkTimeoutPreset = prefs.networkTimeoutPreset,
                    verboseNetworkLogging = prefs.verboseNetworkLogging,
                )
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = OkHttpConfig(
                    maxCacheSizeMb = 0,
                    networkTimeoutPreset = NetworkTimeoutPreset.DEFAULT,
                    verboseNetworkLogging = false,
                ),
            )
}
