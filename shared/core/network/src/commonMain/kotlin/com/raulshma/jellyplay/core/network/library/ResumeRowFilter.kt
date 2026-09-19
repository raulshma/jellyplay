package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

/**
 * Resume rows the user can actually continue — the client-side half of the
 * #157 rule, applied by every `getContinueWatching` implementation
 * (JVM `LibraryApiClientImpl`, wasm `KtorWasmLibraryApiClient`).
 *
 * /Items/Resume filters on PlaybackPositionTicks > 0 only — it does NOT
 * exclude played items. A position report landing on an already-played item
 * (a sub-threshold replayed STOP, a brief re-watch of a finished episode)
 * leaves the server with Played=true + position>0, which stays resumable
 * forever because nothing server-side ever resets it. Dropping played rows
 * here keeps a watched episode from occupying Continue Watching — the same
 * rule the offline row enforces (OfflineHomeSections) and the one behind
 * `MediaItem.hasWatchProgress`. The server-side half is the
 * `PlayedStateSyncImpl.reconcileOfflineRow` self-heal, which zeroes the
 * poisoned position at the source.
 *
 * The filter runs after the server applied its `limit`, so heavy poisoning
 * can leave the row short of `limit`; the self-heal repairs the server rows,
 * shrinking the gap. Over-fetching to compensate was considered and
 * rejected: the pre-existing parental-rating filter trims post-limit the
 * same way, so the row already accepts under-fill there.
 *
 * BOOK rows are excluded from the video resume row outright — they surface in
 * their own Continue Reading section ([readingResumableOnly]) and must not
 * pollute the video resume row.
 */
fun List<MediaItem>.resumableOnly(): List<MediaItem> =
    filter { !it.isPlayed && it.mediaType != MediaType.BOOK }

/**
 * The books half of the resume query — the exact complement of
 * [resumableOnly]'s book exclusion: keep only BOOK items, under the same
 * #157 played-row rule (the resume endpoint does not exclude played items, so
 * a finished book's lingering position would otherwise occupy the row
 * forever). Applied by every `getContinueReading` implementation (JVM
 * `LibraryApiClientImpl`, wasm `KtorWasmLibraryApiClient`) as the
 * belt-and-braces client-side filter behind the server's
 * `IncludeItemTypes=Book` narrowing.
 */
fun List<MediaItem>.readingResumableOnly(): List<MediaItem> =
    filter { !it.isPlayed && it.mediaType == MediaType.BOOK }

/**
 * The resume rows' full post-fetch chain, folded once for both client twins
 * (`LibraryApiClientImpl`, `KtorWasmLibraryApiClient` — each used to hand-copy
 * this tail per endpoint): parental filter → id-distinct → the #157
 * played-row rule's books-or-video half. [isBooks] selects
 * [readingResumableOnly] over [resumableOnly] exactly as the two
 * getContinueReading implementations do behind their server-side Book
 * narrowing.
 */
internal fun List<MediaItem>.toFilteredResumeRows(
    maxParentalRating: Int?,
    isBooks: Boolean,
): List<MediaItem> {
    val filtered = filterByParentalRating(maxParentalRating).distinctBy { it.id }
    return if (isBooks) filtered.readingResumableOnly() else filtered.resumableOnly()
}
