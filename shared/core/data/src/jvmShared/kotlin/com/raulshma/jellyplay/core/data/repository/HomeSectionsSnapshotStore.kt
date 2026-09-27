package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionIdentity
import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The deep "home-sections snapshot store": the single owner of the PERSISTED
 * half of the home-sections pipeline — the stale-while-revalidate snapshot in
 * Room's `home_section_cache` table. Its one semantic unit is the snapshot
 * row's whole lifecycle:
 *  - [persist]: the fetch-path write choreography (dedup-window fingerprint
 *    cheap path → off-dispatch encode → byte-identical compare → Room
 *    upsert), so the ~1/min foreground refresh does not re-encode and
 *    rewrite a several-hundred-KB payload that did not change;
 *  - [cached]: the cold-open SWR read (identity + cacheKey scoped, ceilinged
 *    by [HomeFreshness]'s 24h staleness policy);
 *  - [offlineLayout]: the key-agnostic, UNCEILINGED layout mirror the
 *    offline home renders from (issue #147);
 *  - [clearIdentity]: the identity-scoped privacy clear (logout / switch).
 *
 * Extracted verbatim from [MediaRepositoryImpl] (which keeps the IN-MEMORY
 * half — the TtlCache, the discover-roll epoch and the SWR layering — and
 * now delegates the persisted half here, exactly like its seasons/episodes
 * delegation to [com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue]).
 *
 * ## Dependency direction
 *
 * The store depends on `HomeSectionCacheDao` + [HomeSession] + [TimeSource]
 * only — **never** on `MediaRepository`. `MediaRepositoryImpl` instead
 * depends on the store, so the three legacy entry points (`getHomeSections`'
 * persist hook, `getCachedHomeSections`, `getOfflineHomeLayout`) become thin
 * passthroughs over the snapshot. This keeps the Koin construction graph
 * acyclic (both are Koin singles in `core:data`, the direction explicit).
 *
 * ## Contracts this class owns (moved verbatim from the repository)
 *
 *  - **Write-on-fetch only.** [persist] is invoked by the repository from
 *    `TtlCache.getOrFetch`'s `onFetched` hook — the fetch-path-only write —
 *    never on a cache hit, so a hit cannot slide the persisted row's
 *    `fetchedAt` forward and defeat the 24h SWR staleness ceiling in
 *    [cached]. The store itself does not care WHO calls [persist]; the
 *    fetch-path-only discipline lives at the repository's call site.
 *  - **`fetchedAt` is wall-clock on purpose.** The value must survive a
 *    reboot to serve the next cold open, and monotonic clocks reset on
 *    boot. It goes through the injected [TimeSource] (wall-clock read in
 *    production) — the repository's in-memory home TTL reads the same
 *    seam's MONOTONIC clock, so both freshness gates share one test fake.
 *  - **Dedup ordering: fingerprint cheap path FIRST, exact encode+compare
 *    second.** Inside the 60s window, a fingerprint match against the last
 *    payload this process persisted/verified skips even the encode (see
 *    [HomeSnapshotFingerprint] for what the fingerprint covers — and for
 *    the "equal fingerprint does not imply a byte-identical encode" caveat,
 *    which is why a metadata-only change inside the window persists one
 *    refresh cycle later). A miss there falls through to the exact
 *    encode + byte-compare + write path.
 */
class HomeSectionsSnapshotStore(
    private val homeSectionCacheDao: HomeSectionCacheDao,
    /**
     * The single owner of identity transitions (see [HomeSession]). Every
     * read/write keys through [HomeSession.currentIdentity] — the sanctioned
     * suspend source-flow read, immune to the observe-ordering race where a
     * caller fires before the identity mirror is written (the mirror lags a
     * switch by a dispatch, which would read/write the previous identity's
     * rows).
     */
    private val homeSession: HomeSession,
    /**
     * The clock seam for this store's freshness decisions: the wall-clock
     * read stamps [persist]'s `fetchedAt` and drives [cached]'s 24h SWR
     * staleness ceiling. A Koin single in the data JVM module
     * (`SystemTimeSource`); injected so both are unit-testable with one
     * fake.
     */
    private val timeSource: TimeSource,
) {

    /**
     * In-memory record of the last home snapshot this process persisted (or
     * verified byte-identical) for one (server, user, cacheKey): the DB row's
     * `fetchedAt` it was computed against, plus the cheap fingerprint of the
     * payload ([HomeSnapshotFingerprint]). Lets the dedup window in [persist]
     * skip the full JSON re-encode on usually-identical foreground
     * refreshes. Null until the first persist of the process — a miss simply
     * takes the exact encode+compare path.
     */
    private class HomeSnapshotDedupState(
        val serverId: String,
        val userId: String,
        val cacheKey: String,
        val rowFetchedAt: Long,
        val fingerprint: Int,
    )

    // @Volatile: persist runs on the caller's dispatcher (the home refresh
    // path), which is not pinned to one thread.
    @Volatile
    private var lastHomeSnapshotDedup: HomeSnapshotDedupState? = null

    /**
     * The SWR persist: encodes [result] and upserts the row for
     * (current identity, [cacheKey]) — unless the dedup window proves the
     * rewrite would change nothing. Invoked by the repository from the FETCH
     * path only (see the class KDoc); a cancelled collector must still
     * cancel, not park cancellation in a discarded Result — the block
     * suspends on Room reads/writes and the encode.
     */
    suspend fun persist(cacheKey: String, result: HomeSectionsResult) {
        val identity = homeSession.currentIdentity() ?: return
        // Fire-and-forget persist on the home refresh path: a cancelled
        // collector must still cancel, not park cancellation in a discarded
        // Result — the block suspends on Room reads/writes and the encode.
        runCatchingRethrowingCancellation {
            // Foreground refreshes arrive ~once/minute with usually-identical
            // content; when the prior row is younger than the refresh cadence
            // and the payload is byte-identical, the rewrite would advance
            // nothing (fetchedAt refreshes at the next real change) — skip it.
            // Rows older than the window still rewrite, preserving the 24h
            // fetchedAt SWR staleness ceiling for every other path.
            val now = timeSource.nowEpochMillis()
            val existing = homeSectionCacheDao.get(identity.serverId, identity.userId, cacheKey)
            // Computed once here: the cheap-path check and both
            // rememberHomeSnapshotDedup exits below all need the same value.
            val fingerprint = HomeSnapshotFingerprint.of(result)
            // Cheap-path dedup: inside the window, a fingerprint
            // match against the last payload this process persisted/verified
            // for this exact (server, user, cacheKey, row) skips the full
            // encode — that encode used to run on every ~1/min refresh and
            // allocate a several-hundred-KB string even when byte-identical.
            // INVARIANT: fingerprint equal ⇒ the encode would have been
            // byte-identical is NOT guaranteed (only section identity, item
            // ids and their user-data fields are fingerprinted — see
            // [HomeSnapshotFingerprint]), so a metadata-only change inside
            // the window persists one refresh cycle later.
            // Fingerprint unequal, window expired, or no prior state ⇒ the
            // exact pre-existing encode+compare+write path below runs.
            if (existing != null && now - existing.fetchedAt < HOME_PERSIST_DEDUP_WINDOW_MS) {
                val last = lastHomeSnapshotDedup
                if (last != null &&
                    last.serverId == identity.serverId &&
                    last.userId == identity.userId &&
                    last.cacheKey == cacheKey &&
                    last.rowFetchedAt == existing.fetchedAt &&
                    last.fingerprint == fingerprint
                ) {
                    return
                }
            }
            // Encode off the caller's (Main) dispatcher — this runs on every
            // successful home refresh (min. once/minute in foreground).
            val payloadJson = withContext(Dispatchers.Default) {
                com.raulshma.jellyplay.core.database.Converters.encodeHomeSectionsResult(result)
            }
            if (existing != null &&
                now - existing.fetchedAt < HOME_PERSIST_DEDUP_WINDOW_MS &&
                existing.payloadJson == payloadJson
            ) {
                // Byte-identical inside the window: remember the fingerprint
                // (against this row's fetchedAt) so the next in-window
                // refresh can take the cheap path above.
                rememberHomeSnapshotDedup(identity, cacheKey, existing.fetchedAt, fingerprint)
                return
            }
            homeSectionCacheDao.upsert(
                HomeSectionCacheEntity(
                    serverId = identity.serverId,
                    userId = identity.userId,
                    cacheKey = cacheKey,
                    payloadJson = payloadJson,
                    // Wall-clock on purpose: this value must survive a reboot to
                    // serve the next cold open, and monotonic clocks reset on
                    // boot. Goes through the injected [TimeSource] (wall-clock
                    // read in production) — the repository's in-memory TTL uses
                    // the same seam's monotonic read, so both freshness gates
                    // share one test fake.
                    // fetchedAt is load-bearing: [cached] reads it against
                    // HomeFreshness's 24h SWR staleness ceiling.
                    fetchedAt = now,
                ),
            )
            rememberHomeSnapshotDedup(identity, cacheKey, now, fingerprint)
        }
    }

    /**
     * Records [lastHomeSnapshotDedup] for the row fetchedAt the fingerprint
     * was computed against — both persist paths (skipped rewrite and fresh
     * upsert) funnel through here. Takes the fingerprint the caller already
     * computed rather than recomputing it.
     */
    private fun rememberHomeSnapshotDedup(
        identity: SessionIdentity,
        cacheKey: String,
        rowFetchedAt: Long,
        fingerprint: Int,
    ) {
        lastHomeSnapshotDedup = HomeSnapshotDedupState(
            serverId = identity.serverId,
            userId = identity.userId,
            cacheKey = cacheKey,
            rowFetchedAt = rowFetchedAt,
            fingerprint = fingerprint,
        )
    }

    /**
     * The cold-open SWR read: the persisted snapshot for the current
     * (server, user) + [query], ceilinged by [HomeFreshness]'s 24h staleness
     * policy. Room-only — no network. Null when no identity is established,
     * no row exists, or the row is past the ceiling (a stale snapshot must
     * not instant-paint — the cold open shows a spinner instead of ancient
     * content, then the normal refresh re-persists).
     */
    suspend fun cached(query: HomeSectionQuery): HomeSectionsResult? {
        // Read identity from the source flow (via HomeSession's sanctioned
        // suspend read), not the mirror: this runs from the Home VM's
        // currentUser collector, which can fire before the session's identity
        // observer has written the mirror. .first() is suspend + non-blocking
        // and guarantees the current value, so the SWR read never misses due
        // to an observe ordering race.
        val identity = homeSession.currentIdentity() ?: return null
        val entity = homeSectionCacheDao.get(identity.serverId, identity.userId, query.cacheKey()) ?: return null
        // SWR staleness ceiling (HomeFreshness): a snapshot older than 24h
        // must not instant-paint — return null so a cold open shows the
        // spinner instead of ancient content, then the normal refresh
        // proceeds and upserts a fresh row.
        if (!HomeFreshness.isRoomSnapshotFresh(entity.fetchedAt, timeSource.nowEpochMillis())) {
            return null
        }
        // Decode the payload off the caller's (Main) dispatcher — this is the
        // cold-open critical path and the blob spans hundreds of MediaItems.
        return withContext(Dispatchers.Default) { entity.payload }
    }

    /**
     * The offline home's layout mirror: the most recently persisted
     * snapshot for the current (server, user), across cacheKeys and with NO
     * freshness ceiling. Key-agnostic and unceilinged by contract (see the
     * `MediaRepository.getOfflineHomeLayout` KDoc): the offline home
     * re-filters membership against the offline store, so staleness only
     * costs section ORDER/titles, never content.
     */
    suspend fun offlineLayout(): HomeSectionsResult? {
        val identity = homeSession.currentIdentity() ?: return null
        // Key-agnostic latest row and no freshness ceiling, by contract (see
        // the interface KDoc): the offline home re-filters membership against
        // the offline store, so staleness only costs section ORDER/titles,
        // never content. Decode off the caller's dispatcher like the SWR read.
        val entity = homeSectionCacheDao.getLatestForIdentity(identity.serverId, identity.userId)
            ?: return null
        return withContext(Dispatchers.Default) { entity.payload }
    }

    /**
     * Clears the persisted home-section SWR snapshot for a single (server, user).
     * Failure is logged, not swallowed: this runs on logout / identity switch and
     * a silent failure would leave the just-logged-out user's home payload in the
     * table, to be served to a different user on the next cold open.
     */
    suspend fun clearIdentity(serverId: String, userId: String) {
        runCatchingRethrowingCancellation { homeSectionCacheDao.clearForIdentity(serverId, userId) }
            .onFailure { e ->
                Log.w(
                    "MediaRepo",
                    "Failed to clear home-section SWR cache for server=$serverId user=$userId",
                    e,
                )
            }
    }

    companion object {
        /** Window within which a byte-identical home SWR persist is skipped (foreground refresh cadence). */
        private const val HOME_PERSIST_DEDUP_WINDOW_MS = 60 * 1000L
    }
}
