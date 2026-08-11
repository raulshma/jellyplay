package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarkSeasonReactorTest {

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val messages = mutableListOf<DetailMessage>()
    private val rewriteCalls = mutableListOf<RewriteCall>()

    /** Captured (itemId, seasonId, transform) triple for applyRewrite invocations. */
    private class RewriteCall(
        val itemId: String,
        val seasonId: String,
        val transform: (List<MediaItem>) -> List<MediaItem>,
    )

    private fun reactor(
        scope: TestScope,
        itemId: String? = "s1",
        episodes: Map<String, List<MediaItem>> = emptyMap(),
    ): MarkSeasonReactor {
        messages.clear()
        rewriteCalls.clear()
        return MarkSeasonReactor(
            scope = scope,
            mediaRepository = mediaRepository,
            context = context,
            itemIdProvider = { itemId },
            episodesProvider = { episodes },
            applyRewrite = { iid, sid, transform ->
                rewriteCalls += RewriteCall(iid, sid, transform)
            },
            messageSink = { message -> messages += message },
        )
    }

    private fun episode(
        id: String,
        played: Boolean = false,
        positionTicks: Long? = null,
    ) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.EPISODE,
        isPlayed = played,
        playbackPositionTicks = positionTicks,
    )

    // region markSeasonPlayed
    @Test
    fun `markSeasonPlayed marks season then applies watched rewrite to every episode`() = runTest {
        val episodes = listOf(episode("e1", played = false), episode("e2", played = false))
        val reactor = reactor(this, episodes = mapOf("season1" to episodes))
        coEvery { mediaRepository.markPlayed("season1") } returns Result.success(Unit)

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.markPlayed("season1") }
        assertEquals(1, rewriteCalls.size)

        val call = rewriteCalls.single()
        assertEquals("s1", call.itemId)
        assertEquals("season1", call.seasonId)

        val rewritten = call.transform(episodes)
        assertTrue(rewritten.all { it.isPlayed })
        // The mark-played endpoint clears the resume position; transform mirrors it.
        assertEquals(0L, rewritten.first().playbackPositionTicks)
        assertEquals(0L, rewritten.last().playbackPositionTicks)
    }

    @Test
    fun `markSeasonPlayed with all episodes already played is a no-op`() = runTest {
        val episodes = listOf(episode("e1", played = true), episode("e2", played = true))
        val reactor = reactor(this, episodes = mapOf("season1" to episodes))

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
        assertTrue(rewriteCalls.isEmpty())
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `markSeasonPlayed repository failure emits message and skips rewrite`() = runTest {
        val episodes = listOf(episode("e1", played = false))
        val reactor = reactor(this, episodes = mapOf("season1" to episodes))
        coEvery { mediaRepository.markPlayed("season1") } returns Result.failure(RuntimeException("boom"))
        every { context.getString(R.string.detail_msg_couldnt_mark_played) } returns "couldn't mark"

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()

        coVerify { mediaRepository.markPlayed("season1") }
        assertTrue(rewriteCalls.isEmpty())

        assertEquals(1, messages.size)
        val message = messages.single()
        assertTrue(message is DetailMessage.Text)
        assertEquals("couldn't mark", (message as DetailMessage.Text).text)
    }

    @Test
    fun `markSeasonPlayed with null itemId is a no-op`() = runTest {
        val reactor = reactor(this, itemId = null, episodes = mapOf("season1" to listOf(episode("e1"))))

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
        assertTrue(rewriteCalls.isEmpty())
    }

    @Test
    fun `markSeasonPlayed with unknown seasonId is a no-op`() = runTest {
        val reactor = reactor(this, episodes = mapOf("season1" to listOf(episode("e1"))))

        reactor.markSeasonPlayed("nope")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
        assertTrue(rewriteCalls.isEmpty())
    }
    // endregion

    // region markSeasonUnplayed mirror
    @Test
    fun `markSeasonUnplayed marks season then applies unwatched rewrite`() = runTest {
        val episodes = listOf(
            episode("e1", played = true, positionTicks = 5_000_000_000L),
            episode("e2", played = true, positionTicks = 5_000_000_000L),
        )
        val reactor = reactor(this, episodes = mapOf("season1" to episodes))
        coEvery { mediaRepository.markUnplayed("season1") } returns Result.success(Unit)

        reactor.markSeasonUnplayed("season1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.markUnplayed("season1") }
        val rewritten = rewriteCalls.single().transform(episodes)
        assertTrue(rewritten.none { it.isPlayed })
        // Resume position cleared on unplayed (mirrors mark-played).
        assertEquals(0L, rewritten.first().playbackPositionTicks)
    }
    // endregion

    // region fire-and-forget single-item toggles
    @Test
    fun `markEpisodePlayed played delegates markPlayed`() = runTest {
        val reactor = reactor(this)
        coEvery { mediaRepository.markPlayed("ep1") } returns Result.success(Unit)

        reactor.markEpisodePlayed("ep1", played = true)
        advanceUntilIdle()

        coVerify { mediaRepository.markPlayed("ep1") }
        assertTrue(rewriteCalls.isEmpty())
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `markEpisodePlayed unplayed delegates markUnplayed`() = runTest {
        val reactor = reactor(this)
        coEvery { mediaRepository.markUnplayed("ep1") } returns Result.success(Unit)

        reactor.markEpisodePlayed("ep1", played = false)
        advanceUntilIdle()

        coVerify { mediaRepository.markUnplayed("ep1") }
    }
    // endregion
}
