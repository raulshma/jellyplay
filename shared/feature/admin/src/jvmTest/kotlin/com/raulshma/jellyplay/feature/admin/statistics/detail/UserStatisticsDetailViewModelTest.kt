package com.raulshma.jellyplay.feature.admin.statistics.detail

import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.model.UserTopItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the per-user statistics drill-down (`UserStatisticsDetailViewModel`):
 *
 *  - loadUser(page 0) resolves the plugin status hot flow and paints the
 *    detail page;
 *  - loadMore appends the next page's top items and advances the cursor, and
 *    is a no-op once hasMoreItems is false;
 *  - a repeated loadUser with the same id and data is a no-op, while a new
 *    id resets the state and reloads from page 0;
 *  - failures surface as state.error with loading flags cleared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserStatisticsDetailViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var repository: AdminStatisticsRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repository = mockk(relaxed = true)
        // loadPage reads the hot status flow once per page via first().
        every { repository.getPlaybackReportingStatus() } returns
            flowOf(PlaybackReportingStatus.AVAILABLE)
        coEvery { repository.refreshPlaybackReportingStatus() } just runs
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun page(vararg items: UserTopItem, hasMore: Boolean = false) =
        UserDetailPage(topItems = items.toList(), hasMoreItems = hasMore)

    private fun item(id: String) = UserTopItem(itemId = id, name = "Item $id", type = "Movie")

    // ── load ──

    @Test
    fun `loadUser paints the first page and the plugin status`() = runTest(mainDispatcher) {
        coEvery { repository.getUserDetailStatistics("u-1", 0, 50) } returns
            Result.success(page(item("i-1"), item("i-2"), hasMore = true))

        val viewModel = UserStatisticsDetailViewModel(repository)
        viewModel.loadUser("u-1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(listOf("i-1", "i-2"), state.detail.topItems.map { it.itemId })
        assertTrue(state.detail.hasMoreItems)
        assertEquals(0, state.currentPage)
        assertEquals(PlaybackReportingStatus.AVAILABLE, state.pluginStatus)
    }

    @Test
    fun `loadUser failure surfaces the error`() = runTest(mainDispatcher) {
        coEvery { repository.getUserDetailStatistics("u-1", 0, 50) } returns
            Result.failure(RuntimeException("plugin unavailable"))

        val viewModel = UserStatisticsDetailViewModel(repository)
        viewModel.loadUser("u-1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("plugin unavailable", state.error)
        assertFalse(state.isLoading)
        assertTrue(state.detail.topItems.isEmpty())
    }

    // ── paging ──

    @Test
    fun `loadMore appends the next page and stops when exhausted`() = runTest(mainDispatcher) {
        coEvery { repository.getUserDetailStatistics("u-1", 0, 50) } returns
            Result.success(page(item("i-1"), hasMore = true))
        coEvery { repository.getUserDetailStatistics("u-1", 1, 50) } returns
            Result.success(page(item("i-2"), hasMore = false))

        val viewModel = UserStatisticsDetailViewModel(repository)
        viewModel.loadUser("u-1")
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("i-1", "i-2"), state.detail.topItems.map { it.itemId })
        assertEquals(1, state.currentPage)
        assertFalse(state.isLoadingMore)

        // Exhausted → guarded, no further fetch.
        viewModel.loadMore()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.getUserDetailStatistics("u-1", 1, 50) }
        coVerify(exactly = 1) { repository.getUserDetailStatistics("u-1", 0, 50) }
    }

    // ── reload / user switch ──

    @Test
    fun `loadUser with the same id and data does not refetch`() = runTest(mainDispatcher) {
        coEvery { repository.getUserDetailStatistics("u-1", 0, 50) } returns
            Result.success(page(item("i-1")))

        val viewModel = UserStatisticsDetailViewModel(repository)
        viewModel.loadUser("u-1")
        advanceUntilIdle()
        viewModel.loadUser("u-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getUserDetailStatistics("u-1", 0, 50) }
    }

    @Test
    fun `loadUser with a different id resets state and reloads from page zero`() = runTest(mainDispatcher) {
        coEvery { repository.getUserDetailStatistics("u-1", 0, 50) } returns
            Result.success(page(item("i-1"), hasMore = true))
        coEvery { repository.getUserDetailStatistics("u-2", 0, 50) } returns
            Result.success(page(item("i-3")))

        val viewModel = UserStatisticsDetailViewModel(repository)
        viewModel.loadUser("u-1")
        advanceUntilIdle()

        viewModel.loadUser("u-2")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("i-3"), state.detail.topItems.map { it.itemId })
        assertEquals(0, state.currentPage)
        assertFalse(state.isLoading)
        assertFalse(state.isLoadingMore)
    }
}
