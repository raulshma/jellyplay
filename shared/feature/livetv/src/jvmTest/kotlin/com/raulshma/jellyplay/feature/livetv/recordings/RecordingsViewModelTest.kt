package com.raulshma.jellyplay.feature.livetv.recordings

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvRecording
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: RecordingsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        coEvery { mediaRepository.getRecordings(any(), any()) } returns Result.success(emptyList())
        viewModel = RecordingsViewModel(mediaRepository, imageUrlProvider)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_fetches_recordings_with_limit() = runTest(mainDispatcher) {
        advanceUntilIdle()
        coVerify { mediaRepository.getRecordings(limit = 24, isInProgress = null) }
    }

    @Test
    fun load_populates_state_from_results() = runTest(mainDispatcher) {
        val recordings = listOf(LiveTvRecording(id = "r1", name = "Recording 1"))
        coEvery { mediaRepository.getRecordings(any(), any()) } returns Result.success(recordings)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(recordings, viewModel.uiState.value.recordings)
    }

    @Test
    fun load_surfaces_error_when_getRecordings_fails() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getRecordings(any(), any()) } returns Result.failure(RuntimeException("boom"))
        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("boom") == true)
    }

    // ── Delete confirmation flow ─────────────────────────────────────────────

    private fun recording(id: String, seriesTimerId: String? = null) =
        LiveTvRecording(id = id, name = "Recording $id", seriesTimerId = seriesTimerId)

    @Test
    fun showDeleteDialog_and_dismissDeleteDialog_round_trip() = runTest(mainDispatcher) {
        val rec = recording("r1")
        viewModel.showDeleteDialog(rec)
        assertEquals(rec, viewModel.uiState.value.pendingDelete)

        viewModel.dismissDeleteDialog()

        assertEquals(null, viewModel.uiState.value.pendingDelete)
    }

    @Test
    fun deleteRecording_deletes_then_reloads() = runTest(mainDispatcher) {
        coEvery { mediaRepository.deleteRecording("r1") } returns Result.success(Unit)
        viewModel.showDeleteDialog(recording("r1"))

        viewModel.deleteRecording()
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.deleteRecording("r1") }
        assertEquals(null, viewModel.uiState.value.pendingDelete)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertEquals(null, viewModel.uiState.value.error)
        // init load + post-delete reload.
        coVerify(exactly = 2) { mediaRepository.getRecordings(any(), any()) }
    }

    @Test
    fun deleteRecording_cancels_the_series_timer_first_when_one_is_attached() = runTest(mainDispatcher) {
        coEvery { mediaRepository.cancelSeriesTimer("st-1") } returns Result.success(Unit)
        coEvery { mediaRepository.deleteRecording("r1") } returns Result.success(Unit)
        viewModel.showDeleteDialog(recording("r1", seriesTimerId = "st-1"))

        viewModel.deleteRecording()
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.cancelSeriesTimer("st-1") }
        coVerify(exactly = 1) { mediaRepository.deleteRecording("r1") }
    }

    @Test
    fun deleteRecording_failure_surfaces_the_error_and_keeps_the_dialog_open() = runTest(mainDispatcher) {
        coEvery { mediaRepository.deleteRecording("r1") } returns Result.failure(RuntimeException("locked"))
        val rec = recording("r1")
        viewModel.showDeleteDialog(rec)

        viewModel.deleteRecording()
        advanceUntilIdle()

        assertEquals("locked", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertEquals(rec, viewModel.uiState.value.pendingDelete)
    }

    @Test
    fun deleteRecording_without_a_pending_confirmation_is_a_no_op() = runTest(mainDispatcher) {
        viewModel.deleteRecording()
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.deleteRecording(any()) }
    }

    @Test
    fun dismissDeleteDialog_is_blocked_while_a_delete_is_in_flight() = runTest(mainDispatcher) {
        // Park the delete inside the repository so isDeleting is observable.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { mediaRepository.deleteRecording("r1") } coAnswers { gate.await(); Result.success(Unit) }
        viewModel.showDeleteDialog(recording("r1"))

        viewModel.deleteRecording()
        testScheduler.runCurrent() // runs up to the parked repository call
        assertTrue(viewModel.uiState.value.isDeleting)

        viewModel.dismissDeleteDialog()
        assertEquals(recording("r1"), viewModel.uiState.value.pendingDelete)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.pendingDelete)
    }

    @Test
    fun getImageUrl_without_a_tag_returns_empty_and_with_one_delegates() {
        every { imageUrlProvider.getImageUrl("r1") } returns "http://img/r1"

        assertEquals("", viewModel.getImageUrl("r1", null))
        assertEquals("http://img/r1", viewModel.getImageUrl("r1", "tag"))
        verify(exactly = 1) { imageUrlProvider.getImageUrl("r1") }
    }
}
