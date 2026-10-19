package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Bounds for the "More in this collection" franchise rows: at most two
 * BoxSet ancestors render as rows, each hydrated with a bounded member page
 * in the server's default order (the same page shape the collection detail
 * screen's first page takes).
 */
internal const val FRANCHISE_MAX_COLLECTIONS = 2
internal const val FRANCHISE_MEMBER_LIMIT = 50

/**
 * The "Play collection in order" plan for one franchise row (Fider #4214):
 * [start] is the entry playback STARTS on — the FIRST UNWATCHED member in
 * the server-default order — and [queuedTail] is everything after it, the
 * list the caller appends to the manual up-next queue via
 * `VideoQueueController.enqueueLast` (queue-first semantics; the manual
 * queue is suppressed under SyncPlay by the player's advance routing, where
 * the group's server-side queue owns the order — the contract documented on
 * VideoQueueController itself). Starting the head entry is the caller's
 * play dispatch, not this fold's.
 *
 * Watched state rides the members' server user data (`MediaItem.isPlayed`,
 * mapped from `UserData.played` on every list projection — the
 * `getCollectionItems` members carry it). When EVERY member is watched the
 * fold replays the collection from the top (the tail still queues in order);
 * a member whose user data never arrived reads as unwatched, so a
 * user-data-less payload degrades to the plain in-order play — the first
 * member starts and the rest queue after it. Null only for an empty member
 * list, which the admission/renderer gates never admit.
 *
 * Compose-free and directly unit-testable (the [EpisodeRowPresentation]
 * pattern); the renderer and the enrichment consume it, nothing else.
 */
@Immutable
internal data class FranchisePlayOrder(
    val start: MediaItem,
    val queuedTail: List<MediaItem>,
)

/** See [FranchisePlayOrder]. */
internal fun franchisePlayOrder(members: List<MediaItem>): FranchisePlayOrder? {
    val startIndex = members.indexOfFirst { !it.isPlayed }.takeIf { it >= 0 } ?: 0
    val start = members.getOrNull(startIndex) ?: return null
    return FranchisePlayOrder(
        start = start,
        queuedTail = members.drop(startIndex + 1),
    )
}
