package com.raulshma.jellyplay.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.raulshma.jellyplay.core.model.HomeSectionsResult

/**
 * On-disk stale-while-revalidate snapshot of a user's home-screen sections.
 *
 * The home screen previously relied on a single in-memory 60s TTL cache
 * (`MediaRepositoryImpl.cachedHomeSections`), so every cold open past that
 * window re-fetched the full section set (8–20 network requests) before the
 * first frame. This table holds the last successful payload keyed by
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
    indices = [
        Index(value = ["serverId", "userId"]),
    ],
)
data class HomeSectionCacheEntity(
    val serverId: String,
    val userId: String,
    /** Structural fingerprint of the query params, matching `MediaRepositoryImpl`'s in-memory cacheKey. */
    val cacheKey: String,
    /** `HomeSectionsResult` encoded as JSON (see [com.raulshma.jellyplay.core.database.Converters]). */
    @ColumnInfo(name = "payloadJson") val payloadJson: String,
    @ColumnInfo(name = "fetchedAt") val fetchedAt: Long = System.currentTimeMillis(),
) {
    val payload: HomeSectionsResult?
        get() = com.raulshma.jellyplay.core.database.Converters.decodeHomeSectionsResult(payloadJson)
}
