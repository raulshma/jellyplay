package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.download.DownloadQueue
import com.raulshma.jellyplay.core.data.download.OfflineResync
import com.raulshma.jellyplay.core.data.repository.DownloadProgress
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineSyncUpdate
import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import com.raulshma.jellyplay.core.model.ResyncCategory
import com.raulshma.jellyplay.core.model.ResyncOptions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    // MainDispatcherRule (:core:testing) inlined — jvmTest has no access to
    // that module.
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var downloadRepository: DownloadQueue
    /** The gh-181 suggestion/policy seam (a separate mock from the queue view of the repository). */
    private lateinit var policyRepository: com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
    private lateinit var intake: com.raulshma.jellyplay.core.data.download.DownloadIntake
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var syncManager: OfflineResync
    private lateinit var downloadsStore: DownloadsStore
    private lateinit var viewModel: DownloadsViewModel

    /** Backing flow behind getAllDownloads so tests can push list changes. */
    private lateinit var downloadsFlow: MutableStateFlow<List<DownloadItem>>

    /** Backing flow behind getActiveDownloadProgress so tests can push ticks. */
    private lateinit var progressFlow: MutableStateFlow<Map<String, DownloadProgress>>

    /** Backing flow behind the downloads preference slice (persisted list state). */
    private lateinit var storeSliceFlow: MutableStateFlow<DownloadsSlice>

    /** Backing flow behind getOfflineEpisodes (group play-target played state). */
    private lateinit var offlineEpisodesFlow: MutableStateFlow<List<OfflineMediaItem>>

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        downloadRepository = mockk<DownloadQueue>(relaxed = true)
        policyRepository = mockk(relaxed = true)
        intake = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        syncManager = mockk<OfflineResync>(relaxed = true)
        downloadsFlow = MutableStateFlow(emptyList())
        progressFlow = MutableStateFlow(emptyMap())
        storeSliceFlow = MutableStateFlow(DownloadsSlice())
        offlineEpisodesFlow = MutableStateFlow(emptyList())
        every { downloadRepository.getAllDownloads() } returns downloadsFlow
        // The JVM queue seam is supported in production; the VM only emits
        // success messages when the seam says deletions are real.
        every { downloadRepository.isSupported } returns true
        every { downloadRepository.getActiveDownloadProgress() } returns progressFlow
        // forceResyncCandidates() reads the suspend snapshot; answer with the
        // flow's current value so pushItems drives both paths.
        coEvery { downloadRepository.getAllDownloadsSnapshot() } answers { downloadsFlow.value }
        // batchProgress is a non-suspend val → stub with `every`, not `coEvery`.
        every { syncManager.batchProgress } returns MutableStateFlow(ResyncBatchProgress())
        // Flow-returning repo reads are non-suspend → `every` as well.
        every { offlineRepository.getUpdatesCount() } returns MutableStateFlow(0)
        every { offlineRepository.getItemsWithUpdates() } returns MutableStateFlow(emptyList())
        every { offlineRepository.getOfflineEpisodes() } returns offlineEpisodesFlow
        downloadsStore = mockk(relaxed = true)
        every { downloadsStore.downloads } returns storeSliceFlow
        viewModel = DownloadsViewModel(downloadRepository, offlineRepository, syncManager, downloadsStore, policyRepository, intake)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Pushes [items] into the repository flow and settles the init collector. */
    private fun pushItems(items: List<DownloadItem>) {
        downloadsFlow.value = items
    }

    // ── init collection (queue list projection) ───────────────────────────

    @Test
    fun init_collects_downloads_and_sums_storage_bytes() = runTest(mainDispatcher) {
        // totalStorageBytes is a WhileSubscribed stateIn flow (collected only
        // by the screen's storage-header leaf) — warm it so the settled .value
        // is observable here.
        backgroundScope.launch { viewModel.totalStorageBytes.collect { /* warm */ } }
        pushItems(
            listOf(
                item("d1", downloadedBytes = 100, totalBytes = 200),
                item("d2", downloadedBytes = 50, totalBytes = 300),
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.downloads.size)
        assertEquals(150L, viewModel.totalStorageBytes.value)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    // ── progress/structure split (per-tick bytes are not structural) ──────

    @Test
    fun progress_tick_updates_live_progress_but_not_the_structural_list() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.progressById.collect { /* warm */ } }
        backgroundScope.launch { viewModel.totalStorageBytes.collect { /* warm */ } }
        val downloading = item("d1", downloadedBytes = 100, totalBytes = 200, status = DownloadStatus.DOWNLOADING)
        pushItems(listOf(downloading))
        advanceUntilIdle()
        assertEquals(100L, viewModel.uiState.value.downloads.single().downloadedBytes)

        // 2 s tick: same ids in order, same statuses, bytes moved — the list
        // emission is suppressed downstream of the change filter, and the
        // moving values surface through progressById instead.
        pushItems(listOf(downloading.copy(downloadedBytes = 180)))
        progressFlow.value = mapOf("d1" to DownloadProgress("d1", 180, 40))
        advanceUntilIdle()

        assertEquals(100L, viewModel.uiState.value.downloads.single().downloadedBytes)
        assertEquals(180L, viewModel.progressById.value["d1"]?.downloadedBytes)
        assertEquals(40L, viewModel.progressById.value["d1"]?.speedBytesPerSec)
        // The live total still follows the tick (structural bytes + live override).
        assertEquals(180L, viewModel.totalStorageBytes.value)
    }

    @Test
    fun status_change_re_emits_the_structural_list_and_clears_the_progress_entry() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.progressById.collect { /* warm */ } }
        backgroundScope.launch { viewModel.totalStorageBytes.collect { /* warm */ } }
        pushItems(listOf(item("d1", downloadedBytes = 200, totalBytes = 200, status = DownloadStatus.DOWNLOADING)))
        progressFlow.value = mapOf("d1" to DownloadProgress("d1", 150, 40))
        advanceUntilIdle()

        // Completion: the row leaves the in-flight projection and the status
        // flip re-emits the list with the row's final bytes.
        pushItems(listOf(item("d1", downloadedBytes = 200, totalBytes = 200, status = DownloadStatus.COMPLETED)))
        progressFlow.value = emptyMap()
        advanceUntilIdle()

        val row = viewModel.uiState.value.downloads.single()
        assertEquals(DownloadStatus.COMPLETED, row.status)
        assertEquals(200L, row.downloadedBytes)
        assertTrue(viewModel.progressById.value.isEmpty())
        assertEquals(200L, viewModel.totalStorageBytes.value)
    }

    @Test
    fun init_load_failure_sets_error_with_fallback_literal() = runTest(mainDispatcher) {
        every { downloadRepository.getAllDownloads() } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException(null as String?)
        }
        val failingViewModel = DownloadsViewModel(downloadRepository, offlineRepository, syncManager, downloadsStore, policyRepository, intake)
        advanceUntilIdle()

        // Throwable.message == null must hit the fallback literal.
        assertEquals("Failed to load downloads", failingViewModel.uiState.value.error)
        assertFalse(failingViewModel.uiState.value.isLoading)
    }

    // ── per-item orchestration (applyBulkAction at Item scope) ────────────

    @Test
    fun applyBulkAction_cancel_item_calls_repository_with_item_id() = runTest(mainDispatcher) {
        pushItems(listOf(item("d9", status = DownloadStatus.DOWNLOADING)))
        advanceUntilIdle()

        viewModel.applyBulkAction(DownloadBulkAction.CANCEL, DownloadActionScope.Item("d9"))
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.cancelDownload("d9") }
    }

    @Test
    fun applyBulkAction_pause_item_calls_pause_only() = runTest(mainDispatcher) {
        pushItems(listOf(item("d9", status = DownloadStatus.DOWNLOADING)))
        advanceUntilIdle()

        viewModel.applyBulkAction(DownloadBulkAction.PAUSE, DownloadActionScope.Item("d9"))
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.pauseDownload("d9") }
        coVerify(exactly = 0) { downloadRepository.enqueueDownload(any()) }
    }

    @Test
    fun applyBulkAction_resume_item_dispatches_the_composite_resume_verb() = runTest(mainDispatcher) {
        pushItems(listOf(item("d9", status = DownloadStatus.PAUSED)))
        advanceUntilIdle()

        viewModel.applyBulkAction(DownloadBulkAction.RESUME, DownloadActionScope.Item("d9"))
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.resumeDownload("d9") }
        // The enqueue follow-up lives INSIDE the queue's composite resume verb
        // (DownloadRepositoryImpl, shared with the notification receiver) — the
        // VM dispatches one verb and never enqueues itself.
        verify(exactly = 0) { downloadRepository.enqueueDownload(any()) }
    }

    @Test
    fun applyBulkAction_retry_item_dispatches_the_composite_retry_verb() = runTest(mainDispatcher) {
        pushItems(listOf(item("d9", status = DownloadStatus.FAILED)))
        advanceUntilIdle()

        viewModel.applyBulkAction(DownloadBulkAction.RETRY_FAILED, DownloadActionScope.Item("d9"))
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.retryDownload("d9") }
        verify(exactly = 0) { downloadRepository.enqueueDownload(any()) }
    }

    // ── Bus→flow seam ─────────────────────────────────────────────────────
    // The delete ack travels as a one-shot DownloadsUserMessage flow; the
    // screen resolves it for the shared message bus.

    @Test
    fun deleteDownload_calls_repository_and_emits_Deleted_message() = runTest(mainDispatcher) {
        viewModel.deleteDownload(item("d9", status = DownloadStatus.COMPLETED))
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.deleteDownload("d9") }
        assertEquals(DownloadsUserMessage.Deleted, viewModel.messages.first())
    }

    // ── Confirmation hosts ────────────────────────────────────────────────
    // DownloadsScreen drives its two delete dialogs off these hosts; the
    // synchronous settle arm is pinned by the tests below.

    @Test
    fun pendingDelete_host_show_holds_the_item_for_the_dialog() = runTest(mainDispatcher) {
        val target = item("d1", status = DownloadStatus.COMPLETED)

        viewModel.pendingDelete.show(target)

        assertEquals(target, viewModel.pendingDelete.item)
        assertTrue(viewModel.pendingDelete.isPending)
        // A second show replaces the pending target rather than stacking.
        val other = item("d2", status = DownloadStatus.COMPLETED)
        viewModel.pendingDelete.show(other)
        assertEquals(other, viewModel.pendingDelete.item)
    }

    @Test
    fun pendingDelete_host_confirm_returns_the_target_and_clears_synchronously() = runTest(mainDispatcher) {
        val target = item("d1", status = DownloadStatus.COMPLETED)
        viewModel.pendingDelete.show(target)

        val confirmed = viewModel.pendingDelete.confirm()

        assertEquals(target, confirmed)
        // The settle arm is INSIDE confirm: nothing stays armed for the next
        // open.
        assertFalse(viewModel.pendingDelete.isPending)
        assertNull(viewModel.pendingDelete.item)
        assertNull(viewModel.pendingDelete.confirm())
    }

    @Test
    fun pendingDelete_host_dismiss_clears_without_returning_a_target() = runTest(mainDispatcher) {
        viewModel.pendingDelete.show(item("d1", status = DownloadStatus.COMPLETED))

        viewModel.pendingDelete.dismiss(inFlight = false)

        assertFalse(viewModel.pendingDelete.isPending)
        assertNull(viewModel.pendingDelete.item)
    }

    @Test
    fun pendingBulkDelete_host_round_trips_the_unit_payload_with_the_same_settle() = runTest(mainDispatcher) {
        assertFalse(viewModel.pendingBulkDelete.isPending)

        viewModel.pendingBulkDelete.show(Unit)

        assertTrue(viewModel.pendingBulkDelete.isPending)
        assertEquals(Unit, viewModel.pendingBulkDelete.confirm())
        assertFalse(viewModel.pendingBulkDelete.isPending)
    }

    // ── Selection ─────────────────────────────────────────────────────────

    @Test
    fun toggleSelection_adds_then_removes_and_tracks_selection_mode() = runTest(mainDispatcher) {
        val a = item("a")
        val b = item("b")
        pushItems(listOf(a, b))
        advanceUntilIdle()

        viewModel.toggleSelection(a)
        assertEquals(setOf("a"), viewModel.uiState.value.selectedIds)
        assertTrue(viewModel.uiState.value.selectionMode)

        viewModel.toggleSelection(b)
        assertEquals(setOf("a", "b"), viewModel.uiState.value.selectedIds)

        viewModel.toggleSelection(a)
        viewModel.toggleSelection(b)
        assertEquals(emptySet(), viewModel.uiState.value.selectedIds)
        assertFalse(viewModel.uiState.value.selectionMode)
    }

    @Test
    fun selectAll_selects_every_listed_id_then_clearSelection_resets() = runTest(mainDispatcher) {
        pushItems(listOf(item("a"), item("b"), item("c")))
        advanceUntilIdle()

        viewModel.selectAll()
        assertEquals(setOf("a", "b", "c"), viewModel.uiState.value.selectedIds)
        assertTrue(viewModel.uiState.value.selectionMode)

        viewModel.clearSelection()
        assertEquals(emptySet(), viewModel.uiState.value.selectedIds)
        assertFalse(viewModel.uiState.value.selectionMode)
    }

    // ── Grouped rows (gh-175) ─────────────────────────────────────────────
    // rows hops to Dispatchers.Default and back (a real thread) like the
    // OfflineLibrary pipeline — the virtual scheduler can't see the returning
    // continuation, so assert via the same real-time poll idiom.

    private fun TestScope.awaitRows(expectedKeys: List<String>) {
        val deadline = System.currentTimeMillis() + 5_000
        // rows is null until the first (off-Main) derivation lands — orEmpty
        // keeps the poll honest during that window.
        while (viewModel.rows.value.orEmpty().map { it.key } != expectedKeys &&
            System.currentTimeMillis() < deadline
        ) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
        advanceUntilIdle()
        assertEquals(expectedKeys, viewModel.rows.value.orEmpty().map { it.key })
    }

    @Test
    fun rows_group_episodes_collapsed_with_flat_rows_interleaved_by_recency() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.rows.collect {} }
        pushItems(
            listOf(
                item("ep2", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 2),
                item("movie"),
                item("ep1", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 1),
            ),
        )
        advanceUntilIdle()

        // The group takes its newest episode's slot (idx 0), collapsed by default.
        awaitRows(listOf("series:s1", "download:movie"))
    }

    @Test
    fun expanding_a_series_discloses_episodes_in_playback_order() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.rows.collect {} }
        pushItems(
            listOf(
                item("ep8", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 8),
                item("ep2", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 2),
            ),
        )
        advanceUntilIdle()

        // Downloaded E08 before E02 — E02 still displays first.
        viewModel.toggleExpanded("series:s1")
        awaitRows(listOf("series:s1", "download:ep2", "download:ep8"))

        viewModel.toggleExpanded("series:s1")
        awaitRows(listOf("series:s1"))
    }

    @Test
    fun filter_and_sort_drive_the_rows_view() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.rows.collect {} }
        pushItems(
            listOf(
                item("z1", mediaType = MediaType.EPISODE, seriesId = "sZ", seriesName = "Zeta", seasonNumber = 1, episodeNumber = 1),
                item("a"),
            ),
        )
        advanceUntilIdle()

        viewModel.setFilter(DownloadListFilter.SERIES)
        awaitRows(listOf("series:sZ"))

        viewModel.setFilter(DownloadListFilter.MOVIES)
        awaitRows(listOf("download:a"))

        viewModel.setFilter(DownloadListFilter.ALL)
        viewModel.setSort(DownloadListSort.NAME)
        awaitRows(listOf("download:a", "series:sZ"))
    }

    @Test
    fun selectAll_respects_the_active_filter() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("ep", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 1),
                item("movie"),
            ),
        )
        advanceUntilIdle()

        viewModel.setFilter(DownloadListFilter.SERIES)
        viewModel.selectAll()
        assertEquals(setOf("ep"), viewModel.uiState.value.selectedIds)

        viewModel.setFilter(DownloadListFilter.MOVIES)
        viewModel.selectAll()
        assertEquals(setOf("movie"), viewModel.uiState.value.selectedIds)

        viewModel.setFilter(DownloadListFilter.ALL)
        viewModel.selectAll()
        assertEquals(setOf("ep", "movie"), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun toggleGroupSelection_selects_then_clears_the_whole_group() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("ep1", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 1),
                item("ep2", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 2),
            ),
        )
        advanceUntilIdle()

        viewModel.toggleGroupSelection(listOf("ep1", "ep2"))
        assertEquals(setOf("ep1", "ep2"), viewModel.uiState.value.selectedIds)
        assertTrue(viewModel.uiState.value.selectionMode)

        viewModel.toggleGroupSelection(listOf("ep1", "ep2"))
        assertEquals(emptySet(), viewModel.uiState.value.selectedIds)
    }

    // ── Persisted list state + expand-all (gh-175 follow-up) ──────────────

    @Test
    fun setFilter_and_setSort_write_through_to_the_store() = runTest(mainDispatcher) {
        viewModel.setFilter(DownloadListFilter.SERIES)
        viewModel.setSort(DownloadListSort.NAME)
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadsStore.setDownloadsListFilter("SERIES") }
        coVerify(exactly = 1) { downloadsStore.setDownloadsListSort("NAME") }
        assertEquals(DownloadListFilter.SERIES, viewModel.filter.value)
        assertEquals(DownloadListSort.NAME, viewModel.sort.value)
    }

    @Test
    fun persisted_slice_seeds_the_initial_filter_and_sort() = runTest(mainDispatcher) {
        storeSliceFlow.value = DownloadsSlice(downloadsListFilter = "SERIES", downloadsListSort = "SIZE")
        val seeded = DownloadsViewModel(downloadRepository, offlineRepository, syncManager, downloadsStore, policyRepository, intake)
        advanceUntilIdle()

        assertEquals(DownloadListFilter.SERIES, seeded.filter.value)
        assertEquals(DownloadListSort.SIZE, seeded.sort.value)
    }

    @Test
    fun rows_carry_series_poster_with_episode_still_fallback() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.rows.collect {} }
        coEvery { offlineRepository.getOfflineItem("s1") } answers {
            OfflineMediaItem(
                id = "s1",
                name = "Show",
                mediaType = MediaType.SERIES,
                posterPath = "/art/s1.jpg",
                blurHashPrimary = "poster-bh",
            )
        }
        coEvery { offlineRepository.getOfflineItem("s2") } answers { null }
        pushItems(
            listOf(
                item("ep1", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 1)
                    .copy(imageUrl = "still-ep1", imageBlurHash = "still-bh1", status = DownloadStatus.COMPLETED),
                item("ep2", mediaType = MediaType.EPISODE, seriesId = "s2", seriesName = "Other", seasonNumber = 1, episodeNumber = 1)
                    .copy(imageUrl = "still-ep2", status = DownloadStatus.COMPLETED),
            ),
        )
        awaitRows(listOf("series:s1", "series:s2"))

        val headers = viewModel.rows.value.orEmpty().filterIsInstance<DownloadListRow.SeriesHeader>()
        assertEquals("/art/s1.jpg", headers.first { it.key == "series:s1" }.imageUrl)
        assertEquals("poster-bh", headers.first { it.key == "series:s1" }.imageBlurHash)
        // No saved poster → the first completed episode's still, blurhash included.
        assertEquals("still-ep2", headers.first { it.key == "series:s2" }.imageUrl)
        assertNull(headers.first { it.key == "series:s2" }.imageBlurHash)
    }

    // ── Selection hint persistence ─────────────────────────────────────────

    @Test
    fun entering_selection_marks_the_hint_seen_and_persists_it() = runTest(mainDispatcher) {
        pushItems(listOf(item("d1", status = DownloadStatus.DOWNLOADING)))
        advanceUntilIdle()
        assertFalse(viewModel.selectionHintSeen.value)

        viewModel.toggleSelection(item("d1", status = DownloadStatus.DOWNLOADING))
        advanceUntilIdle()

        assertTrue(viewModel.selectionHintSeen.value)
        coVerify(exactly = 1) { downloadsStore.setSelectionHintSeen(true) }
    }

    @Test
    fun persisted_hint_seen_seeds_the_vm_and_is_not_rewritten() = runTest(mainDispatcher) {
        storeSliceFlow.value = DownloadsSlice(selectionHintSeen = true)
        val seeded = DownloadsViewModel(downloadRepository, offlineRepository, syncManager, downloadsStore, policyRepository, intake)
        advanceUntilIdle()
        assertTrue(seeded.selectionHintSeen.value)

        // Already seen: further selection activity must not rewrite the pref.
        seeded.toggleSelection(item("d1", status = DownloadStatus.DOWNLOADING))
        advanceUntilIdle()
        coVerify(exactly = 0) { downloadsStore.setSelectionHintSeen(any()) }
    }

    @Test
    fun expandAll_expands_every_group_and_collapseAll_clears() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.rows.collect {} }
        pushItems(
            listOf(
                item("s1e1", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show A", seasonNumber = 1, episodeNumber = 1),
                item("s2e1", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show A", seasonNumber = 2, episodeNumber = 1),
                item("sBe1", mediaType = MediaType.EPISODE, seriesId = "sB", seriesName = "Show B", seasonNumber = 1, episodeNumber = 1),
            ),
        )
        advanceUntilIdle()

        // The rows derivation hops to Dispatchers.Default and back, so
        // advanceUntilIdle cannot guarantee the post-push derivation has
        // landed — and expandAll early-returns on stale rows (it reads the
        // rendered headers). Await the collapsed baseline first; otherwise
        // this races (observed 1-in-5 at HEAD under parallel test load).
        awaitRows(listOf("series:s1", "series:sB"))

        // Everything expanded: the multi-season series discloses its season
        // headers AND their episodes; the single-season one lists directly.
        viewModel.expandAll()
        awaitRows(
            listOf(
                "series:s1",
                "series:s1/season/number:1",
                "download:s1e1",
                "series:s1/season/number:2",
                "download:s2e1",
                "series:sB",
                "download:sBe1",
            ),
        )
        val expanded = viewModel.expandedKeys.value
        assertTrue("series:s1" in expanded)
        assertTrue("series:sB" in expanded)
        assertTrue(expanded.any { it.startsWith("series:s1/season/") })

        viewModel.collapseAll()
        awaitRows(listOf("series:s1", "series:sB"))
        assertEquals(emptySet(), viewModel.expandedKeys.value)
    }

    @Test
    fun resolveGroupPlayTarget_skips_watched_episodes_and_falls_back_to_first() = runTest(mainDispatcher) {
        // Only COMPLETED rows are playable (the row-level Play gate), so the
        // fixture uses completed episodes throughout.
        pushItems(
            listOf(
                item("e1", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 1, status = DownloadStatus.COMPLETED),
                item("e2", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 2, status = DownloadStatus.COMPLETED),
                item("e3", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 3, status = DownloadStatus.COMPLETED),
            ),
        )
        // e1 watched outright; e2 at 96% of its runtime (≥ threshold → watched).
        offlineEpisodesFlow.value = listOf(
            episodeRow("e1", isPlayed = true),
            episodeRow("e2", positionTicks = 96, runTimeTicks = 100),
        )
        advanceUntilIdle()

        val target = viewModel.resolveGroupPlayTarget(listOf("e1", "e2", "e3"))
        assertEquals("e3", target?.id)

        // Everything watched → the first episode in playback order.
        offlineEpisodesFlow.value = listOf(
            episodeRow("e1", isPlayed = true),
            episodeRow("e2", positionTicks = 96, runTimeTicks = 100),
            episodeRow("e3", isPlayed = true),
        )
        val fallback = viewModel.resolveGroupPlayTarget(listOf("e1", "e2", "e3"))
        assertEquals("e1", fallback?.id)
    }

    @Test
    fun resolveGroupPlayTarget_is_null_when_no_episode_is_completed() = runTest(mainDispatcher) {
        // A group with nothing finished has no playable target — the header
        // Play click is a no-op rather than attempting an offline play of a
        // file that isn't on disk.
        pushItems(
            listOf(
                item("e1", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 1, status = DownloadStatus.QUEUED),
                item("e2", mediaType = MediaType.EPISODE, seriesId = "s1", seriesName = "Show", seasonNumber = 1, episodeNumber = 2, status = DownloadStatus.DOWNLOADING),
            ),
        )
        advanceUntilIdle()

        assertNull(viewModel.resolveGroupPlayTarget(listOf("e1", "e2")))
    }

    @Test
    fun maxStorageBytes_maps_gb_cap_to_bytes_and_null_means_unlimited() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.maxStorageBytes.collect {} }

        storeSliceFlow.value = DownloadsSlice(maxDownloadStorageGb = 0)
        advanceUntilIdle()
        assertNull(viewModel.maxStorageBytes.value)

        storeSliceFlow.value = DownloadsSlice(maxDownloadStorageGb = 10)
        advanceUntilIdle()
        assertEquals(10L * 1024 * 1024 * 1024, viewModel.maxStorageBytes.value)
    }

    // ── Bulk actions (status-filtered targets) ────────────────────────────

    @Test
    fun applyBulkAction_delete_selected_deletes_each_target_clears_selection_and_emits_Deleted() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("a", status = DownloadStatus.COMPLETED),
                item("b", status = DownloadStatus.DOWNLOADING),
            ),
        )
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.applyBulkAction(DownloadBulkAction.DELETE, DownloadActionScope.Selected)
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.deleteDownload("a") }
        coVerify(exactly = 1) { downloadRepository.deleteDownload("b") }
        assertEquals(emptySet(), viewModel.uiState.value.selectedIds)
        assertEquals(DownloadsUserMessage.Deleted, viewModel.messages.first())
    }

    @Test
    fun applyBulkAction_delete_selected_with_no_targets_skips_repository_and_message() = runTest(mainDispatcher) {
        viewModel.applyBulkAction(DownloadBulkAction.DELETE, DownloadActionScope.Selected)
        advanceUntilIdle()

        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
        val message = kotlinx.coroutines.withTimeoutOrNull(50) { viewModel.messages.first() }
        assertNull(message)
    }

    @Test
    fun applyBulkAction_pause_selected_pauses_only_selected_downloading_items() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("dl", status = DownloadStatus.DOWNLOADING),
                item("pa", status = DownloadStatus.PAUSED),
                item("ok", status = DownloadStatus.COMPLETED),
            ),
        )
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.applyBulkAction(DownloadBulkAction.PAUSE, DownloadActionScope.Selected)
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.pauseDownload("dl") }
        coVerify(exactly = 0) { downloadRepository.pauseDownload("pa") }
        coVerify(exactly = 0) { downloadRepository.pauseDownload("ok") }
    }

    @Test
    fun applyBulkAction_resume_selected_resumes_only_selected_paused_items() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("pa", status = DownloadStatus.PAUSED),
                item("dl", status = DownloadStatus.DOWNLOADING),
            ),
        )
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.applyBulkAction(DownloadBulkAction.RESUME, DownloadActionScope.Selected)
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.resumeDownload("pa") }
        coVerify(exactly = 0) { downloadRepository.resumeDownload("dl") }
        // enqueue rides inside the composite verb — the VM never enqueues.
        verify(exactly = 0) { downloadRepository.enqueueDownload(any()) }
    }

    @Test
    fun applyBulkAction_cancel_selected_cancels_active_but_not_completed_or_cancelled_items() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("pen", status = DownloadStatus.PENDING),
                item("que", status = DownloadStatus.QUEUED),
                item("dl", status = DownloadStatus.DOWNLOADING),
                item("pa", status = DownloadStatus.PAUSED),
                item("done", status = DownloadStatus.COMPLETED),
                item("cxl", status = DownloadStatus.CANCELLED),
            ),
        )
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.applyBulkAction(DownloadBulkAction.CANCEL, DownloadActionScope.Selected)
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.cancelDownload("pen") }
        coVerify(exactly = 1) { downloadRepository.cancelDownload("que") }
        coVerify(exactly = 1) { downloadRepository.cancelDownload("dl") }
        coVerify(exactly = 1) { downloadRepository.cancelDownload("pa") }
        coVerify(exactly = 0) { downloadRepository.cancelDownload("done") }
        coVerify(exactly = 0) { downloadRepository.cancelDownload("cxl") }
    }

    // ── Global actions (applyBulkAction at All scope) ─────────────────────

    @Test
    fun applyBulkAction_pause_all_pauses_every_downloading_item_without_selection() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("dl1", status = DownloadStatus.DOWNLOADING),
                item("dl2", status = DownloadStatus.DOWNLOADING),
                item("pa", status = DownloadStatus.PAUSED),
            ),
        )
        advanceUntilIdle()

        viewModel.applyBulkAction(DownloadBulkAction.PAUSE, DownloadActionScope.All)
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.pauseDownload("dl1") }
        coVerify(exactly = 1) { downloadRepository.pauseDownload("dl2") }
        coVerify(exactly = 0) { downloadRepository.pauseDownload("pa") }
    }

    @Test
    fun applyBulkAction_retry_failed_retries_every_failed_item() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("f1", status = DownloadStatus.FAILED),
                item("f2", status = DownloadStatus.FAILED),
                item("ok", status = DownloadStatus.COMPLETED),
            ),
        )
        advanceUntilIdle()

        viewModel.applyBulkAction(DownloadBulkAction.RETRY_FAILED, DownloadActionScope.All)
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.retryDownload("f1") }
        coVerify(exactly = 1) { downloadRepository.retryDownload("f2") }
        coVerify(exactly = 0) { downloadRepository.retryDownload("ok") }
        // enqueue rides inside the composite verb — the VM never enqueues.
        verify(exactly = 0) { downloadRepository.enqueueDownload(any()) }
    }

    @Test
    fun moveToFront_and_lowerPriority_use_relative_priorities_from_list() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("a", priority = 3),
                item("b", priority = 7),
                item("c", priority = 5),
            ),
        )
        advanceUntilIdle()

        viewModel.moveToFront(item("a", priority = 3))
        advanceUntilIdle()
        coVerify(exactly = 1) { downloadRepository.setDownloadPriority("a", 8) }

        viewModel.lowerPriority(item("c", priority = 5))
        advanceUntilIdle()
        coVerify(exactly = 1) { downloadRepository.setDownloadPriority("c", 2) }
    }

    // ── Freshness check / resync (the DownloadsResyncActions seam) ────────
    // The VM keeps full construction here — these pin the accessor wiring
    // (VM → holder → sync manager), not the holder in isolation.

    @Test
    fun checkAllForUpdates_checks_batch_with_downloaded_ids_and_resets_checking() = runTest(mainDispatcher) {
        coEvery { offlineRepository.getDownloadedItemIds() } returns listOf("i1", "i2")

        viewModel.resyncActions.checkAllForUpdates()
        advanceUntilIdle()

        coVerify(exactly = 1) { syncManager.checkForUpdatesBatch(listOf("i1", "i2")) }
        assertFalse(viewModel.resyncActions.checking.value)
    }

    @Test
    fun checkAllForUpdates_while_checking_is_a_noop() = runTest(mainDispatcher) {
        // Suspend the first batch check until the test releases it, so the
        // second call observes checking == true.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { offlineRepository.getDownloadedItemIds() } returns listOf("i1")
        coEvery { syncManager.checkForUpdatesBatch(any()) } coAnswers { gate.await(); emptyList() }

        viewModel.resyncActions.checkAllForUpdates()
        advanceUntilIdle()
        assertTrue(viewModel.resyncActions.checking.value)

        viewModel.resyncActions.checkAllForUpdates()
        advanceUntilIdle()
        coVerify(exactly = 1) { syncManager.checkForUpdatesBatch(any()) }

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.resyncActions.checking.value)
    }

    @Test
    fun resyncOne_delegates_single_id_batch() = runTest(mainDispatcher) {
        viewModel.resyncActions.resyncOne("i1")
        verify(exactly = 1) { syncManager.resyncBatch(listOf("i1")) }
    }

    @Test
    fun resyncAll_resolves_flagged_ids_from_repository() = runTest(mainDispatcher) {
        every { offlineRepository.getItemsWithUpdates() } returns kotlinx.coroutines.flow.flowOf(
            listOf(update("u1"), update("u2")),
        )

        viewModel.resyncActions.resyncAll()
        advanceUntilIdle()

        verify(exactly = 1) { syncManager.resyncBatch(listOf("u1", "u2")) }
    }

    @Test
    fun resyncAll_with_explicit_ids_delegates_and_skips_empty() = runTest(mainDispatcher) {
        viewModel.resyncActions.resyncAll(listOf("u1", "u2"))
        verify(exactly = 1) { syncManager.resyncBatch(listOf("u1", "u2")) }

        viewModel.resyncActions.resyncAll(emptyList())
        verify(exactly = 1) { syncManager.resyncBatch(any()) }
    }

    @Test
    fun forceResync_delegates_with_options_and_skips_empty_selections() = runTest(mainDispatcher) {
        val options = ResyncOptions(categories = setOf(ResyncCategory.METADATA))

        viewModel.resyncActions.forceResync(listOf("i1"), options)
        verify(exactly = 1) { syncManager.resyncBatch(listOf("i1"), options) }

        viewModel.resyncActions.forceResync(emptyList(), options)
        viewModel.resyncActions.forceResync(listOf("i1"), ResyncOptions(categories = emptySet()))
        verify(exactly = 1) { syncManager.resyncBatch(any(), any()) }
    }

    @Test
    fun clearResyncProgress_delegates_to_sync_manager() = runTest(mainDispatcher) {
        viewModel.resyncActions.clearResyncProgress()
        verify(exactly = 1) { syncManager.clearBatchProgress() }
    }

    @Test
    fun forceResyncCandidates_filters_completed_and_dedupes_by_media_item_id() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("dl-ep1", mediaItemId = "ep1", status = DownloadStatus.COMPLETED, seriesName = "Show", seasonNumber = 1, episodeNumber = 2),
                item("dl-ep2", mediaItemId = "ep1", status = DownloadStatus.COMPLETED, seriesName = "Show", seasonNumber = 1, episodeNumber = 2),
                item("dl-movie", mediaItemId = "m1", status = DownloadStatus.COMPLETED),
                item("dl-active", mediaItemId = "ep9", status = DownloadStatus.DOWNLOADING),
            ),
        )
        advanceUntilIdle()

        val candidates = viewModel.resyncActions.forceResyncCandidates()
        assertEquals(listOf("ep1", "m1"), candidates.map { it.id })
        // Episode context is carried so the picker renders the SxxExx line.
        assertEquals("Show", candidates.first().seriesName)
        assertEquals(1, candidates.first().seasonNumber)
        assertEquals(2, candidates.first().episodeNumber)
        assertEquals(MediaType.MOVIE, candidates[1].mediaType)
    }

    private fun item(
        id: String,
        mediaItemId: String = id,
        status: DownloadStatus = DownloadStatus.DOWNLOADING,
        priority: Int = 0,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L,
        seriesName: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        mediaType: MediaType = MediaType.MOVIE,
        seriesId: String? = null,
    ) = DownloadItem(
        id = id,
        mediaItemId = mediaItemId,
        name = "Item $id",
        mediaType = mediaType,
        downloadPath = "/data/$id",
        downloadUrl = "https://server/$id",
        totalSizeBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = status,
        priority = priority,
        seriesId = seriesId,
        seriesName = seriesName,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
    )

    private fun update(id: String) = OfflineSyncUpdate(id = id, name = "Update $id", mediaFileChanged = false)

    /** An offline episode row for the group play-target's played-state read. */
    private fun episodeRow(
        mediaItemId: String,
        isPlayed: Boolean = false,
        positionTicks: Long? = null,
        runTimeTicks: Long? = null,
    ) = OfflineMediaItem(
        id = mediaItemId,
        name = "Episode $mediaItemId",
        mediaType = MediaType.EPISODE,
        isPlayed = isPlayed,
        playbackPositionTicks = positionTicks,
        runTimeTicks = runTimeTicks,
    )
}
