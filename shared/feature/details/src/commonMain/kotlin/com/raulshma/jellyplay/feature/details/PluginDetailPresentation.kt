package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.JellyPlayEpisodeScore
import com.raulshma.jellyplay.core.model.JellyPlayEpisodeMarker
import com.raulshma.jellyplay.core.model.MediaDetail

/**
 * Pure presentation helpers for the jellyfin-plugin-jellyplay detail sections
 * (ADR 0010): the provider-id resolutions the three plugin enrichments need
 * plus the anime-marker → badge fold the seasons section renders. Extracted
 * beside [resolveTmdbId] (TmdbIdResolver.kt) so the decision tables are
 * Compose-free and directly unit-testable — the plugin bodies in the VM only
 * thread these results into state.
 *
 * The providerIds map is lowercase-normalized by the network layer's DTO
 * mapper (`k.lowercase()` in JellyfinDtoMappers), so every key below is a
 * lowercase literal.
 */

/**
 * The IMDb id the mdblist ratings probe (`JellyPlayRatingsRoutes.getMdbListRatings`)
 * is keyed on. Resolution order mirrors [resolveTmdbId]:
 *   1. `imdb` provider id
 *   2. `imdbid` provider id
 *   3. The first imdb.com/title URL whose path carries a `tt…` id
 */
internal fun resolveImdbId(detail: MediaDetail): String? {
    val providerIds = detail.providerIds
    providerIds["imdb"]?.takeIf { it.isNotBlank() }?.let { return it }
    providerIds["imdbid"]?.takeIf { it.isNotBlank() }?.let { return it }

    for (url in detail.externalUrls) {
        if (!url.url.contains("imdb.com")) continue
        IMDB_ID_REGEX.find(url.url)?.let { return it.value }
    }
    return null
}

private val IMDB_ID_REGEX = Regex("""tt\d+""")

/**
 * The series-level provider id the anime-markers probe
 * (`JellyPlayMarkersRoutes.getAnimeMarkers`)
 * resolves fillers/recaps by. Anime scrapers key on the anime databases
 * first, falling back to tvdb (the id every Jellyfin series carries):
 * anilist → mal → anidb → tvdb, first non-blank wins, null when the series
 * carries none (the badge section then stays absent).
 */
internal fun resolveProviderSeriesId(detail: MediaDetail): String? {
    val providerIds = detail.providerIds
    for (key in PROVIDER_SERIES_ID_KEYS) {
        providerIds[key]?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

private val PROVIDER_SERIES_ID_KEYS = listOf("anilist", "mal", "anidb", "tvdb")

/**
 * The episode-badge kinds the seasons section renders from the plugin's
 * anime markers. The marker `type` strings are the contract's
 * `filler | mixed | canon | recap`; `canon` (and any forward-compatible
 * unknown type) renders NO badge.
 */
enum class AnimeBadgeKind {
    FILLER,
    MIXED,
    RECAP,
}

/**
 * The badge kind for one wire marker type — null for `canon` / unknown
 * (only filler-ish episodes carry a visible badge).
 */
internal fun animeBadgeKindFor(type: String): AnimeBadgeKind? = when (type.lowercase()) {
    "filler" -> AnimeBadgeKind.FILLER
    "mixed" -> AnimeBadgeKind.MIXED
    "recap" -> AnimeBadgeKind.RECAP
    else -> null
}

/**
 * Folds a series' markers into the episodeNumber → badge map the seasons
 * section reads. Later duplicates of an episodeNumber win (the plugin's
 * scrapers emit one marker per episode; the fold degrades to last-wins
 * rather than crashing). Only badge-carrying kinds are kept — canon
 * episodes are absent from the map and render without a badge.
 */
internal fun animeBadges(markers: List<JellyPlayEpisodeMarker>): Map<Int, AnimeBadgeKind> =
    buildMap {
        for (marker in markers) {
            val kind = animeBadgeKindFor(marker.type) ?: continue
            put(marker.episodeNumber, kind)
        }
    }

/**
 * The season-average TMDB score the seasons section's header renders beside
 * the title: the plain mean over the season's score-carrying episodes,
 * rounded to one decimal, null when no episode carries a score (the chip then
 * stays absent). Pure so the fold is directly unit-testable beside
 * [animeBadges].
 */
internal fun seasonAverageScore(ratings: Map<Int, JellyPlayEpisodeScore>): Double? {
    val scores = ratings.values.mapNotNull { it.tmdbScore }
    if (scores.isEmpty()) return null
    return (scores.sum() / scores.size)
        .let { kotlin.math.round(it * 10.0) / 10.0 }
}
