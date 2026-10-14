package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.ui.components.GlassFilterChip
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * H18 — the exact `Route.LibrarySection` construction a detail-screen genre
 * chip navigates with (`MediaDetailBodySections.kt`'s DetailChipRow onClick:
 * `Route.LibrarySection(title = genre, genre = genre)`). The builder lives
 * inline in the details feature and is not importable from home, so the
 * one-liner is replicated verbatim here — [HomeGenreRailTest] pins the
 * parity, so the two can never drift.
 */
internal fun homeGenreRoute(genreName: String): Route.LibrarySection =
    Route.LibrarySection(title = genreName, genre = genreName)

/**
 * H18 genre-rail fetch + TTL cache — the plan's "mirror the sub-call TTL
 * pattern least-invasively": a VM-scoped cache over
 * [LibraryApiClient.getGenres] with the shared
 * [HomeFreshness.NETWORK_SUBCALL_TTL_MS] window (the same policy constant the
 * network module's own home sub-call caches use). A fresh cache answers
 * without touching the network; an expired/empty one refetches (single-flight
 * latched, like the VM's other one-shot loads); a failed fetch returns the
 * stale-empty cache — the rail renders nothing rather than an error, the
 * home online-only suppression posture.
 *
 * User-scoped like the network module's sub-call caches (the client bean
 * swaps with the signed-in user): the cached entry is keyed on the
 * [CacheIdentity] contract — a different user than the one the entry was
 * fetched for is a guaranteed miss, so a previous user's genre list can never
 * be served to the next within the TTL window.
 */
internal class HomeGenreRailCache(
    private val libraryApiClient: LibraryApiClient,
    private val clock: HomeClock,
) {

    private var cached: List<Genre> = emptyList()
    private var cachedAtEpochMs: Long = 0L
    /** The signed-in user the cached entry was fetched for (identity key). */
    private var cachedForUserId: String? = null
    private var inFlight = false

    /**
     * The top [limit] genres (server-ordered), blank names filtered. Empty
     * when the fetch failed or the caller's [userId] differs from the cached
     * entry's — callers hide the rail on empty.
     */
    suspend fun genres(userId: String, limit: Int = GENRE_RAIL_LIMIT): List<Genre> {
        val now = clock.nowEpochMillis()
        val freshForUser = cached.isNotEmpty() &&
            cachedForUserId == userId &&
            now - cachedAtEpochMs < GENRE_RAIL_TTL_MS
        if (freshForUser) return cached
        if (inFlight) {
            // The single-flight latch must not leak a concurrent load for
            // another user — same-user latch hits ride the existing cache.
            return if (cachedForUserId == userId) cached else emptyList()
        }
        inFlight = true
        try {
            val fetched = libraryApiClient.getGenres(parentId = null, startIndex = 0, limit = limit)
                .getOrDefault(emptyList())
                .filter { it.name.isNotBlank() }
            if (fetched.isNotEmpty()) {
                cached = fetched
                cachedAtEpochMs = clock.nowEpochMillis()
                cachedForUserId = userId
            }
        } finally {
            inFlight = false
        }
        // A failed fetch leaves the (possibly other-user) stale entry — only
        // an entry owned by THIS user may answer.
        return if (cachedForUserId == userId) cached else emptyList()
    }

    companion object {
        /** The plan's "top ~24 genres". */
        const val GENRE_RAIL_LIMIT: Int = 24

        /** Same TTL window as the network module's home sub-call caches. */
        val GENRE_RAIL_TTL_MS: Long = HomeFreshness.NETWORK_SUBCALL_TTL_MS
    }
}

/**
 * The H18 genre quick-access rail — a LazyRow of [GlassFilterChip] (the
 * shared expressive chip, D-pad focus included). Hidden by the caller when
 * the pref is off, the feed is offline, or the cache is empty.
 */
@Composable
internal fun HomeGenreRail(
    genres: List<Genre>,
    onGenreClick: (String) -> Unit,
    contentPad: Dp,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = contentPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(genres, key = { it.id }) { genre ->
            GlassFilterChip(
                label = genre.name,
                selected = false,
                onClick = { onGenreClick(genre.name) },
            )
        }
    }
}
