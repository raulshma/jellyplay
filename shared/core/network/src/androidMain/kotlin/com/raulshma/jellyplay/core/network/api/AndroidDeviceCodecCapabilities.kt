package com.raulshma.jellyplay.core.network.api

import android.media.MediaCodecList
import android.media.MediaFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android [DeviceCodecCapabilities]: enumerates the device's hardware decoders
 * via [MediaCodecList](`REGULAR_CODECS`) and reports which Jellyfin codec
 * names the platform can decode natively (no software fallback). The mime→
 * codec-name map mirrors the Jellyfin naming convention used by the official
 * Android client's `CodecHelpers`.
 */
@Singleton
class AndroidDeviceCodecCapabilities @Inject constructor() : DeviceCodecCapabilities {

    /** Jellyfin video codec names the device decodes in hardware. */
    override val supportedVideoCodecs: Set<String> by lazy { collectCodecs().first }

    /** Jellyfin audio codec names the device decodes in hardware. */
    override val supportedAudioCodecs: Set<String> by lazy { collectCodecs().second }

    private fun collectCodecs(): Pair<Set<String>, Set<String>> {
        val video = mutableSetOf<String>()
        val audio = mutableSetOf<String>()
        return try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (info.isEncoder) continue
                for (mime in info.supportedTypes) {
                    val vCodec = mimeToVideoCodec(mime)
                    if (vCodec != null) {
                        video.add(vCodec)
                        continue
                    }
                    val aCodec = mimeToAudioCodec(mime)
                    if (aCodec != null) audio.add(aCodec)
                }
            }
            // PCM is handled by ExoPlayer's internal (software) renderer but
            // is universally "direct playable" as raw samples, so we always
            // count it — matches the official client's FORCED_AUDIO_CODECS.
            audio.addAll(PCM_CODECS)
            video.toSet() to audio.toSet()
        } catch (_: Throwable) {
            // If the framework query fails (rare — e.g. instrumented test
            // host without MediaCodec), fall back to an empty set so the
            // profile builder still produces a valid (if conservative)
            // profile rather than crashing.
            emptySet<String>() to emptySet()
        }
    }

    private fun mimeToVideoCodec(mime: String): String? = when (mime) {
        MediaFormat.MIMETYPE_VIDEO_MPEG2 -> "mpeg2video"
        MediaFormat.MIMETYPE_VIDEO_H263 -> "h263"
        MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "mpeg4"
        MediaFormat.MIMETYPE_VIDEO_AVC -> "h264"
        MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> "hevc"
        MediaFormat.MIMETYPE_VIDEO_VP8 -> "vp8"
        MediaFormat.MIMETYPE_VIDEO_VP9 -> "vp9"
        MediaFormat.MIMETYPE_VIDEO_AV1 -> "av1"
        else -> null
    }

    private fun mimeToAudioCodec(mime: String): String? = when (mime) {
        MediaFormat.MIMETYPE_AUDIO_AAC -> "aac"
        MediaFormat.MIMETYPE_AUDIO_AC3 -> "ac3"
        MediaFormat.MIMETYPE_AUDIO_EAC3 -> "eac3"
        MediaFormat.MIMETYPE_AUDIO_FLAC -> "flac"
        MediaFormat.MIMETYPE_AUDIO_MPEG -> "mp3"
        MediaFormat.MIMETYPE_AUDIO_OPUS -> "opus"
        MediaFormat.MIMETYPE_AUDIO_VORBIS -> "vorbis"
        MediaFormat.MIMETYPE_AUDIO_RAW -> "raw"
        else -> null
    }

    private companion object {
        val PCM_CODECS = setOf(
            "pcm_s8", "pcm_s16be", "pcm_s16le", "pcm_s24le", "pcm_s32le",
            "pcm_f32le", "pcm_alaw", "pcm_mulaw",
        )
    }
}
