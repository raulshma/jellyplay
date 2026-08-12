package com.raulshma.jellyplay.core.data.sync

import com.raulshma.jellyplay.core.database.dao.OfflineSyncUpdateRow
import com.raulshma.jellyplay.core.database.entity.SyncBaselineEntity
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineSyncState
import com.raulshma.jellyplay.core.model.OfflineSyncUpdate
import com.raulshma.jellyplay.core.model.SyncStatus

/**
 * Lossless projection of a persisted freshness row to its UI-facing state.
 *
 * This is the single source of truth for the freshness projection: both the
 * write path ([OfflineSyncManager], for its TTL / error short-circuits) and the
 * read path (`OfflineRepositoryImpl`, for the reactive DB-driven badge) call
 * it, so the DB-driven badge and the check/resync result can no longer drift.
 *
 * It replaces the historical lossy `offlineSyncStateOf` mapper, which collapsed
 * the five resyncable content axes (metadata, images, subtitles, trickplay,
 * segments) into one coarse `syncUpdateAvailable` flag and could not split them
 * back out. Each axis now has its own persisted flag and is reconstructed here.
 *
 * Rows migrated from the coarse-only shape (per-axis flags all 0 but
 * `syncUpdateAvailable` set) degrade to the old "metadata + images changed"
 * guess so a legacy row still surfaces a badge until its next check repopulates
 * the per-axis detail.
 *
 * Precedence: a stale `checking` marker wins (transient), then error, then
 * media-file change, then any resyncable axis, then current/unknown.
 */
internal fun SyncBaselineEntity.toOfflineSyncState(): OfflineSyncState {
    val coarseUpdate = syncUpdateAvailable != 0
    val specificAxisSet = syncMetadataChanged != 0 || syncImagesChanged != 0 ||
        syncSubtitlesChanged != 0 || syncTrickplayChanged != 0 || syncSegmentsChanged != 0
    val legacyGuess = coarseUpdate && !specificAxisSet
    val status = when {
        syncChecking != 0 -> SyncStatus.CHECKING
        syncError != 0 -> SyncStatus.ERROR
        syncMediaChanged != 0 -> SyncStatus.UPDATE_AVAILABLE
        specificAxisSet -> SyncStatus.UPDATE_AVAILABLE
        legacyGuess -> SyncStatus.UPDATE_AVAILABLE
        lastSyncedAt != null -> SyncStatus.CURRENT
        else -> SyncStatus.UNKNOWN
    }
    return OfflineSyncState(
        status = status,
        metadataChanged = syncMetadataChanged != 0 || legacyGuess,
        imagesChanged = syncImagesChanged != 0 || legacyGuess,
        subtitlesChanged = syncSubtitlesChanged != 0,
        trickplayChanged = syncTrickplayChanged != 0,
        segmentsChanged = syncSegmentsChanged != 0,
        mediaFileChanged = syncMediaChanged != 0,
        lastCheckedAt = lastSyncedAt,
    )
}

/**
 * Maps the reactive "items with updates" DAO projection row to the UI-facing
 * downloads-sheet model. The raw DB media-type string is resolved to the typed
 * enum here so the UI never compares against a magic string.
 */
internal fun OfflineSyncUpdateRow.toOfflineSyncUpdate() = OfflineSyncUpdate(
    id = id,
    name = name,
    mediaFileChanged = mediaFileChanged == 1,
    mediaType = mediaType?.let { mt -> MediaType.entries.firstOrNull { e -> e.name.equals(mt, ignoreCase = true) } },
    seriesName = seriesName,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
)
