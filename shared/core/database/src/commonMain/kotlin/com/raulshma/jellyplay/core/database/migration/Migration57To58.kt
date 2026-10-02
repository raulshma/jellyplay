package com.raulshma.jellyplay.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

// Version memory (1.2): the media-source id the user pinned for this
// item/series in the player's "Version" sheet. One nullable column on
// `item_playback_preferences`, additive (NULL = not remembered, so existing
// rows are unaffected).
val MIGRATION_57_58 = object : Migration(57, 58) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN preferredMediaSourceId TEXT")
    }
}
