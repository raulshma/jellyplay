package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import com.raulshma.jellyplay.core.network.config.OkHttpConfig
import com.raulshma.jellyplay.core.network.config.OkHttpConfigProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Adapts [UserPreferencesStore] (in `core:datastore`) into the
 * [OkHttpConfigProvider] contract owned by `core:network`.
 *
 * This is the bridge that lets `core:network` consume cache-size / timeout /
 * verbose-logging settings without taking a direct dependency on
 * `core:datastore` (which would invert the layered dependency rule).
 *
 * Uses the Koin-provided application scope (the DatastoreQualifiers
 * .applicationScope single) rather than spinning its own private scope —
 * there is already a single process-wide
 * SupervisorJob tree doing this kind of work, so a
 * parallel scope would mean two independent SupervisorJob trees and two
 * dispatcher pools for the same purpose (and the private scope was never
 * cancelled).
 *
 * [config] is shared with `SharingStarted.Eagerly` (not `WhileSubscribed`)
 * because its sole reader — the OkHttp interceptor at the network layer — reads
 * it synchronously via `StateFlow.value`, which does NOT register a collector.
 * Under `WhileSubscribed`, the upstream DataStore subscription would stop once
 * the init-time `.first()` reader unsubscribes and the 5 s grace window
 * expired, freezing `.value` at the last cached config forever: runtime
 * changes to the network-timeout preset or verbose-logging toggle would never
 * propagate. Eager sharing keeps the upstream subscription alive for the life
 * of the process so `.value` stays live. `.distinctUntilChanged()` suppresses
 * redundant re-emits on unrelated preference writes (`OkHttpConfig` is a data
 * class).
 */
class OkHttpConfigProviderImpl(
    networkOfflineStore: com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore,
    private val scope: CoroutineScope,
) : OkHttpConfigProvider {

    override val config: StateFlow<OkHttpConfig> =
        networkOfflineStore.networkOffline
            .map { prefs ->
                OkHttpConfig(
                    maxCacheSizeMb = prefs.maxCacheSizeMb,
                    networkTimeoutPreset = prefs.networkTimeoutPreset,
                    verboseNetworkLogging = prefs.verboseNetworkLogging,
                )
            }
            .distinctUntilChanged()
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
