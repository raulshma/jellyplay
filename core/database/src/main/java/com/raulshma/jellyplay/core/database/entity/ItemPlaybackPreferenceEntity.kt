package com.raulshma.jellyplay.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted form of a per-item / per-series playback-language preference.
 * The `(scope, key)` pair is unique, making an
 * [androidx.room.OnConflictStrategy.REPLACE] insert behave as an upsert.
 *
 * Mapped to/from the domain [com.raulshma.jellyplay.core.model.ItemPlaybackPreference]
 * by the repository layer. `scope`/`updatedAt` are stored as the enum/text +
 * epoch-millis conventions used elsewhere in this database.
 */
@Entity(
    tableName = "item_playback_preferences",
    indices = [
        Index(value = ["scope", "key"], unique = true),
        Index(value = ["updatedAt"]),
    ],
)
data class ItemPlaybackPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String,
    val key: String,
    val audioLanguage: String?,
    val subtitleLanguage: String?,
    val updatedAt: Long,
)
