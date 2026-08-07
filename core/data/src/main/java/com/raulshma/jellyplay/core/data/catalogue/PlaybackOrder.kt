package com.raulshma.jellyplay.core.data.catalogue

import com.raulshma.jellyplay.core.model.MediaItem

/**
 * The single canonical playback order for a series' episodes, ported verbatim
 * from `DetailViewModel.sortedByPlaybackOrder`:
 *
 *   compareBy(seasonNumber ?: Int.MAX, episodeNumber ?: indexNumber ?: Int.MAX, name)
 *
 * Hoisted to a top-level extension (rather than living private on a ViewModel)
 * so [EpisodeCatalogueImpl] and its tests share one definition. Every caller
 * that needs a flattened, playback-sorted list must read it from
 * [EpisodeCatalogueSnapshot.sortedEpisodes]; this helper exists only to build
 * that derived field.
 */
internal fun Iterable<MediaItem>.sortedByPlaybackOrder(): List<MediaItem> =
    sortedWith(
        compareBy(
            { it.seasonNumber ?: Int.MAX_VALUE },
            { it.episodeNumber ?: it.indexNumber ?: Int.MAX_VALUE },
            { it.name },
        )
    )
