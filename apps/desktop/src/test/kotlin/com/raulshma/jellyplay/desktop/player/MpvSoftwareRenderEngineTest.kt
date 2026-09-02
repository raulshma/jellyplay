package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir

/**
 * [MpvSoftwareRenderEngine] contract behavior — the wave-12B sw-render engine
 * subclassing the HWND engine. Cases mirror [MpvDesktopEngineTest] where they
 * apply; the source is a synthetic VIDEO clip (the surface story only exists
 * for video), generated with the host's ffmpeg into a temp dir instead of the
 * checked-in WAV resource.
 */
class MpvSoftwareRenderEngineTest {

    private fun libmpvAvailable(): Boolean = try {
        MpvLib.mpv
        true
    } catch (_: Throwable) {
        false
    }

    @field:TempDir
    lateinit var tempDir: File

    @Test
    fun load_reachesReady_and_reportsDuration() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = newEngine()
        try {
            assumeTrue(engine.isSoftwareRendererActive, { "libmpv has no usable 'sw' render backend" })
            engine.load(PlaybackRequest(uri = clip().absolutePath, title = "contract-test"))
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            // Clip is exactly 2 s; allow encoder/container rounding slack.
            waitUntil(10_000) { engine.durationMs in 1_500..3_000 }
        } finally {
            engine.release()
        }
        // Release must leave no software renderer half-torn-down: the context is
        // freed before terminate_destroy, so a second release pass stays inert.
        assertEquals(EnginePlaybackState.IDLE, engine.playbackState.value)
    }

    @Test
    fun play_seek_pause_stop_roundtrip() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = newEngine()
        try {
            assumeTrue(engine.isSoftwareRendererActive, { "libmpv has no usable 'sw' render backend" })
            engine.load(PlaybackRequest(uri = clip().absolutePath, title = "roundtrip"))
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }

            engine.play()
            waitUntil(10_000) { engine.isPlaying.value }

            engine.seekTo(500)
            waitUntil(10_000) { engine.currentPositionMs in 200..1_400 }

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
    fun pullsFrames_and_tracksLastFrameTimestamp() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = newEngine()
        try {
            assumeTrue(engine.isSoftwareRendererActive, { "libmpv has no usable 'sw' render backend" })
            assertEquals(
                0L,
                engine.lastFrameTimestampMs,
                "no timestamp before any pull",
            )
            engine.load(PlaybackRequest(uri = clip().absolutePath, title = "pull-test"))
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            engine.play()
            // Pull on a background ticker like the compose pane would (default
            // thread stack — exactly the conditions of the real 30 fps pull
            // loop): the first successful pull stamps the timestamp.
            val stride = ((320.toLong() * 4 + 63) / 64) * 64
            val target = com.sun.jna.Memory(stride * 240)
            val stop = java.util.concurrent.atomic.AtomicBoolean(false)
            val puller = Thread {
                while (!stop.get()) {
                    engine.pullFrame(target, 320, 240, stride)
                    Thread.sleep(40)
                }
            }
            puller.name = "sw-puller"
            puller.setUncaughtExceptionHandler { _, e ->
                System.err.println("puller-uncaught: $e")
            }
            puller.isDaemon = true
            puller.start()
            try {
                waitUntil(5_000) { engine.lastFrameTimestampMs > 0L }
            } finally {
                stop.set(true)
                puller.join(2_000)
            }
        } finally {
            engine.release()
        }
    }

    @Test
    fun replay_afterEnd_restartsFromZero() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = newEngine()
        try {
            assumeTrue(engine.isSoftwareRendererActive, { "libmpv has no usable 'sw' render backend" })
            engine.load(PlaybackRequest(uri = clip().absolutePath, title = "replay-test"))
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            engine.seekTo(1_900)
            // keep-open parks the core at eof → eof-reached → ENDED.
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.ENDED }
            engine.play()
            // play-from-ENDED must seek back to 0 and actually resume.
            waitUntil(15_000) { engine.isPlaying.value && engine.currentPositionMs < 1_000 }
        } finally {
            engine.release()
        }
    }

    /** Fresh sw engine; `ao=null` keeps CI quiet, vo stays on the libmpv path. */
    private fun newEngine(): MpvSoftwareRenderEngine =
        MpvSoftwareRenderEngine(extraOptions = mapOf("ao" to "null"))

    private fun clip(): File {
        val out = File(tempDir, "sw-contract-test.mp4")
        if (out.isFile && out.length() > 0) return out
        val process = ProcessBuilder(
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-f",
            "lavfi",
            "-i",
            "testsrc2=duration=2:size=320x240:rate=15",
            "-c:v",
            "libx264",
            "-pix_fmt",
            "yuv420p",
            out.absolutePath,
        ).redirectErrorStream(true)
        val running = try {
            process.start()
        } catch (_: Throwable) {
            null
        }
        assumeTrue(running != null, { "ffmpeg not on PATH — cannot generate the synthetic clip" })
        running!!.waitFor()
        assumeTrue(out.isFile && out.length() > 0, { "ffmpeg failed generating the synthetic clip" })
        return out
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("condition not met within ${timeoutMs}ms")
            }
            Thread.sleep(100)
        }
    }
}
