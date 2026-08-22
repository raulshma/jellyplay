package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Stable

/**
 * Represents the health status of the connected Jellyfin server.
 */
@Stable
sealed class ServerHealth {
    /** Server health is unknown (not yet checked or not connected). */
    data object Unknown : ServerHealth()

    /** Server is being checked (ping in progress). */
    data object Checking : ServerHealth()

    /** Server is reachable and responding. */
    data class Healthy(val latencyMs: Long) : ServerHealth()

    /** Server is unreachable or timed out. */
    data object Unreachable : ServerHealth()
}
