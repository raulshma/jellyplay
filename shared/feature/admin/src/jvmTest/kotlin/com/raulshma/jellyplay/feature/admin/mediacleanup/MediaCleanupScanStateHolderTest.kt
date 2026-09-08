package com.raulshma.jellyplay.feature.admin.mediacleanup

import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.model.ScanProgress
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the media-cleanup scan chassis (`MediaCleanupScanStateHolder`) — the
 * scan lifecycle (detect → progress observe → COMPLETED → result-JSON decode),
 * the selection machine, the confirm/delete choreography, the permission and
 * audit folds, and the progress re-collect race (a new scan cancels the
 * previous scan's collector). One suite replaces the two per-VM suites that
 * pinned this same behaviour twice; the per-feature suites now cover only the
 * adapter arms (detect call, config default, action type).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaCleanupScanStateHolderTest {

    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var scope: CoroutineScope

    private val currentUserFlow = MutableStateFlow<UserInfo?>(null)
    private val auditFlow = MutableSharedFlow<List<AuditLogEntry>>(replay = 1, extraBufferCapacity = 8)

    private val detectedConfigs = mutableListOf<MediaCleanupConfig>()
    private var detectResult: Result<String> = Result.success("scan-1")
    private val progressFlows = mutableMapOf<String, MutableStateFlow<ScanProgress>>()
    private val resultJsons = mutableMapOf<String, String?>()
    private val removedItemIds = mutableListOf<List<String>>()
    private var removeResult: Result<AuditLogEntry> = Result.success(AuditLogEntry(id = "audit-1"))

    private val itemsJson =
        """[{"itemId":"a","name":"banana","type":"Series","sizeText":"2.0 GB","dateText":"2024-03-01"},""" +
            """{"itemId":"b","name":"Apple","type":"Movie","sizeText":"500 MB","dateText":"2024-01-01"},""" +
            """{"itemId":"c","name":"cherry","type":"Episode","sizeText":"1.0 TB","dateText":"2024-02-01"}]"""

    @BeforeTest
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun holder(
        initialConfig: MediaCleanupConfig = MediaCleanupConfig(),
    ) = MediaCleanupScanStateHolder(
        scope = scope,
        initialConfig = initialConfig,
        detectMedia = { config ->
            detectedConfigs += config
            detectResult
        },
        scanProgress = { scanId ->
            progressFlows.getOrPut(scanId) { MutableStateFlow(ScanProgress()) }
        },
        scanResultJson = { scanId -> resultJsons[scanId] },
        removeMediaItems = { itemIds, _, _ ->
            removedItemIds += itemIds
            removeResult
        },
        currentUser = currentUserFlow,
        auditHistory = auditFlow,
    )

    /** A holder whose startScan immediately completes and loads [itemsJson]. */
    private fun completedScanHolder(): MediaCleanupScanStateHolder {
        detectResult = Result.success("scan-1")
        progressFlows["scan-1"] = MutableStateFlow(ScanProgress(phase = ScanPhase.COMPLETED))
        resultJsons["scan-1"] = itemsJson
        return holder()
    }

    // ── scan lifecycle ──

    @Test
    fun `start scan forwards the live config and clears previous results and selection`() = runTest(mainDispatcher) {
        val holder = completedScanHolder()
        holder.startScan()
        advanceUntilIdle()
        holder.toggleItemSelection("a")
        assertEquals(listOf("a", "b", "c"), holder.state.value.rawScanResults.map { it.itemId })

        detectResult = Result.failure(RuntimeException("offline"))
        holder.startScan()
        advanceUntilIdle()

        assertTrue(holder.state.value.selectedItems.isEmpty())
        assertTrue(holder.state.value.rawScanResults.isEmpty())
        assertEquals("offline", holder.state.value.error)
        // A failed scan never touches the id of the last successful one.
        assertEquals("scan-1", holder.state.value.scanId)
        assertFalse(holder.state.value.isLoading)
        // Both scans carried the live (initial) config verbatim.
        assertEquals(2, detectedConfigs.size)
        assertTrue(detectedConfigs.all { it.dryRun })
    }

    @Test
    fun `scan failure on a fresh holder surfaces the error without a scan id`() = runTest(mainDispatcher) {
        detectResult = Result.failure(RuntimeException("offline"))
        val holder = holder()

        holder.startScan()
        advanceUntilIdle()

        assertEquals("offline", holder.state.value.error)
        assertNull(holder.state.value.scanId)
        assertFalse(holder.state.value.isLoading)
    }

    @Test
    fun `updated config is used by the next scan`() = runTest(mainDispatcher) {
        val holder = holder()
        holder.updateConfig(holder.state.value.config.copy(dryRun = false, daysThreshold = 30))
        detectResult = Result.failure(RuntimeException("offline"))

        holder.startScan()
        advanceUntilIdle()

        assertEquals(1, detectedConfigs.size)
        assertFalse(detectedConfigs.single().dryRun)
        assertEquals(30, detectedConfigs.single().daysThreshold)
    }

    @Test
    fun `completed scan stores the scan id and decodes the result items`() = runTest(mainDispatcher) {
        val holder = completedScanHolder()

        holder.startScan()
        advanceUntilIdle()

        assertEquals("scan-1", holder.state.value.scanId)
        assertFalse(holder.state.value.isLoading)
        assertEquals(
            listOf("a", "b", "c"),
            holder.state.value.rawScanResults.map { it.itemId },
        )
    }

    @Test
    fun `undecodable result json degrades to an empty list`() = runTest(mainDispatcher) {
        detectResult = Result.success("scan-bad")
        progressFlows["scan-bad"] = MutableStateFlow(ScanProgress(phase = ScanPhase.COMPLETED))
        resultJsons["scan-bad"] = "not json at all"
        val holder = holder()

        holder.startScan()
        advanceUntilIdle()

        assertTrue(holder.state.value.rawScanResults.isEmpty())
        assertNull(holder.state.value.error)
    }

    @Test
    fun `progress emissions mirror into state until COMPLETED loads results`() = runTest(mainDispatcher) {
        detectResult = Result.success("scan-1")
        val progress = MutableStateFlow(ScanProgress(phase = ScanPhase.SCANNING, scanned = 5, itemsFound = 2))
        progressFlows["scan-1"] = progress
        val holder = holder()

        holder.startScan()
        advanceUntilIdle()

        assertEquals(ScanPhase.SCANNING, holder.state.value.scanProgress.phase)
        assertEquals(5, holder.state.value.scanProgress.scanned)
        assertTrue(holder.state.value.rawScanResults.isEmpty())

        progress.value = ScanProgress(phase = ScanPhase.COMPLETED, scanned = 10, itemsFound = 3)
        resultJsons["scan-1"] = itemsJson
        advanceUntilIdle()

        assertEquals(ScanPhase.COMPLETED, holder.state.value.scanProgress.phase)
        assertEquals(listOf("a", "b", "c"), holder.state.value.rawScanResults.map { it.itemId })
    }

    @Test
    fun `a new scan cancels the previous progress collector - abandoned emissions cannot clobber`() = runTest(mainDispatcher) {
        // Scan 1 hangs in SCANNING.
        detectResult = Result.success("scan-1")
        val oldProgress = MutableStateFlow(ScanProgress(phase = ScanPhase.SCANNING))
        progressFlows["scan-1"] = oldProgress
        val holder = holder()
        holder.startScan()
        advanceUntilIdle()

        // Scan 2 starts and immediately completes with its own results.
        detectResult = Result.success("scan-2")
        progressFlows["scan-2"] = MutableStateFlow(ScanProgress(phase = ScanPhase.COMPLETED))
        resultJsons["scan-2"] = itemsJson
        holder.startScan()
        advanceUntilIdle()

        assertEquals("scan-2", holder.state.value.scanId)
        assertEquals(listOf("a", "b", "c"), holder.state.value.rawScanResults.map { it.itemId })

        // The abandoned scan-1 collector must be cancelled: flipping its flow
        // to COMPLETED (with a different payload) must not overwrite scan 2.
        resultJsons["scan-1"] =
            """[{"itemId":"z","name":"ghost","type":"Movie","sizeText":"1 MB","dateText":"2020-01-01"}]"""
        oldProgress.value = ScanProgress(phase = ScanPhase.COMPLETED)
        advanceUntilIdle()

        assertEquals("scan-2", holder.state.value.scanId)
        assertEquals(ScanPhase.COMPLETED, holder.state.value.scanProgress.phase)
        assertEquals(listOf("a", "b", "c"), holder.state.value.rawScanResults.map { it.itemId })
        assertTrue(holder.state.value.rawScanResults.none { it.itemId == "z" })
    }

    // ── sorting ──

    @Test
    fun `sort options order the derived scan results`() = runTest(mainDispatcher) {
        val holder = completedScanHolder()
        holder.startScan()
        advanceUntilIdle()

        holder.updateSort(MediaSortOption.DEFAULT)
        assertEquals(listOf("a", "b", "c"), holder.state.value.scanResults.map { it.itemId })

        // Case-insensitive: "Apple" sorts before "banana".
        holder.updateSort(MediaSortOption.NAME_ASC)
        assertEquals(listOf("b", "a", "c"), holder.state.value.scanResults.map { it.itemId })

        holder.updateSort(MediaSortOption.NAME_DESC)
        assertEquals(listOf("c", "a", "b"), holder.state.value.scanResults.map { it.itemId })

        // sizeText parsed: 1.0 TB > 2.0 GB > 500 MB.
        holder.updateSort(MediaSortOption.SIZE_DESC)
        assertEquals(listOf("c", "a", "b"), holder.state.value.scanResults.map { it.itemId })

        holder.updateSort(MediaSortOption.SIZE_ASC)
        assertEquals(listOf("b", "a", "c"), holder.state.value.scanResults.map { it.itemId })

        holder.updateSort(MediaSortOption.TYPE)
        assertEquals(listOf("c", "b", "a"), holder.state.value.scanResults.map { it.itemId })

        holder.updateSort(MediaSortOption.DATE)
        assertEquals(listOf("b", "c", "a"), holder.state.value.scanResults.map { it.itemId })
    }

    // ── selection ──

    @Test
    fun `toggleItemSelection adds and removes ids`() = runTest(mainDispatcher) {
        val holder = completedScanHolder()
        holder.startScan()
        advanceUntilIdle()

        holder.toggleItemSelection("a")
        assertEquals(setOf("a"), holder.state.value.selectedItems)

        holder.toggleItemSelection("c")
        assertEquals(setOf("a", "c"), holder.state.value.selectedItems)

        holder.toggleItemSelection("a")
        assertEquals(setOf("c"), holder.state.value.selectedItems)
    }

    @Test
    fun `selectAll toggles between everything and nothing`() = runTest(mainDispatcher) {
        val holder = completedScanHolder()
        holder.startScan()
        advanceUntilIdle()

        holder.selectAll()
        assertEquals(setOf("a", "b", "c"), holder.state.value.selectedItems)

        holder.selectAll()
        assertTrue(holder.state.value.selectedItems.isEmpty())
    }

    // ── confirm / delete choreography ──

    @Test
    fun `confirmed delete removes selected items and drops them from the results`() = runTest(mainDispatcher) {
        val holder = completedScanHolder()
        holder.startScan()
        advanceUntilIdle()
        holder.toggleItemSelection("a")
        holder.toggleItemSelection("c")
        holder.showDeleteConfirmation()
        assertTrue(holder.state.value.showDeleteConfirmation)

        holder.deleteSelected()
        advanceUntilIdle()

        // Insertion order of the selection set is preserved.
        assertEquals(listOf(listOf("a", "c")), removedItemIds)
        assertTrue(holder.state.value.selectedItems.isEmpty())
        assertFalse(holder.state.value.showDeleteConfirmation)
        assertFalse(holder.state.value.isDeleting)
        // The deleted items drop out of the results; the rest stay.
        assertEquals(listOf("b"), holder.state.value.rawScanResults.map { it.itemId })
    }

    @Test
    fun `delete failure closes the dialog retains items and surfaces the error`() = runTest(mainDispatcher) {
        val holder = completedScanHolder()
        holder.startScan()
        advanceUntilIdle()
        holder.toggleItemSelection("a")
        holder.showDeleteConfirmation()
        removeResult = Result.failure(RuntimeException("server refused"))

        holder.deleteSelected()
        advanceUntilIdle()

        assertEquals("server refused", holder.state.value.error)
        assertFalse(holder.state.value.isDeleting)
        assertFalse(holder.state.value.showDeleteConfirmation)
        assertEquals(setOf("a"), holder.state.value.selectedItems)
        assertEquals(3, holder.state.value.rawScanResults.size)
    }

    @Test
    fun `dismissDeleteConfirmation closes the dialog without deleting`() = runTest(mainDispatcher) {
        val holder = completedScanHolder()
        holder.startScan()
        advanceUntilIdle()
        holder.toggleItemSelection("a")
        holder.showDeleteConfirmation()

        holder.dismissDeleteConfirmation()

        assertFalse(holder.state.value.showDeleteConfirmation)
        assertTrue(removedItemIds.isEmpty())
        assertEquals(setOf("a"), holder.state.value.selectedItems)
    }

    // ── permissions + audit ──

    @Test
    fun `delete permission mirrors the auth current user`() = runTest(mainDispatcher) {
        val holder = holder()
        advanceUntilIdle()
        // No signed-in user → the destructive action is locked.
        assertFalse(holder.state.value.canDeleteContent)

        currentUserFlow.value = UserInfo(
            id = "u-admin",
            name = "Alice",
            serverAddress = "http://server:8096",
            accessToken = "token",
            canDeleteContent = false,
        )
        advanceUntilIdle()
        assertFalse(holder.state.value.canDeleteContent)

        currentUserFlow.value = currentUserFlow.value!!.copy(canDeleteContent = true)
        advanceUntilIdle()
        assertTrue(holder.state.value.canDeleteContent)
    }

    @Test
    fun `audit history emissions fold into state`() = runTest(mainDispatcher) {
        val holder = holder()
        val audit = AuditLogEntry(id = "audit-1", itemCount = 2)

        auditFlow.tryEmit(listOf(audit))
        advanceUntilIdle()

        assertEquals(listOf(audit), holder.state.value.auditEntries)
    }
}
