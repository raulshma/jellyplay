package com.raulshma.jellyplay.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Playback progress + watched/favorite state for an offline item, split out of
 * the historical `offline_media` row so each table owns a single invariant.
 *
 * **One row per item that has been seeded or interacted with** — created lazily:
 * download-time seeding writes the server `UserData` snapshot, and the targeted
 * progress/favorite updaters insert a row on first contact. Rows for a deleted
 * item are removed by the repository's cascade delete. Items with no row resolve
 * to the defaults below (not started, not played, not favorite) via the
 * `LEFT JOIN` on the browse paths, so a bare metadata stub is indistinguishable
 * from "never watched" — the invariant this table exists to make explicit.
 *
 * `id` mirrors `offline_media.id` by convention (no DB-level foreign key, matching
 * the rest of the schema's FK-by-convention discipline).
 */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: String,
    val playbackPositionTicks: Long? = null,
    @ColumnInfo(defaultValue = "0.0")
    val playedPercentage: Double = 0.0,
    @ColumnInfo(defaultValue = "0")
    val isPlayed: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isFavorite: Boolean = false,
    val lastPlayedDate: String? = null,
)
