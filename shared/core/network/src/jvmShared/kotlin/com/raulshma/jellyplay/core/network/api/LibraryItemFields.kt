package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.library.LIST_PROJECTION_FIELDS
import org.jellyfin.sdk.model.api.ItemFields

/**
 * Fields every SDK list-shaped query projects, resolved once from the shared
 * commonMain wire projection ([LIST_PROJECTION_FIELDS] stays the declared
 * policy home; this file only resolves its wire names against the SDK enum,
 * failing fast on SDK drift). The hand-copied OVERVIEW +
 * PRIMARY_IMAGE_ASPECT_RATIO pairs across the library, media-info and live-TV
 * clients all flow from here; compositions build on top
 * ([LIST_ITEM_FIELDS_WITH_GENRES], or ad-hoc `LIST_ITEM_FIELDS + …`).
 */
internal val LIST_ITEM_FIELDS: List<ItemFields> = LIST_PROJECTION_FIELDS.map { name ->
    requireNotNull(ItemFields.entries.firstOrNull { it.serialName == name }) {
        "ItemFields has no serial name '$name' — SDK drift vs the shared list projection"
    }
}

/** The genre-rendering composition (the library grid, the empty-library fallback's latest probe). */
internal val LIST_ITEM_FIELDS_WITH_GENRES: List<ItemFields> = LIST_ITEM_FIELDS + ItemFields.GENRES
