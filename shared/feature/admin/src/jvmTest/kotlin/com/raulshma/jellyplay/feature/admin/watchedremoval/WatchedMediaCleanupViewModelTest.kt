package com.raulshma.jellyplay.feature.admin.watchedremoval

import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.model.ScanProgress
import com.raulshma.jellyplay.core.model.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Slim per-feature arm over the shared `MediaCleanupScanStateHolder` chassis
 * (pinned once in `MediaCleanupScanStateHolderTest`): only the adapter facts —
 * the watched detect call, the watched config default, and the
 * WATCHED_REMOVAL action type baked into the delete/audit seams.
 *
 * Note: there is deliberately no password re-entry gate at the ViewModel
 * layer — the destructive-action guards here are the dry-run default config
 * and the canDeleteContent permission (both chassis-pinned).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchedMediaCleanupViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var repository: AdminStatisticsRepository
    private lateinit var authRepository: AuthRepository

    private val currentUserFlow = MutableStateFlow<UserInfo?>(null)
    private val auditFlow = MutableSharedFlow<List<AuditLogEntry>>(replay = 1, extraBufferCapacity = 8)

    private val itemsJson =
        """[{"itemId":"a","name":"banana","type":"Movie","sizeText":"2.0 GB","dateText":"2024-03-01"},""" +
            """{"itemId":"c","name":"cherry","type":"Movie","sizeText":"1.0 TB","dateText":"2024-02-01"}]"""

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        every { authRepository.currentUser } returns currentUserFlow
        every { repository.getAuditHistory(any()) } returns auditFlow
        every { repository.getScanProgress(any()) } returns MutableStateFlow(ScanProgress())
        coEvery { repository.getScanResultJson(any()) } returns null
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model whose startScan immediately completes and loads [itemsJson]. */
    private fun TestScope.scanCompletingViewModel(): WatchedMediaCleanupViewModel {
        coEvery { repository.detectWatchedMedia(any()) } returns Result.success("scan-1")
        every { repository.getScanProgress("scan-1") } returns
            MutableStateFlow(ScanProgress(phase = ScanPhase.COMPLETED))
        coEvery { repository.getScanResultJson("scan-1") } returns itemsJson
        val viewModel = WatchedMediaCleanupViewModel(repository, authRepository)
        viewModel.startScan()
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `initial config is the watched-specific dry-run default`() = runTest(mainDispatcher) {
        val viewModel = WatchedMediaCleanupViewModel(repository, authRepository)
        advanceUntilIdle()

        val config = viewModel.state.value.config
        assertTrue(config.dryRun, "cleanup must default to dry-run")
        assertEquals(setOf("Movie", "Episode"), config.includeItemTypes)
        assertTrue(config.keepFavorites)
        assertEquals(0, config.minDaysSinceWatched)
        assertFalse(config.includePartiallyWatched)
    }

    @Test
    fun `scan forwards the live config to detectWatchedMedia`() = runTest(mainDispatcher) {
        val viewModel = WatchedMediaCleanupViewModel(repository, authRepository)
        coEvery { repository.detectWatchedMedia(any()) } returns Result.failure(RuntimeException("offline"))

        viewModel.startScan()
        advanceUntilIdle()

        coVerify {
            repository.detectWatchedMedia(match {
                it.dryRun && it.keepFavorites && it.includeItemTypes == setOf("Movie", "Episode")
            })
        }
        assertEquals("offline", viewModel.state.value.error)
    }

    @Test
    fun `delete routes through WATCHED_REMOVAL and audit is observed for the same action`() = runTest(mainDispatcher) {
        val viewModel = scanCompletingViewModel()
        viewModel.toggleItemSelection("a")
        viewModel.showDeleteConfirmation()
        coEvery { repository.removeMediaItems(any(), any(), any(), any()) } returns
            Result.success(AuditLogEntry(id = "watched-audit"))

        viewModel.deleteSelected()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.removeMediaItems(
                itemIds = listOf("a"),
                itemNameMap = any(),
                actionType = CleanupActionType.WATCHED_REMOVAL,
                config = match { it.dryRun && it.keepFavorites },
            )
        }
        // The adapter subscribed to the watched-removal slice of the audit log.
        verify { repository.getAuditHistory(CleanupActionType.WATCHED_REMOVAL) }
        // And the chassis held up its half: the deleted item dropped out.
        assertEquals(listOf("c"), viewModel.state.value.rawScanResults.map { it.itemId })
    }
}
