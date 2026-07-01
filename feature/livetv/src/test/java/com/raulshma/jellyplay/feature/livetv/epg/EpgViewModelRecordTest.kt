package com.raulshma.jellyplay.feature.livetv.epg

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for the EPG record-confirmation dialog state machine introduced to
 * fix the no-op "Record" button (previously it navigated to an empty DVR list).
 *
 * These cover the synchronous state transitions driven from [EpgViewModel].
 * The constructor launches auto-refresh/now-tick loops on the VM scope, so we
 * construct the VM outside of `runTest` and assert immediate state changes —
 * mirroring how [EpgViewModel.requestRecord] is invoked synchronously from the
 * UI layer on a tap.
 */
class EpgViewModelRecordTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var viewModel: EpgViewModel

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        coEvery { mediaRepository.getLiveTvGuide(any(), any(), any()) } returns Result.success(
            EpgGuide(channels = emptyList(), programs = emptyList())
        )
        viewModel = EpgViewModel(mediaRepository)
    }

    @Test
    fun `requestRecord opens Confirm dialog state for the program`() {
        viewModel.requestRecord(sampleProgram())

        val state = viewModel.recordDialog
        assertNotNull(state)
        assertTrue("Expected Confirm state, got $state", state is RecordDialogState.Confirm)
    }

    @Test
    fun `requestRecord carries the selected program identity into the Confirm state`() {
        val program = sampleProgram(name = "Late Show", id = "prog-42")
        viewModel.requestRecord(program)

        val state = viewModel.recordDialog as RecordDialogState.Confirm
        assertTrue(state.program.id == "prog-42")
        assertTrue(state.program.name == "Late Show")
    }

    @Test
    fun `dismissRecordDialog clears the dialog state`() {
        viewModel.requestRecord(sampleProgram())
        assertNotNull(viewModel.recordDialog)

        viewModel.dismissRecordDialog()

        assertNull(viewModel.recordDialog)
    }

    private fun sampleProgram(name: String = "Evening News", id: String = "prog-1") = LiveTvProgram(
        id = id,
        name = name,
        channelId = "chan-1",
        startDate = "2026-07-01T19:00:00Z",
        endDate = "2026-07-01T19:30:00Z",
    )
}
