package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter.NowPlayingEvent
import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter.NowPlayingMeta
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the now-playing seam's state machine (feature 4.2/4.3's shared spine):
 * a (kind, itemId) change — or a first publish — emits `Started`; same-item
 * refreshes only replace the state; `markEnded` emits without clearing;
 * `clear` emits `Stopped` exactly once; the idle transitions ride the same
 * stream and carry no meta.
 */
class NowPlayingReporterTest {

    private lateinit var reporter: NowPlayingReporter

    @BeforeTest
    fun setUp() {
        reporter = NowPlayingReporter()
    }

    private val video = NowPlayingMeta(
        itemId = "video-1",
        title = "The Movie",
        subtitle = "",
        kind = NowPlayingReporter.Kind.VIDEO,
        positionMs = 5_000,
        durationMs = 90_000,
    )

    private val music = NowPlayingMeta(
        itemId = "track-1",
        title = "Song A",
        subtitle = "Artist",
        kind = NowPlayingReporter.Kind.MUSIC,
        positionMs = 1_000,
        durationMs = 200_000,
    )

    @Test
    fun initialSeam_isEmpty() {
        assertEquals(null, reporter.nowPlaying.value)
    }

    @Test
    fun firstPublish_emitsStartedAndPublishesState() = runTest {
        val events = recordedEvents {
            reporter.publish(video)
        }
        assertEquals(listOf<NowPlayingEvent>(NowPlayingEvent.Started(video)), events)
        assertEquals(video, reporter.nowPlaying.value)
    }

    @Test
    fun kindChange_emitsStartedEvenForTheSameItemId() = runTest {
        reporter.publish(video.copy(itemId = "shared"))
        val events = recordedEvents {
            reporter.publish(music.copy(itemId = "shared"))
        }
        assertEquals(1, events.size)
        assertTrue(events.single() is NowPlayingEvent.Started, "a VIDEO→MUSIC flip is a new activity")
    }

    @Test
    fun sameItemRefresh_updatesStateSilently() = runTest {
        reporter.publish(video)
        val refreshed = video.copy(positionMs = 42_000)
        val events = recordedEvents {
            reporter.publish(refreshed)
        }
        assertEquals(emptyList(), events, "position refreshes / play-pause mirrors emit nothing")
        assertEquals(refreshed, reporter.nowPlaying.value)
    }

    @Test
    fun markEnded_emitsEndedAndKeepsTheMetaPublished() = runTest {
        reporter.publish(video)
        val events = recordedEvents {
            reporter.markEnded()
        }
        assertEquals(listOf<NowPlayingEvent>(NowPlayingEvent.Ended(video)), events)
        assertEquals(video, reporter.nowPlaying.value, "an auto-advance replaces the meta; nothing follows when nothing is next")
    }

    @Test
    fun markEnded_onAnEmptySeam_emitsNothing() = runTest {
        assertEquals(emptyList(), recordedEvents { reporter.markEnded() })
    }

    @Test
    fun clear_emitsStoppedAndEmptiesTheSeam() = runTest {
        reporter.publish(video)
        val events = recordedEvents {
            reporter.clear()
        }
        assertEquals(listOf<NowPlayingEvent>(NowPlayingEvent.Stopped(video)), events)
        assertEquals(null, reporter.nowPlaying.value)
    }

    @Test
    fun clear_onAnEmptySeam_emitsNothing() = runTest {
        assertEquals(emptyList(), recordedEvents { reporter.clear() })
    }

    @Test
    fun anEndedThenAdvanceReadsEndedThenStarted() = runTest {
        reporter.publish(music)
        val next = music.copy(itemId = "track-2", title = "Song B")
        val events = recordedEvents {
            reporter.markEnded()
            reporter.publish(next)
        }
        assertEquals(
            listOf(NowPlayingEvent.Ended(music), NowPlayingEvent.Started(next)),
            events,
            "the audio auto-advance reads ended(prev) → started(next) to the shell consumers",
        )
    }

    @Test
    fun idleTransitions_rideTheSameStream_withoutMeta() = runTest {
        val events = recordedEvents {
            reporter.reportIdle(true)
            reporter.reportIdle(false)
        }
        assertEquals(
            listOf<NowPlayingEvent>(NowPlayingEvent.IdleEntered, NowPlayingEvent.IdleLeft),
            events,
        )
    }

    /**
     * Collects the events emitted inside [block] — the collector starts
     * UNDISPATCHED so its subscription is live before the block's first
     * `tryEmit` (the SharedFlow has no replay; a later-subscribed collector
     * would see nothing), and the scheduler drains the buffered emissions
     * before the job cancels.
     */
    private fun TestScope.recordedEvents(block: () -> Unit): List<NowPlayingEvent> {
        val recorded = mutableListOf<NowPlayingEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            reporter.events.collect { event -> recorded.add(event) }
        }
        block()
        testScheduler.runCurrent()
        job.cancel()
        return recorded
    }
}
