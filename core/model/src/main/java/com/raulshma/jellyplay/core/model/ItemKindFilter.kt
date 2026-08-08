package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * Which nested media kinds a library item query excludes by default.
 *
 * Library browsing shows top-level items (movies, shows) — seasons and
 * episodes are both excluded. The two exclusions travel together for every
 * caller, so they're bundled here instead of being two loose booleans.
 */
@Immutable
data class ItemKindFilter(
    /**
     * When true, EPISODE items are excluded unless the query's [mediaTypes]
     * explicitly include them. Seasons are always excluded unless explicitly
     * requested via mediaTypes.
     */
    val includeEpisodes: Boolean = false,
) {
    companion object {
        /** Top-level library browsing: seasons and episodes both excluded. */
        val TOP_LEVEL = ItemKindFilter()
    }
}
