package com.raulshma.jellyplay.feature.livetv.epg

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.LiveTvProgram
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the EPG record-confirmation dialog state machine introduced to
 * fix the no-op "Record" button (previously it navigated to an empty DVR list).
 *
 * These cover the synchronous state transitions driven from [EpgViewModel].
 * The constructor launches auto-refresh/now-tick loops on the VM scope, so we
 * construct the VM outside of `runTest` and assert immediate state changes —
 * mirroring how [EpgViewModel.requestRecord] is invoked synchronously from the
 * UI layer on a tap. (The MainDispatcherRule is inlined as
 * setMain(StandardTestDispatcher) — jvmTest has no :core:testing access, and
 * the queued dispatcher keeps the ctor loops inert.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpgViewModelRecordTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var viewModel: EpgViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        coEvery { mediaRepository.getLiveTvGuide(any(), any(), any()) } returns Result.success(
            EpgGuide(channels = emptyList(), programs = emptyList())
        )
        viewModel = EpgViewModel(mediaRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun requestRecord_opens_Confirm_dialog_state_for_the_program() {
        viewModel.requestRecord(sampleProgram())

        val state = viewModel.recordDialog
        assertNotNull(state)
        assertTrue(state is RecordDialogState.Confirm, "Expected Confirm state, got $state")
    }

    @Test
    fun requestRecord_carries_the_selected_program_identity_into_the_Confirm_state() {
        val program = sampleProgram(name = "Late Show", id = "prog-42")
        viewModel.requestRecord(program)

        val state = viewModel.recordDialog as RecordDialogState.Confirm
        assertTrue(state.program.id == "prog-42")
        assertTrue(state.program.name == "Late Show")
    }

    @Test
    fun dismissRecordDialog_clears_the_dialog_state() {
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
