package com.raulshma.jellyplay.core.database.dao

import androidx.room.DatabaseView
import androidx.room.Embedded
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity

/**
 * View + SQL defined as constants so the [DatabaseView] annotation and the
 * v46→v47 migration's `CREATE VIEW` execute byte-identical SQL. Room validates
 * a migrated database by comparing `sqlite_master.sql` against the expected
 * view string verbatim, so the two must match exactly (same name, same body,
 * backtick quoting). Defining both from one source guarantees that.
 */
internal const val OFFLINE_MEDIA_WITH_PLAYBACK_VIEW_NAME = "offline_media_with_playback"
internal const val OFFLINE_MEDIA_WITH_PLAYBACK_SQL =
    "SELECT m.*, p.playbackPositionTicks AS playbackPositionTicks, " +
        "p.playedPercentage AS playedPercentage, p.isPlayed AS isPlayed, " +
        "p.isFavorite AS isFavorite, p.lastPlayedDate AS lastPlayedDate " +
        "FROM offline_media m LEFT JOIN playback_state p ON p.id = m.id"

/**
 * Read-only join of an [OfflineMediaEntity] with its optional playback state —
 * the single shape every browse / detail / search query reads. Defined once as
 * a [DatabaseView] so the `offline_media ⟕ playback_state` join lives in one
 * place: adding or renaming a playback column is a one-line edit here, not a
 * seven-query shotgun-surgery.
 *
 * The LEFT JOIN yields null playback columns for a row that has never been
 * seeded or interacted with; the repository maps those nulls to the same
 * "not started" defaults the old single-table row carried.
 */
@DatabaseView(
    viewName = OFFLINE_MEDIA_WITH_PLAYBACK_VIEW_NAME,
    value = OFFLINE_MEDIA_WITH_PLAYBACK_SQL,
)
data class OfflineMediaWithPlayback(
    @Embedded val media: OfflineMediaEntity,
    val playbackPositionTicks: Long?,
    val playedPercentage: Double?,
    val isPlayed: Boolean?,
    val isFavorite: Boolean?,
    val lastPlayedDate: String?,
)
