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

    /**
     * @param pgsDirectPlay when `true`, image-based subtitles (PGS, HDMV-PGS,
     * VOBSUB, DVB) are advertised as direct-playable external profiles so the
     * server sends them unmodified (MPV renders them natively via libav). When
     * `false` (default), they are omitted from the profile so the server burns
     * them into the video track. Named for PGS (the common case) but covers
     * the full image-subtitle family.
     */
    fun forPlayer(
        playerType: PlayerType,
        pgsDirectPlay: Boolean = false,
    ): org.jellyfin.sdk.model.api.DeviceProfile = when (playerType) {
        PlayerType.MPV -> if (pgsDirectPlay) mpvProfilePgsDirectPlay else mpvProfileDefault
        PlayerType.EXO_PLAYER, PlayerType.LIBVLC -> hardwareProfile
        PlayerType.EXTERNAL -> hardwareProfile
    }

    /**
     * Subtitle delivery map shared by every profile: deliver SRT/ASS/VTT/etc.
     * as external side-loaded files rather than burning them in. Declared
     * before the eager [directPlayAll] initializer so it is non-null when
     * [directPlayAll]'s initializer runs (Kotlin initializes properties in
     * declaration order — a forward reference here would NPE on construction,
     * crashing the app at launch via Hilt's provider).
     */
    private val subtitleFormats = listOf(
        "srt" to SubtitleDeliveryMethod.EXTERNAL,
        "subrip" to SubtitleDeliveryMethod.EXTERNAL,
        "ass" to SubtitleDeliveryMethod.EXTERNAL,
        "ssa" to SubtitleDeliveryMethod.EXTERNAL,
        "vtt" to SubtitleDeliveryMethod.EXTERNAL,
        "webvtt" to SubtitleDeliveryMethod.EXTERNAL,
        "ttml" to SubtitleDeliveryMethod.EXTERNAL,
    )

    /**
     * "Direct play all" profile for [PlaybackMode.FORCE_DIRECT_PLAY]. no codec/container
     * restrictions, no transcoding profiles, and a 1 Gbps bitrate sentinel so
     * the server marks every media source as `supportsDirectPlay=true`. The
     * client then requests `/Videos/{id}/stream?static=true` verbatim.
     *
     * Consequence: the server hands back a static URL even for codecs the
     * player may not actually decode; the player surfaces a runtime error and
     * the UI offers a transcode fallback. This is the explicit trade-off of "force direct play" —
     * the user has asked the server not to transcode, so we trust the static
     * URL and let the player be the arbiter.
     */
    val directPlayAll: org.jellyfin.sdk.model.api.DeviceProfile = buildDeviceProfile {
        name = "jellyplay-direct-all"

        // No transcodingProfile → server has no transcode target and will not
        // report SupportsTranscoding. No codecProfile / containerProfile → no
        // restrictions shrink the direct-play set. An empty directPlayProfile
        // list means "allow direct play of everything" in Jellyfin's DLNA
        // resolution (the absence of a constraining profile is permissive).
        maxStreamingBitrate = 1_000_000_000

        subtitleFormats.forEach { (format, method) -> subtitleProfile(format, method) }
    }

    /**
     * Profile used for client-capabilities registration when no player is
     * known yet (defaults to the device-derived hardware profile).
     */
    val default: org.jellyfin.sdk.model.api.DeviceProfile get() = hardwareProfile

    /**
     * Both [mpvProfile] variants, lazily computed once each — the profile
     * content is process-static (libav's decoder set never changes at runtime),
     * so rebuilding it per `PlaybackInfo` request is pure overhead. Mirrors the
     * memoization of [hardwareProfile].
     */
    private val mpvProfileDefault: org.jellyfin.sdk.model.api.DeviceProfile by lazy {
        mpvProfile(pgsDirectPlay = false)
    }
    private val mpvProfilePgsDirectPlay: org.jellyfin.sdk.model.api.DeviceProfile by lazy {
        mpvProfile(pgsDirectPlay = true)
    }

    private fun mpvProfile(pgsDirectPlay: Boolean): org.jellyfin.sdk.model.api.DeviceProfile = buildDeviceProfile {
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

        // Live TV (HTSP / M3U / TVMosaic tuners) reports the source container
        // as "hls"; without an hls DirectPlayProfile the server forces a
        // transcode (ContainerNotSupported) and the user's "Direct Stream"
        // preference is silently ignored. MPV demuxes HLS natively via libav.
        liveHlsDirectPlayProfile()

        subtitleFormats.forEach { (format, method) -> subtitleProfile(format, method) }
        // Image-based subtitles can only be rendered locally by the MPV engine
        // (via libav's PGS/VOBSUB/DVB decoders). When the user opts into PGS
        // direct play we advertise the whole image-subtitle family as external
        // (deliver-as-is) profiles so the server populates a `deliveryUrl` for
        // them rather than burning them in; otherwise they are omitted, which
        // causes the server to burn the subtitles into the video during
        // transcoding. The setting is named for PGS (the most common case) but
        // covers VOBSUB/DVB/HDMV-PGS so Blu-ray/DVD rips are handled too.
        if (pgsDirectPlay) {
            subtitleProfile("pgssub", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("pgs", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("hdmv_pgs_subtitle", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("dvd_subtitle", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("vobsub", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("dvb_subtitle", SubtitleDeliveryMethod.EXTERNAL)
        }
    }

    /**
     * Built from [DeviceCodecCapabilities]: each candidate codec is only
     * advertised for direct play if the device actually exposes a decoder
     * for it. Lazily computed once (codec availability is fixed for the
     * process lifetime).
     *
     * The audio set additionally includes [FORCED_AUDIO_CODECS] — codecs
     * ExoPlayer handles but that are not reliably surfaced by
     * [android.media.MediaCodecList] (notably DTS/MLP/TrueHD, which have no
     * standard [android.media.MediaFormat] mime and are therefore never
     * detected, yet are extremely common in MKV movie rips). Without these,
     * the server transcodes every file carrying such audio. This mirrors the
     * official Jellyfin Android client's `FORCED_AUDIO_CODECS`.
     */
    private val hardwareProfile: org.jellyfin.sdk.model.api.DeviceProfile by lazy {
        val videoCodecs = HARDWARE_VIDEO_CANDIDATES.intersect(deviceCodecCapabilities.supportedVideoCodecs)
        val audioCodecs = HARDWARE_AUDIO_CANDIDATES.intersect(deviceCodecCapabilities.supportedAudioCodecs) + FORCED_AUDIO_CODECS
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
                // Containers ExoPlayer's bundled extractors can demux. Includes
                // MPEG-TS variants (m2ts/mts for Blu-ray BDAV / AVCHD) and
                // MPEG-PS (mpeg/mpg/vob) — omitting these made the server
                // transcode items in those containers even with supported codecs.
                container(
                    "mp4", "m4v", "mov",                 // MP4 / QuickTime family
                    "mkv",                                // Matroska
                    "webm",                               // WebM
                    "ts", "mpegts", "m2ts", "mts",       // MPEG-TS family
                    "mpeg", "mpg", "vob",                 // MPEG-PS family
                    "flv",                                // FLV
                    "3gp", "3g2", "3gpp",                 // 3GPP family
                )
                if (videoCodecs.isNotEmpty()) videoCodec(*videoCodecs.toTypedArray())
                if (audioCodecs.isNotEmpty()) audioCodec(*audioCodecs.toTypedArray())
            }

            // Live TV (HTSP / M3U / TVMosaic tuners) reports the source
            // container as "hls"; without an hls DirectPlayProfile the server
            // forces a transcode (ContainerNotSupported) and the user's
            // "Direct Stream" preference is silently ignored. ExoPlayer
            // decodes HLS via HlsMediaSource.
            liveHlsDirectPlayProfile()

            subtitleFormats.forEach { (format, method) -> subtitleProfile(format, method) }
        }
    }

    /**
     * DirectPlayProfile for the `hls` container reported by live-TV tuners
     * (HTSP / M3U / TVMosaic). Advertising the common live codec set here stops
     * the server from forcing a transcode (ContainerNotSupported) so the user's
     * "Direct Stream" preference is honoured. Both MPV (libav demux) and
     * ExoPlayer (HlsMediaSource) handle this container. Mirrors the official
     * web client's `Container: 'hls'` DirectPlayProfile.
     */
    private fun org.jellyfin.sdk.model.deviceprofile.DeviceProfileBuilder.liveHlsDirectPlayProfile() {
        directPlayProfile {
            type = DlnaProfileType.VIDEO
            container("hls")
            videoCodec("h264", "hevc")
            audioCodec("aac", "ac3", "eac3", "mp3")
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
        /**
         * Audio codecs advertised for direct play regardless of a
         * [DeviceCodecCapabilities] hit. Either ExoPlayer decodes them in
         * software, or the platform decoder exists but is not enumerated via a
         * recognisable [android.media.MediaFormat] mime (DTS, MLP, TrueHD).
         * Omitting these caused the server to transcode media whose audio the
         * device can in fact decode.
         */
        private val FORCED_AUDIO_CODECS = setOf(
            "aac", "ac3", "eac3", "mp3", "alac", "dts", "mlp", "truehd",
            "pcm_s8", "pcm_s16be", "pcm_s16le", "pcm_s24le", "pcm_s32le",
            "pcm_f32le", "pcm_alaw", "pcm_mulaw",
        )
    }
}
