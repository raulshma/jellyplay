package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.AutoDownloadSuggestion
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
 * policy/intake/store seams the VM already carries; the state children ride
 * the VM's [scope].
 *
 * The one-shot feedback (SuggestionsQueued / a failed batch's Raw text) rides
 * the [postMessage] sink: the screen-forward channel stays VM-owned — the
 * screen resolves the resources and posts them to the shared UserMessageBus —
 * while the action's outcome reporting travels with the action.
 *
 * [unArmSeries] is THE disarm verb's only UI home (ADR-0015 decision 5):
 * `setSeriesPolicy(id, null)` deletes the arm row and the series' pending Ask
 * prompts. The sheet's "Default" chip deliberately does NOT write it (an
 * ADR-0015 Default ARMS as FOLLOW_GLOBAL); list-management deletions un-arm
 * through the repository's `clearSeriesArmIfNoDownloads` instead.
 */
internal class DownloadsSeriesPolicyActions(
    private val scope: CoroutineScope,
    private val policyStore: SeriesDownloadPolicyStore,
    private val intake: DownloadIntake,
    private val downloadsStore: DownloadsStore,
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

    /** The armed list's un-arm action: deletes the series' arm row (the disarm verb). */
    fun unArmSeries(seriesId: String) {
        scope.launch { policyStore.setSeriesPolicy(seriesId, null) }
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
}
