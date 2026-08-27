package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.sun.jna.Memory
import com.sun.jna.Pointer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir

/**
 * The wave-12B software-render pixel pipeline against real libmpv, fully
 * offscreen (`vo=libmpv`, `ao=null`) — no window, no GL context, no display
 * dependency. A tiny synthetic clip is generated ONCE per test method with the
 * host's ffmpeg (`testsrc2`, 320x240@15fps, 2 s) into a JUnit temp dir.
 *
 * Sources are passed as ABSOLUTE FILE PATHS, matching the checked-in
 * MpvDesktopEngineTest fixtures: empirically this libmpv dev build on Windows
 * silently stalls loadfile forever when handed `file:/...` URIs — no START_FILE
 * and no error event ever arrive (recorded in the spike doc). HTTP URLs
 * (production sources) are unaffected.
 *
 * Skips honestly when either prerequisite is missing: libmpv (same gate as
 * [MpvDesktopEngineTest]) or a working ffmpeg CLI. Claims coverage only where
 * both ran.
 */
class MpvSwRenderPipelineTest {

    private fun libmpvAvailable(): Boolean = try {
        MpvLib.mpv
        true
    } catch (_: Throwable) {
        false
    }

    private companion object {
        /** Requested surface geometry — matches the generated clip's size family. */
        const val PULL_W = 320
        const val PULL_H = 240

        const val FRAME_POLL_ATTEMPTS = 45
        const val FRAME_POLL_INTERVAL_MS = 60L
        const val SEEK_SETTLE_ATTEMPTS = 40
        const val SEEK_SETTLE_INTERVAL_MS = 50L
    }

    @field:TempDir
    lateinit var tempDir: File

    @Test
    fun supportProbe_smokePasses_whenLibmpvLoads() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val supported = MpvSoftwareSurfaceSupport.isSupported
        assertTrue(
            supported,
            "sw render-context smoke probe failed: ${MpvSoftwareSurfaceSupport.lastProbeFailure}",
        )
    }

    @Test
    fun lifecycle_pullsDistinctFrames_whilePlaying() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val clip = generateClip()
        val engine = MpvSoftwareRenderEngine(extraOptions = mapOf("ao" to "null"))
        try {
            assumeTrue(engine.isSoftwareRendererActive, { "libmpv has no usable 'sw' render backend" })

            engine.load(PlaybackRequest(uri = clip.absolutePath, title = "sw-lifecycle"))
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            engine.play()

            // Poll-pull while the clip plays: expect >= 3 successful pulls with
            // >= 2 DISTINCT pixel payloads (testsrc2 animates every frame).
            var successes = 0
            var lastBytes: ByteArray? = null
            var distinctPayloads = 0
            repeat(FRAME_POLL_ATTEMPTS) {
                val pulled = pullInto(engine)
                if (pulled != null) {
                    successes++
                    if (lastBytes == null || !lastBytes.contentEquals(pulled)) {
                        distinctPayloads++
                    }
                    lastBytes = pulled
                }
                Thread.sleep(FRAME_POLL_INTERVAL_MS)
            }
            assertTrue(successes >= 3, "expected >=3 successful sw pulls, got $successes")
            assertTrue(distinctPayloads >= 2, "expected >=2 distinct frames, got $distinctPayloads")
            assertEquals(PULL_W, intProperty(engine, "video-params/w"), "decoded width")
            assertEquals(PULL_H, intProperty(engine, "video-params/h"), "decoded height")
            assertTrue(engine.lastFrameTimestampMs > 0L, "lastFrameTimestampMs must update on pull")
        } finally {
            engine.release()
        }
    }

    @Test
    fun seek_changesPixels() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val clip = generateClip()
        val engine = MpvSoftwareRenderEngine(extraOptions = mapOf("ao" to "null"))
        try {
            assumeTrue(engine.isSoftwareRendererActive, { "libmpv has no usable 'sw' render backend" })

            engine.load(PlaybackRequest(uri = clip.absolutePath, title = "sw-seek"))
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            engine.play()
            waitUntil(5_000) { engine.currentPositionMs > 100 }

            engine.pause()
            val beforeSeek = waitUntilDifferentFrom(null) { pullInto(engine) }

            engine.seekTo(1_500)
            // Frozen clock snaps forward on the seek restart; wait for it so the
            // new position is actually decoded before comparing pixels.
            waitUntil(5_000) { engine.currentPositionMs >= 1_000 }
            val afterSeek = waitUntilDifferentFrom(beforeSeek) { pullInto(engine) }

            assertTrue(afterSeek != null, "pixels must change materially after a seek")
        } finally {
            engine.release()
        }
    }

    // ── Pixel helpers ────────────────────────────────────────────────────────

    /**
     * Renders one frame into a fresh scratch buffer and snapshots the payload;
     * null when the pull dropped (tick race / not yet decoded).
     */
    private fun pullInto(engine: MpvSoftwareRenderEngine): ByteArray? {
        val stride = ((PULL_W.toLong() * 4 + 63) / 64) * 64
        val mem = Memory(stride * PULL_H)
        return if (engine.pullFrame(mem, PULL_W, PULL_H, stride)) {
            mem.getByteArray(0, (stride * PULL_H).toInt())
        } else {
            null
        }
    }

    /**
     * Keeps pulling (bounded) until a payload differing from [baseline] shows
     * up; returns that payload or null. When [baseline] is null the FIRST
     * successful pull wins — establishing the frozen pre-seek reference.
     */
    private fun waitUntilDifferentFrom(baseline: ByteArray?, pull: () -> ByteArray?): ByteArray? {
        repeat(SEEK_SETTLE_ATTEMPTS) {
            pull()?.let { current ->
                if (baseline == null) {
                    return current
                }
                if (!current.contentEquals(baseline)) {
                    val diffingBytes = current.indices.count { current[it] != baseline[it] }
                    // Material difference, not scaler dither noise.
                    if (diffingBytes > baseline.size / 100) {
                        return current
                    }
                }
            }
            Thread.sleep(SEEK_SETTLE_INTERVAL_MS)
        }
        return null
    }

    private fun intProperty(engine: MpvSoftwareRenderEngine, name: String): Int =
        (engine.underlyingPlayer as? Pointer)
            ?.let { ctx -> MpvLib.getPropertyString(ctx, name)?.toIntOrNull() }
            ?: -1

    /** Generates the 2 s synthetic clip once per temp dir; assumes ffmpeg exists. */
    private fun generateClip(): File {
        val out = File(tempDir, "sw-render-test.mp4")
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
            "testsrc2=duration=2:size=${PULL_W}x${PULL_H}:rate=15",
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
        val output = running!!.inputStream.readBytes().decodeToString()
        val code = running.waitFor()
        assumeTrue(code == 0 && out.isFile && out.length() > 0) {
            "ffmpeg failed generating the synthetic clip (exit=$code): $output"
        }
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
