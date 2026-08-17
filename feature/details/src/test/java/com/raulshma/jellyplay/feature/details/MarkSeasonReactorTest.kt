package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
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
 *
 * Session-seam fixture: one [MutableStateFlow] of [DetailSession] replaces the
 * former itemId/episodes/seriesId provider lambdas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarkSeasonReactorTest {

    private val strings = fakeDetailStrings()
    private val mutator = FakeUserDataMutator()

    private val messages = RecordingMessages()

    /**
     * The reactor's session collector runs for the reactor's lifetime (it
     * never completes), so it must not be a child of the [TestScope] —
     * `runTest` would wait for it forever. It shares the test scheduler so
     * [advanceUntilIdle] drives it, and [tearDown] cancels it.
     */
    private var reactorScope: CoroutineScope? = null

    @After
    fun tearDown() {
        reactorScope?.cancel()
        reactorScope = null
    }

    private fun reactorScopeOf(scope: TestScope): CoroutineScope =
        CoroutineScope(scope.coroutineContext + Job()).also { reactorScope = it }

    private fun reactor(
        scope: TestScope,
        itemId: String? = "s1",
        episodes: Map<String, List<MediaItem>> = emptyMap(),
        seriesId: String? = null,
    ): MarkSeasonReactor {
        messages.reset()
        mutator.seasonCalls.clear()
        return MarkSeasonReactor(
            scope = reactorScopeOf(scope),
            session = MutableStateFlow(
                itemId?.let {
                    DetailSession(itemId = it, seriesId = seriesId, episodes = episodes)
                },
            ),
            userDataMutator = mutator,
            messages = messages.flow,
            strings = strings,
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
        assertTrue(messages.recorded.isEmpty())
    }

    @Test
    fun `markSeasonPlayed failure emits message and does not skip a retry`() = runTest {
        val episodes = listOf(episode("e1", played = false))
        val reactor = reactor(this, episodes = mapOf("season1" to episodes))
        mutator.seasonResult = { _, _, _ -> Result.failure(RuntimeException("boom")) }

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()

        assertEquals(1, mutator.seasonCalls.size)
        assertEquals(1, messages.recorded.size)
        val message = messages.recorded.single()
        assertTrue(message is DetailMessage.Text)
        assertEquals(
            strings.get(R.string.detail_msg_couldnt_mark_played),
            (message as DetailMessage.Text).text,
        )

        // Nothing was recorded (success-only): a retry of the same direction
        // must not be swallowed as "already in target state".
        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()
        assertEquals(2, mutator.seasonCalls.size)
    }

    @Test
    fun `markSeasonPlayed with null session is a no-op`() = runTest {
        val reactor = reactor(this, itemId = null, episodes = mapOf("season1" to listOf(episode("e1"))))

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()

        assertTrue(mutator.seasonCalls.isEmpty())
        assertTrue(messages.recorded.isEmpty())
    }

    @Test
    fun `markSeasonPlayed with unknown seasonId is a no-op`() = runTest {
        val reactor = reactor(this, episodes = mapOf("season1" to listOf(episode("e1"))))

        reactor.markSeasonPlayed("nope")
        advanceUntilIdle()

        assertTrue(mutator.seasonCalls.isEmpty())
        assertTrue(messages.recorded.isEmpty())
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

    private fun liveSessionReactor(
        scope: TestScope,
        episodes: Map<String, List<MediaItem>>,
    ): MarkSeasonReactor = MarkSeasonReactor(
        scope = reactorScopeOf(scope),
        session = MutableStateFlow(DetailSession(itemId = "s1", episodes = episodes)),
        userDataMutator = mutator,
        messages = messages.flow,
        strings = strings,
    )

    @Test
    fun `markSeasonUnplayed on a season of half-played episodes is not treated as already unplayed`() = runTest {
        // No episode is fully watched, but e1 keeps a resume position: the
        // season is NOT in the unwatched target state (a mark-unwatched must
        // clear that progress). An isPlayed-only guard would call this
        // "already unplayed" and swallow the tap.
        val episodes = listOf(
            episode("e1", played = false, positionTicks = 5_000_000_000L),
            episode("e2", played = false),
        )
        val reactor = reactor(this, episodes = mapOf("season1" to episodes), seriesId = "s1")

        reactor.markSeasonUnplayed("season1")
        advanceUntilIdle()

        assertEquals(listOf(Triple("s1", "season1", false)), mutator.seasonCalls)
    }

    @Test
    fun `markSeasonUnplayed after a partial re-watch is not swallowed by the stale success record`() = runTest {
        val session = MutableStateFlow(
            DetailSession(
                itemId = "s1",
                episodes = mapOf("season1" to listOf(episode("e1", played = true), episode("e2", played = true))),
            ),
        )
        val reactor = MarkSeasonReactor(
            scope = reactorScopeOf(this),
            session = session,
            userDataMutator = mutator,
            messages = messages.flow,
            strings = strings,
        )

        // Tap 1: fully-watched season → unwatched (the case that works today).
        reactor.markSeasonUnplayed("season1")
        advanceUntilIdle()
        assertEquals(1, mutator.seasonCalls.size)

        // The provider rewrite is adopted (season now clean-unplayed), then
        // the user watches one episode again → mixed state.
        session.value = DetailSession(
            itemId = "s1",
            episodes = mapOf("season1" to listOf(episode("e1", played = true), episode("e2", played = false))),
        )

        // Tap 2 must flip the season again, not be deduped against the record
        // of tap 1's success.
        reactor.markSeasonUnplayed("season1")
        advanceUntilIdle()
        assertEquals(2, mutator.seasonCalls.size)
        assertTrue(mutator.seasonCalls.all { it.third == false })
    }

    @Test
    fun `markSeasonUnplayed after re-watching the whole season is not swallowed`() = runTest {
        val session = MutableStateFlow(
            DetailSession(
                itemId = "s1",
                episodes = mapOf("season1" to listOf(episode("e1", played = true), episode("e2", played = true))),
            ),
        )
        val reactor = MarkSeasonReactor(
            scope = reactorScopeOf(this),
            session = session,
            userDataMutator = mutator,
            messages = messages.flow,
            strings = strings,
        )

        reactor.markSeasonUnplayed("season1")
        advanceUntilIdle()
        assertEquals(1, mutator.seasonCalls.size)

        // Adoption observed (all clean-unplayed), then every episode is
        // re-watched — the snapshot returns to the full pre-mutation state.
        // advanceUntilIdle between updates: a StateFlow conflates back-to-back
        // assignments, and the retiring collector must observe the adoption
        // emission (in production the reducer's emissions arrive separately).
        session.value = DetailSession(
            itemId = "s1",
            episodes = mapOf("season1" to listOf(episode("e1", played = false), episode("e2", played = false))),
        )
        advanceUntilIdle()
        session.value = DetailSession(
            itemId = "s1",
            episodes = mapOf("season1" to listOf(episode("e1", played = true), episode("e2", played = true))),
        )

        reactor.markSeasonUnplayed("season1")
        advanceUntilIdle()
        assertEquals(2, mutator.seasonCalls.size)
    }

    @Test
    fun `markSeasonPlayed after a single-episode unwatch is not swallowed by the stale success record`() = runTest {
        val session = MutableStateFlow(
            DetailSession(
                itemId = "s1",
                episodes = mapOf("season1" to listOf(episode("e1", played = false), episode("e2", played = false))),
            ),
        )
        val reactor = MarkSeasonReactor(
            scope = reactorScopeOf(this),
            session = session,
            userDataMutator = mutator,
            messages = messages.flow,
            strings = strings,
        )

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()
        assertEquals(1, mutator.seasonCalls.size)

        // Adoption (all watched), then one episode is marked unwatched → mixed.
        session.value = DetailSession(
            itemId = "s1",
            episodes = mapOf("season1" to listOf(episode("e1", played = true), episode("e2", played = false))),
        )

        reactor.markSeasonPlayed("season1")
        advanceUntilIdle()
        assertEquals(2, mutator.seasonCalls.size)
        assertTrue(mutator.seasonCalls.all { it.third == true })
    }

    @Test
    fun `rapid inverse season toggles both apply in order`() = runTest {
        val currentEpisodes = listOf(episode("e1", played = false))
        val reactor = liveSessionReactor(this, mapOf("season1" to currentEpisodes))

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
        val reactor = liveSessionReactor(this, mapOf("season1" to currentEpisodes))

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
