package com.raulshma.jellyplay.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

// Four redundant-index drops, all schema-only (query results identical):
//  - home_section_cache(serverId, userId): a full left-prefix of the table's
//    composite primary key, so SQLite never picked it over the PK — pure
//    write amplification on every snapshot upsert. It is replaced by
//    (serverId, userId, fetchedAt), which serves
//    HomeSectionCacheDao.getLatestForIdentity (filter identity, ORDER BY
//    fetchedAt DESC LIMIT 1) as an ordered range read instead of a sort.
//  - offline_media's seriesId / seasonId / mediaType single-column indices:
//    exact left-prefixes of the surviving (seriesId, mediaType),
//    (seasonId, mediaType) and (mediaType, createdAt) composites — redundant
//    on every read, so only their write cost remained on the app's
//    highest-churn table (the MIGRATION_51_52 precedent).
val MIGRATION_62_63 = object : Migration(62, 63) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("DROP INDEX IF EXISTS index_home_section_cache_serverId_userId")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "index_home_section_cache_serverId_userId_fetchedAt " +
                "ON home_section_cache(serverId, userId, fetchedAt)"
        )
        db.execSQL("DROP INDEX IF EXISTS index_offline_media_seriesId")
        db.execSQL("DROP INDEX IF EXISTS index_offline_media_seasonId")
        db.execSQL("DROP INDEX IF EXISTS index_offline_media_mediaType")
    }
}
