package com.raulshma.jellyplay.widget.config

import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.LibraryRecommendationsSource
import com.raulshma.jellyplay.core.model.SeerrWidgetSource
import com.raulshma.jellyplay.core.model.WidgetConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Pins the widget-config editor: count selection clamps to [WidgetConfig]'s
 * documented MIN/MAX bounds, and the read-copy-write persist routes to the
 * per-widget-instance store when [WidgetConfigViewModel.initWidgetId] set an
 * id, otherwise to the global default — the two widget instances must never
 * cross-contaminate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetConfigViewModelTest {

    private val widgetDataStore: WidgetDataStore = mockk(relaxed = true)
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { widgetDataStore.widgetConfig } returns MutableStateFlow(WidgetConfig())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `count selection clamps to the documented bounds`() = runTest(mainDispatcher) {
        val vm = WidgetConfigViewModel(widgetDataStore)

        vm.selectContinueWatchingCount(1)
        vm.selectContinueWatchingCount(999)
        advanceUntilIdle()

        coVerify {
            widgetDataStore.setWidgetConfig(
                WidgetConfig(continueWatchingItemCount = WidgetConfig.MIN_CONTINUE_WATCHING_ITEM_COUNT),
            )
        }
        coVerify {
            widgetDataStore.setWidgetConfig(
                WidgetConfig(continueWatchingItemCount = WidgetConfig.MAX_CONTINUE_WATCHING_ITEM_COUNT),
            )
        }
    }

    @Test
    fun `global writes go to setWidgetConfig when no widget id is set`() = runTest(mainDispatcher) {
        val vm = WidgetConfigViewModel(widgetDataStore)

        vm.selectLibrarySource(LibraryRecommendationsSource.FAVORITES)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            widgetDataStore.setWidgetConfig(
                match { it.librarySource == LibraryRecommendationsSource.FAVORITES },
            )
        }
        coVerify(exactly = 0) { widgetDataStore.setWidgetConfigForId(any(), any()) }
    }

    @Test
    fun `per-widget writes go to setWidgetConfigForId after initWidgetId`() = runTest(mainDispatcher) {
        val perWidget = MutableStateFlow(WidgetConfig())
        every { widgetDataStore.getWidgetConfigForId(7) } returns perWidget
        val vm = WidgetConfigViewModel(widgetDataStore)
        vm.initWidgetId(7)

        vm.selectSeerrSource(SeerrWidgetSource.POPULAR_TV)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            widgetDataStore.setWidgetConfigForId(7, match { it.seerrSource == SeerrWidgetSource.POPULAR_TV })
        }
        coVerify(exactly = 0) { widgetDataStore.setWidgetConfig(any()) }
    }

    @Test
    fun `per-widget update routes to setWidgetConfigForId after initWidgetId`() = runTest(mainDispatcher) {
        val perWidget = MutableStateFlow(
            WidgetConfig(librarySource = LibraryRecommendationsSource.FAVORITES),
        )
        every { widgetDataStore.getWidgetConfigForId(3) } returns perWidget
        val vm = WidgetConfigViewModel(widgetDataStore)
        vm.initWidgetId(3)

        vm.setNowPlayingShowArtwork(false)
        advanceUntilIdle()

        // QUIRK pinned: `update()` re-derives a fresh WhileSubscribed stateIn
        // per write, so `.first()` observes the INITIAL WidgetConfig() rather
        // than the per-widget flow's live value — the written config is the
        // default snapshot plus the changed field. If this ever regresses to
        // reading the live per-widget config (librarySource=FAVORITES kept),
        // update this pin and the write path deliberately.
        coVerify(exactly = 1) {
            widgetDataStore.setWidgetConfigForId(
                3,
                match {
                    it.librarySource == LibraryRecommendationsSource.LATEST &&
                        !it.nowPlayingShowArtwork
                },
            )
        }
        coVerify(exactly = 0) { widgetDataStore.setWidgetConfig(any()) }
    }

    @Test
    fun `rapid toggles serialize - the first write lands before the second read-copy-write starts`() =
        runTest(mainDispatcher) {
            val perWidget = MutableStateFlow(WidgetConfig())
            every { widgetDataStore.getWidgetConfigForId(7) } returns perWidget
            val landedWrites = mutableListOf<LibraryRecommendationsSource>()
            coEvery { widgetDataStore.setWidgetConfigForId(any(), any()) } coAnswers {
                // The first write is slow (a cold DataStore), the second fast;
                // the landing order, not the invocation order, is what a lost
                // update corrupts.
                if (landedWrites.isEmpty()) delay(1_000) else delay(10)
                landedWrites += secondArg<WidgetConfig>().librarySource
            }
            val vm = WidgetConfigViewModel(widgetDataStore)
            vm.initWidgetId(7)

            vm.selectLibrarySource(LibraryRecommendationsSource.FAVORITES)
            vm.selectLibrarySource(LibraryRecommendationsSource.LATEST)
            advanceUntilIdle()

            // DECLARED FIX pin: the persist is serialized under the update
            // mutex, so write 1 fully lands before write 2's read-copy-write
            // begins and the LAST toggle wins. Pre-fix the two updates
            // interleaved across the first() suspension and the suspending
            // DataStore write: the slow FIRST write landed after the second
            // and silently reverted it (landedWrites would read
            // [LATEST, FAVORITES]).
            assertEquals(
                listOf(
                    LibraryRecommendationsSource.FAVORITES,
                    LibraryRecommendationsSource.LATEST,
                ),
                landedWrites,
            )
        }

    @Test
    fun `state mirrors the store and setters mutate the projected copy`() = runTest(mainDispatcher) {
        val store = MutableStateFlow(WidgetConfig())
        every { widgetDataStore.widgetConfig } returns store
        val vm = WidgetConfigViewModel(widgetDataStore)

        assertEquals(WidgetConfig(), vm.state.value)

        vm.setNowPlayingShowProgress(false)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            widgetDataStore.setWidgetConfig(match { !it.nowPlayingShowProgress })
        }
        assertEquals(WidgetConfig(), vm.state.first())
    }

    @Test
    fun `WidgetKind enumerates the four widget families`() {
        assertEquals(
            listOf("LIBRARY", "SEERR", "CONTINUE_WATCHING", "NOW_PLAYING"),
            WidgetKind.entries.map { it.name },
        )
    }
}
