package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.monotonicNowMillis
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * H7: the hero clip's metadata leg — a per-candidate TTL-memoised probe with
 * the spec's two-tier source: the server ThemeVideo clip first, the muted
 * YouTube trailer embed second (the detail screen's D22 pair). One candidate
 * = one probe chain per [ttlMs]; a revisit inside the window (hero rotation
 * back to a seen candidate, or a pause-chip toggle re-mounting the surface)
 * serves the memo instead of re-hitting the network. A miss and a FAILED
 * probe memoize identically (both halves null) for the same window, so a
 * candidate with no clip anywhere — or probed while an endpoint was down —
 * degrades to the static hero without hammering the network on every
 * rotation.
 *
 * The memo holds METADATA only (the ThemeVideo clip id / the YouTube key),
 * never a stream URL: the URL is shaped fresh at serve time through
 * [PlaybackRepository.getStreamUrl] — a pure string-shaping call against the
 * ACTIVE session — so a memoized metadata hit can never hand a previous
 * user's stream token to the player (the process-wide single outlives user
 * switches, the HomeGenreRailCache per-VM caveat).
 *
 * Single-threaded by usage (hero effects on the main dispatcher); the fetch
 * hops to [Dispatchers.IO] only around the network calls.
 */
internal class HomeHeroVideoLoader(
    private val libraryApiClient: LibraryApiClient,
    private val playbackRepository: PlaybackRepository,
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository,
    private val clock: () -> Long = ::monotonicNowMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) {
    /** The resolved hero clip for one candidate, serve-ready. */
    internal sealed interface HeroClip {
        /** Server-hosted ThemeVideo — the ambient ExoPlayer transport. */
        data class ThemeVideo(val streamUrl: String) : HeroClip

        /** A YouTube trailer key — the muted WebView embed fallback (Android/TV). */
        data class TrailerEmbed(val videoKey: String) : HeroClip
    }

    private class Entry(
        /** The ThemeVideo clip id; null = the theme-video leg found nothing (or failed). */
        val themeVideoClipId: String?,
        /** The YouTube trailer key; null = the embed leg found nothing (or failed). */
        val trailerKey: String?,
        val fetchedAtMs: Long,
    )

    private val memo = HashMap<String, Entry>()

    /**
     * The hero clip for [itemId], or null when neither leg resolved (static
     * hero). Memoized per the TTL above; the ThemeVideo URL itself is always
     * freshly shaped.
     */
    suspend fun heroClip(itemId: String, mediaType: MediaType): HeroClip? {
        val now = clock()
        val cached = memo[itemId]
        val entry = if (cached != null && now - cached.fetchedAtMs < ttlMs) {
            cached
        } else {
            probe(itemId, mediaType, now)
        }
        entry.themeVideoClipId?.let { clipId ->
            val url = playbackRepository
                .getStreamUrl(itemId = clipId, mediaSourceId = clipId)
                .takeIf { it.isNotBlank() }
            if (url != null) return HeroClip.ThemeVideo(url)
        }
        return entry.trailerKey?.let(HeroClip::TrailerEmbed)
    }

    /** Drops the whole memo — test seam and a cheap session-reset affordance. */
    fun clear() {
        memo.clear()
    }

    private suspend fun probe(itemId: String, mediaType: MediaType, now: Long): Entry {
        val themeVideoClipId = fetchThemeVideoClipId(itemId)
        val trailerKey = if (themeVideoClipId == null && (mediaType == MediaType.MOVIE || mediaType == MediaType.SERIES)) {
            fetchTrailerKey(itemId, mediaType)
        } else {
            null
        }
        return Entry(themeVideoClipId, trailerKey, now).also { entry ->
            memo[itemId] = entry
            prune()
        }
    }

    private suspend fun fetchThemeVideoClipId(itemId: String): String? =
        withContext(Dispatchers.IO) {
            libraryApiClient.getThemeVideos(itemId).getOrNull()?.firstOrNull()?.id
        }

    /**
     * The embed fallback leg: the detail read's TMDB provider id → the Seerr
     * TMDB videos (the detail screen's related-videos leg). A YouTube trailer
     * key only — the WebView embed plays nothing else. Any failure degrades
     * to null (the probe memoizes it).
     */
    private suspend fun fetchTrailerKey(itemId: String, mediaType: MediaType): String? =
        withContext(Dispatchers.IO) {
            val tmdbId = mediaRepository.getMediaDetail(itemId).getOrNull()
                ?.providerIds?.get("tmdb")?.toIntOrNull()
                ?: return@withContext null
            seerrRepository.getTmdbVideos(tmdbId, mediaType).getOrNull()
                ?.firstOrNull { video ->
                    video.site.equals("YouTube", ignoreCase = true) && !video.key.isNullOrBlank()
                }
                ?.key
        }

    /** Bounded memory: the candidate pool is ~3 but Surprise-Me picks roam — evict the stalest entries past the cap. */
    private fun prune() {
        if (memo.size <= MAX_ENTRIES) return
        memo.entries
            .sortedBy { it.value.fetchedAtMs }
            .take(memo.size - MAX_ENTRIES)
            .forEach { memo.remove(it.key) }
    }

    companion object {
        /** Rotation-revisit window: cheap enough to be invisible, fresh enough for a session. */
        const val DEFAULT_TTL_MS = 10 * 60_000L

        private const val MAX_ENTRIES = 16
    }
}
