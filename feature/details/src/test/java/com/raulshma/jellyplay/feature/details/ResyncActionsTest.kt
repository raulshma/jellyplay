package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.ResyncResult
import com.raulshma.jellyplay.core.model.ResyncStep
import com.raulshma.jellyplay.core.model.ResyncStepResult
import com.raulshma.jellyplay.core.data.util.DownloadResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResyncActionsTest {

    private val offlineSyncManager: OfflineSyncManager = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val offlineRepository: OfflineRepository = mockk(relaxed = true)
    private val downloadIntake: DownloadIntake = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private fun actions(
        scope: TestScope,
        itemId: String? = "item1",
    ): ResyncActions = ResyncActions(
        scope = scope,
        offlineSyncManager = offlineSyncManager,
        mediaRepository = mediaRepository,
        offlineRepository = offlineRepository,
        downloadIntake = downloadIntake,
        context = context,
        itemIdProvider = { itemId },
    )

    private fun ok(itemId: String = "item1") = ResyncResult(
        itemId,
        listOf(ResyncStepResult(itemId, ResyncStep.FETCH_DETAIL, success = true)),
        mediaFileChanged = false,
    )

    private fun failed(itemId: String = "item1", message: String = "boom") = ResyncResult(
        itemId,
        listOf(ResyncStepResult(itemId, ResyncStep.FETCH_DETAIL, success = false, message = message)),
        mediaFileChanged = false,
    )

    // region resync
    @Test
    fun `resync success maps to Done`() = runTest {
        coEvery { offlineSyncManager.resyncItem("item1") } returns ok()
        val actions = actions(this)

        actions.resync()
        advanceUntilIdle()

        val state = actions.state.value
        assertTrue("expected Done, was $state", state is ResyncUiState.Done)
        assertEquals(ok(), (state as ResyncUiState.Done).result)
    }

    @Test
    fun `resync failure maps to Error`() = runTest {
        coEvery { offlineSyncManager.resyncItem("item1") } returns failed(message = "boom")
        val actions = actions(this)

        actions.resync()
        advanceUntilIdle()

        val state = actions.state.value
        assertTrue("expected Error, was $state", state is ResyncUiState.Error)
        assertEquals("boom", (state as ResyncUiState.Error).message)
    }

    @Test
    fun `resync with null itemId is a no-op`() = runTest {
        val actions = actions(this, itemId = null)

        actions.resync()
        advanceUntilIdle()

        assertTrue(actions.state.value is ResyncUiState.Idle)
        coVerify(exactly = 0) { offlineSyncManager.resyncItem(any()) }
    }

    @Test
    fun `resync is a no-op while already Working`() = runTest {
        val gate = CompletableDeferred<ResyncResult>()
        coEvery { offlineSyncManager.resyncItem("item1") } coAnswers { gate.await() }
        val actions = actions(this)

        actions.resync()
        advanceUntilIdle()
        // Now in Working (resyncItem suspended on the gate).
        assertTrue(actions.state.value is ResyncUiState.Working)

        actions.resync()
        advanceUntilIdle()
        // Still Working — second call must not kick off a second fetch.
        coVerify(exactly = 1) { offlineSyncManager.resyncItem("item1") }

        gate.complete(ok())
        advanceUntilIdle()
    }
    // endregion

    // region checkForUpdates
    @Test
    fun `checkForUpdates delegates to sync manager with current itemId`() = runTest {
        val actions = actions(this)

        actions.checkForUpdates()
        advanceUntilIdle()

        coVerify { offlineSyncManager.checkForUpdates("item1") }
    }

    @Test
    fun `checkForUpdates with null itemId is a no-op`() = runTest {
        val actions = actions(this, itemId = null)

        actions.checkForUpdates()
        advanceUntilIdle()

        coVerify(exactly = 0) { offlineSyncManager.checkForUpdates(any()) }
    }
    // endregion

    // region clearResyncState
    @Test
    fun `clearResyncState resets Done to Idle`() = runTest {
        coEvery { offlineSyncManager.resyncItem("item1") } returns ok()
        val actions = actions(this)

        actions.resync()
        advanceUntilIdle()
        assertTrue(actions.state.value is ResyncUiState.Done)

        actions.clearResyncState()
        assertEquals(ResyncUiState.Idle, actions.state.value)
    }

    @Test
    fun `clearResyncState resets Error to Idle`() = runTest {
        coEvery { offlineSyncManager.resyncItem("item1") } returns failed()
        val actions = actions(this)

        actions.resync()
        advanceUntilIdle()
        assertTrue(actions.state.value is ResyncUiState.Error)

        actions.clearResyncState()
        assertEquals(ResyncUiState.Idle, actions.state.value)
    }

    @Test
    fun `clearResyncState is a no-op while Working`() = runTest {
        val gate = CompletableDeferred<ResyncResult>()
        coEvery { offlineSyncManager.resyncItem("item1") } coAnswers { gate.await() }
        val actions = actions(this)

        actions.resync()
        advanceUntilIdle()
        assertTrue(actions.state.value is ResyncUiState.Working)

        actions.clearResyncState()
        // Still Working — the guard fired.
        assertTrue(actions.state.value is ResyncUiState.Working)

        gate.complete(ok())
        advanceUntilIdle()
    }
    // endregion

    // region redownloadMedia
    @Test
    fun `redownloadMedia success maps to Done`() = runTest {
        val detail = MediaDetail(item = MediaItem(id = "item1", name = "Movie", mediaType = MediaType.MOVIE))
        coEvery { mediaRepository.getMediaDetail("item1") } returns Result.success(detail)
        coEvery { downloadIntake.start(detail) } returns DownloadResult(
            downloadItem = mockk(relaxed = true),
            error = null,
        )
        val actions = actions(this)

        actions.redownloadMedia()
        advanceUntilIdle()

        coVerify { mediaRepository.invalidateDetailCache("item1") }
        coVerify { offlineRepository.deleteOfflineItem("item1") }
        val state = actions.state.value
        assertTrue("expected Done, was $state", state is ResyncUiState.Done)
        assertEquals(false, (state as ResyncUiState.Done).result.mediaFileChanged)
    }

    @Test
    fun `redownloadMedia intake error maps to Error`() = runTest {
        val detail = MediaDetail(item = MediaItem(id = "item1", name = "Movie", mediaType = MediaType.MOVIE))
        coEvery { mediaRepository.getMediaDetail("item1") } returns Result.success(detail)
        coEvery { downloadIntake.start(detail) } returns DownloadResult(
            downloadItem = null,
            error = "disk full",
        )
        val actions = actions(this)

        actions.redownloadMedia()
        advanceUntilIdle()

        val state = actions.state.value
        assertTrue(state is ResyncUiState.Error)
        assertEquals("disk full", (state as ResyncUiState.Error).message)
    }

    @Test
    fun `redownloadMedia detail fetch failure maps to Error`() = runTest {
        coEvery { mediaRepository.getMediaDetail("item1") } returns Result.failure(RuntimeException("network"))
        val actions = actions(this)

        actions.redownloadMedia()
        advanceUntilIdle()

        val state = actions.state.value
        assertTrue(state is ResyncUiState.Error)
        assertEquals("Couldn't load latest details", (state as ResyncUiState.Error).message)
    }

    @Test
    fun `redownloadMedia is a no-op while Working`() = runTest {
        val gate = CompletableDeferred<MediaDetail>()
        coEvery { mediaRepository.getMediaDetail("item1") } coAnswers { Result.success(gate.await()) }
        val actions = actions(this)

        actions.redownloadMedia()
        advanceUntilIdle()
        assertTrue(actions.state.value is ResyncUiState.Working)

        actions.redownloadMedia()
        advanceUntilIdle()
        // Second call must not invalidate the cache again.
        coVerify(exactly = 1) { mediaRepository.invalidateDetailCache("item1") }

        gate.complete(MediaDetail(item = MediaItem(id = "item1", name = "Movie", mediaType = MediaType.MOVIE)))
        advanceUntilIdle()
    }
    // endregion
}
