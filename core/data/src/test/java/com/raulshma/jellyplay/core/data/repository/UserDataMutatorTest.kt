package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [UserDataMutatorImpl] — the module that owns the user-data mutation
 * protocol (serialize → write → optimistic rewrite), previously hand-assembled
 * per ViewModel. Same style as [PlayedStateSyncImplTest]: plain JUnit + mockk,
 * with a recording container standing in for a screen's exposed state.
 *
 * The write-path fan-out (outbox, offline mirror) stays pinned by
 * [PlayedStateSyncImplTest]; the repository's self-invalidation by
 * MediaRepositoryCacheInvalidationTest. This suite pins the protocol layer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserDataMutatorTest {

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val mediaDetailProvider: MediaDetailProvider = mockk(relaxed = true)

    private lateinit var mutator: UserDataMutatorImpl

    @Before
    fun setup() {
        // kotlin Lazy (dagger.Lazy at the time this suite was written — the
        // Phase X cluster flip converted the ctor params) — mockk cannot mock
        // kotlin.Lazy directly, so the real `lazy { }` wraps the mock; the
        // impl only reads .value.
        val lazyRepo: Lazy<MediaRepository> = lazy { mediaRepository }
        val lazyProvider: Lazy<MediaDetailProvider> = lazy { mediaDetailProvider }
        mutator = UserDataMutatorImpl(lazyRepo, lazyProvider)
    }

    /**
     * In-memory container mirroring a screen's exposed list, with the same
     * id-guard real adapters use (non-matching items pass through untouched,
     * preserving referential equality for Compose skipping).
     */
    private class ListContainer(items: List<MediaItem>) : UserDataContainer {
        var current: List<MediaItem> = items.toList()
            private set

        override fun rewrite(itemId: String, patch: (MediaItem) -> MediaItem) {
            current = current.map { if (it.id == itemId) patch(it) else it }
        }
    }

    private fun item(
        id: String,
        isPlayed: Boolean = false,
        isFavorite: Boolean = false,
        positionTicks: Long? = null,
    ) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.MOVIE,
        isPlayed = isPlayed,
        isFavorite = isFavorite,
        playbackPositionTicks = positionTicks,
    )

    // ── setPlayed: optimistic flip ─────────────────────────────────────

    @Test
    fun `optimistic setPlayed rewrites matching item and leaves others referentially equal`() = runTest {
        val target = item("a", positionTicks = 5_000_000_000L)
        val untouched1 = item("b")
        val untouched2 = item("c", isPlayed = true)
        val container = ListContainer(listOf(target, untouched1, untouched2))
        coEvery { mediaRepository.markPlayed("a") } returns Result.success(Unit)

        val result = mutator.setPlayed(
            itemId = "a",
            played = true,
            mode = UserDataMutator.FlipMode.Optimistic,
            containers = listOf(container),
        )

        assertEquals(Result.success(AppliedMutation(itemId = "a", played = true)), result)
        val flipped = container.current.first { it.id == "a" }
        assertTrue(flipped.isPlayed)
        // Untouched items keep their exact instances (no spurious copies).
        assertSame(untouched1, container.current[1])
        assertSame(untouched2, container.current[2])
    }

    @Test
    fun `optimistic setPlayed zeroes resume position in both directions`() = runTest {
        // played → true: an in-progress item loses its resume bar.
        val playedContainer = ListContainer(listOf(item("a", positionTicks = 5_000_000_000L)))
        coEvery { mediaRepository.markPlayed("a") } returns Result.success(Unit)
        mutator.setPlayed("a", played = true, mode = UserDataMutator.FlipMode.Optimistic, containers = listOf(playedContainer))
        assertEquals(0L, playedContainer.current.single().playbackPositionTicks)

        // played → false: the server also clears resume on manual unwatch;
        // the local mirror must not retain a phantom in-progress bar.
        val unplayedContainer = ListContainer(listOf(item("b", isPlayed = true, positionTicks = 5_000_000_000L)))
        coEvery { mediaRepository.markUnplayed("b") } returns Result.success(Unit)
        mutator.setPlayed("b", played = false, mode = UserDataMutator.FlipMode.Optimistic, containers = listOf(unplayedContainer))
        val flipped = unplayedContainer.current.single()
        assertFalse(flipped.isPlayed)
        assertEquals(0L, flipped.playbackPositionTicks)
    }

    @Test
    fun `silent setPlayed calls repository but rewrites nothing`() = runTest {
        val container = ListContainer(listOf(item("a")))
        coEvery { mediaRepository.markPlayed("a") } returns Result.success(Unit)

        val result = mutator.setPlayed(itemId = "a", played = true, containers = listOf(container))

        assertEquals(Result.success(AppliedMutation(itemId = "a", played = true)), result)
        coVerify(exactly = 1) { mediaRepository.markPlayed("a") }
        // The grid contract: no container flip, no provider session touch.
        assertFalse(container.current.single().isPlayed)
        coVerify(exactly = 0) { mediaDetailProvider.applyOptimisticItemState(any(), any(), any()) }
        coVerify(exactly = 0) { mediaDetailProvider.applyOptimisticSeasonRewrite(any(), any(), any()) }
    }

    @Test
    fun `write failure returns the failure with no flip and no provider call`() = runTest {
        val container = ListContainer(listOf(item("a", positionTicks = 5_000_000_000L)))
        val failure = RuntimeException("catastrophic I/O")
        coEvery { mediaRepository.markPlayed("a") } returns Result.failure(failure)

        val result = mutator.setPlayed(
            itemId = "a",
            played = true,
            mode = UserDataMutator.FlipMode.Optimistic,
            containers = listOf(container),
            seriesId = "series-1",
        )

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
        // Nothing flipped, nothing invalidated — the caller decides messaging.
        assertFalse(container.current.single().isPlayed)
        coVerify(exactly = 0) { mediaDetailProvider.applyOptimisticItemState(any(), any(), any()) }
        coVerify(exactly = 0) { mediaDetailProvider.invalidate(any()) }
    }

    @Test
    fun `optimistic setPlayed rewrites provider session and invalidates series only when supplied`() = runTest {
        coEvery { mediaRepository.markPlayed("ep-1") } returns Result.success(Unit)

        mutator.setPlayed(
            itemId = "ep-1",
            played = true,
            mode = UserDataMutator.FlipMode.Optimistic,
            seriesId = "series-1",
        )

        coVerify(exactly = 1) {
            mediaDetailProvider.applyOptimisticItemState("ep-1", isFavorite = null, isPlayed = true)
        }
        coVerify(exactly = 1) { mediaDetailProvider.invalidate("series-1") }

        // Without a series scope (movie / non-series item) no catalogue drop.
        coEvery { mediaRepository.markPlayed("movie-1") } returns Result.success(Unit)
        mutator.setPlayed(itemId = "movie-1", played = true, mode = UserDataMutator.FlipMode.Optimistic)
        coVerify(exactly = 0) { mediaDetailProvider.invalidate("movie-1") }
        coVerify(exactly = 1) { mediaDetailProvider.invalidate(any()) }
    }

    // ── setFavorite: target resolution ─────────────────────────────────

    @Test
    fun `setFavorite resolves the target from the repository Result and preserves resume`() = runTest {
        val container = ListContainer(listOf(item("a", isFavorite = false, positionTicks = 5_000_000_000L)))
        coEvery { mediaRepository.toggleFavorite("a") } returns Result.success(true)

        val result = mutator.setFavorite(
            itemId = "a",
            mode = UserDataMutator.FlipMode.Optimistic,
            containers = listOf(container),
        )

        val applied = result.getOrThrow()
        assertEquals(true, applied.favorite)
        assertNull(applied.played)
        val flipped = container.current.single()
        assertTrue(flipped.isFavorite)
        // Favorite-only mutations never touch the resume point.
        assertEquals(5_000_000_000L, flipped.playbackPositionTicks)
        coVerify(exactly = 1) {
            mediaDetailProvider.applyOptimisticItemState("a", isFavorite = true, isPlayed = null)
        }
    }

    @Test
    fun `silent setFavorite still returns the resolved target`() = runTest {
        // The audio player's scalar case: no containers, no provider session,
        // but the resolved favorite target must come back for its uiState flip.
        coEvery { mediaRepository.toggleFavorite("track-1") } returns Result.success(false)

        val result = mutator.setFavorite(itemId = "track-1")

        assertEquals(false, result.getOrThrow().favorite)
        coVerify(exactly = 0) { mediaDetailProvider.applyOptimisticItemState(any(), any(), any()) }
    }

    @Test
    fun `setFavorite failure returns the failure with no flip`() = runTest {
        val container = ListContainer(listOf(item("a")))
        coEvery { mediaRepository.toggleFavorite("a") } returns Result.failure(RuntimeException("boom"))

        val result = mutator.setFavorite(
            itemId = "a",
            mode = UserDataMutator.FlipMode.Optimistic,
            containers = listOf(container),
        )

        assertTrue(result.isFailure)
        assertFalse(container.current.single().isFavorite)
        coVerify(exactly = 0) { mediaDetailProvider.applyOptimisticItemState(any(), any(), any()) }
    }

    // ── setSeasonPlayed: season semantics ──────────────────────────────

    @Test
    fun `setSeasonPlayed wraps the season-aware repo call and rewrites the provider season`() = runTest {
        coEvery { mediaRepository.markSeasonPlayed("season-1", "series-1") } returns Result.success(Unit)
        var capturedTransform: ((List<MediaItem>) -> List<MediaItem>)? = null
        coEvery { mediaDetailProvider.applyOptimisticSeasonRewrite("series-1", "season-1", any()) } coAnswers {
            capturedTransform = thirdArg()
        }

        val result = mutator.setSeasonPlayed(seriesId = "series-1", seasonId = "season-1", played = true)

        assertEquals(AppliedMutation(itemId = "season-1", played = true), result.getOrThrow())
        coVerify(exactly = 1) { mediaRepository.markSeasonPlayed("season-1", "series-1") }
        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
        // The rewrite flips every episode of the season and clears resume in
        // both directions (server-side cascade mirrored locally).
        val episodes = listOf(
            item("e1", positionTicks = 5_000_000_000L),
            item("e2", isPlayed = false),
        )
        val rewritten = capturedTransform!!.invoke(episodes)
        assertTrue(rewritten.all { it.isPlayed })
        assertTrue(rewritten.all { it.playbackPositionTicks == 0L })
    }

    @Test
    fun `setSeasonPlayed unplayed mirror clears resume too`() = runTest {
        coEvery { mediaRepository.markSeasonUnplayed("season-1", "series-1") } returns Result.success(Unit)
        var capturedTransform: ((List<MediaItem>) -> List<MediaItem>)? = null
        coEvery { mediaDetailProvider.applyOptimisticSeasonRewrite("series-1", "season-1", any()) } coAnswers {
            capturedTransform = thirdArg()
        }

        mutator.setSeasonPlayed(seriesId = "series-1", seasonId = "season-1", played = false)

        coVerify(exactly = 1) { mediaRepository.markSeasonUnplayed("season-1", "series-1") }
        val rewritten = capturedTransform!!.invoke(listOf(item("e1", isPlayed = true, positionTicks = 5_000_000_000L)))
        assertFalse(rewritten.single().isPlayed)
        assertEquals(0L, rewritten.single().playbackPositionTicks)
    }

    @Test
    fun `setSeasonPlayed failure returns the failure and skips the provider rewrite`() = runTest {
        coEvery { mediaRepository.markSeasonPlayed("season-1", "series-1") } returns Result.failure(RuntimeException("boom"))

        val result = mutator.setSeasonPlayed(seriesId = "series-1", seasonId = "season-1", played = true)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { mediaDetailProvider.applyOptimisticSeasonRewrite(any(), any(), any()) }
    }

    // ── serialization: the module mutex ────────────────────────────────

    @Test
    fun `concurrent setPlayed calls execute in launch order`() = runTest {
        val order = mutableListOf<String>()
        val releaseFirst = CompletableDeferred<Unit>()
        coEvery { mediaRepository.markPlayed("a") } coAnswers {
            releaseFirst.await()
            order += "a"
            Result.success(Unit)
        }
        coEvery { mediaRepository.markPlayed("b") } coAnswers {
            order += "b"
            Result.success(Unit)
        }

        val first = async { mutator.setPlayed("a", played = true) }
        runCurrent()
        val second = async { mutator.setPlayed("b", played = true) }
        // While the first write is mid-flight, the second must be blocked on
        // the module mutex — its repository call must NOT have run.
        advanceUntilIdle()
        assertTrue(order.isEmpty())

        releaseFirst.complete(Unit)
        advanceUntilIdle()
        first.await()
        second.await()

        assertEquals(listOf("a", "b"), order)
    }
}
