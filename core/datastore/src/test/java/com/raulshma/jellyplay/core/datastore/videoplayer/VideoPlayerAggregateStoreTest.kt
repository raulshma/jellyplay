package com.raulshma.jellyplay.core.datastore.videoplayer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.PreferenceSliceGraph
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.createPreferenceSliceGraph
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [VideoPlayerAggregateStore] — the single read seam that combines
 * the seven player-relevant slices (playback, video-player, audio,
 * audio-effects, subtitle, engine, security) so the player stack collects one
 * aggregate instead of seven flows.
 *
 * Mirrors the projection-isolation property of `PreferenceProjectionsTest`: a
 * write to a constituent store must surface in the matching slice of the
 * aggregate, and the aggregate must emit a distinct value only when a slice
 * actually changes (`distinctUntilChanged`).
 *
 * All stores share the single `"user_prefs"` DataStore (AndroidX requires one
 * delegate per file per process), so a write via any store graph is observed
 * by the aggregate's own store instances.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoPlayerAggregateStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var graph: PreferenceSliceGraph
    private lateinit var aggregateStore: VideoPlayerAggregateStore

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            graph = createPreferenceSliceGraph(scope, dataStore)
            aggregateStore = VideoPlayerAggregateStore(
                scope,
                graph.playbackStore,
                graph.videoPlayerStore,
                graph.audioStore,
                graph.audioEffectsStore,
                graph.subtitleLanguageStore,
                graph.engineStore,
                graph.securityStore,
            )
            // Drain the seven slice StateFlows so the cleared state is observed
            // before each test writes + reads the aggregate.
            graph.playbackStore.playback.first()
            graph.videoPlayerStore.videoPlayer.first()
            graph.audioStore.audio.first()
            graph.audioEffectsStore.audioEffects.first()
            graph.subtitleLanguageStore.subtitle.first()
            graph.engineStore.playerEngine.first()
            graph.securityStore.security.first()
            // Drain the aggregate's WhileSubscribed(5_000) upstream too.
            aggregateStore.aggregate.first()
        }
    }

    @Test
    fun `aggregate emits the seven drained slice defaults before any write`() = runTest {
        // The aggregate holds the seven slices verbatim, so its cold value must
        // equal the slices the constituent stores emit on an empty DataStore.
        // (PlaybackStore migrates the legacy `force_direct_play` default, so its
        // slice default is FORCE_DIRECT_PLAY, not the PlaybackSlice data-class
        // default of AUTO — hence we read the actual drained slices, not
        // VideoPlayerAggregate().)
        val expected = VideoPlayerAggregate(
            playback = graph.playbackStore.playback.value,
            videoPlayer = graph.videoPlayerStore.videoPlayer.value,
            audio = graph.audioStore.audio.value,
            audioEffects = graph.audioEffectsStore.audioEffects.value,
            subtitle = graph.subtitleLanguageStore.subtitle.value,
            engine = graph.engineStore.playerEngine.value,
            security = graph.securityStore.security.value,
        )
        assertEquals(expected, aggregateStore.aggregate.first())
    }

    @Test
    fun `a video-player slice write reaches the aggregate`() = runTest {
        val before = aggregateStore.aggregate.first()
        val target = if (before.videoPlayer.videoPreloadBufferSize == PreloadBufferSize.HIGH) {
            PreloadBufferSize.LOW
        } else {
            PreloadBufferSize.HIGH
        }

        graph.videoPlayerStore.setVideoPreloadBufferSize(target)

        val after = aggregateStore.aggregate.first()
        assertNotEquals(before.videoPlayer, after.videoPlayer)
        assertEquals(target, after.videoPlayer.videoPreloadBufferSize)
    }

    @Test
    fun `a playback preferred-player write reaches the aggregate`() = runTest {
        val before = aggregateStore.aggregate.first().playback.preferredPlayer
        graph.playbackStore.setPreferredPlayer(
            if (before == PlayerType.MPV) PlayerType.EXO_PLAYER else PlayerType.MPV,
        )

        val after = aggregateStore.aggregate.first().playback.preferredPlayer
        assertNotEquals(before, after)
    }

    @Test
    fun `an audio-effects slice change reaches the aggregate`() = runTest {
        val before = aggregateStore.aggregate.first().audioEffects.bassBoostEnabled
        graph.audioEffectsStore.setBassBoostEnabled(!before)

        val after = aggregateStore.aggregate.first().audioEffects.bassBoostEnabled
        assertNotEquals(before, after)
    }

    @Test
    fun `a security slice change reaches the aggregate`() = runTest {
        val before = aggregateStore.aggregate.first().security.remoteControlEnabled
        graph.securityStore.setRemoteControlEnabled(!before)

        val after = aggregateStore.aggregate.first().security.remoteControlEnabled
        assertNotEquals(before, after)
    }

    @Test
    fun `writing each player-space slice produces a distinct aggregate emission`() = runTest {
        val initial = aggregateStore.aggregate.first()

        // Touch one field per slice and confirm each surfaces in the aggregate.
        graph.videoPlayerStore.setVideoGesturesEnabled(!initial.videoPlayer.videoGesturesEnabled)
        assertNotEquals(initial.videoPlayer, aggregateStore.aggregate.first().videoPlayer)

        graph.audioStore.setAudioAutoplayNext(!initial.audio.audioAutoplayNext)
        assertNotEquals(initial.audio.audioAutoplayNext, aggregateStore.aggregate.first().audio.audioAutoplayNext)

        graph.subtitleLanguageStore.setSubtitlesForcedOnly(!initial.subtitle.subtitlesForcedOnly)
        assertNotEquals(initial.subtitle.subtitlesForcedOnly, aggregateStore.aggregate.first().subtitle.subtitlesForcedOnly)

        graph.playbackStore.setPreferredPlayer(
            if (initial.playback.preferredPlayer == PlayerType.MPV) PlayerType.EXO_PLAYER else PlayerType.MPV,
        )
        assertNotEquals(initial.playback.preferredPlayer, aggregateStore.aggregate.first().playback.preferredPlayer)
    }
}
