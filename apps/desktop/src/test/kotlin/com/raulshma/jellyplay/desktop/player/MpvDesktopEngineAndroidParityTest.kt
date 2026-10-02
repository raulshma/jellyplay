package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.sun.jna.Pointer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir

/**
 * Android-mpv parity slice for the desktop engine's drift fixes, in the
 * real-libmpv property-readback style of [MpvDesktopEngineTest] and
 * [MpvDesktopEngineVideoTest]: the engine is driven through the CONTRACT
 * surface, then mpv's own properties (or the contract getters) are read back.
 * Covers the shared [com.raulshma.jellyplay.feature.player.video.engine.AspectRatioMapping]
 * plan application in [MpvDesktopEngine.setAspectRatio] (all four properties,
 * including the subtitle-margin pair the hand-rolled body never wrote), the
 * Android `selectTrack` decision logic (negative index = deselect; the
 * positive-path id writes for audio and subtitle), the
 * shared duration ladder's server rung ([PlaybackRequest.serverDurationMs]),
 * the stop() reset of that rung (the Android load/release reset parity), and
 * the shared [com.raulshma.jellyplay.feature.player.video.engine.TrackRefreshCoalescer]
 * adoption (the FILE_LOADED/sid/aid/track-list refresh burst collapses into
 * one coalesced catalog rebuild — the collapse-to-one itself is pinned by the
 * moved commonMain TrackRefreshCoalescerTest; here the real engine pins that
 * the coalesced body fires and converges on the settled catalog).
 * Skips on machines without libmpv, like the other engine suites.
 */
class MpvDesktopEngineAndroidParityTest {

    private fun libmpvAvailable(): Boolean = try {
        MpvLib.mpv
        true
    } catch (_: Throwable) {
        false
    }

    // ── aspect ratio: the shared mpvPlan, all four properties ───────────────

    @Test
    fun setAspectRatio_appliesTheFullSharedMpvPlan_perRatio() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null"),
        )
        try {
            val ctx = engine.liveMpvHandle() as Pointer
            // The override is a ratio-typed property: the plan's fraction
            // string ("177:100") and its "-1" clear round-trip to mpv's
            // canonical decimal readback ("1.770000" / "-1.000000").
            fun override(): Double? = MpvLib.getPropertyString(ctx, "video-aspect-override")?.toDoubleOrNull()
            fun panscan(): Double? = MpvLib.getPropertyString(ctx, "panscan")?.toDoubleOrNull()
            fun subUseMargins(): String? = MpvLib.getPropertyString(ctx, "sub-use-margins")
            fun subAssForceMargins(): String? = MpvLib.getPropertyString(ctx, "sub-ass-force-margins")

            // CROP: override cleared, full panscan, both margin keys engaged —
            // captions ride the visible frame (the hand-rolled body never
            // wrote them).
            engine.setAspectRatio(AspectRatio.CROP)
            assertEquals(-1.0, override(), "CROP override")
            assertEquals(1.0, panscan(), "CROP panscan")
            assertEquals("yes", subUseMargins(), "CROP sub-use-margins")
            assertEquals("yes", subAssForceMargins(), "CROP sub-ass-force-margins")

            // FILL: native frame — panscan reset AND margins released (the
            // former desktop bug: FILL was mis-applied as a second CROP).
            engine.setAspectRatio(AspectRatio.FILL)
            assertEquals(-1.0, override())
            assertEquals(0.0, panscan(), "FILL panscan")
            assertEquals("no", subUseMargins(), "FILL sub-use-margins")
            assertEquals("no", subAssForceMargins(), "FILL sub-ass-force-margins")

            // 16:9: the Android spelling of the override — the plan's reduced
            // fraction STRING ("177:100" = 1.77), not the raw double the
            // desktop used to write.
            engine.setAspectRatio(AspectRatio.RATIO_16_9)
            assertEquals(1.77, override(), "16:9 override (177:100)")
            assertEquals(0.0, panscan(), "16:9 panscan")
            assertEquals("no", subUseMargins(), "16:9 sub-use-margins")
            assertEquals("no", subAssForceMargins(), "16:9 sub-ass-force-margins")

            // AUTO: fully cleared back to the stream's own ratio.
            engine.setAspectRatio(AspectRatio.AUTO)
            assertEquals(-1.0, override())
            assertEquals(0.0, panscan())
            assertEquals("no", subUseMargins())
            assertEquals("no", subAssForceMargins())
        } finally {
            engine.release()
        }
    }

    // ── tracks: the Android selectTrack decision logic ──────────────────────

    @Test
    fun selectTrack_negativeIndex_deselectsTheAndroidWay() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null", "pause" to "yes"),
        )
        try {
            engine.load(
                PlaybackRequest(
                    uri = twoAudioTracks().absolutePath,
                    title = "track-deselect",
                    externalSubtitles = listOf(srtSidecar()),
                ),
            )
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            // The track refresh is coalesced (Android parity) — the catalog
            // lands one debounce window after the FILE_LOADED burst.
            waitUntil(5_000) { engine.availableTracks.value.any { it.type == TrackType.SUBTITLE } }
            val subTrack = engine.availableTracks.value.firstOrNull { it.type == TrackType.SUBTITLE }
            assertNotNull(subTrack, "sidecar subtitle track listed")
            val ctx = engine.liveMpvHandle() as Pointer

            // Audio: drive a real transition first — mpv auto-selects track 1;
            // pick track 2, then deselect. The Android arm writes `aid=auto`,
            // which re-resolves to the DEFAULT track (1); the former desktop
            // body wrote the raw "-1", which mpv REJECTS (choice property) —
            // the selection stayed stuck on track 2.
            waitUntil(5_000) { MpvLib.getPropertyString(ctx, "aid") == "1" }
            engine.selectTrack(TrackType.AUDIO, 2)
            waitUntil(5_000) { MpvLib.getPropertyString(ctx, "aid") == "2" }
            engine.selectTrack(TrackType.AUDIO, -1)
            waitUntil(5_000) { MpvLib.getPropertyString(ctx, "aid") == "1" }

            // Subtitle: a positive selection first (sid N, sub-visibility
            // re-enabled), then the deselect — `sid` back to "no" while
            // sub-visibility keeps the app-set state (only a positive
            // selection re-enables it).
            engine.setNativeSubtitlesVisible(false)
            assertEquals("no", MpvLib.getPropertyString(ctx, "sub-visibility"))
            engine.selectTrack(TrackType.SUBTITLE, subTrack.index)
            waitUntil(5_000) { MpvLib.getPropertyString(ctx, "sid") == subTrack.index.toString() }
            waitUntil(5_000) { MpvLib.getPropertyString(ctx, "sub-visibility") == "yes" }
            engine.selectTrack(TrackType.SUBTITLE, -1)
            waitUntil(5_000) { MpvLib.getPropertyString(ctx, "sid") == "no" }
            assertEquals(
                "yes",
                MpvLib.getPropertyString(ctx, "sub-visibility"),
                "deselect must not flip sub-visibility (Android arm leaves it to the app)",
            )
        } finally {
            engine.release()
        }
    }

    @Test
    fun selectTrack_positiveSubtitle_writesTheIdAndReEnablesVisibility() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null", "pause" to "yes"),
        )
        try {
            engine.load(
                PlaybackRequest(
                    uri = clip().absolutePath,
                    title = "track-select",
                    externalSubtitles = listOf(srtSidecar()),
                ),
            )
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            // Coalesced catalog (see the deselect test above).
            waitUntil(5_000) { engine.availableTracks.value.any { it.type == TrackType.SUBTITLE } }
            val subTrack = engine.availableTracks.value.firstOrNull { it.type == TrackType.SUBTITLE }
            assertNotNull(subTrack, "sidecar subtitle track listed")

            // The app hid native subs; an explicit user pick must undo that
            // (Android's sub-visibility re-enable on selection).
            val ctx = engine.liveMpvHandle() as Pointer
            engine.setNativeSubtitlesVisible(false)
            assertEquals("no", MpvLib.getPropertyString(ctx, "sub-visibility"))

            engine.selectTrack(TrackType.SUBTITLE, subTrack.index)
            waitUntil(5_000) {
                MpvLib.getPropertyString(ctx, "sid") == subTrack.index.toString()
            }
            waitUntil(5_000) { MpvLib.getPropertyString(ctx, "sub-visibility") == "yes" }
        } finally {
            engine.release()
        }
    }

    @Test
    fun selectTrack_positiveAudio_writesTheId() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null", "pause" to "yes"),
        )
        try {
            // The dual-audio fixture: a real transition between two real audio
            // tracks — the int-first write discipline (string form only as the
            // fallback) must land the picked id, not mpv's default.
            engine.load(
                PlaybackRequest(
                    uri = twoAudioTracks().absolutePath,
                    title = "track-select-audio",
                ),
            )
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            val ctx = engine.liveMpvHandle() as Pointer
            waitUntil(5_000) { MpvLib.getPropertyString(ctx, "aid") == "1" }
            engine.selectTrack(TrackType.AUDIO, 2)
            waitUntil(5_000) { MpvLib.getPropertyString(ctx, "aid") == "2" }
        } finally {
            engine.release()
        }
    }

    // ── duration: the shared engine→server ladder ───────────────────────────

    @Test
    fun durationMs_fallsBackToTheServerRuntime_whenTheDemuxerResolvesNone() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null"),
        )
        try {
            // A missing file: the demuxer never resolves a duration, so the
            // ladder must keep serving the server runtime the load captured
            // from [PlaybackRequest.serverDurationMs].
            engine.load(
                PlaybackRequest(
                    uri = File(tempDir, "does-not-exist.mkv").absolutePath,
                    title = "ladder-server",
                    serverDurationMs = 654_321,
                ),
            )
            assertEquals(654_321L, engine.durationMs, "server rung serves synchronously")
            waitUntil(2_000) { engine.durationMs == 654_321L }
        } finally {
            engine.release()
        }
    }

    @Test
    fun durationMs_prefersTheDemuxerDuration_overTheServerRuntime() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null", "pause" to "yes"),
        )
        try {
            engine.load(
                PlaybackRequest(
                    uri = testMedia("test-tone.wav"),
                    title = "ladder-demuxer",
                    serverDurationMs = 999_000,
                ),
            )
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            // ~6 s tone: the demuxer's duration wins over the (bogus) server
            // value — the ladder's positive-engine-duration guard.
            waitUntil(10_000) { engine.durationMs in 5_000..7_000 }
        } finally {
            engine.release()
        }
    }

    // ── stop: the server-duration rung dies with the item (Android parity) ──

    @Test
    fun stop_resetsTheServerDurationRung_likeTheAndroidEngine() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null"),
        )
        try {
            // A missing file keeps the demuxer rung dead, so the ladder serves
            // the server runtime — proving the rung is live before the stop.
            engine.load(
                PlaybackRequest(
                    uri = File(tempDir, "does-not-exist-stop.mkv").absolutePath,
                    title = "stop-server-duration",
                    serverDurationMs = 654_321,
                ),
            )
            assertEquals(654_321L, engine.durationMs, "server rung live before stop")
            engine.stop()
            assertEquals(
                0L,
                engine.durationMs,
                "the server rung must reset with the stopped item (the former drift: " +
                    "durationValue reset, serverDurationMs served the dead item forever)",
            )
        } finally {
            engine.release()
        }
    }

    // ── tracks: the coalesced refresh (shared TrackRefreshCoalescer) ────────

    @Test
    fun trackRefresh_burstCollapses_andTheCoalescedBodyPublishesTheSettledCatalog() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null", "pause" to "yes"),
        )
        try {
            // The dual-audio fixture: the burst flips between real audio tracks.
            engine.load(
                PlaybackRequest(
                    uri = twoAudioTracks().absolutePath,
                    title = "coalesced-refresh",
                    externalSubtitles = listOf(srtSidecar()),
                ),
            )
            waitUntil(15_000) { engine.playbackState.value == EnginePlaybackState.READY }
            // The FILE_LOADED + sub-add + track-list-observer burst collapses
            // into ONE debounced rebuild — it must still fire and publish the
            // settled catalog (sidecar included), not drop the burst.
            waitUntil(5_000) { engine.availableTracks.value.any { it.type == TrackType.SUBTITLE } }

            // A second observer burst: three rapid audio selections land within
            // one debounce window. The single coalesced rebuild must converge
            // on the FINAL selection state — track 2 selected — with the full
            // catalog intact (nothing lost to the collapse).
            engine.selectTrack(TrackType.AUDIO, 2)
            engine.selectTrack(TrackType.AUDIO, 1)
            engine.selectTrack(TrackType.AUDIO, 2)
            waitUntil(5_000) {
                engine.availableTracks.value
                    .firstOrNull { it.type == TrackType.AUDIO && it.isSelected }?.index == 2
            }
            assertTrue(
                engine.availableTracks.value.any { it.type == TrackType.SUBTITLE },
                "the coalesced rebuild publishes the complete catalog",
            )
            val ctx = engine.liveMpvHandle() as Pointer
            assertEquals("2", MpvLib.getPropertyString(ctx, "aid"), "mpv's own state agrees with the catalog")
        } finally {
            engine.release()
        }
    }

    // ── fixtures (the MpvDesktopEngineVideoTest shapes) ─────────────────────

    @field:TempDir
    lateinit var tempDir: File

    /** Dual-audio fixture: the audio deselect must have a non-default track to leave. */
    private fun twoAudioTracks(): File {
        val out = File(tempDir, "track-deselect.mka")
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
            "sine=frequency=440:duration=4",
            "-f",
            "lavfi",
            "-i",
            "sine=frequency=880:duration=4",
            "-map",
            "0:a",
            "-map",
            "1:a",
            out.absolutePath,
        ).redirectErrorStream(true)
        val running = try {
            process.start()
        } catch (_: Throwable) {
            null
        }
        assumeTrue(running != null, { "ffmpeg not on PATH — cannot generate the synthetic fixture" })
        running!!.waitFor()
        assumeTrue(out.isFile && out.length() > 0, { "ffmpeg failed generating the synthetic fixture" })
        return out
    }

    private fun clip(): File {
        val out = File(tempDir, "track-select-test.mp4")
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
        val srt = File(tempDir, "track-select.srt")
        srt.writeText(
            """
            1
            00:00:00,200 --> 00:00:01,800
            First cue line
            """.trimIndent(),
        )
        return SubtitleSource(
            url = srt.absolutePath,
            label = "track-select-subs",
            language = "en",
            mimeType = "application/x-subrip",
            id = "track-select-subs",
        )
    }
}

private fun testMedia(name: String): String =
    java.io.File(
        java.util.Objects.requireNonNull(
            MpvDesktopEngineAndroidParityTest::class.java.classLoader.getResource("media/$name"),
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
