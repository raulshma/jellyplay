package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.AutoDownloadSuggestion
import kotlinx.coroutines.flow.Flow

/**
 * The gh-181 download-policy surface: per-series new-episode mode overrides
 * and the ASK-mode pending prompts. A narrow collaborator interface (the
 * DownloadRepositorySurfaceTest ratchet: the wide repository never grows for
 * a new concern) implemented DIRECTLY by [DownloadRepositoryImpl] — the
 * DownloadQueue/SeriesEpisodeDownloads precedent: the surface's members carry
 * the repository's own vocabulary, so the impl's overrides ARE the seam.
 *
 * Consumers: the auto-download check (per-series resolution + Ask recording),
 * the intake (the sheet's policy choice), the schedulers (keep-alive gate),
 * and the Downloads screen's suggestion card.
 */
interface SeriesDownloadPolicyStore {

    /**
     * The per-series new-episode policy override, or `null` when the series
     * inherits the global `autoDownloadMode` preference (no row stored).
     */
    suspend fun seriesPolicy(seriesId: String): AutoDownloadMode?

    /**
     * The per-series overrides for a batch of series in one query — the
     * auto-download check pass's per-series resolution read. Missing ids
     * inherit the global mode.
     */
    suspend fun seriesPolicies(seriesIds: List<String>): Map<String, AutoDownloadMode>

    /**
     * Whether ANY series carries an override. The schedulers' keep-alive gate
     * reads this: a global NEVER with even one per-series INSTANT/ASK series
     * must not cancel the periodic pass.
     */
    suspend fun hasSeriesPolicies(): Boolean

    /**
     * Stores ([mode] non-null) or clears (`null` = follow the global
     * preference) a series' policy override. [AutoDownloadMode.NEVER] and
     * clearing both drop the series' pending Ask-mode suggestions — prompts
     * written under an override that no longer wants them are dead.
     */
    suspend fun setSeriesPolicy(seriesId: String, mode: AutoDownloadMode?)

    /** The pending Ask-mode prompts, newest first — the Downloads screen's suggestion card. */
    fun observeAutoDownloadSuggestions(): Flow<List<AutoDownloadSuggestion>>

    /**
     * Upserts Ask-mode candidates, skipping ids that already have a known
     * row (pending OR dismissed — a dismissed prompt must not resurrect),
     * and returns how many were newly recorded — the notification surface's
     * "N new episodes" trigger.
     */
    suspend fun recordAutoDownloadSuggestions(suggestions: List<AutoDownloadSuggestion>): Int

    /**
     * Reads the PENDING prompts for the given ids (the Downloads screen
     * resolves its confirm selection back to rows so it can group by
     * series/season). Dismissed tombstones are not confirmable.
     */
    suspend fun autoDownloadSuggestionsByIds(episodeIds: List<String>): List<AutoDownloadSuggestion>

    /**
     * Dismisses the given prompts — a tombstone write, not a delete: the row
     * keeps its episode id in the dedupe query so the next check pass cannot
     * re-record and re-notify an answered prompt.
     */
    suspend fun dismissAutoDownloadSuggestions(episodeIds: List<String>)
}
