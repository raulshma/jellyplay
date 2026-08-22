package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Lightweight summary of a Jellyfin collection (BoxSet) for the detail
 * screen's "Add to Collection" picker. Mirrors the shape of [Playlist]: the
 * picker renders id, name and item count; the primary image tag is carried
 * (fetched alongside the rest) but not currently drawn in the picker row.
 * The full collection model lives behind the dedicated collection detail
 * screen.
 */
@Immutable
@Serializable
data class CollectionSummary(
    val id: String,
    val name: String,
    val itemCount: Int = 0,
    val imageTag: String? = null,
)
