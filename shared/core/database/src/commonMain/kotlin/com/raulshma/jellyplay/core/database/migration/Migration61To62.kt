package com.raulshma.jellyplay.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

// Auto-download policy (gh-181): two new tables, both additive.
//  - `series_download_policies` — the per-series Instant/Ask/Never override
//    chosen in the series download sheet (empty table = every series inherits
//    the global preference, the pre-gh-181 behavior).
//  - `auto_download_suggestions` — the ASK-mode pending prompts; empty until
//    a pass runs with ASK effective somewhere, so upgrading installs see no
//    behavior change until they opt in. Column DEFAULTs mirror the entities'
//    @ColumnInfo(defaultValue) declarations — Room's migration validation
//    compares them (the v58 precedent).
val MIGRATION_61_62 = object : Migration(61, 62) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `series_download_policies` (" +
                "`seriesId` TEXT NOT NULL, " +
                "`mode` TEXT NOT NULL, " +
                "PRIMARY KEY(`seriesId`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `auto_download_suggestions` (" +
                "`episodeId` TEXT NOT NULL, " +
                "`seriesId` TEXT NOT NULL, " +
                "`seriesName` TEXT NOT NULL, " +
                "`seasonId` TEXT NOT NULL, " +
                "`seasonName` TEXT NOT NULL, " +
                "`seasonNumber` INTEGER NOT NULL, " +
                "`episodeNumber` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`suggestedAt` INTEGER NOT NULL DEFAULT 0, " +
                "`dismissedAt` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`episodeId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_auto_download_suggestions_seriesId` ON `auto_download_suggestions` (`seriesId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_auto_download_suggestions_suggestedAt` ON `auto_download_suggestions` (`suggestedAt`)")
    }
}
