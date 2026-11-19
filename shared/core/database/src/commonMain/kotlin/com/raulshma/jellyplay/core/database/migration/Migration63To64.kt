package com.raulshma.jellyplay.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

// ADR-0015 (armed series outlive local files): `series_download_policies`
// becomes the series' auto-download intent record — the Downloads screen's
// armed-series list renders series with zero local rows, and such series have
// no offline_media row to read a display name from (those rows are
// orphan-pruned), so the arm row carries the name the download request saw at
// arm time. Pre-existing rows (explicit sheet overrides) backfill blank and
// get filled opportunistically by the next download. The column DEFAULT
// mirrors the entity's @ColumnInfo(defaultValue) declaration — Room's
// migration validation compares them (the v58 precedent).
val MIGRATION_63_64 = object : Migration(63, 64) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE `series_download_policies` ADD COLUMN `seriesName` TEXT NOT NULL DEFAULT ''")
    }
}
