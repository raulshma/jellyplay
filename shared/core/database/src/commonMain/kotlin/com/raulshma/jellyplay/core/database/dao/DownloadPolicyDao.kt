package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.raulshma.jellyplay.core.database.entity.AutoDownloadSuggestionEntity
import com.raulshma.jellyplay.core.database.entity.SeriesDownloadPolicyEntity
import kotlinx.coroutines.flow.Flow

/**
 * The gh-181 download-policy tables: per-series mode overrides
 * ([SeriesDownloadPolicyEntity]) and the ASK-mode pending prompts
 * ([AutoDownloadSuggestionEntity]). Both live with the download feature but
 * are deliberately not [DownloadDao] — that interface owns the transfer queue;
 * these rows are policy and prompt state, queried by the auto-download check
 * and the Downloads screen's suggestion card.
 */
@Dao
interface DownloadPolicyDao {

    // ─── Per-series policy ────────────────────────────────────────────────

    @Query("SELECT * FROM series_download_policies WHERE seriesId = :seriesId")
    suspend fun getPolicyBySeriesId(seriesId: String): SeriesDownloadPolicyEntity?

    /** One query for a whole check pass's worth of series — the per-series override lookup. */
    @Query("SELECT * FROM series_download_policies WHERE seriesId IN (:seriesIds)")
    suspend fun getPoliciesBySeriesIds(seriesIds: List<String>): List<SeriesDownloadPolicyEntity>

    /**
     * Whether ANY series carries an override. The schedulers' keep-alive gate
     * reads this: a global NEVER with even one per-series INSTANT/ASK series
     * must not cancel the periodic pass (the overrides would starve).
     */
    @Query("SELECT COUNT(*) FROM series_download_policies")
    suspend fun countPolicies(): Int

    @Upsert
    suspend fun upsertPolicy(policy: SeriesDownloadPolicyEntity)

    /** Called when a series' downloads are fully removed — an override with no downloads is dead state. */
    @Query("DELETE FROM series_download_policies WHERE seriesId = :seriesId")
    suspend fun deletePolicyBySeriesId(seriesId: String)

    // ─── Ask-mode suggestions ─────────────────────────────────────────────

    /**
     * The Downloads screen's suggestion card observation seam (table-level
     * invalidation). Pending rows only — dismissed tombstones stay in the
     * table for the dedupe query but never render again.
     */
    @Query("SELECT * FROM auto_download_suggestions WHERE dismissedAt = 0 ORDER BY suggestedAt DESC")
    fun observePendingSuggestions(): Flow<List<AutoDownloadSuggestionEntity>>

    /**
     * The dedupe read behind upserts — deliberately NOT filtered on
     * [AutoDownloadSuggestionEntity.dismissedAt]: a dismissed row must keep
     * its episode id known so a later pass cannot re-record (and re-notify)
     * a prompt the user already answered.
     */
    @Query("SELECT episodeId FROM auto_download_suggestions WHERE episodeId IN (:episodeIds)")
    suspend fun getKnownSuggestionIds(episodeIds: List<String>): List<String>

    @Upsert
    suspend fun upsertSuggestions(suggestions: List<AutoDownloadSuggestionEntity>)

    @Query("SELECT * FROM auto_download_suggestions WHERE episodeId IN (:episodeIds) AND dismissedAt = 0")
    suspend fun getPendingSuggestionsByIds(episodeIds: List<String>): List<AutoDownloadSuggestionEntity>

    /** The dismissal tombstone: hides the prompt, keeps the dedupe id. */
    @Query("UPDATE auto_download_suggestions SET dismissedAt = :dismissedAt WHERE episodeId IN (:episodeIds)")
    suspend fun dismissSuggestions(episodeIds: List<String>, dismissedAt: Long)

    /** Cleared alongside the policy row when a series' downloads are fully removed. */
    @Query("DELETE FROM auto_download_suggestions WHERE seriesId = :seriesId")
    suspend fun deleteSuggestionsBySeriesId(seriesId: String)
}
