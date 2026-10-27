package com.raulshma.jellyplay.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.raulshma.jellyplay.core.model.wallNowMillis

/**
 * Per-series override of the global new-episode auto-download policy
 * (gh-181): chosen in the series download sheet, it wins over the
 * `autoDownloadMode` preference for exactly this series. Absence of a row
 * means "inherit the global mode". [mode] is the enum name string of
 * [com.raulshma.jellyplay.core.model.AutoDownloadMode] (the same
 * store-the-name convention the `downloads.status` column uses), so the
 * model enum stays free to evolve without a schema change.
 */
@Entity(tableName = "series_download_policies")
data class SeriesDownloadPolicyEntity(
    @PrimaryKey val seriesId: String,
    val mode: String,
)

/**
 * One pending "Ask before download" candidate (gh-181): an episode of a
 * downloaded series that an auto-download pass found new on the server while
 * the effective mode was ASK. Rows are prompts, not queue entries — the user
 * confirms (→ enqueued through the normal intake) or dismisses them on the
 * Downloads screen. Deduped on [episodeId], so a repeated pass over the same
 * not-yet-answered episode upserts in place instead of stacking prompts.
 *
 * Dismissal is a TOMBSTONE, not a delete: [dismissedAt] > 0 hides the row
 * from the prompt surface while keeping the id in the dedupe query, so the
 * next check pass (which still sees the episode as undownloaded on the
 * server) cannot resurrect a prompt the user already answered with "no".
 * Tombstones die with the series' download state
 * ([com.raulshma.jellyplay.core.database.dao.DownloadPolicyDao.deleteSuggestionsBySeriesId]).
 */
@Entity(
    tableName = "auto_download_suggestions",
    indices = [
        Index(value = ["seriesId"]),
        Index(value = ["suggestedAt"]),
    ],
)
data class AutoDownloadSuggestionEntity(
    @PrimaryKey val episodeId: String,
    val seriesId: String,
    val seriesName: String,
    val seasonId: String,
    val seasonName: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    @ColumnInfo(defaultValue = "0")
    val suggestedAt: Long = wallNowMillis(),
    /** Epoch millis the user dismissed the prompt; `0` = still pending. */
    @ColumnInfo(defaultValue = "0")
    val dismissedAt: Long = 0L,
)
