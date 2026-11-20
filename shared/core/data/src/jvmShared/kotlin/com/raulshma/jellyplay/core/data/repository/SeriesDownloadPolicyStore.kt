package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.AutoDownloadSuggestion
import kotlinx.coroutines.flow.Flow

/**
 * One armed-series row rendered for the Downloads screen's armed list
 * (ADR-0015): a series whose `series_download_policies` arm keeps it in the
 * auto-download check's universe even at zero local download rows.
 *
 * [mode] is the RAW stored row value — a [AutoDownloadMode.FOLLOW_GLOBAL] arm
 * carries the sentinel verbatim; the consumer resolves the effective mode
 * against the global preference via
 * [com.raulshma.jellyplay.core.model.resolveAgainst] (the one resolution
 * home). [seriesName] is the arm's display cache — blank for arms created
 * before the name rode the download request, backfilled opportunistically.
 */
data class SeriesDownloadArm(
    val seriesId: String,
    val mode: AutoDownloadMode,
    val seriesName: String,
)

/**
 * The gh-181 download-policy surface, repurposed by ADR-0015: per-series
 * auto-download arms (the `series_download_policies` intent record) and the
 * ASK-mode pending prompts. A narrow collaborator interface (the
 * DownloadRepositorySurfaceTest ratchet: the wide repository never grows for
 * a new concern) implemented DIRECTLY by [DownloadRepositoryImpl] — the
 * DownloadQueue/SeriesEpisodeDownloads precedent: the surface's members carry
 * the repository's own vocabulary, so the impl's overrides ARE the seam.
 *
 * Consumers: the auto-download check (universe union + per-series resolution
 * + Ask recording), the intake (the sheet's policy choice), the schedulers
 * (keep-alive gate), the delete funnels (the un-arm rule), and the Downloads
 * screen's suggestion card + armed-series list.
 */
interface SeriesDownloadPolicyStore {

    /**
     * The per-series new-episode policy row, or `null` when the series is not
     * armed (no row stored). A [AutoDownloadMode.FOLLOW_GLOBAL] return is the
     * implicit arm (ADR-0015): armed, effective mode = the global preference.
     */
    suspend fun seriesPolicy(seriesId: String): AutoDownloadMode?

    /**
     * The per-series policy rows for a batch of series in one query — the
     * auto-download check pass's per-series resolution read. May carry
     * [AutoDownloadMode.FOLLOW_GLOBAL] arms; resolve through
     * [com.raulshma.jellyplay.core.model.resolveAgainst]. Missing ids are
     * un-armed series (effective mode = the global mode under the check's
     * coverage-derived membership).
     */
    suspend fun seriesPolicies(seriesIds: List<String>): Map<String, AutoDownloadMode>

    /**
     * Whether ANY series carries a policy row. The schedulers' keep-alive gate
     * reads this: a global NEVER with even one armed series (explicit override
     * or an implicit FOLLOW_GLOBAL arm) must not cancel the periodic pass.
     */
    suspend fun hasSeriesPolicies(): Boolean

    /**
     * Every armed series' id, one-shot — the check's universe union read
     * (ADR-0015 decision 4): coverage seriesIds ∪ armed ids, so a series with
     * zero local download rows is still visited each pass. Parseable arms
     * only: an unknown mode name (a newer build's value read after a
     * downgrade) must stay inert per the downloads.status convention
     * (ADR-0015 decision 3's fail-safe), so it does not join the universe.
     */
    suspend fun armedSeriesIds(): List<String>

    /**
     * The armed-series observation seam — the Downloads screen's armed list
     * (ADR-0015 consequence: armed series are invisible in the downloads list
     * at zero rows, so the state needs its own surface or it is
     * undiscoverable). Raw row values; the consumer resolves via
     * [com.raulshma.jellyplay.core.model.resolveAgainst].
     */
    fun observeArmedSeries(): Flow<List<SeriesDownloadArm>>

    /**
     * The zero-rows un-arm rule's seam (ADR-0015 decision 5): when [seriesId]
     * has no `downloads` rows left, its arm row and pending Ask prompts are
     * deleted. THE one body of the rule — OfflineRepositoryImpl's three delete
     * scopes call this member through its lazy ctor store edge (the
     * DesktopDownloadManager cycle-break precedent), so no second body exists
     * anywhere; a hand-folded copy would be a defect.
     *
     * The rule's verb split — WATCH-BOUNDARY deletions NEVER call this
     * (auto-delete-after-watch, smart-downloads cleanup, the #14 post-playback
     * delete offer, the keep-days retention sweep: hygiene must keep the
     * weekly arming alive); LIST-MANAGEMENT deletions DO (Downloads-screen
     * single/bulk delete, offline-library series/season/item remove). An
     * explicit NEVER in the series sheet un-arms unconditionally instead
     * ([setSeriesPolicy] with a NEVER mode keeps the row and drops prompts).
     */
    suspend fun clearSeriesArmIfNoDownloads(seriesId: String)

    /**
     * Stores ([mode] non-null) or clears (`null` = un-arm) a series' policy
     * row. [AutoDownloadMode.NEVER] and clearing both drop the series' pending
     * Ask-mode suggestions — prompts recorded under an override that no longer
     * wants them are dead. A [AutoDownloadMode.FOLLOW_GLOBAL] write is the
     * sheet's "Default" (ADR-0015: Default now ARMS — follow-global, survives
     * hygiene) and keeps its suggestions, exactly like any explicit mode.
     * Clearing (writing no row) is the disarm verb — only the Downloads
     * screen's un-arm action writes it.
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
