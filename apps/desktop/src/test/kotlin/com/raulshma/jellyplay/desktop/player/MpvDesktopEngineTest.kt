package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import kotlin.test.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract behavior of [MpvDesktopEngine] against a real libmpv, headless
 * (`vo=null`/`ao=null`) with lavfi synthetic sources — no display, no network,
 * no media files. Skips on machines without libmpv (jna.library.path /
 * MPV_LIBRARY / system install).
 */
class MpvDesktopEngineTest {

    private fun libmpvAvailable(): Boolean = try {
        MpvLib.mpv
        true
    } catch (_: Throwable) {
        false
    }

    @Test
    fun load_reachesReady_and_reportsDuration() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null", "pause" to "yes"),
        )
        try {
            engine.load(
                PlaybackRequest(
                    uri = testMedia("test-tone.wav"),
                    title = "contract-test",
                ),
            )
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            waitUntil(10_000) { engine.durationMs in 5_000..7_000 }
        } finally {
            engine.release()
        }
    }

    @Test
    fun play_seek_pause_roundtrip() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null"),
        )
        try {
            engine.load(
                PlaybackRequest(
                    uri = testMedia("test-tone.wav"),
                    title = "seek-test",
                ),
            )
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }

            engine.play()
            waitUntil(10_000) { engine.isPlaying.value }

            engine.seekTo(4_000)
            waitUntil(10_000) { engine.currentPositionMs in 3_000..5_500 }

            engine.pause()
            waitUntil(5_000) { !engine.isPlaying.value }

            engine.stop()
            assertEquals(EnginePlaybackState.IDLE, engine.playbackState.value)
            assertTrue(engine.availableTracks.value.isEmpty())
        } finally {
            engine.release()
        }
    }

    @Test
    fun startPosition_isHonoured() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null", "pause" to "yes"),
        )
        try {
            engine.load(
                PlaybackRequest(
                    uri = testMedia("test-tone.wav"),
                    title = "start-test",
                    startPositionMs = 5_000,
                ),
            )
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            // start=+ applies at load; position settles within a second of it.
            waitUntil(10_000) { engine.currentPositionMs in 4_000..6_500 }
        } finally {
            engine.release()
        }
    }

    @Test
    fun replay_afterEnd_restartsFromZero() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null"),
        )
        try {
            engine.load(
                PlaybackRequest(uri = testMedia("test-tone.wav"), title = "replay-test"),
            )
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            engine.seekTo(5_700)
            // keep-open parks the core at eof → eof-reached → ENDED.
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.ENDED }
            engine.play()
            // play-from-ENDED must seek back to 0 and actually resume.
            waitUntil(15_000) { engine.isPlaying.value && engine.currentPositionMs < 4_000 }
        } finally {
            engine.release()
        }
    }
}

private fun testMedia(name: String): String =
    java.io.File(
        java.util.Objects.requireNonNull(
            MpvDesktopEngineTest::class.java.classLoader.getResource("media/$name"),
        ) { "missing test resource media/$name" }.toURI(),
    ).absolutePath

/** Polls [condition] every 100 ms until true or [timeoutMs] elapses. */
private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        if (System.currentTimeMillis() > deadline) {
            throw AssertionError("condition not met within ${timeoutMs}ms")
        }
        Thread.sleep(100)
    }
}
