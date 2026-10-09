package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.home.ContinueWatchingRowRule

/**
 * Resume rows the user can actually continue — the client-side half of the
 * #157 rule, applied by every `getContinueWatching` implementation
 * (JVM `LibraryApiClientImpl`).
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
 *
 * The rule itself lives in
 * [com.raulshma.jellyplay.core.model.home.ContinueWatchingRowRule] — the
 * single owner the online fetch, the single-row refresh, the ordering use
 * case and the offline mirror all consume; these extensions are this module's
 * named handles on it.
 */
fun List<MediaItem>.resumableOnly(): List<MediaItem> =
    ContinueWatchingRowRule.filterResumable(this)

/**
 * The books half of the resume query — the exact complement of
 * [resumableOnly]'s book exclusion: keep only BOOK items, under the same
 * #157 played-row rule (the resume endpoint does not exclude played items, so
 * a finished book's lingering position would otherwise occupy the row
 * forever). Applied by every `getContinueReading` implementation (JVM
 * `LibraryApiClientImpl`) as the
 * belt-and-braces client-side filter behind the server's
 * `IncludeItemTypes=Book` narrowing. The rule's single owner is
 * [ContinueWatchingRowRule.filterReadingResumable].
 */
fun List<MediaItem>.readingResumableOnly(): List<MediaItem> =
    ContinueWatchingRowRule.filterReadingResumable(this)

/**
 * The resume rows' full post-fetch chain, folded once for the client twins
 * (`LibraryApiClientImpl` — which used to hand-copy
 * this tail per endpoint): parental filter → id-distinct → the #157
 * played-row rule's books-or-video half → the optional classic-rows rollup
 * fold. [isBooks] selects [readingResumableOnly] over [resumableOnly]
 * exactly as the getContinueReading implementations do behind their
 * server-side `Book` narrowing — both halves are
 * [ContinueWatchingRowRule] members; the parental and rollup folds around
 * them are this module's wire-adjacent shaping and stay here.
 *
 * [dropContainerRollups] is the classic-rows (#168) half: Jellyfin 12.x
 * reports Series/Season containers as resumable themselves (upstream
 * `folderIsResumableFilter`), which the pre-12 server never did — the fold
 * drops them so the rendered row is the pre-12 leaf set (Episode, Movie,
 * MusicVideo, and the edge leaf kinds a pre-12 server also reported: home
 * videos, resumable audio). A no-op on ≤10.x servers (no rollups arrive) and
 * on the video row's non-classic mode. Runs post-limit like every other
 * fold, so heavy rollup pollution can under-fill the row — the same accepted
 * tradeoff as the parental and played-row folds above.
 */
internal fun List<MediaItem>.toFilteredResumeRows(
    maxParentalRating: Int?,
    isBooks: Boolean,
    dropContainerRollups: Boolean = false,
): List<MediaItem> {
    val filtered = filterByParentalRating(maxParentalRating).distinctBy { it.id }
    val folded = if (isBooks) filtered.readingResumableOnly() else filtered.resumableOnly()
    if (!dropContainerRollups || isBooks) return folded
    return folded.filter { it.mediaType != MediaType.SERIES && it.mediaType != MediaType.SEASON }
}
