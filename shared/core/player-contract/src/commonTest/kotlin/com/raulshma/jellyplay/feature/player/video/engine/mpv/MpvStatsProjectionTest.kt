package com.raulshma.jellyplay.feature.player.video.engine.mpv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the shared mpv stats projection — the ONE property-name table +
 * sanitize fold both mpv engines consume — through a fake [MpvStatsReads]
 * seam. The drifted names the engines formerly disagreed on are pinned by
 * asserting WHICH property each field read (see [MpvStatsProjection]'s KDoc
 * for the unification rationale).
 */
class MpvStatsProjectionTest {

    /** Scripted reads: name → value; unread names answer null; a log records asks. */
    private class FakeReads(private val values: Map<String, Any?> = emptyMap()) : MpvStatsReads {
        val asked = mutableListOf<String>()

        override fun readString(name: String): String? {
            asked += name
            @Suppress("UNCHECKED_CAST")
            return values[name] as? String
        }

        override fun readDouble(name: String): Double? {
            asked += name
            return (values[name] as? Number)?.toDouble()
        }

        override fun readLong(name: String): Long? {
            asked += name
            return (values[name] as? Number)?.toLong()
        }
    }

    @Test
    fun guardScalars_bitrateZeroNormalizesToNull_countersDefaultToZero() {
        val reads = FakeReads(
            mapOf(
                "video-bitrate" to 0.0,              // mpv's "unset"
                "audio-bitrate" to 1_500_000.0,
                "decoder-frame-drop-count" to null,  // read failure
                "displayed-frame-count" to 4_096L,
            ),
        )
        val scalars = MpvStatsProjection.readGuardScalars(reads)
        assertNull(scalars.videoBitrateBps)
        assertEquals(1_500_000, scalars.audioBitrateBps)
        assertEquals(0L, scalars.droppedFrames)
        assertEquals(4_096L, scalars.totalVideoFrames)
    }

    @Test
    fun project_fullTable_sanitizedAndTyped() {
        val reads = FakeReads(
            mapOf(
                "video-format" to "hevc",
                "hwdec-current" to "d3d11va",
                "video-params/w" to 3840.0,
                "video-params/h" to 2160.0,
                "container-fps" to 23.976,
                "video-bitrate" to 24_000_000.0,
                "audio-codec" to "eac3 (E-AC-3)",
                "audio-params/samplerate" to 48_000.0,
                "audio-params/channel-count" to 6.0,
                "audio-bitrate" to 640_000.0,
                "demuxer-cache-state/total-bytes" to 52_428_800.0,
                "decoder-frame-drop-count" to 3L,
                "displayed-frame-count" to 12_345L,
                "total-avsync" to -0.42,
                "display-fps" to 60.0,
                "vo-delayed" to 8.5,
                "frame-drop-count" to 7L,
            ),
        )
        val stats = MpvStatsProjection.project(
            reads = reads,
            scalars = MpvStatsProjection.readGuardScalars(reads),
            positionMs = 60_000L,
            bufferedPositionMs = 90_000L,
        )
        assertEquals("hevc", stats.videoCodec)
        assertEquals("d3d11va", stats.videoDecoder)
        assertEquals("3840x2160", stats.videoResolution)
        assertEquals(23.976f, stats.videoFrameRate)
        assertEquals(24_000_000, stats.videoBitrate)
        // The richer audio-codec string — NOT audio-codec-name.
        assertEquals("eac3 (E-AC-3)", stats.audioCodec)
        assertEquals(48_000, stats.audioSampleRate)
        assertEquals(6, stats.audioChannels)
        assertEquals(640_000, stats.audioBitrate)
        assertEquals((24_000_000 + 640_000).toLong(), stats.estimatedBandwidthBps)
        assertEquals(3L, stats.droppedFrames)
        assertEquals(12_345L, stats.totalVideoFrames)
        assertEquals(90_000L, stats.bufferedPositionMs)
        // The demuxer's true byte count wins over the estimate.
        assertEquals(52_428_800L, stats.bufferSizeBytes)
        assertEquals(-0.42f, stats.avsyncMs)
        assertEquals(60.0f, stats.displayFps)
        assertEquals(8.5f, stats.voDelayedMs)
        assertEquals(7L, stats.voFrameDropCount)
    }

    @Test
    fun project_driftedNames_readTheCanonicalProperties() {
        val reads = FakeReads(
            mapOf(
                "video-bitrate" to 1_000_000.0,
                "audio-bitrate" to 100_000.0,
                // A resolvable resolution so both halves of the video-params
                // pair are consulted (a null w short-circuits the h read).
                "video-params/w" to 1920.0,
                "video-params/h" to 1080.0,
            ),
        )
        MpvStatsProjection.project(
            reads = reads,
            scalars = MpvStatsProjection.readGuardScalars(reads),
            positionMs = 0L,
            bufferedPositionMs = 0L,
        )
        // The former desktop reads must NOT be asked.
        assertTrue("audio-codec-name" !in reads.asked, "audioCodec must read audio-codec, not audio-codec-name")
        assertTrue("width" !in reads.asked, "videoResolution must read video-params/w|h, not the width/height aliases")
        // The canonical ones must be.
        assertTrue("audio-codec" in reads.asked)
        assertTrue("video-params/w" in reads.asked)
        assertTrue("video-params/h" in reads.asked)
        assertTrue("demuxer-cache-state/total-bytes" in reads.asked)
    }

    @Test
    fun project_zeroAndNegativeValues_normalizeToNull() {
        val reads = FakeReads(
            mapOf(
                "container-fps" to 0.0,             // unset → null
                "video-bitrate" to 0.0,             // unset → null (guard scalars)
                "audio-params/samplerate" to 0.0,
                "audio-params/channel-count" to 0.0,
                "total-avsync" to 0.0,              // exactly zero = "no deviation yet"
                "display-fps" to 0.0,
                "vo-delayed" to 0.0,
                "video-params/w" to 1920.0,
                "video-params/h" to 0.0,            // half a resolution is no resolution
            ),
        )
        val stats = MpvStatsProjection.project(
            reads = reads,
            scalars = MpvStatsProjection.readGuardScalars(reads),
            positionMs = 0L,
            bufferedPositionMs = 0L,
        )
        assertNull(stats.videoFrameRate)
        assertNull(stats.videoBitrate)
        assertNull(stats.audioSampleRate)
        assertNull(stats.audioChannels)
        assertNull(stats.avsyncMs)
        assertNull(stats.displayFps)
        assertNull(stats.voDelayedMs)
        assertNull(stats.videoResolution)
    }

    @Test
    fun project_hwdecNo_staysRawAsTheSoftwareVerdict() {
        val reads = FakeReads(mapOf("hwdec-current" to "no"))
        val stats = MpvStatsProjection.project(
            reads = reads,
            scalars = MpvStatsProjection.readGuardScalars(reads),
            positionMs = 0L,
            bufferedPositionMs = 0L,
        )
        assertEquals("no", stats.videoDecoder)
    }

    @Test
    fun project_bufferSize_fallsBackToBitrateHealthEstimate() {
        // No demuxer byte count exposed → the estimate: bits/8 × seconds of
        // buffer health ahead of the playhead.
        val reads = FakeReads(mapOf("video-bitrate" to 800_000.0, "audio-bitrate" to 0.0))
        val stats = MpvStatsProjection.project(
            reads = reads,
            scalars = MpvStatsProjection.readGuardScalars(reads),
            positionMs = 100_000L,
            bufferedPositionMs = 110_000L, // 10 s of health at 800 kbps → 1 MB
        )
        assertEquals(800_000L * 10_000L / 8000L, stats.bufferSizeBytes)
    }

    @Test
    fun project_bufferSize_zeroWhenNoSourceHasAValue() {
        val reads = FakeReads(emptyMap())
        val stats = MpvStatsProjection.project(
            reads = reads,
            scalars = MpvStatsProjection.readGuardScalars(reads),
            positionMs = 0L,
            bufferedPositionMs = 60_000L,
        )
        assertEquals(0L, stats.bufferSizeBytes)
    }
}
