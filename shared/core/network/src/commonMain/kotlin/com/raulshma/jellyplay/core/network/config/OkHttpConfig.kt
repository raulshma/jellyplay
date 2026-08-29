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
    /**
     * Server addresses the user has explicitly trusted to present a
     * self-signed / otherwise unverifiable TLS certificate, in the canonical
     * `scheme://host[:port]` form [com.raulshma.jellyplay.core.network.failover.ServerAddressRouter]
     * uses for endpoint addresses (no trailing slash, port omitted when it is
     * the scheme default). Empty by default — trust is strictly opt-in per
     * server.
     *
     * Read dynamically by the network layer's trust manager / hostname
     * verifier at handshake time (same live-config contract as the timeout /
     * logging interceptor), so granting or revoking takes effect on the next
     * TLS handshake without rebuilding any client. Wasm ignores the field:
     * the browser owns certificate decisions there.
     */
    val selfSignedTrustHosts: Set<String> = emptySet(),
)

interface OkHttpConfigProvider {
    val config: StateFlow<OkHttpConfig>
}
