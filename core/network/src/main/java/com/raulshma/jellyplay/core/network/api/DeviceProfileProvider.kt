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
 *   everything, so it advertises a permissive direct-play profile
 *   (adapted from Wholphin's `mpvDeviceProfile`).
 * - **ExoPlayer / LibVLC** rely on the hardware/media framework codecs,
 *   so they advertise the common natively-decodable set (H.264/HEVC in
 *   MP4/MKV/TS, AAC/AC3/EAC3/MP3 audio). Anything outside that set the
 *   server will remux or transcode.
 *
 * The profile is intentionally conservative on the hardware path: an
 * over-claim here (e.g. declaring AV1/HEVC 10-bit direct-playable when
 * the device decoder cannot) would make the server hand back a direct
 * stream that the player then fails to render. The
 * [com.raulshma.jellyplay.core.model.PlaybackMode.FORCE_DIRECT_PLAY]
 * failure fallback handles the residual edge cases.
 */
@Singleton
class DeviceProfileProvider @Inject constructor() {

    fun forPlayer(playerType: PlayerType): org.jellyfin.sdk.model.api.DeviceProfile = when (playerType) {
        PlayerType.MPV -> mpvProfile
        PlayerType.EXO_PLAYER, PlayerType.LIBVLC -> hardwareProfile
        PlayerType.EXTERNAL -> hardwareProfile
    }

    /**
     * Profile used for client-capabilities registration when no player is
     * known yet (defaults to the conservative hardware profile).
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

    private val hardwareProfile = buildDeviceProfile {
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
            container("mp4", "mkv", "ts", "mov", "webm", "m4v")
            videoCodec("h264", "hevc", "vp9", "av1", "mpeg2video", "mpeg4")
            audioCodec(
                "aac", "ac3", "eac3", "mp3", "opus", "vorbis", "flac", "alac",
            )
        }

        subtitleFormats.forEach { (format, method) -> subtitleProfile(format, method) }
    }
}
