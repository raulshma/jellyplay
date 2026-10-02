package com.raulshma.jellyplay.feature.arrqueue

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import com.raulshma.jellyplay.core.data.repository.ArrReleaseOperations
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrRelease
import com.raulshma.jellyplay.core.model.arr.ArrReleaseHistoryStatus
import com.raulshma.jellyplay.core.data.repository.ArrReleaseCacheUnavailable
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.Res
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_grab_sent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Screen-forward one-shot feedback for the release sheet, the same unresolved
 * seal the queue screen's actions emit ([ArrQueueMessage]) so the host screen
 * resolves + posts both through one bus collector.
 */
@Immutable
data class ReleaseSearchUiState(
    /** The queue row the sheet was opened for; null while the sheet is closed. */
    val item: ArrQueueItem? = null,
    /** The sheet's machine state (reset fresh on every open). */
    val sheet: ReleaseSearchState = ReleaseSearchState(),
)

/**
 * Drives the release-search sheet: opens it for a queue row, runs the
 * interactive search + the best-effort history-badge pass against the
 * [ArrReleaseOperations] seam, and grabs the confirmed release (plain, or
 * `shouldOverride` for a rejected row). All UI transitions go through the
 * pure [ReleaseSearchStateMachine] so the choreography is testable without
 * coroutines (the reducer carries the rules; this class only maps intents →
 * events → repository calls).
 */
class ReleaseSearchViewModel(
    private val arrReleaseOperations: ArrReleaseOperations,
) : JellyPlayViewModel() {

    private val _state = composeState(ReleaseSearchUiState())
    val state: State<ReleaseSearchUiState> = _state.asState()

    /**
     * One-shot grab ack, resolved + posted by the host screen (the
     * ArrQueueViewModel messages conveyor; BUFFERED + single collector).
     */
    private val messageChannel = Channel<ArrQueueMessage>(Channel.BUFFERED)
    val messages: Flow<ArrQueueMessage> = messageChannel.receiveAsFlow()

    /**
     * The in-flight search run; at most one (the RESTART single-flight
     * policy). The settling run checks it lost no registration before
     * landing its outcome.
     */
    private var searchJob: Job? = null

    /** Opens the sheet for [item] and immediately starts its search. */
    fun open(item: ArrQueueItem) {
        apply(ReleaseSearchEvent.SearchStarted)
        _state.value = ReleaseSearchUiState(item = item, sheet = _state.value.sheet)
        runSearch(item)
    }

    /** Closes the sheet and drops its state. */
    fun dismiss() {
        _state.value = ReleaseSearchUiState()
    }

    /** The cache-miss affordance: re-run the search for the open item. */
    fun searchAgain() {
        val item = _state.value.item ?: return
        apply(ReleaseSearchEvent.SearchStarted)
        runSearch(item)
    }

    fun selectSort(sort: ReleaseSort) = apply(ReleaseSearchEvent.SortSelected(sort))

    fun toggleInfo(guid: String) = apply(ReleaseSearchEvent.InfoToggled(guid))

    fun requestGrab(release: ArrRelease) = apply(ReleaseSearchEvent.GrabRequested(release))

    fun dismissGrabDialog() = apply(ReleaseSearchEvent.GrabDismissed)

    /**
     * Confirms the pending grab: rejected rows ride `shouldOverride` (the
     * "grab anyway" arm) with the release's own quality prefilled by the
     * client layer; success acks through [messages] and closes the sheet (the
     * repository already refreshed the hot queue feed).
     */
    fun confirmGrab() {
        val item = _state.value.item ?: return
        val release = _state.value.sheet.confirmGrab ?: return
        apply(ReleaseSearchEvent.GrabStarted)
        launch {
            arrReleaseOperations.grabRelease(item, release, override = release.needsOverride)
                .fold(
                    onSuccess = {
                        messageChannel.trySend(
                            ArrQueueMessage.Info(Res.string.releaseSearch_grab_sent, listOf(release.title)),
                        )
                        apply(ReleaseSearchEvent.GrabSucceeded(release))
                        dismiss()
                    },
                    onFailure = { e ->
                        apply(ReleaseSearchEvent.GrabFailed(e.message))
                    },
                )
        }
    }

    /**
     * Runs the search + the badges pass concurrently; both are single-shot Results.
     *
     * RESTART single-flight (the `ConnectionProbe` mechanism): a new run
     * cancels and supersedes the in-flight one, and only the CURRENT run's
     * job may land its outcome — a superseded search's late `SearchSucceeded`
     * never overwrites the newer rows.
     */
    private fun runSearch(item: ArrQueueItem) {
        searchJob?.cancel()
        // LAZY + explicit start: the job MUST be registered before its body
        // can run — an eagerly-started launch on an immediate dispatcher (the
        // viewModelScope reality) would run a suspension-free body inline and
        // the identity guard below would discard a perfectly fresh outcome.
        val job = launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            coroutineScope {
                val search = async { arrReleaseOperations.searchReleases(item) }
                // Badges are best-effort — a history failure never fails the search.
                val badges = async { arrReleaseOperations.releaseHistoryStatuses(item).getOrDefault(emptyMap()) }
                search.await().fold(
                    onSuccess = { rows ->
                        // Stale-outcome guard: a superseded run loses the
                        // registration and its late outcome is discarded.
                        if (searchJob === self) {
                            apply(ReleaseSearchEvent.SearchSucceeded(rows, badges.await()))
                        }
                    },
                    onFailure = { e ->
                        if (searchJob === self) {
                            apply(
                                ReleaseSearchEvent.SearchFailed(
                                    message = e.message,
                                    cacheMiss = e is ArrReleaseCacheUnavailable,
                                ),
                            )
                        }
                    },
                )
            }
        }
        searchJob = job
        job.start()
    }

    private fun apply(event: ReleaseSearchEvent) {
        _state.value = _state.value.copy(sheet = ReleaseSearchStateMachine.reduce(_state.value.sheet, event))
    }
}
