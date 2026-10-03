package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The playback-delivery preference enums: stream quality / mode selection,
 * segment skip behavior triggers, and the per-codec audio-passthrough
 * vocabulary shared by every engine.
 */

/**
 * The per-item persisted audio/subtitle stream selection. The indices are the
 * server `MediaStream.index` values (or the `-1` "off" placeholder).
 *
 * The four nullable descriptor fields (additive — `ignoreUnknownKeys`
 * JSON and serializer defaults keep old blobs and old writers compatible)
 * snapshot what the stored index pointed at when it was written. On restore
 * the index is only trusted when it still resolves to a stream matching the
 * recorded label/language (± codec); a mismatch (server-side reorder,
 * transcode re-enumeration) demotes the load to remembered-track matching
 * instead of blindly selecting the stale index. Absent fields (legacy
 * entries) keep today's trust: the index only has to still point at a stream
 * of the same type.
 */
@Immutable
@Serializable
data class MediaStreamSelection(
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val audioLabel: String? = null,
    val audioLanguage: String? = null,
    val subtitleLabel: String? = null,
    val subtitleLanguage: String? = null,
)

@Immutable
@Serializable
enum class StreamingQuality(override val displayName: String) : HasDisplayName {
    AUTO("Auto"),
    LOW_360P("360p"),
    SD_480P("480p"),
    HD_720P("720p"),
    FHD_1080P("1080p"),
    UHD_4K("4K"),
}

@Immutable
@Serializable
enum class PlaybackMode(override val displayName: String) : HasDisplayName {
    AUTO("Auto"),
    FORCE_DIRECT_PLAY("Force Direct Play"),
    FORCE_TRANSCODE("Force Transcode"),
}

/**
 * Live TV stream delivery option. Unlike VOD [PlaybackMode], live tuners
 * cannot be served verbatim (their output is non-seekable), so Force Direct
 * Play is not offered — the real choice is whether the server re-encodes.
 */
@Immutable
@Serializable
enum class LiveStreamOption(override val displayName: String) : HasDisplayName {
    AUTO("Auto"),
    DIRECT_STREAM("Direct Stream"),
    TRANSCODE("Transcode"),
}

/**
 * Which copy serves playback when an item both has a completed download and
 * a reachable server. `PREFER_DOWNLOADED` is the historical behaviour (the
 * local file always wins); `PREFER_STREAMING` plays the server copy instead
 * whenever the device is online. Offline mode is untouched: with no server
 * to stream from, a usable download always plays.
 */
@Immutable
@Serializable
enum class OfflinePlaybackPreference(override val displayName: String) : HasDisplayName {
    PREFER_DOWNLOADED("Prefer Downloaded"),
    PREFER_STREAMING("Prefer Streaming"),
}

/**
 * Which "Still watching?" trigger arms are on. The episode arm asks for
 * confirmation after N consecutive auto-played episodes; the hours arm
 * upgrades the pre-existing pass-out protection's silent pause into the same
 * confirm overlay (the hours value stays the `video_pass_out_protection_hours`
 * key — one setting surface, two triggers). [OFF] keeps both silent.
 */
@Immutable
@Serializable
enum class StillWatchingMode(override val displayName: String) : HasDisplayName {
    OFF("Off"),
    EPISODES("Episodes"),
    HOURS("Hours"),
    BOTH("Episodes & Hours"),
}

/**
 * One toggle of the per-codec audio-passthrough allow-list. The master
 * `audio_passthrough` boolean gates passthrough as a whole; each codec here
 * decides whether THAT codec may be bitstreamed raw to the receiver (a
 * disabled codec is dropped from the engine's passthrough list and from the
 * server profile's direct-play audio set, so the server transcodes it to a
 * codec the user allows).
 *
 * The per-engine raw tokens ride the enum so every consumer (mpv
 * `audio-spdif`, libVLC `--codec`, the Jellyfin device profile) composes its
 * list from one table:
 *  - [mpvKey] — the mpv `audio-spdif` token (`dtshd` is mpv's historical
 *    alias for dts-hd, kept for byte parity with the legacy list).
 *  - [vlcKey] — the libVLC `--codec` allowlist token.
 *  - [jellyfinKeys] — the Jellyfin direct-play audio codec tokens this
 *    toggle covers (dts-hd rides ffmpeg's `dca` decoder; TrueHD is spelled
 *    `truehd` and `mlp` in the profile codec lists).
 */
@Immutable
@Serializable
enum class AudioPassthroughCodec(
    override val displayName: String,
    val mpvKey: String,
    val vlcKey: String,
    val jellyfinKeys: Set<String>,
) : HasDisplayName {
    AC3("Dolby Digital (AC3)", "ac3", "ac3", setOf("ac3")),
    EAC3("Dolby Digital Plus (E-AC3)", "eac3", "eac3", setOf("eac3")),
    DTS("DTS", "dts", "dts", setOf("dts")),
    DTS_HD("DTS-HD", "dtshd", "dtshd", setOf("dca")),
    TRUEHD("Dolby TrueHD", "truehd", "truehd", setOf("truehd", "mlp")),
    ;

    companion object {
        /** The full allow-list — the historical single-boolean behaviour. */
        val ALL: Set<AudioPassthroughCodec> = entries.toSet()
    }
}

/**
 * The maximum speaker layout the audio output may use. `AUTO` defers to the
 * source and the audio-effects chain; every other value caps the output —
 * a source with more channels is downmixed to the nearest allowed layout.
 * [channelCount] is the raw speaker count (the Jellyfin
 * `AudioChannels` LessThanEqual profile condition value); [mpvAudioChannelsKey]
 * is mpv's `audio-channels` layout token (`null` = mpv's `auto` default).
 */
@Immutable
@Serializable
enum class MaxAudioChannelsEnum(override val displayName: String, val channelCount: Int?, val mpvAudioChannelsKey: String?) : HasDisplayName {
    AUTO("Auto", null, null),
    MONO("Mono", 1, "mono"),
    STEREO("Stereo", 2, "stereo"),
    FIVE_POINT_ONE("5.1", 6, "5.1"),
    SEVEN_POINT_ONE("7.1", 8, "7.1"),
}
