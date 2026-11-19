package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import com.raulshma.jellyplay.core.database.entity.AutoDownloadSuggestionEntity
import com.raulshma.jellyplay.core.database.entity.SeriesDownloadPolicyEntity
import kotlinx.coroutines.flow.Flow

/**
 * The gh-181 download-policy tables, repurposed by ADR-0015: the per-series
 * ARM rows ([SeriesDownloadPolicyEntity] — explicit sheet overrides beside
 * implicit follow-global arms) and the ASK-mode pending prompts
 * ([AutoDownloadSuggestionEntity]). Both live with the download feature but
 * are deliberately not [DownloadDao] — that interface owns the transfer queue;
 * these rows are policy, intent, and prompt state, queried by the
 * auto-download check and rendered by the Downloads screen's suggestion and
 * armed-series surfaces.
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
     * Whether ANY series carries a policy row. The schedulers' keep-alive gate
     * reads this: a global NEVER with even one armed series (explicit
     * INSTANT/ASK/NEVER override or an implicit FOLLOW_GLOBAL arm, ADR-0015)
     * must not cancel the periodic pass — the arms' series would starve.
     */
    @Query("SELECT COUNT(*) FROM series_download_policies")
    suspend fun countPolicies(): Int

    /**
     * The whole-arms-table observation seam (table-level invalidation) — the
     * Downloads screen's armed-series list (ADR-0015 consequence: armed series
     * are invisible in the downloads list at zero rows, so the state needs its
     * own surface or it is undiscoverable).
     */
    @Query("SELECT * FROM series_download_policies")
    fun observePolicies(): Flow<List<SeriesDownloadPolicyEntity>>

    @Upsert
    suspend fun upsertPolicy(policy: SeriesDownloadPolicyEntity)

    /**
     * The implicit-arm write (ADR-0015 decision 2): IGNORE-on-conflict so an
     * existing row — an explicit sheet override, or any earlier arm — is never
     * clobbered by a later download of the same series. Returns 0 when a row
     * already existed.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPolicyIfAbsent(policy: SeriesDownloadPolicyEntity): Long

    /** Backfills an arm's blank display name once a download request carries one. */
    @Query("UPDATE series_download_policies SET seriesName = :name WHERE seriesId = :seriesId")
    suspend fun updatePolicySeriesName(seriesId: String, name: String)

    /**
     * The un-arm verb. Superseded rationale (gh-181: "an override with no
     * downloads is dead state") — under ADR-0015 the row IS the series'
     * auto-download intent record and survives watched-deletion hygiene; only
     * list-management deletion at zero rows (via the policy store's
     * clearSeriesArmIfNoDownloads) and the explicit NEVER sheet choice reach
     * this delete.
     */
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

    /** Cleared alongside the arm row when a series un-arms (ADR-0015's zero-rows rule). */
    @Query("DELETE FROM auto_download_suggestions WHERE seriesId = :seriesId")
    suspend fun deleteSuggestionsBySeriesId(seriesId: String)
}
