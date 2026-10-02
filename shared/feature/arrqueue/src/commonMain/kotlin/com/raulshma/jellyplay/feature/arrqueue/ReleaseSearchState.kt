package com.raulshma.jellyplay.feature.arrqueue

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.arr.ArrRelease
import com.raulshma.jellyplay.core.model.arr.ArrReleaseHistoryStatus

/**
 * Sort orders for the release sheet's rows — the sheet header's sort switch.
 */
enum class ReleaseSort {
    /** Custom-format score, best first (seeders break ties). */
    SCORE,
    /** Seeders, most first (unknowns last; age breaks ties). */
    SEEDERS,
    /** Age, newest first (unknowns last; seeders break ties). */
    AGE,
}

/** What the sheet's body renders, per the machine's progress through a search. */
@Immutable
sealed interface ReleaseSearchPhase {
    /** The indexer query is in flight (10–60 s — the sheet shows progress copy). */
    data object Loading : ReleaseSearchPhase

    /** The search answered successfully with zero rows. */
    data object Empty : ReleaseSearchPhase

    /**
     * The server had no cached search results (the ~30 min decision cache was
     * cold — the search command must run first). Distinct from [Error] so the
     * sheet offers a "Search again" action instead of a dead end.
     */
    data object CacheMiss : ReleaseSearchPhase

    /**
     * The search failed; [message] is the user-visible failure text (null when
     * the throwable carried none — the sheet renders the localized
     * unknown-error string).
     */
    data class Error(val message: String?) : ReleaseSearchPhase

    /** Rows are showing (the grab confirm / a grab error may layer on top). */
    data object Results : ReleaseSearchPhase
}

/** The release sheet's intent/event vocabulary — the [ReleaseSearchState] reducer's input. */
internal sealed interface ReleaseSearchEvent {
    /** A search (initial or "Search again") started for the open item. */
    data object SearchStarted : ReleaseSearchEvent

    /** The search answered with [releases] (possibly empty) and the best-effort [history] badges. */
    data class SearchSucceeded(
        val releases: List<ArrRelease>,
        val history: Map<String, ArrReleaseHistoryStatus>,
    ) : ReleaseSearchEvent

    /** The search failed; [cacheMiss] marks the server's cold decision cache. */
    data class SearchFailed(val message: String?, val cacheMiss: Boolean = false) : ReleaseSearchEvent

    /** The user picked a row sort. */
    data class SortSelected(val sort: ReleaseSort) : ReleaseSearchEvent

    /** The user toggled a row's rejections expand (same guid collapses). */
    data class InfoToggled(val guid: String) : ReleaseSearchEvent

    /** The user asked to grab [release] — the confirm dialog opens. */
    data class GrabRequested(val release: ArrRelease) : ReleaseSearchEvent

    /** The confirm dialog was dismissed. */
    data object GrabDismissed : ReleaseSearchEvent

    /** The confirmed grab is in flight. */
    data object GrabStarted : ReleaseSearchEvent

    /** The grab landed; the row leaves the list. */
    data class GrabSucceeded(val release: ArrRelease) : ReleaseSearchEvent

    /** The grab failed; [message] is the user-visible failure text (null = localized unknown-error). */
    data class GrabFailed(val message: String?) : ReleaseSearchEvent
}

/**
 * The release sheet's UI state — the pure machine [ReleaseSearchStateMachine]
 * reduces, kept compose-free apart from the [Immutable] marker so jvmTest pins
 * the transitions directly (the ArrQueuePresentation precedent: the screen's
 * composables keep only the drawing). One instance per opened sheet; closing
 * the sheet discards it.
 */
@Immutable
data class ReleaseSearchState(
    val phase: ReleaseSearchPhase = ReleaseSearchPhase.Loading,
    val releases: List<ArrRelease> = emptyList(),
    val sort: ReleaseSort = ReleaseSort.SCORE,
    /** The release row whose rejection reasons are expanded, if any. */
    val infoExpandedGuid: String? = null,
    /** The release awaiting a grab confirm, if any. */
    val confirmGrab: ArrRelease? = null,
    /** True while a confirmed grab is in flight (rows' grab buttons disable). */
    val grabbing: Boolean = false,
    /**
     * The last failed grab's message (null = no failure since the last
     * attempt; the sheet renders the localized unknown-error string on null),
     * surfaced above the list until the next attempt.
     */
    val grabError: String? = null,
    /** Client-side "previously grabbed / failed" badges keyed by release guid. */
    val historyStatuses: Map<String, ArrReleaseHistoryStatus> = emptyMap(),
) {
    /** [releases] in [sort] order — the list the sheet renders. */
    val sortedReleases: List<ArrRelease>
        get() = when (sort) {
            ReleaseSort.SCORE -> releases.sortedWith(
                compareByDescending<ArrRelease> { it.customFormatScore }.thenByDescending { it.seeders ?: -1 },
            )
            ReleaseSort.SEEDERS -> releases.sortedWith(
                compareByDescending<ArrRelease> { it.seeders ?: -1 }.thenByDescending { it.customFormatScore },
            )
            ReleaseSort.AGE -> releases.sortedWith(
                compareBy<ArrRelease> { it.ageHours ?: Double.MAX_VALUE }.thenByDescending { it.seeders ?: -1 },
            )
        }
}

/** True when a release row was rejected and its grab needs `shouldOverride`. */
internal val ArrRelease.needsOverride: Boolean get() = !approved

/**
 * The release sheet's pure reducer — every state transition, once. The VM maps
 * its intents onto events and applies [reduce]; the tests pin each arm here.
 *
 * Transition rules:
 * - [ReleaseSearchEvent.SearchStarted] clears every per-search artefact (rows,
 *   badges, expand, confirm, grab error) and lands in Loading; the sort survives
 *   (a "Search again" keeps the user's ordering).
 * - [ReleaseSearchEvent.SearchSucceeded] picks Empty vs Results by row count.
 * - [ReleaseSearchEvent.SearchFailed] picks CacheMiss vs Error by the flag.
 * - Grab events form their own arm: request → confirm dialog, start → dialog
 *   closed + rows disabled, success → row removed (Empty when the last one),
 *   failure → [ReleaseSearchState.grabError] with the list intact.
 */
internal object ReleaseSearchStateMachine {

    fun reduce(state: ReleaseSearchState, event: ReleaseSearchEvent): ReleaseSearchState = when (event) {
        is ReleaseSearchEvent.SearchStarted -> ReleaseSearchState(sort = state.sort)

        is ReleaseSearchEvent.SearchSucceeded -> state.copy(
            phase = if (event.releases.isEmpty()) ReleaseSearchPhase.Empty else ReleaseSearchPhase.Results,
            releases = event.releases,
            historyStatuses = event.history,
        )

        is ReleaseSearchEvent.SearchFailed -> state.copy(
            phase = if (event.cacheMiss) ReleaseSearchPhase.CacheMiss else ReleaseSearchPhase.Error(event.message),
        )

        is ReleaseSearchEvent.SortSelected -> state.copy(sort = event.sort)

        is ReleaseSearchEvent.InfoToggled -> state.copy(
            infoExpandedGuid = if (state.infoExpandedGuid == event.guid) null else event.guid,
        )

        is ReleaseSearchEvent.GrabRequested -> state.copy(confirmGrab = event.release, grabError = null)

        is ReleaseSearchEvent.GrabDismissed -> state.copy(confirmGrab = null)

        is ReleaseSearchEvent.GrabStarted -> state.copy(
            confirmGrab = null,
            grabbing = true,
            grabError = null,
        )

        is ReleaseSearchEvent.GrabSucceeded -> {
            val remaining = state.releases.filterNot { it.guid == event.release.guid }
            state.copy(
                releases = remaining,
                grabbing = false,
                phase = if (remaining.isEmpty()) ReleaseSearchPhase.Empty else ReleaseSearchPhase.Results,
            )
        }

        is ReleaseSearchEvent.GrabFailed -> state.copy(
            grabbing = false,
            grabError = event.message,
        )
    }
}
