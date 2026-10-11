package com.raulshma.jellyplay.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

// Per-item/series audio-description preference pin (the player's audio-picker
// "remember for this series" footer): one nullable column on
// `item_playback_preferences`, additive (NULL = follow the global setting —
// every row written before this migration means exactly that).
val MIGRATION_60_61 = object : Migration(60, 61) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN preferAudioDescription INTEGER")
    }
}
