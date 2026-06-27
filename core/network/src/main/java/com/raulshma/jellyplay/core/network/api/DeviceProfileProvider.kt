package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.PlayerType
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.deviceprofile.buildDeviceProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the Jellyfin [org.jellyfin.sdk.model.api.DeviceProfile] sent with
 * the `PlaybackInfo` request and with client-capabilities registration.
 *
 * The profile tells the server which container/codec combinations the
 * player can decode natively (so it picks Direct Play) and which
 * transcode target to fall back to. It is varied by [PlayerType]:
 *
 * - **MPV** uses libav/ffmpeg under the hood and can decode almost
 *   everything, so it advertises a fixed permissive direct-play profile.
 * - **ExoPlayer / LibVLC** rely on the hardware/media framework codecs,
 *   so the direct-play codec set is derived from
 *   [DeviceCodecCapabilities] (an actual `MediaCodecList` query) rather
 *   than a hardcoded list. This avoids over-claiming codecs the device
 *   cannot decode (which would make the server hand back an unplayable
 *   direct stream) and under-claiming (which would trigger needless
 *   transcoding).
 *
 * The candidate codec lists per container mirror the official Jellyfin
 * Android client's `AVAILABLE_VIDEO_CODECS` / `AVAILABLE_AUDIO_CODECS`.
 */
@Singleton
class DeviceProfileProvider @Inject constructor(
    private val deviceCodecCapabilities: DeviceCodecCapabilities,
) {

    fun forPlayer(playerType: PlayerType): org.jellyfin.sdk.model.api.DeviceProfile = when (playerType) {
        PlayerType.MPV -> mpvProfile
        PlayerType.EXO_PLAYER, PlayerType.LIBVLC -> hardwareProfile
        PlayerType.EXTERNAL -> hardwareProfile
    }

    /**
     * Profile used for client-capabilities registration when no player is
     * known yet (defaults to the device-derived hardware profile).
     */
    val default: org.jellyfin.sdk.model.api.DeviceProfile get() = hardwareProfile

    private val subtitleFormats = listOf(
        "srt" to SubtitleDeliveryMethod.EXTERNAL,
        "subrip" to SubtitleDeliveryMethod.EXTERNAL,
        "ass" to SubtitleDeliveryMethod.EXTERNAL,
        "ssa" to SubtitleDeliveryMethod.EXTERNAL,
        "vtt" to SubtitleDeliveryMethod.EXTERNAL,
        "webvtt" to SubtitleDeliveryMethod.EXTERNAL,
        "ttml" to SubtitleDeliveryMethod.EXTERNAL,
    )

    private val mpvProfile = buildDeviceProfile {
        name = "jellyplay-mpv"

        transcodingProfile {
            type = DlnaProfileType.VIDEO
            context = EncodingContext.STREAMING
            container = "ts"
            protocol = MediaStreamProtocol.HLS
            videoCodec("hevc", "h264")
            audioCodec("aac", "ac3", "eac3", "mp3", "opus", "flac")
            copyTimestamps = false
            enableSubtitlesInManifest = true
        }

        directPlayProfile {
            type = DlnaProfileType.VIDEO
            container(
                "asf", "avi", "flv", "m4v", "mkv", "mov", "mp4", "mpeg",
                "mpg", "ogm", "ogv", "ts", "vob", "webm", "wmv", "3gp",
            )
            videoCodec(
                "h264", "hevc", "vp8", "vp9", "av1", "mpeg1video", "mpeg2video",
                "mpeg4", "msmpeg4v3", "vc1", "wmv2", "wmv3",
            )
            audioCodec(
                "aac", "ac3", "eac3", "flac", "mp3", "mp2", "opus", "vorbis",
                "dca", "dts", "alac", "pcm_s16le", "pcm_s24le", "truehd",
                "wmav2",
            )
        }

        subtitleFormats.forEach { (format, method) -> subtitleProfile(format, method) }
    }

    /**
     * Built from [DeviceCodecCapabilities]: each candidate codec is only
     * advertised for direct play if the device actually exposes a decoder
     * for it. Lazily computed once (codec availability is fixed for the
     * process lifetime).
     */
    private val hardwareProfile: org.jellyfin.sdk.model.api.DeviceProfile by lazy {
        val videoCodecs = HARDWARE_VIDEO_CANDIDATES.intersect(deviceCodecCapabilities.supportedVideoCodecs)
        val audioCodecs = HARDWARE_AUDIO_CANDIDATES.intersect(deviceCodecCapabilities.supportedAudioCodecs)
        buildDeviceProfile {
            name = "jellyplay-hardware"

            transcodingProfile {
                type = DlnaProfileType.VIDEO
                context = EncodingContext.STREAMING
                container = "ts"
                protocol = MediaStreamProtocol.HLS
                videoCodec("h264", "hevc")
                audioCodec("aac", "ac3", "eac3", "mp3")
                copyTimestamps = false
                enableSubtitlesInManifest = true
            }

            directPlayProfile {
                type = DlnaProfileType.VIDEO
                container("mp4", "mkv", "ts", "mov", "webm", "m4v", "mpegts", "flv", "3gp")
                if (videoCodecs.isNotEmpty()) videoCodec(*videoCodecs.toTypedArray())
                if (audioCodecs.isNotEmpty()) audioCodec(*audioCodecs.toTypedArray())
            }

            subtitleFormats.forEach { (format, method) -> subtitleProfile(format, method) }
        }
    }

    private companion object {
        /** Candidate video codecs intersected with the device decoder list. */
        private val HARDWARE_VIDEO_CANDIDATES = setOf(
            "h264", "hevc", "vp8", "vp9", "av1", "mpeg2video", "mpeg4", "h263",
        )
        /** Candidate audio codecs intersected with the device decoder list. */
        private val HARDWARE_AUDIO_CANDIDATES = setOf(
            "aac", "ac3", "eac3", "mp3", "opus", "vorbis", "flac", "alac", "raw",
        )
    }
}
