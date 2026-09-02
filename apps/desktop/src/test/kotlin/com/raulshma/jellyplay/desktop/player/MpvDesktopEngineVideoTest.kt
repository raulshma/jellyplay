package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.sun.jna.Pointer
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir

/**
 * Wave 17B real-libmpv slice for the closed V2 cuts — the live `vf` /
 * `video-rotate` application ([DesktopVideoEffectChain] through
 * [MpvDesktopEngine.updateConfig]), the [MpvDesktopEngine.captureVideoFrame]
 * screenshot path, and the [MediaEngine.currentCues] live-cue history. Cases
 * mirror the wave-14C real-engine af test's property-level assertion style:
 * the engine is driven through the CONTRACT surface, then mpv's own
 * properties are read back.
 *
 * Runs on the software-render engine (`vo=libmpv`, `ao=null`) — the capture
 * and sub cases need a video OUTPUT, which `vo=null` engines don't have; the
 * vf/rotate cases ride along on the same engine for one fixture. Skips on
 * machines without libmpv (jna.library.path / MPV_LIBRARY / system install),
 * like [MpvDesktopEngineTest].
 */
class MpvDesktopEngineVideoTest {

    private fun libmpvAvailable(): Boolean = try {
        MpvLib.mpv
        true
    } catch (_: Throwable) {
        false
    }

    @field:TempDir
    lateinit var tempDir: File

    // ── video effects (`vf` chain + `video-rotate`) ────────────────────────

    @Test
    fun videoEffects_applyOntoTheLiveVfAndRotateProperties() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = newEngine()
        try {
            assumeTrue(engine.isSoftwareRendererActive, { "libmpv has no usable 'sw' render backend" })
            engine.load(PlaybackRequest(uri = clip().absolutePath, title = "vf-test"))
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            val ctx = engine.underlyingPlayer as Pointer
            // mpv's vf readback inserts %len% escapes before every string
            // value ('brightness=%3%0.2') and renames unsharp's positional
            // args ('@0=5'); strip the escapes so assertions see the plain
            // chain (live-probe verified both spellings).
            fun vfPlain(): String = (MpvLib.getPropertyString(ctx, "vf") ?: "")
                .replace(Regex("%\\d+%"), "")

            // All-defaults config first: ZERO writes — vf stays mpv's own
            // empty default (the change-cache discipline from 14C).
            engine.updateConfig(EngineConfig(videoEffects = VideoEffectsConfig()))
            assertTrue(vfPlain().isBlank(), "neutral config must leave vf untouched")

            // Full tonal stack via the contract surface. Substring (not
            // exact) assertions: mpv may re-serialize parameter values
            // ("0.20" vs "0.2") — "brightness=0.2" covers both spellings.
            engine.updateConfig(
                EngineConfig(
                    videoEffects = VideoEffectsConfig(
                        brightness = 0.2f,
                        contrast = 1.3f,
                        saturation = 1.4f,
                    ),
                ),
            )
            waitUntil(10_000) {
                val vf = vfPlain()
                vf.contains("eq=") && vf.contains("brightness=0.2") &&
                    vf.contains("contrast=1.3") && vf.contains("saturation=1.4")
            }

            // Rotation rides the separate video-rotate property, snapped to
            // the right angle — never the vf chain.
            engine.updateConfig(
                EngineConfig(
                    videoEffects = VideoEffectsConfig(rotationDegrees = 90f),
                ),
            )
            waitUntil(10_000) { MpvLib.getPropertyString(ctx, "video-rotate") == "90" }
            val vfAfterRotate = vfPlain()
            assertTrue(
                !vfAfterRotate.contains("rotate") && !vfAfterRotate.contains("transpose"),
                vfAfterRotate,
            )

            // Back to neutral: the chain CLEARS (vf clr) and rotation resets.
            engine.updateConfig(EngineConfig(videoEffects = VideoEffectsConfig()))
            waitUntil(10_000) {
                vfPlain().isBlank() && MpvLib.getPropertyString(ctx, "video-rotate") == "0"
            }
        } finally {
            engine.release()
        }
    }

    @Test
    fun videoEffects_fullStackCombinesEqUnsharpAndGblurOnTheEngine() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = newEngine()
        try {
            assumeTrue(engine.isSoftwareRendererActive, { "libmpv has no usable 'sw' render backend" })
            engine.load(PlaybackRequest(uri = clip().absolutePath, title = "vf-stack-test"))
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            val ctx = engine.underlyingPlayer as Pointer

            engine.updateConfig(
                EngineConfig(
                    videoEffects = VideoEffectsConfig(
                        brightness = 0.2f,
                        sharpness = 1f,
                        gaussianBlur = 4f,
                    ),
                ),
            )
            // gblur rides a lavfi graph and unsharp's positional args gain
            // @0/@1/@2 names in readback (live-probe verified:
            // `lavfi=graph=%13%gblur=sigma=2`, `unsharp=%2%@0=5:%2%@1=5:%2%@2=%3%1.5`)
            // — strip mpv's %len% escapes and assert structurally. blur=4
            // halves to sigma=2.00; sharpness=1 scales to unsharp amount 1.5.
            waitUntil(10_000) {
                val vf = (MpvLib.getPropertyString(ctx, "vf") ?: "")
                    .replace(Regex("%\\d+%"), "")
                vf.contains("eq=") && vf.contains("brightness=0.2") &&
                    vf.contains("unsharp=") && vf.contains("@0=5") && vf.contains("@2=1.5") &&
                    vf.contains("gblur") && vf.contains("sigma=2")
            }
        } finally {
            engine.release()
        }
    }

    // ── screenshot capture ─────────────────────────────────────────────────

    @Test
    fun captureVideoFrame_producesADecodablePngOfTheFrameSize() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = newEngine()
        try {
            assumeTrue(engine.isSoftwareRendererActive, { "libmpv has no usable 'sw' render backend" })
            engine.load(PlaybackRequest(uri = clip().absolutePath, title = "shot-test"))
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            engine.play()
            waitUntil(10_000) { engine.isPlaying.value }

            // Property-level evidence first: the raw command produces a
            // non-trivial PNG on disk (the engine method's underlying path).
            val ctx = engine.underlyingPlayer as Pointer
            val rawShot = File(tempDir, "raw-shot.png")
            val rc = MpvLib.command(ctx, "screenshot-to-file", rawShot.absolutePath, "subtitles")
            assertTrue(rc, "screenshot-to-file command accepted")
            assertTrue(rawShot.isFile, "screenshot file created")
            assertTrue(rawShot.length() > 1_000, "PNG is non-trivial (${rawShot.length()} bytes)")

            // Contract surface: decodes into the platform bitmap at the
            // source geometry (320x240 testsrc2 clip).
            val frame = assertNotNull(engine.captureVideoFrame(), "engine capture decodes a frame")
            assertEquals(320, frame.width, "captured width matches video-params/w")
            assertEquals(240, frame.height, "captured height matches video-params/h")
            // And the returned bitmap re-encodes (i.e. it is real pixel data).
            val reencode = File(tempDir, "re-encode.png")
            ImageIO.write(frame, "png", reencode)
            assertTrue(reencode.length() > 1_000, "re-encoded PNG is non-trivial")
        } finally {
            engine.release()
        }
    }

    @Test
    fun captureVideoFrame_beforeLoad_degradesToNull() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = newEngine()
        try {
            assumeTrue(engine.isSoftwareRendererActive, { "libmpv has no usable 'sw' render backend" })
            assertNull(engine.captureVideoFrame(), "no file loaded → null, not a throw")
        } finally {
            engine.release()
        }
    }

    // ── currentCues (live-cue history) ─────────────────────────────────────

    @Test
    fun currentCues_accumulateSubtitleHistoryWithResolvedSpans() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        // Paused load: the first cue starts at 0.2 s — selecting the track
        // while parked at 0 keeps the whole history deterministic.
        val engine = newEngine(paused = true)
        try {
            assumeTrue(engine.isSoftwareRendererActive, { "libmpv has no usable 'sw' render backend" })
            engine.load(
                PlaybackRequest(
                    uri = clip().absolutePath,
                    title = "cue-test",
                    externalSubtitles = listOf(srtSidecar()),
                ),
            )
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }

            // sub-add "auto" selects nothing — pick the sidecar explicitly
            // (also regression-covers the wave-17B NODE_ARRAY readNode fix:
            // this was empty before it).
            val subTrack = engine.availableTracks.value.firstOrNull { it.type == TrackType.SUBTITLE }
            assertNotNull(subTrack, "sidecar subtitle track listed")
            engine.selectTrack(TrackType.SUBTITLE, subTrack.index)
            engine.play()

            // First line becomes live, then folds into the cue history.
            waitUntil(15_000) { engine.liveSubtitleCue.value?.contains("First cue line") == true }
            waitUntil(10_000) {
                engine.currentCues.value.any { it.text.contains("First cue line") }
            }
            // Second line closes the first cue's open-ended span at its own
            // start (the merge rule mirrored from player-video's accumulator).
            waitUntil(15_000) {
                engine.currentCues.value.any { it.text.contains("Second cue line") }
            }
            val cues = engine.currentCues.value
            assertEquals(2, cues.size, "both lines accumulated: $cues")
            val first = cues.first { it.text.contains("First cue line") }
            val second = cues.first { it.text.contains("Second cue line") }
            assertTrue(first.startTimeUs in 100_000..400_000, "start ≈ sub-start 0.2 s: ${first.startTimeUs}")
            assertTrue(
                second.startTimeUs in 1_900_000..2_200_000,
                "second start ≈ sub-start 2.0 s (live-read, not the stale cache): ${second.startTimeUs}",
            )
            assertEquals(second.startTimeUs, first.endTimeUs, "open span closed at the next line's start")
            assertTrue(second.endTimeUs == Long.MAX_VALUE, "latest line stays open-ended")

            // A re-load resets the history — it belongs to the played item.
            engine.load(PlaybackRequest(uri = clip().absolutePath, title = "cue-test-2"))
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            assertTrue(engine.currentCues.value.isEmpty(), "cue history resets per item")
        } finally {
            engine.release()
        }
    }

    // ── fixtures ───────────────────────────────────────────────────────────

    /**
     * Fresh sw engine; `ao=null` keeps CI quiet, vo stays on the libmpv path.
     * [paused] parks the core at load (cue-history determinism).
     */
    private fun newEngine(paused: Boolean = false): MpvSoftwareRenderEngine =
        MpvSoftwareRenderEngine(
            extraOptions = buildMap {
                put("ao", "null")
                if (paused) put("pause", "yes")
            },
        )

    private fun clip(): File {
        val out = File(tempDir, "video-fx-test.mp4")
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
            "testsrc2=duration=4:size=320x240:rate=15",
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

    private fun srtSidecar(): SubtitleSource {
        val srt = File(tempDir, "cue-test.srt")
        srt.writeText(
            """
            1
            00:00:00,200 --> 00:00:01,800
            First cue line

            2
            00:00:02,000 --> 00:00:03,800
            Second cue line
            """.trimIndent(),
        )
        return SubtitleSource(
            url = srt.absolutePath,
            label = "cue-test-subs",
            language = "en",
            mimeType = "application/x-subrip",
            id = "cue-test-subs",
        )
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
