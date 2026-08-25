package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * What an action helper needs to know about the loaded item. Published by
 * [DetailViewModel] at the two places it already owns the load lifecycle:
 * reset to a bare `itemId`-only session in `loadItemInternal`, adopted
 * (content sections filled) in `reduceLoaded` on each new resolution.
 *
 * This replaces the former provider-lambda web (`detailProvider`,
 * `itemIdProvider`, `seasonsProvider`, …) stitched into every helper at
 * construction: helpers now take this [StateFlow]-shaped snapshot as an
 * explicit, test-settable constructor dependency and read `.value` at command
 * time — the same deferred-read timing the lambdas provided, without the
 * shallow inverted interface.
 */
@Immutable
internal data class DetailSession(
    val itemId: String,
    val seriesId: String? = null,
    val detail: MediaDetail? = null,
    val seasons: List<MediaItem> = emptyList(),
    val episodes: Map<String, List<MediaItem>> = emptyMap(),
    val sortedEpisodes: List<MediaItem> = emptyList(),
)
