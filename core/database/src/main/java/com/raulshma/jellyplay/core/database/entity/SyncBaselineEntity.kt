package com.raulshma.jellyplay.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Freshness baseline + per-axis result flags for an offline item — the
 * persistence home of the offline freshness module.
 *
 * Split out of the historical `offline_media` row so the freshness state has a
 * single owner (decision + persistence + projection live together) and a
 * metadata re-persist can no longer clobber it. The fragile "copy the sync
 * columns forward on every upsert" block disappears with the split.
 *
 * **Per-axis flags.** The historical shape collapsed five resyncable content
 * axes (metadata, images, subtitles, trickplay, segments) into one coarse
 * `syncUpdateAvailable` flag, which made the DB-driven badge lossy — it could
 * not tell subtitles changed from metadata changed. Each axis now has its own
 * persisted `Int` 0/1 flag, so projection back to [com.raulshma.jellyplay.core.model.OfflineSyncState]
 * is lossless and every axis survives end to end. `syncUpdateAvailable` is kept
 * as a denormalized OR of the five so the "items with updates" query and badge
 * count stay a single indexed-ish predicate; the writer sets all six together.
 *
 * **Rows are lazy** — seeded at download time and after every check/resync. A
 * missing row means "never checked" and projects to `SyncStatus.UNKNOWN`.
 */
@Entity(tableName = "sync_baseline")
data class SyncBaselineEntity(
    @PrimaryKey val id: String,
    // ---- Baseline signatures (the snapshot a check diffs against) ----
    val syncedPosterTag: String? = null,
    val syncedBackdropTag: String? = null,
    val syncedMetadataSignature: String? = null,
    val syncedSubtitleSignature: String? = null,
    val syncedTrickplaySignature: String? = null,
    val syncedSegmentsSignature: String? = null,
    val syncedMediaSourceId: String? = null,
    val syncedMediaSizeBytes: Long? = null,
    val lastSyncedAt: Long? = null,
    // ---- Result flags ----
    // Denormalized OR of the five resyncable per-axis flags below; kept so the
    // "items with updates" query and badge count stay one predicate.
    @ColumnInfo(defaultValue = "0")
    val syncUpdateAvailable: Int = 0,
    // The media file itself changed (MediaSource id/size). Surfaced separately
    // because a resync cannot fix it — it requires a full re-download.
    @ColumnInfo(defaultValue = "0")
    val syncMediaChanged: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val syncChecking: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val syncError: Int = 0,
    // ---- Per-axis change flags (lossless projection of the comparator output) ----
    @ColumnInfo(defaultValue = "0")
    val syncMetadataChanged: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val syncImagesChanged: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val syncSubtitlesChanged: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val syncTrickplayChanged: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val syncSegmentsChanged: Int = 0,
)
