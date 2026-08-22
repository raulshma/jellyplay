package com.raulshma.jellyplay.core.data.repository

/**
 * Tracks which media items the new-media-notification worker has already
 * surfaced, so the same item isn't notified twice.
 *
 * Defined in `core:data` so the `core:notification` worker + action receiver
 * don't import `core:database`'s DAOs/entities directly — schema changes in
 * `core:database` no longer ripple into `core:notification`.
 *
 * Implementations are backed by [com.raulshma.jellyplay.core.database.dao.SeenMediaDao].
 */
interface SeenMediaRepository {
    /** Total number of tracked items. Used to detect the first-ever scan. */
    suspend fun count(): Int

    /**
     * Returns the subset of [itemIds] that have already been recorded as seen.
     * Callers use the complement to determine which items are genuinely new.
     */
    suspend fun getSeenIds(itemIds: List<String>): Set<String>

    /**
     * Records that the given item was surfaced to the user (and therefore
     * should not be re-notified). Idempotent: re-inserting an item with the
     * same [itemId] is a no-op.
     */
    suspend fun markAsSeen(
        itemId: String,
        libraryId: String,
        mediaType: String,
        seenAtEpochMs: Long = System.currentTimeMillis(),
    )

    /** Convenience bulk variant of [markAsSeen]. */
    suspend fun markAsSeen(
        records: Iterable<SeenMediaRecord>,
    )

    /**
     * Removes seen records older than [cutoffEpochMs]. Called periodically to
     * bound the table size; the time window is decided by the caller (the
     * notification worker prunes anything older than 30 days).
     */
    suspend fun pruneOlderThan(cutoffEpochMs: Long)

    /**
     * Reconciles the seen-media table against the [liveItemIds] the library
     * currently reports, removing orphan rows whose underlying item no longer
     * exists server-side.
     *
     * Orphan rows otherwise persist until the 30-day age prune and keep matching
     * a deleted id, which blocks a future re-add of that id from ever
     * re-triggering a notification. The caller passes the set of item ids it
     * observed in the most recent library scan; any tracked id absent from that
     * set is considered deleted and dropped.
     *
     * Returns the number of orphan rows removed (0 when the table is empty or
     * [liveItemIds] exactly covers it).
     */
    suspend fun reconcileAgainstLiveItemIds(liveItemIds: Set<String>): Int
}

/** Plain-data view of a seen-media record, decoupled from the Room entity. */
data class SeenMediaRecord(
    val itemId: String,
    val libraryId: String,
    val mediaType: String,
    val seenAtEpochMs: Long = System.currentTimeMillis(),
)
