package com.raulshma.jellyplay.feature.admin.watchedremoval

import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.model.MediaItemStub
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
 * Pins the watched-media destructive cleanup flow (`WatchedMediaCleanupViewModel`):
 *
 *  - the AuthRepository gate: canDeleteContent mirrors the signed-in user's
 *    policy and is locked (false) until the user flow emits;
 *  - the initial config is the watched-specific dry-run default (Movie +
 *    Episode, keep-favorites, 0-day threshold, no partial watches) and is
 *    forwarded verbatim to both the scan and the delete call;
 *  - a confirmed deletion invokes the repository with
 *    CleanupActionType.WATCHED_REMOVAL and the selected ids, drops the items
 *    from the results and clears the dialog on success;
 *  - a failed (refused) deletion is a no-op: nothing is removed, the error is
 *    surfaced, the dialog closes.
 *
 * Note: there is deliberately no password re-entry gate at the ViewModel
 * layer — the destructive-action guards here are the dry-run default config
 * and the canDeleteContent permission.
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
            """{"itemId":"b","name":"Apple","type":"Episode","sizeText":"500 MB","dateText":"2024-01-01"},""" +
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

    private fun user(canDelete: Boolean) = UserInfo(
        id = "u-admin",
        name = "Alice",
        serverAddress = "http://server:8096",
        accessToken = "token",
        isAdmin = true,
        canDeleteContent = canDelete,
    )

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

    // ── AuthRepository gate ──

    @Test
    fun `delete permission is locked until the auth user flow emits`() = runTest(mainDispatcher) {
        val viewModel = WatchedMediaCleanupViewModel(repository, authRepository)
        advanceUntilIdle()

        // Null user (signed out / unknown policy) — destructive action locked.
        assertFalse(viewModel.state.value.canDeleteContent)

        currentUserFlow.value = user(canDelete = false)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.canDeleteContent)

        currentUserFlow.value = user(canDelete = true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canDeleteContent)
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
    fun `scan forwards the live config verbatim to the repository`() = runTest(mainDispatcher) {
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
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `completed scan loads the result items`() = runTest(mainDispatcher) {
        val viewModel = scanCompletingViewModel()

        assertEquals("scan-1", viewModel.state.value.scanId)
        assertEquals(
            listOf("a", "b", "c"),
            viewModel.state.value.rawScanResults.map { it.itemId },
        )
    }

    @Test
    fun `confirmed deletion invokes the repository with the watched action`() = runTest(mainDispatcher) {
        val viewModel = scanCompletingViewModel()
        viewModel.toggleItemSelection("b")
        viewModel.showDeleteConfirmation()
        assertTrue(viewModel.state.value.showDeleteConfirmation)
        coEvery { repository.removeMediaItems(any(), any(), any(), any()) } returns
            Result.success(AuditLogEntry(id = "watched-audit"))

        viewModel.deleteSelected()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.removeMediaItems(
                itemIds = listOf("b"),
                itemNameMap = any(),
                actionType = CleanupActionType.WATCHED_REMOVAL,
                config = match { it.dryRun && it.keepFavorites },
            )
        }
        // Success clears the dialog + selection and drops the deleted item.
        assertFalse(viewModel.state.value.showDeleteConfirmation)
        assertFalse(viewModel.state.value.isDeleting)
        assertTrue(viewModel.state.value.selectedItems.isEmpty())
        assertEquals(listOf("a", "c"), viewModel.state.value.rawScanResults.map { it.itemId })
    }

    @Test
    fun `refused deletion is a no-op on the results`() = runTest(mainDispatcher) {
        val viewModel = scanCompletingViewModel()
        viewModel.toggleItemSelection("a")
        viewModel.showDeleteConfirmation()
        coEvery { repository.removeMediaItems(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("wrong password"))

        viewModel.deleteSelected()
        advanceUntilIdle()

        assertEquals("wrong password", viewModel.state.value.error)
        assertFalse(viewModel.state.value.showDeleteConfirmation)
        assertFalse(viewModel.state.value.isDeleting)
        // Nothing removed, selection retained for a retry.
        assertEquals(3, viewModel.state.value.rawScanResults.size)
        assertEquals(setOf("a"), viewModel.state.value.selectedItems)
    }

    @Test
    fun `audit history is observed for the watched-removal action`() = runTest(mainDispatcher) {
        val viewModel = WatchedMediaCleanupViewModel(repository, authRepository)
        val audit = AuditLogEntry(id = "audit-1", actionType = CleanupActionType.WATCHED_REMOVAL, itemCount = 1)

        auditFlow.tryEmit(listOf(audit))
        advanceUntilIdle()

        assertEquals(listOf(audit), viewModel.state.value.auditEntries)
        verify { repository.getAuditHistory(CleanupActionType.WATCHED_REMOVAL) }
    }
}
