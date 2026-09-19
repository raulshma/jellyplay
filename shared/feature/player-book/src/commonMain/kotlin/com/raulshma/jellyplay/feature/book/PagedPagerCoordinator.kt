package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The paged pager as the coordinator sees it — the narrow turn/jump/current-
 * page seam (cf. [com.raulshma.jellyplay.feature.book.epub.EpubReaderHandle]):
 * the real [PagerState] adapts through [rememberPagedPagerCoordinator]'s
 * private adapter; tests fake it directly. [settles] is the current-page
 * flow — a collect emits the seed once, then one emission per settle (the
 * same shape a `snapshotFlow { state.currentPage }` collect has).
 */
internal interface PagerHandle {
    /** The settled page ([PagerState.currentPage]). */
    val currentPage: Int

    /** The page an in-flight scroll is already heading to ([PagerState.targetPage]). */
    val targetPage: Int

    /** Cold flow of [currentPage]: seed emission on collect, then every settle. */
    val settles: Flow<Int>

    suspend fun animateTo(page: Int)

    suspend fun snapTo(page: Int)
}

/**
 * The paged reader's ONE page-turn protocol — the animate-vs-snap ladder and
 * the two named orderings for landing on a page, which previously lived as
 * three hand-copied ladders in [PagedReaderContent] with the ordering
 * contract surviving only as a comment on one of them.
 *
 * **[turnTo] — VM-first.** The VM's uiState page is already the truth when
 * this runs: keyboard/tap-zone/volume-key turns move the VM first
 * ([BookReaderViewModel.nextPage]) and the screen's sync effect re-offers the
 * new page here; the outline/bookmark sheets call
 * [BookReaderViewModel.onPageChanged] / [BookReaderViewModel.jumpToBookmark],
 * the uiState moves, and the SAME sync effect turns the pager. The guard
 * skips a page the pager already sits on or is already flying to — which is
 * also what makes the round trip below idempotent.
 *
 * **[jumpTo] — pager-first.** The TOC tick-rail jump and the bottom-bar
 * slider seek scroll the pager immediately, deliberately WITHOUT telling the
 * VM first (a slider drag would drown the VM in per-tick turns); the VM
 * learns through the settle collector [attach] installs — every settle
 * reports [onPageSettled], and the VM's own same-page guard folds that echo
 * into a no-op when the page already matches. User swipes ride the same
 * collector; it is the only swipe reporting path there is.
 *
 * Programmatic turns (both orderings) animate or snap per the
 * animatedPageTurns preference, read fresh at dispatch ([animated]); user
 * swipes always animate — the pager owns those. Compose-free and
 * constructible in a jvmTest over [PagerHandle] (the module's controller
 * convention — cf. [ReflowableReaderSession]).
 */
internal class PagedPagerCoordinator(
    private val pager: PagerHandle,
    /** The animatedPageTurns preference, read at dispatch time (never a captured value). */
    private val animated: () -> Boolean,
    /** The settle report — the screen hands in `viewModel::onPageChanged`. */
    private val onPageSettled: (Int) -> Unit,
) {

    private var reportingJob: Job? = null

    /**
     * Installs the settle → [onPageSettled] collector once (idempotent while
     * the job lives): the screen's [PagedReaderContent] calls this from a
     * `LaunchedEffect(coordinator) { coordinator.attach(this) }`. The seed
     * emission drops — the composition already opened on that page — and
     * every later settle (swipe, [turnTo], [jumpTo]) reports once.
     */
    fun attach(scope: CoroutineScope) {
        if (reportingJob?.isActive == true) return
        reportingJob = scope.launch {
            pager.settles
                .drop(1)
                .collect { onPageSettled(it) }
        }
    }

    /**
     * VM-first turn (the sync effect's half of the contract): skip when the
     * pager already holds [page] or is already flying to it (the uiState echo
     * of an in-flight turn must not re-dispatch and must not interrupt the
     * scroll it echoes), then animate or snap per the preference.
     */
    suspend fun turnTo(page: Int) {
        if (pager.currentPage == page || pager.targetPage == page) return
        dispatch(page)
    }

    /**
     * Pager-first jump (the tick rail / slider half of the contract): NO
     * guard — a same-page or in-flight-retargeting jump still dispatches,
     * exactly the old `scope.launch { animate|snap }` behavior. The VM learns
     * through the settle collector; its same-page guard makes that report a
     * no-op.
     */
    suspend fun jumpTo(page: Int) {
        dispatch(page)
    }

    /** The one animate-vs-snap ladder — ReaderInput.kt's [pageTurnScroll] mapping. */
    private suspend fun dispatch(page: Int) {
        if (pageTurnScroll(animated()) == PageTurnScroll.ANIMATED) {
            pager.animateTo(page)
        } else {
            pager.snapTo(page)
        }
    }
}

/**
 * The composition-scoped construction: adapts the real [PagerState] behind
 * [PagerHandle] and remembers one coordinator per pager state. Both lambdas
 * ride [rememberUpdatedState] — the coordinator dispatches long after the
 * recomposition that handed them over, so a preference flip or a fresh
 * callback identity must be visible at dispatch time.
 */
@Composable
internal fun rememberPagedPagerCoordinator(
    pagerState: PagerState,
    animated: () -> Boolean,
    onPageSettled: (Int) -> Unit,
): PagedPagerCoordinator {
    val currentAnimated by rememberUpdatedState(animated)
    val currentOnPageSettled by rememberUpdatedState(onPageSettled)
    return remember(pagerState) {
        PagedPagerCoordinator(
            pager = object : PagerHandle {
                override val currentPage: Int get() = pagerState.currentPage
                override val targetPage: Int get() = pagerState.targetPage
                override val settles: Flow<Int> = snapshotFlow { pagerState.currentPage }
                override suspend fun animateTo(page: Int) {
                    pagerState.animateScrollToPage(page)
                }

                override suspend fun snapTo(page: Int) {
                    pagerState.scrollToPage(page)
                }
            },
            animated = { currentAnimated() },
            onPageSettled = { currentOnPageSettled(it) },
        )
    }
}
