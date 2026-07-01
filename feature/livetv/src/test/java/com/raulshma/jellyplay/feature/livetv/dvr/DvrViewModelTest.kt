package com.raulshma.jellyplay.feature.livetv.dvr

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DvrViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var preferencesStore: UserPreferencesStore

    private lateinit var viewModel: DvrViewModel

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        preferencesStore = mockk(relaxed = true)
        every { preferencesStore.preferences } returns MutableStateFlow(UserPreferences())

        coEvery { mediaRepository.getTimers() } returns Result.success(emptyList())
        coEvery { mediaRepository.getSeriesTimers() } returns Result.success(emptyList())

        viewModel = DvrViewModel(mediaRepository, preferencesStore)
    }

    @Test
    fun `cancelTimer calls single-timer cancel endpoint`() = runTest {
        coEvery { mediaRepository.cancelTimer(any()) } returns Result.success(Unit)
        coEvery { mediaRepository.getTimers() } returns Result.success(emptyList())
        coEvery { mediaRepository.getSeriesTimers() } returns Result.success(emptyList())

        viewModel.cancelTimer("timer-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.cancelTimer("timer-1") }
    }

    @Test
    fun `cancelSeriesTimer calls series-timer cancel endpoint not single timer`() = runTest {
        // Regression guard: cancelSeriesTimer previously called the *single-timer*
        // cancel endpoint (mediaRepository.cancelTimer). It must now route to the
        // dedicated cancelSeriesTimer method.
        coEvery { mediaRepository.cancelSeriesTimer(any()) } returns Result.success(Unit)
        coEvery { mediaRepository.getTimers() } returns Result.success(emptyList())
        coEvery { mediaRepository.getSeriesTimers() } returns Result.success(emptyList())

        viewModel.cancelSeriesTimer("series-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.cancelSeriesTimer("series-1") }
        coVerify(exactly = 0) { mediaRepository.cancelTimer(any()) }
    }

    @Test
    fun `showTimerDetail exposes Timer detail state and dismiss clears it`() {
        val timer = DvrTimer(id = "t1", programId = "p1", programName = "News", channelId = "c1", channelName = "CNN")
        viewModel.showTimerDetail(timer)
        assertNotNull(viewModel.detail)
        assertTrue(viewModel.detail is DvrDetailState.Timer)

        viewModel.dismissDetail()
        assertNull(viewModel.detail)
    }

    @Test
    fun `showSeriesTimerDetail exposes SeriesTimer detail state`() {
        val timer = DvrSeriesTimer(id = "s1", name = "Daily Show")
        viewModel.showSeriesTimerDetail(timer)
        assertNotNull(viewModel.detail)
        assertTrue(viewModel.detail is DvrDetailState.SeriesTimer)
    }

    @Test
    fun `createTimer delegates to repository with padding`() = runTest {
        coEvery { mediaRepository.createTimer(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { mediaRepository.getTimers() } returns Result.success(emptyList())
        coEvery { mediaRepository.getSeriesTimers() } returns Result.success(emptyList())

        viewModel.createTimer("prog-1", "chan-1", "2026-07-01T10:00:00Z", "2026-07-01T11:00:00Z")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mediaRepository.createTimer("prog-1", "chan-1", any(), any())
        }
    }
}
