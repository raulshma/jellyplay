package com.raulshma.jellyplay.core.network.config

import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import kotlinx.coroutines.flow.StateFlow

/**
 * Network-layer view of the user preferences that drive OkHttp configuration.
 *
 * Defining this interface in the network module lets it own its
 * configuration contract without depending on the datastore layer (which would
 * invert the layered dependency rule `data → network → ...`). Concrete
 * implementations live upstream (e.g. `core:data` adapts `UserPreferencesStore`
 * into this shape).
 *
 * The [StateFlow] form lets the network layer read a current value cheaply via
 * `.value` (used by request interceptors that must not suspend) while still
 * reacting to runtime preference changes.
 */
data class OkHttpConfig(
    val maxCacheSizeMb: Int,
    val networkTimeoutPreset: NetworkTimeoutPreset,
    val verboseNetworkLogging: Boolean,
)

interface OkHttpConfigProvider {
    val config: StateFlow<OkHttpConfig>
}
