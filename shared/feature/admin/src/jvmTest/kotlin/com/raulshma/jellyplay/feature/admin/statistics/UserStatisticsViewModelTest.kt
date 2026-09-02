package com.raulshma.jellyplay.feature.admin.statistics

import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserStatistics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Pins the user statistics overview (`UserStatisticsViewModel`):
 *
 *  - the load derives totals (users / currently-active / total plays) and
 *    applies the default sort (plays desc, watch time desc, name asc
 *    tie-break); the plugin status rides in from its hot flow;
 *  - setSort re-sorts the already-loaded users in place without refetching;
 *  - failures surface as state.error with loading cleared;
 *  - refresh failures are silent and keep the loaded users;
 *  - the export share request is a consumable one-shot flag.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserStatisticsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var repository: AdminStatisticsRepository

    private val pluginStatusFlow = MutableStateFlow(PlaybackReportingStatus.UNAVAILABLE)

    private val carol = UserStatistics(
        userId = "u-1", userName = "carol", totalPlayCount = 10, totalWatchTimeSec = 100,
    )
    private val alice = UserStatistics(
        userId = "u-2", userName = "alice", totalPlayCount = 10, totalWatchTimeSec = 200,
    )
    private val bob = UserStatistics(
        userId = "u-3", userName = "Bob", totalPlayCount = 30, totalWatchTimeSec = 50,
        isCurrentlyActive = true,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repository = mockk(relaxed = true)
        every { repository.getPlaybackReportingStatus() } returns pluginStatusFlow
        coEvery { repository.refreshPlaybackReportingStatus() } just runs
        coEvery { repository.getAllUsersWithStatistics() } returns
            Result.success(listOf(carol, alice, bob))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── load ──

    @Test
    fun `load applies the default plays sort and derives the totals`() = runTest(mainDispatcher) {
        val viewModel = UserStatisticsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        // PLAYS desc; the 10-play tie breaks by watch time desc.
        assertEquals(listOf(bob, alice, carol), state.users)
        assertEquals(3, state.totalUsers)
        assertEquals(1, state.activeThisWeek)
        assertEquals(50, state.totalPlays)
        assertEquals(UserStatisticsSort.PLAYS, state.sort)
        assertEquals(PlaybackReportingStatus.UNAVAILABLE, state.pluginStatus)
    }

    @Test
    fun `load failure surfaces the error`() = runTest(mainDispatcher) {
        coEvery { repository.getAllUsersWithStatistics() } returns
            Result.failure(RuntimeException("plugin missing"))

        val viewModel = UserStatisticsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("plugin missing", state.error)
        assertFalse(state.isLoading)
        assertTrue(state.users.isEmpty())
    }

    // ── sorting ──

    @Test
    fun `setSort re-sorts the loaded users without refetching`() = runTest(mainDispatcher) {
        val viewModel = UserStatisticsViewModel(repository)
        advanceUntilIdle()

        viewModel.setSort(UserStatisticsSort.NAME)
        assertEquals(listOf("alice", "Bob", "carol"), viewModel.state.value.users.map { it.userName })

        viewModel.setSort(UserStatisticsSort.TIME)
        assertEquals(listOf(alice, carol, bob), viewModel.state.value.users)

        viewModel.setSort(UserStatisticsSort.PLAYS)
        assertEquals(listOf(bob, alice, carol), viewModel.state.value.users)

        coVerify(exactly = 1) { repository.getAllUsersWithStatistics() }
    }

    @Test
    fun `name sort is case-insensitive and breaks ties by play count`() = runTest(mainDispatcher) {
        val daveUpper = UserStatistics(userId = "u-1", userName = "Dave", totalPlayCount = 2)
        val daveLower = UserStatistics(userId = "u-2", userName = "dave", totalPlayCount = 9)
        coEvery { repository.getAllUsersWithStatistics() } returns
            Result.success(listOf(daveUpper, daveLower))

        val viewModel = UserStatisticsViewModel(repository)
        advanceUntilIdle()
        viewModel.setSort(UserStatisticsSort.NAME)
        advanceUntilIdle()

        assertEquals(listOf(daveLower, daveUpper), viewModel.state.value.users)
    }

    // ── refresh ──

    @Test
    fun `refresh failure keeps the loaded users silently`() = runTest(mainDispatcher) {
        val viewModel = UserStatisticsViewModel(repository)
        advanceUntilIdle()
        coEvery { repository.getAllUsersWithStatistics() } returns
            Result.failure(RuntimeException("offline"))

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(3, viewModel.state.value.users.size)
        assertNull(viewModel.state.value.error)
    }

    // ── plugin status ──

    @Test
    fun `plugin status follows the repository flow`() = runTest(mainDispatcher) {
        val viewModel = UserStatisticsViewModel(repository)
        advanceUntilIdle()

        pluginStatusFlow.value = PlaybackReportingStatus.AVAILABLE
        advanceUntilIdle()

        assertEquals(PlaybackReportingStatus.AVAILABLE, viewModel.state.value.pluginStatus)
    }

    // ── export share request ──

    @Test
    fun `export request is a consumable one-shot`() = runTest(mainDispatcher) {
        val viewModel = UserStatisticsViewModel(repository)
        advanceUntilIdle()

        viewModel.requestExport()
        assertTrue(viewModel.state.value.shareRequested)

        viewModel.consumeExportRequest()
        assertFalse(viewModel.state.value.shareRequested)
    }
}
