package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.JellyPlayEpisodeMarker
import com.raulshma.jellyplay.core.model.JellyPlayEpisodeScore
import com.raulshma.jellyplay.core.model.JellyPlayPluginRating

/**
 * Wire → model mappers for the JellyPlay plugin's detail-screen faces (the
 * [JellyfinDtoMappers] convention, plugin half): the ratings role's mdblist
 * chips and per-episode TMDB scores plus the markers role's anime markers.
 * The detail feature consumes the core:model projections — the wire DTOs
 * stop at the client boundary.
 *
 * Field-for-field verbatim by design: the plugin contract is versioned
 * (docs/CONTRACT.md) and these shapes carry no client-side reshaping beyond
 * the type move — any future normalization belongs in the plugin, not here.
 */

/** [JellyPlayRatingEntry] → the detail ratings row's chip model. */
fun JellyPlayRatingEntry.toModel(): JellyPlayPluginRating =
    JellyPlayPluginRating(
        source = source,
        score = score,
        votes = votes,
        url = url,
    )

/** [JellyPlayEpisodeRatings] → the seasons section's per-episode score model. */
fun JellyPlayEpisodeRatings.toModel(): JellyPlayEpisodeScore =
    JellyPlayEpisodeScore(
        tmdbScore = tmdbScore,
        tmdbVotes = tmdbVotes,
    )

/** [JellyPlayAnimeMarker] → the seasons section's badge-fold marker model. */
fun JellyPlayAnimeMarker.toModel(): JellyPlayEpisodeMarker =
    JellyPlayEpisodeMarker(
        type = type,
        episodeNumber = episodeNumber,
        note = note,
    )
