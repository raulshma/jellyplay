package com.raulshma.jellyplay.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

// Track-memory refinement + the render-profile groundwork: three
// nullable columns on `item_playback_preferences`, all additive (NULL = absent,
// so existing rows are unaffected):
//  - rememberedAudioCodec / rememberedSubtitleCodec — the remembered track's
//    container codec, an extra re-match rung when a series' track layout
//    churns between episodes (labels change, codecs don't).
//  - renderProfile — a serialized per-series/per-item rendering override blob
//    (MpvRenderOverrides: shader pack / tone mapping) for the desktop
//    render-profile feature (RenderProfileResolver/RenderSheet, same wave).
val MIGRATION_56_57 = object : Migration(56, 57) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN rememberedAudioCodec TEXT")
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN rememberedSubtitleCodec TEXT")
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN renderProfile TEXT")
    }
}
