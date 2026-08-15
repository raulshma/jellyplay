package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [MarkSeasonReactor] against [FakeUserDataMutator]. The mutation
 * protocol (write, serialization, provider season rewrite, series-catalogue
 * drop) moved into [com.raulshma.jellyplay.core.data.repository.UserDataMutator]
 * and is pinned by its own suite; what stays pinned here is the reactor's
 * screen-stateful part — the idempotence guard (last successful target), the
 * episodes-on-screen early-exit, and the failure message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarkSeasonReactorTest {

    private val context: Context = mockk(relaxed = true)
    private val mutator = FakeUserDataMutator()

    private val messages = mutableListOf<DetailMessage>()

    private fun reactor(
        scope: TestScope,
        itemId: String? = "s1",
        episodes: Map<String, List<MediaItem>> = emptyMap(),
        seriesId: String? = null,
    ): MarkSeasonReactor {
        messages.clear()
        mutator.seasonCalls.clear()
        return MarkSeasonReactor(
            scope = scope,
            userDataMutator = mutator,
            context = context,
            itemIdProvider = { itemId },
            episodesProvider = { episodes },
            messageSink = { message -> messages += message },
            seriesIdProvider = { seriesId },
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
    fun `markSeasonPlayed delegates to setSeasonPlayed with the screen's series scope`() = runTest {
        val episodes = listOf(episode("e1", played = false), episode("e2", played = false))
        val reactor = reactor(this, episodes = mapOf("season1" to episodes), seriesId = "s1")

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()

        assertEquals(listOf(Triple("s1", "season1", true)), mutator.seasonCalls)
    }

    @Test
    fun `markSeasonPlayed with null seriesId falls back to the screen item id`() = runTest {
        // Seasons only render on a series screen, where itemId IS the series id;
        // the fallback is the defensive mid-navigation path.
        val episodes = listOf(episode("e1", played = false))
        val reactor = reactor(this, episodes = mapOf("season1" to episodes), seriesId = null)

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()

        assertEquals(listOf(Triple("s1", "season1", true)), mutator.seasonCalls)
    }

    @Test
    fun `markSeasonPlayed with all episodes already played is a no-op`() = runTest {
        val episodes = listOf(episode("e1", played = true), episode("e2", played = true))
        val reactor = reactor(this, episodes = mapOf("season1" to episodes))

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()

        assertTrue(mutator.seasonCalls.isEmpty())
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `markSeasonPlayed failure emits message and does not skip a retry`() = runTest {
        val episodes = listOf(episode("e1", played = false))
        val reactor = reactor(this, episodes = mapOf("season1" to episodes))
        mutator.seasonResult = { _, _, _ -> Result.failure(RuntimeException("boom")) }
        every { context.getString(R.string.detail_msg_couldnt_mark_played) } returns "couldn't mark"

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()

        assertEquals(1, mutator.seasonCalls.size)
        assertEquals(1, messages.size)
        val message = messages.single()
        assertTrue(message is DetailMessage.Text)
        assertEquals("couldn't mark", (message as DetailMessage.Text).text)

        // Nothing was recorded (success-only): a retry of the same direction
        // must not be swallowed as "already in target state".
        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()
        assertEquals(2, mutator.seasonCalls.size)
    }

    @Test
    fun `markSeasonPlayed with null itemId is a no-op`() = runTest {
        val reactor = reactor(this, itemId = null, episodes = mapOf("season1" to listOf(episode("e1"))))

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()

        assertTrue(mutator.seasonCalls.isEmpty())
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `markSeasonPlayed with unknown seasonId is a no-op`() = runTest {
        val reactor = reactor(this, episodes = mapOf("season1" to listOf(episode("e1"))))

        reactor.markSeasonPlayed("nope")
        advanceUntilIdle()

        assertTrue(mutator.seasonCalls.isEmpty())
        assertTrue(messages.isEmpty())
    }
    // endregion

    // region markSeasonUnplayed mirror
    @Test
    fun `markSeasonUnplayed delegates with played false`() = runTest {
        val episodes = listOf(
            episode("e1", played = true, positionTicks = 5_000_000_000L),
            episode("e2", played = true),
        )
        val reactor = reactor(this, episodes = mapOf("season1" to episodes), seriesId = "s1")

        reactor.markSeasonUnplayed("season1")
        advanceUntilIdle()

        assertEquals(listOf(Triple("s1", "season1", false)), mutator.seasonCalls)
    }

    @Test
    fun `rapid inverse season toggles both apply in order`() = runTest {
        val currentEpisodes = listOf(episode("e1", played = false))
        val reactor = MarkSeasonReactor(
            scope = this,
            userDataMutator = mutator,
            context = context,
            itemIdProvider = { "s1" },
            episodesProvider = { mapOf("season1" to currentEpisodes) },
            messageSink = { message -> messages += message },
        )

        reactor.markSeasonPlayed("season1")
        reactor.markSeasonUnplayed("season1")
        advanceUntilIdle()

        // The guard must not swallow the inverse toggle (the UI snapshot is
        // still pre-mutation) — both mutations land, in order.
        assertEquals(
            listOf(Triple("s1", "season1", true), Triple("s1", "season1", false)),
            mutator.seasonCalls,
        )
    }

    @Test
    fun `rapid same-direction taps are deduped by the success-recorded guard`() = runTest {
        val currentEpisodes = listOf(episode("e1", played = false))
        val reactor = MarkSeasonReactor(
            scope = this,
            userDataMutator = mutator,
            context = context,
            itemIdProvider = { "s1" },
            episodesProvider = { mapOf("season1" to currentEpisodes) },
            messageSink = { message -> messages += message },
        )

        reactor.markSeasonPlayed("season1")
        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()

        // The second tap dedups against the record written by the first tap's
        // success (the fake resolves synchronously, so the record is in place
        // when the second tap's guard runs) — one mutation, not two.
        assertEquals(listOf(Triple("s1", "season1", true)), mutator.seasonCalls)
    }
    // endregion
}
