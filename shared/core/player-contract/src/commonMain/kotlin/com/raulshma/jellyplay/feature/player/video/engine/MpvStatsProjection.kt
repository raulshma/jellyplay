package com.raulshma.jellyplay.feature.player.video.engine

/**
 * The mpv property READ seam the shared [MpvStatsProjection] folds over —
 * one member per wire format both mpv hosts expose (the Android
 * `is.xyz.mpv.MPV` wrapper, the desktop JNA binding). Each read returns null
 * when the property is unset or the native read fails; implementations
 * absorb their platform's failure mode (throw vs. error code) so the
 * projection is exception-free by construction (the [MpvPropertySurface]
 * write-side twin).
 */
interface MpvStatsReads {

    /** MPV_FORMAT_STRING read; null when unset/failed. */
    fun readString(name: String): String?

    /** MPV_FORMAT_DOUBLE read (mpv converts integer properties); null when unset/failed. */
    fun readDouble(name: String): Double?

    /** MPV_FORMAT_INT64 read; null when unset/failed. */
    fun readLong(name: String): Long?
}

/**
 * The ONE mpv property-name table + sanitize fold behind both mpv engines'
 * [EngineVideoStats] (the [MpvEventFold] precedent: policy in the contract,
 * thin adapters per host). The two engines previously re-typed ~15 property
 * reads per tick with drift; the unifications this projection pins:
 *
 * - **audioCodec** reads `audio-codec` — the descriptive codec string — and
 *   NOT `audio-codec-name` (the desktop's former bare canonical name).
 *   `ExoPlayerEngine` fills the same field with a descriptive string
 *   (`codecFromMime`, e.g. `mp4a-latm`), not a bare name: the richer form is
 *   the field's cross-engine contract.
 * - **videoResolution** reads the canonical `video-params/w`+`/h` map (the
 *   Android engine's former `width`/`height` reads are its legacy aliases).
 * - **bufferSizeBytes** prefers the demuxer's true byte count
 *   (`demuxer-cache-state/total-bytes`) and falls back to the
 *   bitrate × buffer-health estimate the Android engine formerly used as its
 *   ONLY source (bytes = bits/8 × seconds).
 * - **Sanitize guards** are the Android/ExoPlayer set everywhere: bitrates,
 *   frame rate, sample rate, channel count and display-fps must be > 0 to
 *   surface (0 = mpv's "unset"); avsync and vo-delay must be non-zero. The
 *   desktop formerly passed raw values through (its `0` renders showed up as
 *   noise instead of a blank row).
 * - **videoDecoder** keeps the RAW `hwdec-current` value — `"no"` is the
 *   honest "software decoding active" verdict in a stats overlay (the
 *   desktop's former `takeIf { it != "no" }` hid the row instead).
 *
 * What deliberately stays ADAPTER-side (declared divergences, not drift):
 * the Android engine's cheap-scalar change-guard + `FULL_STATS_REREAD`
 * cadence (a polling optimization over THIS projection's output), and the
 * desktop's HDR badge trio (`videoHdrType` / `hdrOutputActive` /
 * `hdrOutputNotice` — its display-target probe, merged via
 * `EngineVideoStats.copy`). Both hosts now also fill the mpv-generic fields
 * only one of them used to read (`totalVideoFrames` / `voDelayedMs` /
 * `estimatedBandwidthBps` on desktop) — they are plain mpv properties, not
 * platform behavior.
 *
 * Pinned by `MpvStatsProjectionTest` through a fake reads seam.
 */
object MpvStatsProjection {

    /**
     * The mpv cheap scalars the Android engine's change-guard samples each
     * tick BEFORE committing to the full property re-read (the guard itself
     * stays adapter-side; these are its inputs, produced by
     * [readGuardScalars] so the guard's property names cannot drift from the
     * projection's either).
     */
    data class GuardScalars(
        val videoBitrateBps: Int?,
        val audioBitrateBps: Int?,
        val droppedFrames: Long,
        val totalVideoFrames: Long,
    )

    private object NAMES {
        const val VIDEO_FORMAT = "video-format"
        const val HWDEC_CURRENT = "hwdec-current"
        const val VIDEO_PARAMS_W = "video-params/w"
        const val VIDEO_PARAMS_H = "video-params/h"
        const val CONTAINER_FPS = "container-fps"
        const val VIDEO_BITRATE = "video-bitrate"
        const val AUDIO_CODEC = "audio-codec"
        const val AUDIO_SAMPLE_RATE = "audio-params/samplerate"
        const val AUDIO_CHANNELS = "audio-params/channel-count"
        const val AUDIO_BITRATE = "audio-bitrate"
        const val DEMUXER_TOTAL_BYTES = "demuxer-cache-state/total-bytes"
        const val DECODER_FRAME_DROP_COUNT = "decoder-frame-drop-count"
        const val DISPLAYED_FRAME_COUNT = "displayed-frame-count"
        const val TOTAL_AVSYNC = "total-avsync"
        const val DISPLAY_FPS = "display-fps"
        const val VO_DELAYED = "vo-delayed"
        const val FRAME_DROP_COUNT = "frame-drop-count"
    }

    /**
     * The guard-input scalars: video/audio bitrate (mpv reports 0 for unset —
     * the >0 guard normalizes to null) plus the two monotonic frame counters
     * (0 on a failed read, matching a fresh handle).
     */
    fun readGuardScalars(reads: MpvStatsReads): GuardScalars = GuardScalars(
        videoBitrateBps = reads.readDouble(NAMES.VIDEO_BITRATE)?.takeIf { it > 0 }?.toInt(),
        audioBitrateBps = reads.readDouble(NAMES.AUDIO_BITRATE)?.takeIf { it > 0 }?.toInt(),
        droppedFrames = reads.readLong(NAMES.DECODER_FRAME_DROP_COUNT) ?: 0L,
        totalVideoFrames = reads.readLong(NAMES.DISPLAYED_FRAME_COUNT) ?: 0L,
    )

    /**
     * The full stats projection over the shared property-name table.
     * [scalars] are the already-read guard inputs (bitrates/counters are
     * read exactly once per tick); [positionMs]/[bufferedPositionMs] come
     * from the adapter's own cached/mirrored values, never from extra reads.
     */
    fun project(
        reads: MpvStatsReads,
        scalars: GuardScalars,
        positionMs: Long,
        bufferedPositionMs: Long,
    ): EngineVideoStats {
        val combinedBitrateBps = (scalars.videoBitrateBps ?: 0) + (scalars.audioBitrateBps ?: 0)
        return EngineVideoStats(
            videoCodec = reads.readString(NAMES.VIDEO_FORMAT),
            videoDecoder = reads.readString(NAMES.HWDEC_CURRENT),
            videoResolution = resolution(reads),
            videoFrameRate = reads.readDouble(NAMES.CONTAINER_FPS)?.takeIf { it > 0.0 }?.toFloat(),
            videoBitrate = scalars.videoBitrateBps,
            audioCodec = reads.readString(NAMES.AUDIO_CODEC),
            audioSampleRate = reads.readDouble(NAMES.AUDIO_SAMPLE_RATE)?.takeIf { it > 0.0 }?.toInt(),
            audioChannels = reads.readDouble(NAMES.AUDIO_CHANNELS)?.takeIf { it > 0.0 }?.toInt(),
            audioBitrate = scalars.audioBitrateBps,
            estimatedBandwidthBps = combinedBitrateBps.toLong(),
            droppedFrames = scalars.droppedFrames,
            totalVideoFrames = scalars.totalVideoFrames,
            bufferedPositionMs = bufferedPositionMs,
            bufferSizeBytes = bufferSizeBytes(reads, positionMs, bufferedPositionMs, combinedBitrateBps),
            avsyncMs = reads.readDouble(NAMES.TOTAL_AVSYNC)?.toFloat()?.takeIf { it != 0f },
            displayFps = reads.readDouble(NAMES.DISPLAY_FPS)?.takeIf { it > 0.0 }?.toFloat(),
            voDelayedMs = reads.readDouble(NAMES.VO_DELAYED)?.toFloat()?.takeIf { it != 0f },
            voFrameDropCount = reads.readLong(NAMES.FRAME_DROP_COUNT),
        )
    }

    /** `"{w}x{h}"` from the canonical video-params map; null unless both are > 0. */
    private fun resolution(reads: MpvStatsReads): String? {
        val w = reads.readDouble(NAMES.VIDEO_PARAMS_W)?.takeIf { it > 0.0 }?.toInt() ?: return null
        val h = reads.readDouble(NAMES.VIDEO_PARAMS_H)?.takeIf { it > 0.0 }?.toInt() ?: return null
        return "${w}x${h}"
    }

    /**
     * The demuxer's true buffered byte count when it exposes one; otherwise
     * the bitrate × buffer-health estimate (bytes = bits/8 × seconds), 0 when
     * neither source has a value.
     */
    private fun bufferSizeBytes(
        reads: MpvStatsReads,
        positionMs: Long,
        bufferedPositionMs: Long,
        combinedBitrateBps: Int,
    ): Long {
        reads.readDouble(NAMES.DEMUXER_TOTAL_BYTES)?.toLong()?.takeIf { it > 0L }?.let { return it }
        val bufferHealthMs = (bufferedPositionMs - positionMs).coerceAtLeast(0L)
        return if (combinedBitrateBps > 0) combinedBitrateBps * bufferHealthMs / 8000 else 0L
    }
}
