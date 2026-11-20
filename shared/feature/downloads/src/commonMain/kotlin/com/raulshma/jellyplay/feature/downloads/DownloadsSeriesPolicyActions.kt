package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.LibraryFeed
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.AutoDownloadSuggestion
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The series download-policy surface behind one constructed module (the
 * [DownloadsResyncActions] seam shape): both of [SeriesDownloadPolicyStore]'s
 * Downloads-screen consumers — the gh-181 Ask-mode suggestion card (observe,
 * confirm through the normal series intake, dismiss) and the ADR-0015
 * armed-series list (observe + the un-arm verb) — live here, so the
 * [DownloadsViewModel] keeps one accessor instead of five members (the
 * ownership ratchet's relocation precedent). Constructed from the
 * policy/intake/store seams the VM already carries (plus the [LibraryFeed]
 * browse family behind the arm picker's series search — the narrow
 * core:data seam, never the network layer); the state children ride
 * the VM's [scope].
 *
 * The one-shot feedback (SuggestionsQueued / a failed batch's Raw text) rides
 * the [postMessage] sink: the screen-forward channel stays VM-owned — the
 * screen resolves the resources and posts them to the shared UserMessageBus —
 * while the action's outcome reporting travels with the action.
 *
 * [unArmSeries] is THE disarm verb's only UI home (ADR-0015 decision 5):
 * it writes `setSeriesPolicy(id, NEVER)` — the disarm TOMBSTONE (the gh-225
 * follow-up's arr auto-arm sync re-arms any series with no policy row, so a
 * disarmed series must keep a NEVER row to stay disarmed; prompts drop
 * store-side either way). The stopped series stays listed in the armed card
 * as a Never row with the re-arm affordance ([reArmSeries]). The sheet's
 * "Default" chip deliberately does NOT write the tombstone (an ADR-0015
 * Default ARMS as FOLLOW_GLOBAL); list-management deletions un-arm
 * through the repository's `clearSeriesArmIfNoDownloads` instead.
 */
internal class DownloadsSeriesPolicyActions(
    private val scope: CoroutineScope,
    private val policyStore: SeriesDownloadPolicyStore,
    private val intake: DownloadIntake,
    private val downloadsStore: DownloadsStore,
    /** The library browse family seam behind the arm picker's series search. */
    private val libraryFeed: LibraryFeed,
    /**
     * The manual arm's schedule reconcile: the first arm row is a keep-alive
     * term on its own (`hasSeriesPolicies`), so with the global mode off and
     * `autoArmFromArr` off the periodic pass this arm exists for is NOT
     * scheduled until [AutoDownloadScheduler.sync] runs — the arm write pokes
     * it in the same launch.
     */
    private val autoDownloadScheduler: AutoDownloadScheduler,
    private val postMessage: (DownloadsUserMessage) -> Unit,
) {

    /**
     * The pending Ask-mode prompts (gh-181), newest first — the suggestion
     * card's content. Empty when the user has nothing to answer.
     */
    val suggestions: StateFlow<List<AutoDownloadSuggestion>> =
        policyStore.observeAutoDownloadSuggestions()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The armed-series rows (ADR-0015 consequence: armed series are invisible
     * in the downloads list at zero rows, so the state needs its own surface
     * or it is undiscoverable) — the pure [armedSeriesRows] projection over
     * the arm rows resolved against the live global mode, so flipping the
     * global preference re-renders the follows-global labels without any
     * arm-row write. A small table; no polling or fetches behind it.
     */
    val armedSeries: StateFlow<List<ArmedSeriesRow>> =
        combine(policyStore.observeArmedSeries(), downloadsStore.downloads) { arms, slice ->
            armedSeriesRows(arms, slice.autoDownloadMode)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The armed list's un-arm action (the disarm verb's only UI home): writes
     * the NEVER tombstone — the row survives (it must: the arr auto-arm sync
     * re-arms rowless series, so deletion would be resurrected), the series'
     * pending Ask prompts drop store-side, and the armed card re-renders the
     * row as a Never entry with the re-arm affordance.
     */
    fun unArmSeries(seriesId: String) {
        scope.launch { policyStore.setSeriesPolicy(seriesId, AutoDownloadMode.NEVER) }
    }

    /**
     * The stopped row's re-arm action: flips the NEVER tombstone back to the
     * follow-global ARM. [setSeriesPolicy] — not [armSeries] — because the row
     * exists and the insert-if-absent arm would no-op on it. An explicit
     * NEVER from the sheet re-arms the same way.
     */
    fun reArmSeries(seriesId: String) {
        scope.launch { policyStore.setSeriesPolicy(seriesId, AutoDownloadMode.FOLLOW_GLOBAL) }
    }

    /**
     * The arm picker's confirm action: [SeriesDownloadPolicyStore.manualArmSeries]
     * passthrough — inserts the follow-global arm row when absent, and flips
     * a raw NEVER tombstone back to the arm, so the picker doubles as a safe
     * re-arm entry for a stopped series. An explicit INSTANT/ASK override is
     * never demoted (insert-if-absent). The landed arm is confirmed
     * ([DownloadsUserMessage.SeriesArmed] — the details sheet's armed-message
     * behavior; the bus outlives the sheet that closed on the same tap) and a
     * failed write is surfaced ([DownloadsUserMessage.Raw]) instead of
     * escaping the launched coroutine — cancellation still propagates.
     */
    fun armSeries(seriesId: String, seriesName: String) {
        scope.launch {
            runCatchingRethrowingCancellation { policyStore.manualArmSeries(seriesId, seriesName) }
                .onSuccess { postMessage(DownloadsUserMessage.SeriesArmed) }
                .onFailure { error ->
                    postMessage(DownloadsUserMessage.Raw(error.message ?: ARM_WRITE_FAILED_FALLBACK))
                }
            // The FIRST arm row is a keep-alive term of its own: with the
            // global mode off and autoArmFromArr off, the periodic pass this
            // arm exists for is unscheduled until this reconcile runs.
            // Guarded separately from the write: a sync failure must not undo
            // or misreport the arm that already landed.
            runCatchingRethrowingCancellation { autoDownloadScheduler.sync() }
                .onFailure { error ->
                    postMessage(DownloadsUserMessage.Raw(error.message ?: SCHEDULE_SYNC_FAILED_FALLBACK))
                }
        }
    }

    /**
     * The arm picker's series query: a server search restricted to SERIES
     * items through the [LibraryFeed] browse family seam (features never call
     * the network layer directly; the repository owns the request). Blank
     * queries yield an empty success — the picker renders its own idle state
     * instead of firing a query for nothing. A failed search travels as
     * [Result.failure] so the picker can tell "no matches" from "couldn't
     * search" instead of folding both into the empty state.
     */
    suspend fun searchArmCandidates(query: String): Result<List<MediaItem>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        return libraryFeed
            .search(
                query = trimmed,
                filters = LibraryFilters(mediaTypes = listOf(MediaType.SERIES)),
                limit = ARM_CANDIDATE_LIMIT,
                startIndex = 0,
            )
            .map { it.items }
    }

    /**
     * Confirms suggestions: groups the prompts by series → season (episodes
     * in numeric order), enqueues each season's batch through the normal
     * series download path, then clears the answered rows. [episodeIds] is
     * the confirmed selection — a per-row Download passes one id, the card's
     * Download all passes every id. A failed batch is surfaced (Raw message)
     * instead of silently reporting success; its prompts stay pending so the
     * user can retry.
     */
    fun downloadSuggestions(episodeIds: List<String>) {
        if (episodeIds.isEmpty()) return
        scope.launch {
            val rows = policyStore.autoDownloadSuggestionsByIds(episodeIds)
            if (rows.isEmpty()) return@launch
            val confirmed = mutableListOf<AutoDownloadSuggestion>()
            rows.groupBy { it.seriesId }
                .forEach { (_, seriesEpisodes) ->
                    seriesEpisodes.groupBy { it.seasonId }
                        .forEach { (seasonId, seasonEpisodes) ->
                            val result = intake.startSeries(
                                seriesId = seriesEpisodes.first().seriesId,
                                episodeIds = mapOf(
                                    seasonId to seasonEpisodes
                                        .sortedBy { it.episodeNumber }
                                        .map { it.episodeId },
                                ),
                            )
                            if (result.isSuccess) {
                                confirmed += seasonEpisodes
                            } else {
                                postMessage(
                                    DownloadsUserMessage.Raw(
                                        result.exceptionOrNull()?.message ?: "Download failed",
                                    ),
                                )
                            }
                        }
                }
            if (confirmed.isNotEmpty()) {
                policyStore.dismissAutoDownloadSuggestions(confirmed.map { it.episodeId })
                postMessage(DownloadsUserMessage.SuggestionsQueued)
            }
        }
    }

    /** Dismisses suggestions without downloading. */
    fun dismissSuggestions(episodeIds: List<String>) {
        if (episodeIds.isEmpty()) return
        scope.launch { policyStore.dismissAutoDownloadSuggestions(episodeIds) }
    }

    private companion object {
        /** The arm picker's result window — a small dialog, not a browse page. */
        const val ARM_CANDIDATE_LIMIT = 20

        /** The arm write failure's fixed fallback (the Raw idiom's `?: "Download failed"` shape). */
        const val ARM_WRITE_FAILED_FALLBACK = "Arm failed"

        /** The schedule reconcile failure's fixed fallback — the arm itself landed. */
        const val SCHEDULE_SYNC_FAILED_FALLBACK = "Auto-download scheduling failed"
    }
}
