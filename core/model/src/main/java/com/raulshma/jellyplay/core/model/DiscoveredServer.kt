package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * Represents a Jellyfin server discovered on the local network via SSDP.
 */
@Immutable
data class DiscoveredServer(
    val id: String,
    val name: String,
    val address: String,
)
