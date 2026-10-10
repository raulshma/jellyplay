package com.raulshma.jellyplay.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

// Seeded shuffle-order persistence (the finamp pattern): the
// `kotlin.random.Random` seed the audio queue's current shuffle order was
// generated with, so a restored queue can reproduce the exact arrangement
// (`AudioQueueStateCore.shuffleWithSeed`). One nullable column on the
// `audio_queue_state` singleton, additive (NULL = shuffle off, or a row that
// predates the column — the persisted queue rows themselves already ARE the
// shuffled order, so a NULL seed only means "not reproducible", never
// "corrupt").
val MIGRATION_59_60 = object : Migration(59, 60) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE audio_queue_state ADD COLUMN shuffleSeed INTEGER")
    }
}
