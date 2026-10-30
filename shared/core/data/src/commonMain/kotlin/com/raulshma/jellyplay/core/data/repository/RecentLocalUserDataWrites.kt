package com.raulshma.jellyplay.core.data.repository

/**
 * In-memory log of the item ids THIS device recently wrote (playback
 * progress saves, played/favorite flips — everything funnelling through
 * [UserDataWriteOperations] and the outbox drain's
 * [UserDataAnnouncer.notifyUserDataChanged]).
 *
 * Sole consumer: the home refresher's user-data-push chain. A
 * `UserDataChanged` push carries only user + item ids, so a socket echo of
 * this device's own write burst is indistinguishable from an external
 * push — and every echo used to trigger the forced, cache-bypassing full
 * home refetch. The refresher consults this log instead: when EVERY item id
 * of the pushed change was recently written locally, the echo runs a
 * non-forced (TTL-semantics) chained refresh; the forced path stays
 * reserved for external pushes and the raced-sync bypass.
 *
 * Deliberately in-memory and unbounded in scope: entries older than the
 * echo window are pruned lazily, and losing the log on process death only
 * reverts to today's behavior (all echoes forced) — never the reverse.
 */
interface RecentLocalUserDataWrites {

    /**
     * Records a local write of [itemIds] at the impl's clock's now. Cheap
     * and best-effort: logging never throws into the write path.
     */
    fun record(itemIds: List<String>)

    /**
     * True iff [itemIds] is non-empty and every id was [record]ed within
     * the echo window (entries exactly at the window's age count as
     * expired). An empty list is never "all local" — nothing names the
     * change, so the conservative forced path applies.
     */
    fun wereAllRecentlyWritten(itemIds: List<String>): Boolean
}

/**
 * Null-object implementation: constructor default of the write paths and a
 * stand-in for tests. Records nothing and answers false to every query —
 * exactly the "no echo demotion" stance, so wiring it by accident only
 * preserves the pre-demotion behavior (all echoes forced).
 */
class NoopRecentLocalUserDataWrites : RecentLocalUserDataWrites {
    override fun record(itemIds: List<String>) = Unit
    override fun wereAllRecentlyWritten(itemIds: List<String>): Boolean = false
}
