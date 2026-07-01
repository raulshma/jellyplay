package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Regression tests: [SyncPlayController] must rethrow [CancellationException] so
 * that scope/job cancellation (e.g. user leaving SyncPlay, logging out) propagates correctly
 * through the in-flight `apiClient` call instead of being silently swallowed.
 */
class SyncPlayControllerCancellationTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private lateinit var controller: SyncPlayController

    @Before
    fun setup() {
        controller = SyncPlayController(apiClient)
    }

    @Test
    fun `pause rethrows CancellationException`() {
        coEvery { apiClient.syncPlayPause() } throws CancellationException("scope cancelled")

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.pause() }
        }
    }

    @Test
    fun `unpause rethrows CancellationException`() {
        coEvery { apiClient.syncPlayUnpause() } throws CancellationException("scope cancelled")

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.unpause() }
        }
    }

    @Test
    fun `seek rethrows CancellationException`() {
        coEvery { apiClient.syncPlaySeek(any()) } throws CancellationException("scope cancelled")

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.seek(1L) }
        }
    }

    @Test
    fun `generic Exception is logged and swallowed (does not propagate)`() = runTest {
        coEvery { apiClient.syncPlayPause() } throws RuntimeException("transient")

        // Must NOT throw — controller is a fire-and-forget wrapper for non-fatal errors.
        controller.pause()
    }

    @Test
    fun `queue method delegates to apiClient with correct default mode`() = runTest {
        coEvery { apiClient.syncPlayQueue(any(), any()) } returns Result.success(Unit)

        controller.queue(listOf("item-1", "item-2"))

        coVerify { apiClient.syncPlayQueue(match { it == listOf("item-1", "item-2") }, match { it == "Queue" }) }
    }

    @Test
    fun `setRepeatMode delegates with provided mode`() = runTest {
        val mode = com.raulshma.jellyplay.core.model.SyncPlayRepeatMode.REPEAT_ONE
        coEvery { apiClient.syncPlaySetRepeatMode(any()) } returns Result.success(Unit)

        controller.setRepeatMode(mode)

        coVerify { apiClient.syncPlaySetRepeatMode(match { it == mode }) }
    }

    @Test
    fun `stop swallows non-fatal exceptions without throwing`() = runTest {
        coEvery { apiClient.syncPlayStop() } throws IllegalStateException("bug")
        coEvery { apiClient.syncPlayNextItem(any()) } throws IllegalArgumentException("bad arg")
        coEvery { apiClient.syncPlayPreviousItem(any()) } throws IllegalStateException("bug")

        controller.stop()
        controller.nextItem("id")
        controller.previousItem("id")
    }
}
