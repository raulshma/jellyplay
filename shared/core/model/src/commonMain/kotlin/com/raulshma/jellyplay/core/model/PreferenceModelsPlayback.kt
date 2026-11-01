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
enum class StreamingQuality(override val displayName: String, val maxHeight: Int? = null) : HasDisplayName {
    AUTO("Auto"),
    LOW_360P("360p", 360),
    SD_480P("480p", 480),
    HD_720P("720p", 720),
    FHD_1080P("1080p", 1080),
    UHD_4K("4K", 2160),
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

/**
 * What happens to an interrupted playback session when the app cold-starts.
 * [OFF] discards the saved state (today's behavior); [ASK] offers the
 * restore on the first launch after the interruption; [AUTO] resumes the
 * saved position without asking. Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class SessionRestoreMode {
    OFF,
    ASK,
    AUTO,
}

/**
 * The HDR delivery posture for direct play: which HDR family may reach the
 * display and which wins when the device decodes more than one. [AUTO] keeps
 * the engine's default negotiation; the SDR arm forces tone mapping; the
 * family arms restrict direct play to one codec (falling back per the
 * playback-mode rules when the stream does not match). Persistence is the
 * enum NAME.
 */
@Immutable
@Serializable
enum class HdrFormatPreference {
    AUTO,
    PREFER_DV_OVER_HDR10,
    SDR_ONLY,
    HDR10_ONLY,
    DOLBY_VISION_ONLY,
}

/**
 * The preferred video codec when the server transcodes. [AUTO] lets the
 * server/device profile decide; the rest pin the transcode target so
 * bandwidth-constrained or compatibility-sensitive setups get a predictable
 * stream. Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class TranscodeVideoCodecPreference {
    AUTO,
    H264,
    HEVC,
    AV1,
}

/**
 * The preferred audio codec when the server transcodes. [AUTO] lets the
 * server/device profile decide; the rest pin the transcode target. Persistence
 * is the enum NAME.
 */
@Immutable
@Serializable
enum class TranscodeAudioCodecPreference {
    AUTO,
    AAC,
    AC3,
    EAC3,
    OPUS,
}

/**
 * The scrub-preview presentation for trickplay tiles. [BUBBLE] floats the
 * tile above the seek bar; [FILMSTRIP] docks a strip along the bar;
 * [FULLSCREEN] takes over the player surface while scrubbing. Persistence is
 * the enum NAME.
 */
@Immutable
@Serializable
enum class TrickplayMode {
    BUBBLE,
    FILMSTRIP,
    FULLSCREEN,
}

/**
 * What replaces the video while playback is paused for a long time (the
 * screensaver arm of the player). [OFF] keeps the paused frame; [CLOCK]
 * shows a wall clock; [LOGO] shows the app mark; [FADE_TO_BLACK] dims to
 * black (the burn-in-safe default posture when armed). Persistence is the
 * enum NAME.
 */
@Immutable
@Serializable
enum class PauseScreensaverMode {
    OFF,
    CLOCK,
    LOGO,
    FADE_TO_BLACK,
}

// ---------------------------------------------------------------------------
// Player chrome customization enums — the seek-bar / gesture / overlay knobs.
// The [StreamingQuality] style: a `displayName` face for the settings pickers
// (the screens render it directly), persistence is the enum NAME.
// ---------------------------------------------------------------------------

/** The player seek bar's thickness. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class PlayerSeekBarStyle(override val displayName: String) : HasDisplayName {
    DEFAULT("Standard"),
    TALL("Tall"),
}

/**
 * The brightness/volume swipe-gesture sensitivity curve's exponent: how much
 * of the drag's travel maps onto the adjusted value. Persistence is the enum
 * NAME.
 */
@Immutable
@Serializable
enum class PlayerSwipeSensitivity(override val displayName: String) : HasDisplayName {
    RELAXED("Relaxed"),
    DEFAULT("Default"),
    SENSITIVE("Sensitive"),
}

/** What a double-tap on the video surface's center zone does. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class PlayerCenterDoubleTapAction(override val displayName: String) : HasDisplayName {
    /** Reset the pinch zoom when zoomed, toggle play/pause otherwise (today's behavior). */
    AUTO("Auto"),
    /** Always toggle play/pause. */
    PLAY_PAUSE("Play / Pause"),
    /** Always reset the pinch zoom. */
    ZOOM_RESET("Zoom Reset"),
}

/**
 * The width of the player surface's left/right double-tap seek zones (the
 * center zone takes the remainder). Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class PlayerDoubleTapZoneBias(override val displayName: String) : HasDisplayName {
    /** 35% / 65% split — the long-standing inline thresholds. */
    DEFAULT("Default"),
    /** 50% / 50% split. */
    EVEN("Even"),
    /** 25% / 75% split — bigger seek zones, smaller center. */
    EXTREME("Extreme"),
}

/** The hold-to-speed indicator pill's chrome. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class PlayerHoldSpeedPillStyle(override val displayName: String) : HasDisplayName {
    /** The speed readout ("2.0x"). */
    DEFAULT("Default"),
    /** An icon-only compact pill. */
    MINIMAL("Minimal"),
}

// ---------------------------------------------------------------------------
// The second QoL wave's chrome-behavior enums: the transport/HUD cadence and
// preset families. Same style as the block above — a `displayName` face for
// the settings pickers, persistence is the enum NAME; the behavior folds
// (which ms/step value each arm maps to) live beside the policy that consumes
// them (PlayerChromePolicies, DoubleTapHoldSeekPolicy, the screens), not here.
// ---------------------------------------------------------------------------

/** The TV form factor's controls-auto-hide timeout multiplier over the base pref. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class TvTimeoutScale(override val displayName: String) : HasDisplayName {
    /** TV keeps controls up for exactly the base timeout (touch parity). */
    X1("1x"),
    /** TV keeps controls up twice as long — the long-standing fold. */
    X2("2x"),
    /** TV keeps controls up three times as long. */
    X3("3x"),
}

/**
 * The hardware-keyboard modifier seek-step ladder's magnitude family
 * (Shift/Ctrl/Shift+Ctrl/Alt+Ctrl arrows plus the Home/End and PgUp/PgDn
 * rows). Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class KeyboardSeekPreset(override val displayName: String) : HasDisplayName {
    /** Smaller coherent steps for precise scrubbing. */
    CONSERVATIVE("Conservative"),
    /** VLC's ladder — the long-standing values. */
    DEFAULT("Default"),
    /** Larger coherent steps for long-distance jumping. */
    AGGRESSIVE("Aggressive"),
}

/** The double-tap-and-hold continuous seek's repeat cadence family. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class HoldSeekCadence(override val displayName: String) : HasDisplayName {
    /** Slower repeats (600ms start/normal, 250ms accelerating) — easier to land on a target. */
    RELAXED("Relaxed"),
    /** The long-standing cadence (400ms start/normal, 150ms accelerating). */
    DEFAULT("Default"),
    /** Faster repeats (300ms start/normal, 100ms accelerating) — big distances fast. */
    RAPID("Rapid"),
}

/** The hold-speed pill's clearance above the bottom controls. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class SpeedPillClearance(override val displayName: String) : HasDisplayName {
    /** 140dp — sits closer to the bottom controls. */
    COMPACT("Compact"),
    /** 180dp — the long-standing clearance. */
    DEFAULT("Default"),
    /** 240dp — sits well clear of the bottom controls. */
    ROOMY("Roomy"),
}

/** The pause-details panel's fade-in/out durations. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class PauseFadeStyle(override val displayName: String) : HasDisplayName {
    /** 100ms in / 150ms out — snappy. */
    FAST("Fast"),
    /** 200ms in / 300ms out — the long-standing motion. */
    DEFAULT("Default"),
    /** 400ms in / 500ms out — gentle. */
    SLOW("Slow"),
}

/** The player controls' button-cluster arrangement. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class ControlLayout(override val displayName: String) : HasDisplayName {
    /** prev | play | next center row, full secondary cluster — today's arrangement. */
    DEFAULT("Default"),
    /** The center row mirrored: next | play | prev. */
    SWAPPED_SEEK("Swapped Seek"),
    /** The secondary cluster drops its non-essential buttons (PiP, rotate). */
    MINIMAL("Minimal"),
}

/** The pinch-zoom ceiling. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class MaxZoom(override val displayName: String) : HasDisplayName {
    X2("2x"),
    /** The long-standing 1..3 clamp. */
    X3("3x"),
    X4("4x"),
}

/** The engine's position-poll cadence family (controls-visible / controls-hidden ms). Persistence is the enum NAME. */
@Immutable
@Serializable
enum class PollCadence(override val displayName: String) : HasDisplayName {
    /** 500ms visible / 2000ms hidden — battery-friendlier. */
    LOW_POWER("Low Power"),
    /** 250ms visible / 1000ms hidden — the long-standing cadence. */
    DEFAULT("Default"),
    /** 125ms visible / 500ms hidden — smoother seek bar. */
    RESPONSIVE("Responsive"),
}

/** The buffering spinner's display rule. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class BufferingVeil(override val displayName: String) : HasDisplayName {
    /** Shown on stalls while playback is intended — the long-standing rule. */
    DEFAULT("Default"),
    /** Shown on ANY stall (even a user-paused one). */
    ALWAYS("Always"),
    /** Never shown. */
    HIDDEN("Hidden"),
}

/** The speed sheet's chip preset family. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class SpeedPresetSet(override val displayName: String) : HasDisplayName {
    /** Five common speeds. */
    COMPACT("Compact"),
    /** The full chip ladder — the long-standing list. */
    DEFAULT("Default"),
    /** Finer steps between 0.25x and 3x. */
    EXTENDED("Extended"),
}
