package com.raulshma.jellyplay.core.model.arr

import androidx.compose.runtime.Immutable

/**
 * Non-secret *arr integration preferences, surfaced via [ArrPreferencesStore].
 *
 * - [useSeerrDiscovery]: when true (default), [ArrRepository] queries Seerr's
 *   existing `/service/{radarr,sonarr}` endpoints for auto-discovered server
 *   credentials. When false, only [manualServers] are used.
 * - [pollIntervalSeconds]: hint for how often queue/calendar should refresh
 *   while the consuming screen is foregrounded. Consumers may clamp this to
 *   their own minimum.
 *
 * Manual server credentials live in the encrypted [ArrSecureCredentialsStore]
 * and are merged into [manualServers] here for read convenience.
 */
@Immutable
data class ArrPreferences(
    val useSeerrDiscovery: Boolean = true,
    val pollIntervalSeconds: Int = DEFAULT_POLL_INTERVAL_SECONDS,
    val manualServers: List<ArrServerConfig> = emptyList(),
) {
    companion object {
        const val DEFAULT_POLL_INTERVAL_SECONDS = 30
    }
}
