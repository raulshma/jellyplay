package com.raulshma.jellyplay.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.raulshma.jellyplay.core.model.wallNowMillis

/**
 * Per-series auto-download policy row (gh-181, repurposed by ADR-0015): the
 * series' ARM — the intent record that keeps it in the auto-download check's
 * universe even when no local download rows remain. The row is either an
 * explicit per-series override chosen in the series download sheet (it wins
 * over the `autoDownloadMode` preference for exactly this series) or an
 * implicit [com.raulshma.jellyplay.core.model.AutoDownloadMode.FOLLOW_GLOBAL]
 * arm written beside any series-shaped download; absence of a row means "not
 * armed — no coverage-derived engagement". [mode] is the enum name string of
 * [com.raulshma.jellyplay.core.model.AutoDownloadMode] (the same
 * store-the-name convention the `downloads.status` column uses), so the
 * model enum stays free to evolve without a schema change; a name this build
 * doesn't know decodes to null and stays inert (fail-safe).
 *
 * [seriesName] is display cache for the Downloads screen's armed list: an
 * armed series with zero local rows has no offline_media row to read the name
 * from (those rows are orphan-pruned), so the arm carries the name the
 * download request saw at arm time.
 */
@Entity(tableName = "series_download_policies")
data class SeriesDownloadPolicyEntity(
    @PrimaryKey val seriesId: String,
    val mode: String,
    @ColumnInfo(defaultValue = "")
    val seriesName: String = "",
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
 * Under ADR-0015 prompts survive their series' watched-deletion hygiene and
 * live at zero download rows until answered; they die only when the series
 * un-arms
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
