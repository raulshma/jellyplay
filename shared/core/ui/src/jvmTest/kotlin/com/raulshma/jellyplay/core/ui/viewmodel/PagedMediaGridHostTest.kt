package com.raulshma.jellyplay.core.ui.viewmodel

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Decision-table pins for [PagedMediaGridHost], the paged-grid choreography
 * the feature suites own per VM (`FavoritesViewModelTest`'s deferred-refresh
 * table, `LibraryViewModelTest`'s download-routing table, ...); this suite
 * pins what the MODULE owns: the generation-bump → fresh-pager restart, the
 * refresher linkage (off-screen user-data change defers, re-entry fires
 * exactly once, and no feed ⇒ no refresher), and the once-only forwarding of
 * the mark-played / download / remove-download seams.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagedMediaGridHostTest {

    // paging's cachedIn launches its bootstrap eagerly on Dispatchers.Main —
    // each test points Main at ITS OWN scheduler (unconfined, so the bootstrap
    // and the seam-forwarding launches run deterministically under
    // advanceUntilIdle) and tearDown restores it (:core:testing is not a
    // jvmTest dependency of core:ui, so the rule is inlined).
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── generation bump → fresh pager ──────────────────────────────────────

    @Test
    fun refresh_bumpsTheGeneration_andRebuildsThePager() = runTest {
        var generations = 0
        val host = PagedMediaGridHost(
            scope = backgroundScope,
            source = { trigger ->
                trigger.flatMapLatest {
                    generations++
                    flowOf(PagingData.empty<MediaItem>())
                }
            },
        )
        val collector = launch { host.items.collect {} }
        runCurrent()
        assertEquals(1, generations, "the first collection builds generation 0 exactly once")

        host.refresh()
        runCurrent()
        assertEquals(2, generations, "a refresh() bump restarts the pager as a fresh generation")

        host.refresh()
        runCurrent()
        assertEquals(3, generations, "each bump is one generation — no collapse, no doubling")
        collector.cancel()
    }

    @Test
    fun items_are_lazy_until_collected() = runTest {
        var generations = 0
        val host = PagedMediaGridHost(
            scope = backgroundScope,
            source = { trigger ->
                trigger.flatMapLatest {
                    generations++
                    flowOf(PagingData.empty<MediaItem>())
                }
            },
        )
        advanceUntilIdle()
        assertEquals(0, generations, "no collector — no pager built (cachedIn shares, it never pre-fetches)")
    }

    // ── refresher linkage ──────────────────────────────────────────────────

    @Test
    fun offScreen_userData_change_defers_and_reentry_fires_once() = runTest {
        var generations = 0
        val userDataChanges = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val host = PagedMediaGridHost(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            userDataChanges = userDataChanges,
            source = { trigger ->
                trigger.flatMapLatest {
                    generations++
                    flowOf(PagingData.empty<MediaItem>())
                }
            },
        )
        val refresher = host.deferredRefresher
        assertNotNull(refresher, "a host built with a change feed owns its refresher")
        val collector = launch { host.items.collect {} }
        advanceUntilIdle()
        assertEquals(1, generations)

        // Off-screen: the change only marks the pager stale.
        refresher.onScreenActiveChanged(false)
        userDataChanges.emit(UserDataChange("user-1", listOf("m1")))
        advanceUntilIdle()
        assertEquals(1, generations, "an off-screen change must not regenerate mid-life")

        // Re-entry: the single deferred regeneration rides the same trigger.
        refresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        assertEquals(2, generations, "re-entry fires exactly one deferred refresh()")
        collector.cancel()
    }

    @Test
    fun without_a_change_feed_there_is_no_refresher() = runTest {
        val host = PagedMediaGridHost(
            scope = backgroundScope,
            source = { flowOf(PagingData.empty()) },
        )
        assertNull(host.deferredRefresher, "the photo-album shape: pager only, no deferred refresh")
    }

    @Test
    fun host_is_the_deferredRefreshHost_front_and_noops_without_a_feed() = runTest {
        var generations = 0
        val userDataChanges = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val host = PagedMediaGridHost(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            userDataChanges = userDataChanges,
            source = { trigger ->
                trigger.flatMapLatest {
                    generations++
                    flowOf(PagingData.empty<MediaItem>())
                }
            },
        )
        val collector = launch { host.items.collect {} }
        advanceUntilIdle()
        assertEquals(1, generations)

        // The screen-facing front: activity signal + change defers, re-entry
        // regenerates — without any consumer unwrapping deferredRefresher.
        host.onScreenActiveChanged(false)
        userDataChanges.emit(UserDataChange("user-1", listOf("m1")))
        advanceUntilIdle()
        assertEquals(1, generations)
        host.onScreenActiveChanged(true)
        advanceUntilIdle()
        assertEquals(2, generations)
        collector.cancel()

        // The pager-only shape inherits the front as a no-op.
        val pagerOnly = PagedMediaGridHost(
            scope = backgroundScope,
            source = { flowOf(PagingData.empty()) },
        )
        pagerOnly.onScreenActiveChanged(true)
        pagerOnly.onScreenActiveChanged(false)
    }

    // ── seam forwarding, exactly once ──────────────────────────────────────

    @Test
    fun markPlay_download_and_remove_forward_exactly_once() = runTest {
        val played = mutableListOf<Pair<String, Boolean>>()
        val downloads = mutableListOf<Pair<String, Boolean>>()
        val removed = mutableListOf<String>()
        // The seam-forwarding shape: a scope OUTSIDE the test scheduler whose
        // launches run inline (unconfined) — the fire-and-forget forwards must
        // land without scheduler choreography, and runTest must not track the
        // paging children this scope carries.
        val host = PagedMediaGridHost(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            source = { flowOf(PagingData.empty()) },
            downloadedIds = MutableStateFlow(setOf("done-1")),
            downloadSupported = true,
            setPlayedSilently = { itemId, isPlayed -> played += itemId to isPlayed },
            requestDownload = { item, onOpenDetail, seriesOpensSheet ->
                downloads += item.id to seriesOpensSheet
                if (seriesOpensSheet) onOpenDetail(item.id, true)
            },
            removeDownloadItem = { removed += it.id },
        )
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

        host.markPlayed(item, played = true)
        host.removeDownload(item)
        var opened: Pair<String, Boolean>? = null
        host.download(item, onOpenDetail = { id, sheet -> opened = id to sheet }, seriesOpensSheet = true)
        advanceUntilIdle()

        assertEquals(listOf("m1" to true), played, "one silent mark-played launch per call")
        assertEquals(listOf("m1" to true), downloads, "the sheet flag rides the seam untouched")
        assertEquals("m1" to true, opened, "the caller's open-detail callback is forwarded verbatim")
        assertEquals(listOf("m1"), removed)
        assertEquals(setOf("done-1"), host.downloadedIds?.value, "the id set is re-exposed as-is")
        assertTrue(host.downloadSupported)
    }

    @Test
    fun download_defaults_to_the_plain_open_detail_routing() = runTest {
        var opened: Pair<String, Boolean>? = null
        val host = PagedMediaGridHost(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            source = { flowOf(PagingData.empty()) },
            requestDownload = { item, onOpenDetail, seriesOpensSheet -> onOpenDetail(item.id, seriesOpensSheet) },
        )
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

        host.download(item, onOpenDetail = { id, sheet -> opened = id to sheet })
        advanceUntilIdle()

        assertEquals(
            "m1" to false,
            opened,
            "the default routing opens detail plainly — only the library grid pre-presents the series sheet",
        )
    }

    @Test
    fun absent_seams_no_op_without_touching_the_scope() = runTest {
        val host = PagedMediaGridHost(
            scope = backgroundScope,
            source = { flowOf(PagingData.empty()) },
        )
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

        host.markPlayed(item, played = true)
        host.removeDownload(item)
        host.download(item, onOpenDetail = { _, _ -> })
        advanceUntilIdle()

        assertTrue(true, "no seam — no launch, no crash")
    }
}
