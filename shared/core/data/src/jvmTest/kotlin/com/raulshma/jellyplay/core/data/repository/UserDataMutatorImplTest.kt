package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [UserDataMutatorImpl] — the user-data mutation protocol over
 * mocked [MediaRepository] (the write) and [MediaDetailProvider] (the
 * optimistic rewrite). Pins:
 *  - the write seam per direction (markPlayed/markUnplayed, toggleFavorite,
 *    markSeasonPlayed);
 *  - the optimistic pass ordering: containers → provider item rewrite →
 *    residual series-catalogue drop (episodes only, never other items);
 *  - [UserDataMutator.FlipMode.Silent] suppresses the whole optimistic pass;
 *  - a failed write performs no optimistic patching;
 *  - the resume-clearing rule in [AppliedMutation.patch] (both played
 *    directions clear the resume point; a favorite flip preserves it);
 *  - app-wide serialization of mutations on the module mutex.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserDataMutatorImplTest {

    private val mediaRepository: MediaRepository = mockk()
    private val mediaDetailProvider: MediaDetailProvider = mockk(relaxed = true)

    private lateinit var mutator: UserDataMutatorImpl

    private val episode = MediaItem(
        id = "ep-1",
        name = "Episode 1",
        mediaType = MediaType.EPISODE,
        playbackPositionTicks = 500L,
        isPlayed = false,
        isFavorite = false,
    )

    @BeforeTest
    fun setup() {
        mutator = UserDataMutatorImpl(
            mediaRepository = lazy { mediaRepository },
            mediaDetailProvider = lazy { mediaDetailProvider },
        )
    }

    // ── setPlayed ───────────────────────────────────────────────────────

    @Test
    fun `setPlayed writes via markPlayed and patches containers optimistically`() = runTest {
        coEvery { mediaRepository.markPlayed("ep-1") } returns Result.success(Unit)
        var patched: MediaItem? = null
        val container = UserDataContainer { _, patch -> patched = patch(episode) }

        val result = mutator.setPlayed(
            itemId = "ep-1",
            played = true,
            mode = UserDataMutator.FlipMode.Optimistic,
            containers = listOf(container),
        )

        assertTrue(result.isSuccess)
        assertEquals("ep-1", result.getOrThrow().itemId)
        assertTrue(result.getOrThrow().played == true)
        coVerify(exactly = 1) { mediaRepository.markPlayed("ep-1") }
        // The optimistic patch mirrors the server's resume-point clearing.
        assertTrue(patched!!.isPlayed)
        assertEquals(0L, patched!!.playbackPositionTicks)
        coVerify(exactly = 1) { mediaDetailProvider.applyOptimisticItemState(itemId = "ep-1", isFavorite = null, isPlayed = true) }
        verify(exactly = 0) { mediaDetailProvider.invalidate(any()) }
    }

    @Test
    fun `setPlayed unplayed routes to markUnplayed and also clears the resume point`() = runTest {
        coEvery { mediaRepository.markUnplayed("ep-1") } returns Result.success(Unit)
        var patched: MediaItem? = null
        val container = UserDataContainer { _, patch -> patched = patch(episode) }

        val result = mutator.setPlayed("ep-1", played = false, containers = listOf(container), mode = UserDataMutator.FlipMode.Optimistic)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mediaRepository.markUnplayed("ep-1") }
        assertFalse(patched!!.isPlayed)
        assertEquals(0L, patched!!.playbackPositionTicks)
    }

    @Test
    fun `an episode mutation with a seriesId drops the series catalogue last`() = runTest {
        coEvery { mediaRepository.markPlayed("ep-1") } returns Result.success(Unit)
        val callOrder = mutableListOf<String>()
        coEvery { mediaDetailProvider.applyOptimisticItemState(any(), any(), any()) } answers { callOrder.add("item-state"); Unit }
        every { mediaDetailProvider.invalidate("series-1") } answers { callOrder.add("invalidate"); Unit }

        mutator.setPlayed("ep-1", played = true, containers = emptyList(), mode = UserDataMutator.FlipMode.Optimistic, seriesId = "series-1")

        assertEquals(listOf("item-state", "invalidate"), callOrder)
    }

    @Test
    fun `a failed write performs no optimistic patching`() = runTest {
        coEvery { mediaRepository.markPlayed("ep-1") } returns Result.failure(IllegalStateException("offline"))
        var patched = false
        val container = UserDataContainer { _, _ -> patched = true }

        val result = mutator.setPlayed("ep-1", played = true, containers = listOf(container), mode = UserDataMutator.FlipMode.Optimistic)

        assertTrue(result.isFailure)
        assertFalse(patched)
        coVerify(exactly = 0) { mediaDetailProvider.applyOptimisticItemState(any(), any(), any()) }
        verify(exactly = 0) { mediaDetailProvider.invalidate(any()) }
    }

    @Test
    fun `Silent mode writes without any optimistic pass`() = runTest {
        coEvery { mediaRepository.markPlayed("ep-1") } returns Result.success(Unit)
        var patched = false
        val container = UserDataContainer { _, _ -> patched = true }

        val result = mutator.setPlayed("ep-1", played = true, containers = listOf(container), mode = UserDataMutator.FlipMode.Silent)

        assertTrue(result.isSuccess)
        assertFalse(patched)
        coVerify(exactly = 0) { mediaDetailProvider.applyOptimisticItemState(any(), any(), any()) }
    }

    // ── setFavorite ─────────────────────────────────────────────────────

    @Test
    fun `setFavorite carries the resolved target and preserves the resume point`() = runTest {
        coEvery { mediaRepository.toggleFavorite("ep-1") } returns Result.success(true)
        var patched: MediaItem? = null
        val container = UserDataContainer { _, patch -> patched = patch(episode) }

        val result = mutator.setFavorite("ep-1", containers = listOf(container), mode = UserDataMutator.FlipMode.Optimistic)

        val applied = result.getOrThrow()
        assertTrue(applied.favorite == true)
        assertNull(applied.played)
        assertTrue(patched!!.isFavorite)
        // A favorite flip is not a played flip — the resume point survives.
        assertEquals(500L, patched!!.playbackPositionTicks)
        assertFalse(patched!!.isPlayed)
        coVerify(exactly = 1) { mediaDetailProvider.applyOptimisticItemState(itemId = "ep-1", isFavorite = true, isPlayed = null) }
    }

    @Test
    fun `a failed favorite write performs no optimistic pass`() = runTest {
        coEvery { mediaRepository.toggleFavorite("ep-1") } returns Result.failure(IllegalStateException("offline"))
        var patched = false
        val container = UserDataContainer { _, _ -> patched = true }

        assertTrue(mutator.setFavorite("ep-1", containers = listOf(container)).isFailure)
        assertFalse(patched)
        coVerify(exactly = 0) { mediaDetailProvider.applyOptimisticItemState(any(), any(), any()) }
    }

    // ── setSeasonPlayed ─────────────────────────────────────────────────

    @Test
    fun `setSeasonPlayed rewrites every episode of the season optimistically`() = runTest {
        coEvery { mediaRepository.markSeasonPlayed("season-1", "series-1") } returns Result.success(Unit)
        val transform = slot<(List<MediaItem>) -> List<MediaItem>>()
        coEvery { mediaDetailProvider.applyOptimisticSeasonRewrite("series-1", "season-1", capture(transform)) } returns Unit

        val result = mutator.setSeasonPlayed(seriesId = "series-1", seasonId = "season-1", played = true)

        assertTrue(result.isSuccess)
        assertEquals("season-1", result.getOrThrow().itemId)
        val rewritten = transform.captured(
            listOf(
                episode,
                episode.copy(id = "ep-2", playbackPositionTicks = 900L),
            )
        )
        assertTrue(rewritten.all { it.isPlayed && it.playbackPositionTicks == 0L })
        // The season path rewrites through the provider only — no residual drop.
        verify(exactly = 0) { mediaDetailProvider.invalidate(any()) }
        coVerify(exactly = 0) { mediaDetailProvider.applyOptimisticItemState(any(), any(), any()) }
    }

    @Test
    fun `setSeasonPlayed unplayed routes to markSeasonUnplayed`() = runTest {
        coEvery { mediaRepository.markSeasonUnplayed("season-1", "series-1") } returns Result.success(Unit)
        coEvery { mediaDetailProvider.applyOptimisticSeasonRewrite(any(), any(), any()) } returns Unit

        val result = mutator.setSeasonPlayed("series-1", "season-1", played = false)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mediaRepository.markSeasonUnplayed("season-1", "series-1") }
    }

    @Test
    fun `a failed season write skips the provider rewrite`() = runTest {
        coEvery { mediaRepository.markSeasonPlayed("season-1", "series-1") } returns Result.failure(IllegalStateException("offline"))

        assertTrue(mutator.setSeasonPlayed("series-1", "season-1", played = true).isFailure)
        coVerify(exactly = 0) { mediaDetailProvider.applyOptimisticSeasonRewrite(any(), any(), any()) }
    }

    // ── Serialization ───────────────────────────────────────────────────

    @Test
    fun `concurrent mutations serialize in launch order`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { mediaRepository.markPlayed("a") } coAnswers { gate.await(); Result.success(Unit) }
        coEvery { mediaRepository.markUnplayed("b") } returns Result.success(Unit)
        val completions = mutableListOf<String>()

        val first = async {
            mutator.setPlayed("a", played = true).getOrThrow()
            completions.add("first")
        }
        val second = async {
            mutator.setPlayed("b", played = false).getOrThrow()
            completions.add("second")
        }
        runCurrent()

        // The first write is still parked on its gate; the mutex must keep the
        // second mutation from slipping past it.
        assertTrue(completions.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()
        first.await()
        second.await()

        assertEquals(listOf("first", "second"), completions)
    }
}
