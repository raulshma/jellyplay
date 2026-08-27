package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wave 13B session-harness evidence model — the pure classification the
 * harness asserts from (EngineActivitySnapshot), plus the recorder wiring
 * against the existing FakeMediaEngine double (no libmpv, no AWT; the
 * recorder's collectors run on its own scope, so tests poll the snapshots).
 */
class EngineActivityEvidenceTest {

    private fun sample(
        transitions: List<String> = listOf("IDLE", "BUFFERING", "READY"),
        playing: Boolean = true,
        samples: List<Triple<Long, Long, Boolean>> = emptyList(),
    ) = EngineActivitySnapshot(
        displayName = "mpv",
        surface = EngineActivitySnapshot.SURFACE_HWND,
        createdAtMs = 10_000L,
        transitions = transitions.mapIndexed { i, s ->
            EngineActivitySnapshot.StateTransition(atMs = 1_000L + i, toState = s)
        },
        isPlayingObserved = playing,
        positionSamples = samples.map { (at, pos, isPlaying) ->
            EngineActivitySnapshot.PositionSample(at, pos, isPlaying)
        },
    )

    // ── pure classification ────────────────────────────────────────────────

    @Test
    fun `sawState matches recorded transitions by name`() {
        val snapshot = sample(transitions = listOf("IDLE", "BUFFERING", "READY"))
        assertTrue(snapshot.sawState("READY"))
        assertTrue(snapshot.sawState("BUFFERING"))
        assertFalse(snapshot.sawState("ENDED"))
    }

    @Test
    fun `playingAdvanceMs spans playing samples only`() {
        val snapshot = sample(
            playing = true,
            // Paused samples (0ms and a 50s seek while paused) must NOT count.
            samples = listOf(
                Triple(0L, 0L, false),
                Triple(500L, 0L, true),
                Triple(1_000L, 1_200L, true),
                Triple(1_500L, 2_600L, true),
                Triple(2_000L, 50_000L, false),
            ),
        )
        assertEquals(2_600L, snapshot.playingAdvanceMs())
    }

    @Test
    fun `playingAdvanceMs is zero without two playing samples`() {
        assertEquals(0L, sample(playing = false, samples = listOf(Triple(0, 0, false))).playingAdvanceMs())
        assertEquals(0L, sample().copy(positionSamples = emptyList()).playingAdvanceMs())
    }

    @Test
    fun `advanceSinceMs considers samples strictly after the cutoff regardless of flag`() {
        val snapshot = sample(
            samples = listOf(
                Triple(1_000L, 0L, true),
                Triple(1_500L, 1_000L, true),
                Triple(2_000L, 1_600L, false), // paused, playhead frozen
                Triple(2_500L, 1_600L, false),
            ),
        )
        // The freeze window (after pause): advance stops ⇒ keypress evidence.
        assertEquals(0L, snapshot.advanceSinceMs(1_800L))
        // The whole window: playhead moved 1_600 ms.
        assertEquals(1_600L, snapshot.advanceSinceMs(0L))
        // Nothing strictly after the last sample.
        assertEquals(0L, snapshot.advanceSinceMs(2_500L))
    }

    @Test
    fun `playbackVerified requires playing AND the minimum advance`() {
        val advancing = sample(
            playing = true,
            samples = listOf(
                Triple(0L, 0L, true),
                Triple(500L, 600L, true),
                Triple(1_000L, 1_400L, true),
            ),
        )
        assertTrue(advancing.playbackVerified()) // default bar 1_000 ms
        assertTrue(advancing.playbackVerified(minAdvanceMs = 1_400L))
        assertFalse(advancing.playbackVerified(minAdvanceMs = 1_401L))

        val neverPlaying = advancing.copy(isPlayingObserved = false)
        assertFalse(neverPlaying.playbackVerified())

        val frozen = sample(playing = true, samples = listOf(Triple(0, 500, true), Triple(500, 500, true)))
        assertFalse(frozen.playbackVerified())
    }

    @Test
    fun `NONE snapshot verifies nothing and reports no surface`() {
        assertFalse(EngineActivitySnapshot.NONE.playbackVerified())
        assertEquals("", EngineActivitySnapshot.NONE.surface)
        assertNull(EngineActivitySnapshot.NONE.transitions.firstOrNull())
    }

    // ── recorder wiring (real flows, fake engine) ──────────────────────────

    /** Polls until the predicate holds on a fresh snapshot, or fails. */
    private fun EngineActivityRecorder.awaitSnapshot(
        pick: (EngineActivityRecorder) -> EngineActivitySnapshot,
        timeoutMs: Long = 5_000,
        predicate: (EngineActivitySnapshot) -> Boolean,
    ): EngineActivitySnapshot = runBlocking {
        withTimeout(timeoutMs) {
            var snapshot = pick(this@awaitSnapshot)
            while (!predicate(snapshot)) {
                kotlinx.coroutines.delay(50)
                snapshot = pick(this@awaitSnapshot)
            }
            snapshot
        }
    }

    @Test
    fun `recorder observes state transitions isPlaying without duplicate transitions`() {
        val recorder = EngineActivityRecorder()
        val engine = FakeMediaEngine()
        recorder.recordCreated(engine, EngineActivitySnapshot.SURFACE_HWND)

        engine.load(PlaybackRequest(uri = "http://server/stream", title = "test"))
        // NOTE: FakeMediaEngine.load sets BUFFERING→READY synchronously and
        // StateFlow conflates, so BUFFERING is not guaranteed observable —
        // READY + isPlaying are the observable endpoints of a real load.
        val snapshot = recorder.awaitSnapshot({ it.latest() }) { s ->
            s.isPlayingObserved && s.sawState("READY")
        }
        assertEquals("fake", snapshot.displayName)
        assertEquals(EngineActivitySnapshot.SURFACE_HWND, snapshot.surface)
        assertTrue(snapshot.playbackVerified(minAdvanceMs = 0)) // playing observed

        // A pause/end/resume cycle lands as distinct transitions with no
        // consecutive duplicates (the recorder dedupes StateFlow replays).
        // NOTE: each state change is awaited BEFORE the next — a conflated
        // StateFlow never observes values overwritten before the collector
        // resumes, so back-to-back flips would race.
        engine.simulateEnded()
        recorder.awaitSnapshot({ it.latest() }) { s -> s.sawState("ENDED") }
        engine.play()
        val cycled = recorder.awaitSnapshot({ it.latest() }) { s ->
            s.transitions.count { it.toState == "READY" } >= 2
        }
        val names = cycled.transitions.map { it.toState }
        // No consecutive duplicates (deduped), but READY legitimately repeats
        // across the ENDED→resume cycle.
        assertEquals(names.zipWithNext().none { (a, b) -> a == b }, true)
        assertTrue("ENDED" in names && "READY" in names)
    }

    @Test
    fun `recorder samples the playhead and a later freeze reads as zero advance`() = runBlocking {
        val recorder = EngineActivityRecorder()
        val engine = FakeMediaEngine()
        recorder.recordCreated(engine, EngineActivitySnapshot.SURFACE_HWND)
        engine.load(PlaybackRequest(uri = "http://server/stream", title = "test"))

        // Simulated live playhead: position tracks wall clock while playing.
        val start = System.currentTimeMillis()
        withTimeout(8_000) {
            while (true) {
                engine.currentPositionMs = System.currentTimeMillis() - start
                val s = recorder.latest()
                if (s.positionSamples.count { it.isPlaying } >= 2 && s.playingAdvanceMs() >= 500) {
                    assertTrue(s.playbackVerified(minAdvanceMs = 500))
                    break
                }
                kotlinx.coroutines.delay(100)
            }
        }

        // Freeze (SPACE→pause evidence pattern): stop moving the playhead,
        // let ≥2 post-freeze samples land, then the freeze must read as ~0
        // advance even though isPlaying flipped false for all of them.
        val freezeAt = System.currentTimeMillis()
        engine.pause()
        val frozenAt = engine.currentPositionMs
        withTimeout(6_000) {
            while (recorder.latest().positionSamples.count { it.atMs > freezeAt + 700 } < 2) {
                kotlinx.coroutines.delay(100)
            }
        }
        engine.currentPositionMs = frozenAt
        val advance = recorder.latest().advanceSinceMs(freezeAt)
        assertTrue(advance <= 300L, "expected frozen playhead, advance was $advance ms")
    }

    @Test
    fun `latestVideoEngine skips the EXTERNAL no-op record`() {
        val recorder = EngineActivityRecorder()
        recorder.recordCreated(FakeMediaEngine(), EngineActivitySnapshot.SURFACE_NO_OP)

        val video = FakeMediaEngine()
        recorder.recordCreated(video, EngineActivitySnapshot.SURFACE_SOFTWARE)

        val snapshot = recorder.awaitSnapshot({ it.latestVideoEngine() }) { it.surface.isNotEmpty() }
        assertEquals(EngineActivitySnapshot.SURFACE_SOFTWARE, snapshot.surface)

        // And with ONLY a no-op recorded, latestVideoEngine stays NONE.
        val onlyNoOp = EngineActivityRecorder()
        onlyNoOp.recordCreated(FakeMediaEngine(), EngineActivitySnapshot.SURFACE_NO_OP)
        assertEquals(EngineActivitySnapshot.NONE, onlyNoOp.latestVideoEngine())
    }

    @Test
    fun `playbackState mapping stays aligned with the engine contract enum`() {
        // The recorder stores state names as strings; pin the four states a
        // real mpv session cycles through so a rename fails here, not live.
        listOf("IDLE", "BUFFERING", "READY", "ENDED", "ERROR").forEach { name ->
            EnginePlaybackState.valueOf(name)
        }
    }
}
