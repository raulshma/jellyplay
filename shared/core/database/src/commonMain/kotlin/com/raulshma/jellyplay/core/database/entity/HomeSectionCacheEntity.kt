package com.raulshma.jellyplay.core.database.entity

import com.raulshma.jellyplay.core.model.wallNowMillis
import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.Index
import com.raulshma.jellyplay.core.model.HomeSectionsResult

/**
 * On-disk stale-while-revalidate snapshot of a user's home-screen sections.
 *
 * An in-memory 60s TTL cache (`MediaRepositoryImpl.cachedHomeSections`)
 * cannot cover a cold open past that window, where the full section set
 * (8–20 network requests) would otherwise be re-fetched before the first
 * frame. This table holds the last successful payload keyed by
 * `(serverId, userId, cacheKey)` so the home screen can render instantly on
 * cold open while a network refresh runs in the background.
 *
 * Keyed by server + user identity to avoid serving another user's payload
 * after a switch/logout — parity with the in-memory invalidation in
 * `MediaRepositoryImpl`'s identity observer. The whole `HomeSectionsResult`
 * graph is persisted as one JSON blob ([payloadJson]); with
 * `ignoreUnknownKeys = true` on the serializer, adding fields to `MediaItem`
 * / `HomeSection` later is forward-compatible and won't break older rows.
 */
@Entity(
    tableName = "home_section_cache",
    primaryKeys = ["serverId", "userId", "cacheKey"],
    // (serverId, userId, fetchedAt) serves HomeSectionCacheDao.getLatestForIdentity
    // (filter identity, ORDER BY fetchedAt DESC LIMIT 1) as an ordered range
    // read. The former (serverId, userId) index was a full left-prefix of the
    // composite primary key — SQLite never picked it over the PK — and was
    // dropped by MIGRATION_62_63. See also the pre-v62 shape in
    // [MIGRATION_40_41].
    indices = [
        Index(value = ["serverId", "userId", "fetchedAt"]),
    ],
)
data class HomeSectionCacheEntity(
    val serverId: String,
    val userId: String,
    /** Structural fingerprint of the query params, matching `MediaRepositoryImpl`'s in-memory cacheKey. */
    val cacheKey: String,
    /** `HomeSectionsResult` encoded as JSON (see [com.raulshma.jellyplay.core.database.Converters]). */
    @ColumnInfo(name = "payloadJson") val payloadJson: String,
    @ColumnInfo(name = "fetchedAt") val fetchedAt: Long = wallNowMillis(),
) {
    // Memoized decode of [payloadJson]: the blob spans hundreds of MediaItems
    // (multi-kilobyte JSON) and [payload] is read on the cold-open critical
    // path (HomeSectionsSnapshotStore.cached / offlineLayout), so the decode
    // must run once per entity instance, not on every property access. The
    // backing fields are @Ignore'd — Room persists every un-ignored property,
    // and these are pure derived state.
    @Ignore private var cachedPayload: HomeSectionsResult? = null
    @Ignore private var payloadResolved: Boolean = false

    val payload: HomeSectionsResult?
        get() {
            if (!payloadResolved) {
                cachedPayload =
                    com.raulshma.jellyplay.core.database.Converters.decodeHomeSectionsResult(payloadJson)
                payloadResolved = true
            }
            return cachedPayload
        }
}
