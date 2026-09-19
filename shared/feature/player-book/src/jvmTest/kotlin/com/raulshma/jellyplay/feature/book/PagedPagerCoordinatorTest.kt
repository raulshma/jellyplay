package com.raulshma.jellyplay.feature.book

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the paged reader's page-turn protocol ([PagedPagerCoordinator]) —
 * the animate-vs-snap ladder and the two named orderings that used to live
 * as three hand-copied ladders in the composable: [PagedPagerCoordinator.turnTo]
 * (VM-first, guarded — the sync effect's half) and
 * [PagedPagerCoordinator.jumpTo] (pager-first, unguarded — the tick rail /
 * slider half whose only VM report is the settle round trip).
 */
class PagedPagerCoordinatorTest {

    /** Recording fake pager: settles ride a StateFlow, dispatches append to a log. */
    private class FakePager(initialPage: Int = 0) : PagerHandle {
        private val current = MutableStateFlow(initialPage)

        /** Simulates an in-flight scroll: targetPage differs from currentPage. */
        var inFlightTarget: Int? = null

        val log = mutableListOf<String>()

        override val currentPage: Int get() = current.value
        override val targetPage: Int get() = inFlightTarget ?: current.value
        override val settles: Flow<Int> = current

        override suspend fun animateTo(page: Int) {
            log += "animate:$page"
            current.value = page
        }

        override suspend fun snapTo(page: Int) {
            log += "snap:$page"
            current.value = page
        }

        /** A user swipe: the pager settles without the coordinator's involvement. */
        fun swipeTo(page: Int) {
            current.value = page
        }
    }

    // ------------------------------------------------------------------
    // The animate-vs-snap choice
    // ------------------------------------------------------------------

    @Test
    fun `programmatic turns animate or snap per the live preference`() = runTest {
        val pager = FakePager(initialPage = 0)
        var animatedPref = true
        val coordinator = PagedPagerCoordinator(pager, animated = { animatedPref }, onPageSettled = {})

        coordinator.turnTo(3)
        assertEquals(listOf("animate:3"), pager.log)

        // Read at DISPATCH time, not construction time.
        animatedPref = false
        coordinator.turnTo(5)
        assertEquals(listOf("animate:3", "snap:5"), pager.log)

        // The unguarded pager-first ordering maps through the same ladder.
        pager.log.clear()
        pager.swipeTo(0)
        pager.log.clear()
        coordinator.jumpTo(2)
        assertEquals(listOf("snap:2"), pager.log)
    }

    // ------------------------------------------------------------------
    // turnTo — the guarded, VM-first ordering
    // ------------------------------------------------------------------

    @Test
    fun `turnTo skips the held page and an in-flight target`() = runTest {
        val pager = FakePager(initialPage = 2)
        val coordinator = PagedPagerCoordinator(pager, animated = { true }, onPageSettled = {})

        // The uiState echo of a turn the pager already applied.
        coordinator.turnTo(2)
        assertTrue(pager.log.isEmpty())

        // An animateScrollToPage is mid-flight toward 7; the VM reporting 7
        // (the settle round trip completing) must not re-dispatch or interrupt.
        pager.inFlightTarget = 7
        coordinator.turnTo(7)
        assertTrue(pager.log.isEmpty(), "the in-flight echo must not re-dispatch")

        // A genuinely different page still turns (interrupting a flight
        // toward somewhere else is the point of the sync effect).
        pager.inFlightTarget = null
        coordinator.turnTo(7)
        assertEquals(listOf("animate:7"), pager.log)
    }

    @Test
    fun `a VM-driven turn reports back once through the round trip`() = runTest {
        val pager = FakePager(initialPage = 0)
        val reported = mutableListOf<Int>()
        val coordinator = PagedPagerCoordinator(pager, animated = { true }, onPageSettled = { reported += it })
        coordinator.attach(backgroundScope)
        runCurrent() // the collector subscribes (and drops its seed) on this dispatch

        coordinator.turnTo(5)
        runCurrent()
        assertEquals(listOf("animate:5"), pager.log)
        assertEquals(listOf(5), reported)

        // The VM applied the report; turning again to the now-held page is
        // pure echo — no dispatch, no further report.
        coordinator.turnTo(5)
        runCurrent()
        assertEquals(listOf("animate:5"), pager.log)
        assertEquals(listOf(5), reported)
    }

    // ------------------------------------------------------------------
    // jumpTo — the unguarded, pager-first ordering
    // ------------------------------------------------------------------

    @Test
    fun `jumpTo dispatches without the guard and the VM learns via the settle collector`() = runTest {
        val pager = FakePager(initialPage = 1)
        val reported = mutableListOf<Int>()
        val coordinator = PagedPagerCoordinator(pager, animated = { false }, onPageSettled = { reported += it })
        coordinator.attach(backgroundScope)
        runCurrent() // the collector subscribes (and drops its seed) on this dispatch

        // Same-page jumps still dispatch — the old `launch { animate|snap }`
        // behavior, unguarded (the pager itself absorbs the no-op).
        coordinator.jumpTo(1)
        assertEquals(listOf("snap:1"), pager.log)
        runCurrent()
        assertTrue(reported.isEmpty(), "no page change — no settle to report")

        // Pager-first: the dispatch lands BEFORE any VM call, and the only
        // VM-visible leg is the settle report.
        pager.log.clear()
        coordinator.jumpTo(4)
        runCurrent()
        assertEquals(listOf("snap:4"), pager.log)
        assertEquals(listOf(4), reported)

        // Unlike turnTo, jumpTo retargets an in-flight scroll freely (the
        // slider drag case).
        pager.log.clear()
        pager.inFlightTarget = 6
        coordinator.jumpTo(6)
        assertEquals(listOf("snap:6"), pager.log)
    }

    // ------------------------------------------------------------------
    // The settle collector — the only swipe reporting path
    // ------------------------------------------------------------------

    @Test
    fun `attach forwards each settle exactly once and drops the seed`() = runTest {
        val pager = FakePager(initialPage = 0)
        val reported = mutableListOf<Int>()
        val coordinator = PagedPagerCoordinator(pager, animated = { true }, onPageSettled = { reported += it })

        coordinator.attach(backgroundScope)
        runCurrent() // the collector subscribes (and drops its seed) on this dispatch
        assertTrue(reported.isEmpty(), "the page the composition opened on is not a settle")

        pager.swipeTo(1)
        runCurrent()
        pager.swipeTo(2)
        runCurrent()
        pager.swipeTo(2) // re-affirming the settled page is not a second settle
        runCurrent()
        assertEquals(listOf(1, 2), reported)
    }

    @Test
    fun `attaching twice does not double-report`() = runTest {
        val pager = FakePager(initialPage = 0)
        val reported = mutableListOf<Int>()
        val coordinator = PagedPagerCoordinator(pager, animated = { true }, onPageSettled = { reported += it })

        coordinator.attach(backgroundScope)
        coordinator.attach(backgroundScope)
        runCurrent() // the first attach's collector is the one that lives
        pager.swipeTo(3)
        runCurrent()
        assertEquals(listOf(3), reported)
    }
}
