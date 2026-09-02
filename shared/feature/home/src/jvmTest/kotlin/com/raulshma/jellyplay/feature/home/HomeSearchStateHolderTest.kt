package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.search.MediaSearchPreviewState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers

/**
 * Direct [HomeSearchStateHolder] tests — the query-lifecycle cases migrated
 * from HomeViewModelTest (search_keepsLatestQuery_afterSupersededEntry,
 * clearSearch_resetsSearchState) plus pins for the blank-query isSearching
 * reset. Plain JUnit + [MainDispatcherRule] + MockK over a targeted
 * MediaSearchEngine fake, mirroring the HomeRefresherTest scope hand-off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeSearchStateHolderTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    /** Inline-search kernel — targeted fake instead of five collaborators. */
    private val mediaSearchEngine: MediaSearchEngine = mockk(relaxed = true)

    private var holderScope: CoroutineScope? = null

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { mediaSearchEngine.recentHistory() } returns flowOf(emptyList())
        every { mediaSearchEngine.preview(any()) } returns flowOf(
            MediaSearchPreviewState(query = "", jellyfin = emptyList(), seerr = emptyList(), isSearching = false)
        )
    }

    @AfterTest
    fun stopHolder() {
        holderScope?.cancel()

        Dispatchers.resetMain()
    }

    private fun TestScope.buildHolder(): HomeSearchStateHolder {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        holderScope = scope
        return HomeSearchStateHolder(scope = scope, mediaSearchEngine = mediaSearchEngine)
    }

    @Test
    fun updateSearchQuery_keepsLatestQuery_afterSupersededEntry() = runTest {
        val holder = buildHolder()

        holder.updateSearchQuery("bat")
        holder.updateSearchQuery("batman")
        runCurrent()

        // Query is the latest value; the intermediate "bat" was superseded by
        // the debounce + distinctUntilChanged chain. The live query lives on
        // the holder's searchQuery flow (read by the leaf), not searchState.
        assertEquals("batman", holder.searchQuery.value)
        assertTrue(holder.isSearchActive.value)
    }

    @Test
    fun clearSearch_resetsSearchState() = runTest {
        val holder = buildHolder()

        holder.updateSearchQuery("hello")
        runCurrent()

        holder.clearSearch()
        runCurrent()

        val search = holder.searchState.value
        assertEquals("", holder.searchQuery.value)
        assertFalse(holder.isSearchActive.value)
        assertTrue(search.jellyfinResults.isEmpty())
        assertTrue(search.seerrResults.isEmpty())
    }

    @Test
    fun updateSearchQuery_blankQuery_deactivates_andClearsSpinner() = runTest {
        val holder = buildHolder()

        holder.updateSearchQuery("hello")
        runCurrent()
        holder.updateSearchQuery("   ")
        runCurrent()

        assertFalse(holder.isSearchActive.value)
        assertFalse(holder.searchState.value.isSearching)
        assertEquals("   ", holder.searchQuery.value)
    }

    @Test
    fun preview_emissions_foldIntoSearchState() = runTest {
        every { mediaSearchEngine.preview(any()) } returns flowOf(
            MediaSearchPreviewState(query = "batman", jellyfin = emptyList(), seerr = emptyList(), isSearching = true)
        )
        val holder = buildHolder()
        runCurrent()

        assertTrue(holder.searchState.value.isSearching)
    }
}
