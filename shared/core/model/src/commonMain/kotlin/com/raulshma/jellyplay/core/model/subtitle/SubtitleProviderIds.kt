package com.raulshma.jellyplay.core.model.subtitle

import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

/**
 * Resolves the provider-identifying fields from a [MediaDetail] into a
 * [SubtitleQuery], shared by the player and editor so both build identical
 * requests.
 *
 * Jellyfin's `providerIds` map uses lowercase keys (`tmdb`, `imdb`, `tvdb`) and
 * stores IMDb ids with their `tt` prefix; some servers emit `tmdbid` instead of
 * `tmdb`, and the TMDB id can also be scraped from `externalUrls`
 * (`themoviedb.org/...`) as a last resort. Centralizing that lookup here means
 * the subtitle providers never repeat the `feature:details` `TmdbIdResolver`
 * logic (which is `internal` to that module and therefore unavailable).
 *
 * For TV episodes the season/episode numbers come from the embedded
 * [MediaItem]; for movies they are left null so the providers omit them.
 */
object SubtitleProviderIds {

    /** Extracts a TMDB id (as Int) from [providerIds] or [externalUrls]. */
    fun tmdbId(providerIds: Map<String, String>, externalUrls: List<com.raulshma.jellyplay.core.model.ExternalUrl>): Int? {
        providerIds["tmdb"]?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        providerIds["tmdbid"]?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        // Scrape themoviedb.org/<type>/<id> URLs as a last resort.
        for (url in externalUrls) {
            val match = TMDB_URL.find(url.url) ?: continue
            return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
        }
        return null
    }

    /** Extracts an IMDb id (incl. `tt` prefix) from [providerIds] or [externalUrls]. */
    fun imdbId(providerIds: Map<String, String>, externalUrls: List<com.raulshma.jellyplay.core.model.ExternalUrl>): String? {
        providerIds["imdb"]?.takeIf { it.isNotBlank() }?.let { return it }
        for (url in externalUrls) {
            val match = IMDB_URL.find(url.url) ?: continue
            return match.groupValues[1]
        }
        return null
    }

    /**
     * Builds a [SubtitleQuery] from a [MediaDetail]. Languages are left empty;
     * the caller appends the user-selected language (it is UI state, not item
     * state). Falls back to a title-based [SubtitleQuery.query] when the item
     * has no TMDB/IMDb id (Wyzie/OpenSubtitles will title-search instead).
     */
    fun buildQuery(detail: MediaDetail): SubtitleQuery {
        val tmdb = tmdbId(detail.providerIds, detail.externalUrls)
        val imdb = imdbId(detail.providerIds, detail.externalUrls)
        val item = detail.item
        val isEpisode = item.mediaType == MediaType.EPISODE
        val titleFallback = if (tmdb == null && imdb == null) {
            item.seriesName?.takeIf { it.isNotBlank() }?.let { series ->
                if (isEpisode && item.seasonNumber != null && item.episodeNumber != null) {
                    "$series S${item.seasonNumber.toString().padStart(2, '0')}E${item.episodeNumber.toString().padStart(2, '0')}"
                } else {
                    series
                }
            } ?: item.name.takeIf { it.isNotBlank() }
        } else {
            null
        }
        return SubtitleQuery(
            tmdbId = tmdb,
            imdbId = imdb,
            query = titleFallback,
            season = item.seasonNumber?.takeIf { isEpisode },
            episode = item.episodeNumber?.takeIf { isEpisode },
        )
    }

    private val TMDB_URL = Regex("themoviedb\\.org/(?:movie|tv)/(\\d+)", RegexOption.IGNORE_CASE)
    private val IMDB_URL = Regex("imdb\\.com/title/(tt\\d+)", RegexOption.IGNORE_CASE)
}
