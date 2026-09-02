package com.raulshma.jellyplay.feature.admin.stalemedia

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the stale-media cleanup flow (`StaleMediaViewModel`):
 *
 *  - the destructive action is config-gated: the initial config is dry-run
 *    (dryRun = true, 90 days, never-played included, Movie/Series/Episode)
 *    and whatever config is live is forwarded verbatim to the scan and the
 *    delete call;
 *  - MediaSortOption drives the derived scanResults (case-insensitive name
 *    compare, size parsed from sizeText, plain type/date ordering);
 *  - selection is a toggle set with selectAll as an all-or-clear switch;
 *  - a confirmed delete removes the selected items from the results on
 *    success and retains them + surfaces the error on failure;
 *  - the delete permission mirrors authRepository.currentUser.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaleMediaViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var repository: AdminStatisticsRepository
    private lateinit var authRepository: AuthRepository

    private val currentUserFlow = MutableStateFlow<UserInfo?>(null)
    private val auditFlow = MutableSharedFlow<List<AuditLogEntry>>(replay = 1, extraBufferCapacity = 8)

    private val itemsJson =
        """[{"itemId":"a","name":"banana","type":"Series","sizeText":"2.0 GB","dateText":"2024-03-01"},""" +
            """{"itemId":"b","name":"Apple","type":"Movie","sizeText":"500 MB","dateText":"2024-01-01"},""" +
            """{"itemId":"c","name":"cherry","type":"Episode","sizeText":"1.0 TB","dateText":"2024-02-01"}]"""

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
    private fun TestScope.scanCompletingViewModel(): StaleMediaViewModel {
        coEvery { repository.detectStaleMedia(any()) } returns Result.success("scan-1")
        every { repository.getScanProgress("scan-1") } returns
            MutableStateFlow(ScanProgress(phase = ScanPhase.COMPLETED))
        coEvery { repository.getScanResultJson("scan-1") } returns itemsJson
        val viewModel = StaleMediaViewModel(repository, authRepository)
        viewModel.startScan()
        advanceUntilIdle()
        return viewModel
    }

    // ── config defaults + dry-run gate ──

    @Test
    fun `initial config is dry-run with stale defaults`() = runTest(mainDispatcher) {
        val viewModel = StaleMediaViewModel(repository, authRepository)
        advanceUntilIdle()

        val config = viewModel.state.value.config
        assertTrue(config.dryRun, "cleanup must default to dry-run")
        assertEquals(90, config.daysThreshold)
        assertTrue(config.includeNeverPlayed)
        assertEquals(setOf("Movie", "Series", "Episode"), config.includeItemTypes)
        assertEquals(MediaSortOption.DEFAULT, viewModel.state.value.sortOption)
    }

    @Test
    fun `scan forwards the live config verbatim to the repository`() = runTest(mainDispatcher) {
        val viewModel = StaleMediaViewModel(repository, authRepository)
        coEvery { repository.detectStaleMedia(any()) } returns Result.failure(RuntimeException("offline"))

        viewModel.startScan()
        advanceUntilIdle()

        coVerify {
            repository.detectStaleMedia(match { it.dryRun && it.daysThreshold == 90 && it.includeNeverPlayed })
        }
    }

    @Test
    fun `updated config is used by the next scan`() = runTest(mainDispatcher) {
        val viewModel = StaleMediaViewModel(repository, authRepository)
        viewModel.updateConfig(
            viewModel.state.value.config.copy(dryRun = false, daysThreshold = 30),
        )
        coEvery { repository.detectStaleMedia(any()) } returns Result.failure(RuntimeException("offline"))

        viewModel.startScan()
        advanceUntilIdle()

        coVerify { repository.detectStaleMedia(match { !it.dryRun && it.daysThreshold == 30 }) }
    }

    // ── scan flow ──

    @Test
    fun `completed scan loads the result items`() = runTest(mainDispatcher) {
        val viewModel = scanCompletingViewModel()

        assertEquals("scan-1", viewModel.state.value.scanId)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(
            listOf("a", "b", "c"),
            viewModel.state.value.rawScanResults.map { it.itemId },
        )
    }

    @Test
    fun `scan failure surfaces the error without a scan id`() = runTest(mainDispatcher) {
        val viewModel = StaleMediaViewModel(repository, authRepository)
        coEvery { repository.detectStaleMedia(any()) } returns Result.failure(RuntimeException("offline"))

        viewModel.startScan()
        advanceUntilIdle()

        assertEquals("offline", viewModel.state.value.error)
        assertNull(viewModel.state.value.scanId)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `a new scan clears the previous selection and results`() = runTest(mainDispatcher) {
        val viewModel = scanCompletingViewModel()
        viewModel.toggleItemSelection("a")

        viewModel.startScan()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.selectedItems.isEmpty())
        assertEquals(setOf("a", "b", "c"), viewModel.state.value.rawScanResults.map { it.itemId }.toSet())
    }

    // ── sorting ──

    @Test
    fun `sort options order the derived scan results`() = runTest(mainDispatcher) {
        val viewModel = scanCompletingViewModel()

        viewModel.updateSort(MediaSortOption.DEFAULT)
        assertEquals(listOf("a", "b", "c"), viewModel.state.value.scanResults.map { it.itemId })

        // Case-insensitive: "Apple" sorts before "banana".
        viewModel.updateSort(MediaSortOption.NAME_ASC)
        assertEquals(listOf("b", "a", "c"), viewModel.state.value.scanResults.map { it.itemId })

        viewModel.updateSort(MediaSortOption.NAME_DESC)
        assertEquals(listOf("c", "a", "b"), viewModel.state.value.scanResults.map { it.itemId })

        // sizeText parsed: 1.0 TB > 2.0 GB > 500 MB.
        viewModel.updateSort(MediaSortOption.SIZE_DESC)
        assertEquals(listOf("c", "a", "b"), viewModel.state.value.scanResults.map { it.itemId })

        viewModel.updateSort(MediaSortOption.SIZE_ASC)
        assertEquals(listOf("b", "a", "c"), viewModel.state.value.scanResults.map { it.itemId })

        viewModel.updateSort(MediaSortOption.TYPE)
        assertEquals(listOf("c", "b", "a"), viewModel.state.value.scanResults.map { it.itemId })

        viewModel.updateSort(MediaSortOption.DATE)
        assertEquals(listOf("b", "c", "a"), viewModel.state.value.scanResults.map { it.itemId })
    }

    // ── selection ──

    @Test
    fun `toggleItemSelection adds and removes ids`() = runTest(mainDispatcher) {
        val viewModel = scanCompletingViewModel()

        viewModel.toggleItemSelection("a")
        assertEquals(setOf("a"), viewModel.state.value.selectedItems)

        viewModel.toggleItemSelection("c")
        assertEquals(setOf("a", "c"), viewModel.state.value.selectedItems)

        viewModel.toggleItemSelection("a")
        assertEquals(setOf("c"), viewModel.state.value.selectedItems)
    }

    @Test
    fun `selectAll toggles between everything and nothing`() = runTest(mainDispatcher) {
        val viewModel = scanCompletingViewModel()

        viewModel.selectAll()
        assertEquals(setOf("a", "b", "c"), viewModel.state.value.selectedItems)

        viewModel.selectAll()
        assertTrue(viewModel.state.value.selectedItems.isEmpty())
    }

    // ── delete confirmation flow ──

    @Test
    fun `confirmed delete removes selected items via the repository`() = runTest(mainDispatcher) {
        val viewModel = scanCompletingViewModel()
        viewModel.toggleItemSelection("a")
        viewModel.toggleItemSelection("c")
        viewModel.showDeleteConfirmation()
        assertTrue(viewModel.state.value.showDeleteConfirmation)
        coEvery { repository.removeMediaItems(any(), any(), any(), any()) } returns
            Result.success(AuditLogEntry(id = "audit-1"))

        viewModel.deleteSelected()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.removeMediaItems(
                itemIds = listOf("a", "c"),
                itemNameMap = any(),
                actionType = CleanupActionType.STALE_REMOVAL,
                config = any(),
            )
        }
        assertTrue(viewModel.state.value.selectedItems.isEmpty())
        assertFalse(viewModel.state.value.showDeleteConfirmation)
        assertFalse(viewModel.state.value.isDeleting)
        // The deleted items drop out of the results; the rest stay.
        assertEquals(listOf("b"), viewModel.state.value.rawScanResults.map { it.itemId })
    }

    @Test
    fun `delete failure closes the dialog retains items and surfaces the error`() = runTest(mainDispatcher) {
        val viewModel = scanCompletingViewModel()
        viewModel.toggleItemSelection("a")
        viewModel.showDeleteConfirmation()
        coEvery { repository.removeMediaItems(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("server refused"))

        viewModel.deleteSelected()
        advanceUntilIdle()

        assertEquals("server refused", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isDeleting)
        assertFalse(viewModel.state.value.showDeleteConfirmation)
        assertEquals(setOf("a"), viewModel.state.value.selectedItems)
        assertEquals(3, viewModel.state.value.rawScanResults.size)
    }

    @Test
    fun `dismissDeleteConfirmation closes the dialog without deleting`() = runTest(mainDispatcher) {
        val viewModel = scanCompletingViewModel()
        viewModel.toggleItemSelection("a")
        viewModel.showDeleteConfirmation()

        viewModel.dismissDeleteConfirmation()

        assertFalse(viewModel.state.value.showDeleteConfirmation)
        coVerify(exactly = 0) { repository.removeMediaItems(any(), any(), any(), any()) }
        assertEquals(setOf("a"), viewModel.state.value.selectedItems)
    }

    // ── permissions + audit history ──

    @Test
    fun `delete permission mirrors the auth current user`() = runTest(mainDispatcher) {
        val viewModel = StaleMediaViewModel(repository, authRepository)
        advanceUntilIdle()
        // No signed-in user → the destructive action is locked.
        assertFalse(viewModel.state.value.canDeleteContent)

        currentUserFlow.value = UserInfo(
            id = "u-admin",
            name = "Alice",
            serverAddress = "http://server:8096",
            accessToken = "token",
            canDeleteContent = false,
        )
        advanceUntilIdle()
        assertFalse(viewModel.state.value.canDeleteContent)

        currentUserFlow.value = currentUserFlow.value!!.copy(canDeleteContent = true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canDeleteContent)
    }

    @Test
    fun `audit history is observed for the stale-removal action`() = runTest(mainDispatcher) {
        val viewModel = StaleMediaViewModel(repository, authRepository)
        val audit = AuditLogEntry(id = "audit-1", actionType = CleanupActionType.STALE_REMOVAL, itemCount = 2)

        auditFlow.tryEmit(listOf(audit))
        advanceUntilIdle()

        assertEquals(listOf(audit), viewModel.state.value.auditEntries)
        verify { repository.getAuditHistory(CleanupActionType.STALE_REMOVAL) }
    }
}
