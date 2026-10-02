package com.raulshma.jellyplay.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

// Auto-download keep-days retention (4.4): the `downloads.completedAt` column
// the sweep's age query filters on, plus the (status, completedAt) index that
// serves it. One nullable-free additive column (defaultValue 0 = "not
// completed yet", matching the entity default for in-flight rows), and a
// one-statement backfill: rows that completed before the column existed carry
// no completion timestamp, so they are anchored to their `createdAt` — a
// just-upgraded library keeps its real age instead of reading as brand-new
// (all zeros would never age out) or infinitely old (all rows swept on first
// run). The backfill touches only COMPLETED rows; a row that resumes and
// completes later overwrites the column via markCompleted.
val MIGRATION_58_59 = object : Migration(58, 59) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN completedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE downloads SET completedAt = createdAt WHERE status = 'COMPLETED'")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_downloads_status_completedAt` ON `downloads` (`status`, `completedAt`)",
        )
    }
}
