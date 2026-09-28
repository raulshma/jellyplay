package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Marker for a preference enum (or other value) that carries a human-readable
 * [displayName]. Used by display helpers to render an enum's label without a
 * per-type `when` switch — anything implementing this is rendered via
 * [displayName], so newly added labeled enums are covered automatically.
 */
interface HasDisplayName {
    val displayName: String
}

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
enum class HomeMode {
    VIDEO,
    MUSIC,
}

/**
 * Played-status filter for library browsing. Maps onto Jellyfin's
 * `ItemFilter` (`IS_PLAYED` / `IS_UNPLAYED`) — see `LibraryApiClientImpl`.
 * Lived in `core/model` so the data + UI layers share one definition.
 */
@Immutable
@Serializable
enum class PlayedStatus(val displayName: String) {
    ALL("All"),
    PLAYED("Played"),
    UNPLAYED("Unplayed"),
}

@Immutable
@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    SCHEDULED,
}

@Immutable
@Serializable
enum class ColorStyle(val displayName: String) {
    TONAL_SPOT("Tonal Spot"),
    VIBRANT("Vibrant"),
    EXPRESSIVE("Expressive"),
    MUTED("Muted"),
    MONOCHROME("Monochrome"),
}

@Immutable
@Serializable
enum class ContrastLevel {
    DEFAULT,
    MEDIUM,
    HIGH,
}

@Immutable
@Serializable
enum class PlayerType(val displayName: String, val description: String) {
    EXO_PLAYER("ExoPlayer", "Built-in Media3 player with full controls"),
    MPV("mpv", "Embedded libmpv engine with broad codec & HDR support"),
    LIBVLC("LibVLC", "Embedded VLC engine for maximum format compatibility"),
    EXTERNAL("External", "Open in an external app (e.g. MX Player)"),
    ;

    companion object {
        fun fromStoredName(name: String): PlayerType = when (name) {
            "INTERNAL" -> EXO_PLAYER
            else -> entries.find { it.name == name } ?: EXO_PLAYER
        }
    }
}

/**
 * Which third-party player app the external hand-off targets when
 * [PlayerType.EXTERNAL] is the preferred player. `SYSTEM_CHOOSER` (the
 * default) keeps the historical `Intent.createChooser` hand-off; every other
 * arm carries the per-player launch component (`packageName`/`activity`) the
 * shell targets directly when the app is installed — falling back to the
 * chooser when it is not (the jellyfin-android auto-revert pattern).
 */
@Immutable
@Serializable
enum class ExternalPlayerApp(
    val displayName: String,
    val packageName: String,
    /** Launch activity class name; `null` for the chooser arm. */
    val activity: String?,
) {
    SYSTEM_CHOOSER("Ask every time", "", null),
    MPV("mpv-android", "is.xyz.mpv", "is.xyz.mpv.MPVActivity"),
    MX_PLAYER_FREE("MX Player", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.ActivityScreen"),
    MX_PLAYER_PRO("MX Player Pro", "com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ActivityScreen"),
    VLC("VLC", "org.videolan.vlc", "org.videolan.vlc.StartActivity"),
    MPV_KT("mpvKt", "live.mehiz.mpvkt", "live.mehiz.mpvkt.ui.player.PlayerActivity"),
    ;

    /** True when this arm carries a concrete launch component to resolve. */
    val isTargeted: Boolean get() = activity != null
}

@Immutable
@Serializable
data class SubtitleStyle(
    val applyCustomStyle: Boolean = false,
    val fontSize: Int = 24,
    val fontColor: SubtitleColor = SubtitleColor.WHITE,
    val backgroundColor: SubtitleColor = SubtitleColor.BLACK,
    val backgroundOpacity: Float = 0.0f,
    val edgeType: SubtitleEdgeType = SubtitleEdgeType.OUTLINE,
    val edgeColor: SubtitleColor = SubtitleColor.BLACK,
    val offsetMs: Long = 0L,
    val verticalPosition: Float = 0.05f,

    // --- ASS / rich styling additions (all default ⇒ back-compat with old DataStore) ---

    /** How user styling interacts with ASS embedded styles. Only honoured when [applyCustomStyle] is true. */
    val assOverride: AssOverrideMode = AssOverrideMode.SCALE,

    /** SAF uri to a user-picked .ttf/.otf; null ⇒ use the bundled fallback font. */
    val fontFamilyPath: String? = null,

    /** Parsed family name for display; null ⇒ "Bundled Default". */
    val fontFamilyName: String? = null,

    /**
     * Free-form text color (ARGB). When non-null, takes precedence over [fontColor].
     * Null on old DataStore entries ⇒ resolves to [fontColor].value, preserving legacy behavior.
     */
    val fontColorArgb: Int? = null,

    /** Free-form background color (ARGB); null ⇒ [backgroundColor].value. */
    val backgroundColorArgb: Int? = null,

    /** Free-form edge color (ARGB); null ⇒ [edgeColor].value. */
    val edgeColorArgb: Int? = null,

    /** Border/background style preset. */
    val borderStyle: SubtitleBorderStyle = SubtitleBorderStyle.OUTLINE_AND_SHADOW,

    /** Outline thickness or opaque-box border size. */
    val borderWidth: Float = 2.0f,

    /** Drop-shadow offset. */
    val shadowOffset: Float = 1.0f,

    val bold: Boolean = false,
    val italic: Boolean = false,
) {
    companion object {
        /**
         * Canonical default for the no-edit user. Identical to the zero-arg
         * constructor except [applyCustomStyle] is forced true so every engine
         * reads the stored style as authoritative rather than falling back to
         * its own hardcoded defaults.
         */
        val DEFAULT: SubtitleStyle = SubtitleStyle(applyCustomStyle = true)
    }
}

@Immutable
@Serializable
enum class DecoderMode(override val displayName: String) : HasDisplayName {
    HW_PREFERRED("Hardware (Preferred)"),
    HW_ONLY("Hardware Only"),
    SW_ONLY("Software Only"),
}

@Immutable
@Serializable
enum class AssOverrideMode {
    /** Keep ASS embedded colors/fonts/positioning; apply only user size+pos scaling. Default. */
    SCALE,

    /** Override ASS styling with the user's colors/fonts/edges. */
    FORCE,
}

@Immutable
@Serializable
enum class SubtitleBorderStyle {
    /** Outline + drop shadow (current default behavior). */
    OUTLINE_AND_SHADOW,

    /** Solid opaque box around each line. */
    OPAQUE_BOX,

    /** Semi-transparent background (uses backgroundOpacity). */
    BACKGROUND_BOX,
}

@Immutable
@Serializable
enum class SubtitleColor(val value: Int) {
    WHITE(0xFFFFFFFF.toInt()),
    YELLOW(0xFFFFFF00.toInt()),
    GREEN(0xFF00FF00.toInt()),
    CYAN(0xFF00FFFF.toInt()),
    RED(0xFFFF0000.toInt()),
    BLACK(0xFF000000.toInt()),
    BLUE(0xFF0000FF.toInt()),
}

@Immutable
@Serializable
enum class SubtitleEdgeType {
    NONE,
    OUTLINE,
    DROP_SHADOW,
    RAISED,
    DEPRESSED,
}

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

@Immutable
@Serializable
data class EqualizerSettings(
    val bandLevels: List<Int> = List(10) { 0 },
) {
    companion object {
        val BAND_FREQUENCIES = listOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)
    }
}

@Immutable
@Serializable
enum class EffectStrength(val displayName: String) {
    NONE("Off"),
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High"),
}

@Immutable
@Serializable
enum class OrientationMode(val displayName: String, val constant: String) {
    SENSOR_LANDSCAPE("Landscape", "sensor_landscape"),
    SENSOR_PORTRAIT("Portrait", "sensor_portrait"),
    SENSOR("Auto Rotate", "sensor"),
    LOCKED_LANDSCAPE("Locked Landscape", "locked_landscape"),
    LOCKED_PORTRAIT("Locked Portrait", "locked_portrait"),
}

/**
 * Where the brightness/volume indicator bar renders relative to the
 * gesture that triggered it. Gesture sides are fixed (left = brightness,
 * right = volume); this only controls the indicator placement.
 */
@Immutable
@Serializable
enum class GestureIndicatorSide(val displayName: String, val constant: String) {
    OPPOSITE("Opposite side", "opposite"),
    SAME("Same side", "same"),
}

@Immutable
@Serializable
enum class PreloadBufferSize(
    val displayName: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
) {
    LOW("Low (10s)", 10_000, 25_000),
    MEDIUM("Medium (25s)", 25_000, 50_000),
    HIGH("High (50s)", 50_000, 120_000),
    UNLIMITED("Unlimited", 50_000, 500_000),
}

@Immutable
@Serializable
enum class NetworkTimeoutPreset(
    val displayName: String,
    val connectSec: Long,
    val readSec: Long,
    val writeSec: Long,
) {
    FAST("Fast (5s connect / 10s read)", 5, 10, 10),
    DEFAULT("Default (15s)", 15, 15, 15),
    RELAXED("Relaxed (30s)", 30, 30, 30),
    VERY_RELAXED("Very Relaxed (60s)", 60, 60, 60),
}

@Immutable
@Serializable
enum class SyncPlayJoinBehavior(val displayName: String) {
    ALWAYS_JOIN("Always Join"),
    ASK("Always Ask"),
    NEVER_JOIN("Never Join"),
}

@Immutable
@Serializable
enum class CastingStrategy(val displayName: String) {
    PREFER_CAST("Prefer Google Cast"),
    PREFER_DLNA("Prefer DLNA"),
    ASK("Always Ask"),
}

@Immutable
@Serializable
enum class ContinueWatchingClickBehavior(val displayName: String) {
    DETAILS("Open Details"),
    PLAY("Resume Playback"),
    ASK("Always Ask"),
}

@Immutable
@Serializable
enum class MeteredNetworkBehavior(val displayName: String) {
    ALLOW("Allow Connection"),
    WARN("Warn Before Streaming"),
    BLOCK("Block Streaming"),
}

@Immutable
@Serializable
enum class DateFormatPreference(val displayName: String) {
    SYSTEM("System Default"),
    US("MM/dd/yyyy"),
    ISO("yyyy-MM-dd"),
    EU("dd/MM/yyyy"),
    LONG("MMMM d, yyyy"),
    SHORT("M/d/yy"),
}

@Immutable
@Serializable
enum class AppFontScale(val displayName: String, val scale: Float) {
    SMALL("Small (85%)", 0.85f),
    DEFAULT("Default (100%)", 1.0f),
    MEDIUM("Medium (115%)", 1.15f),
    LARGE("Large (130%)", 1.3f),
    EXTRA_LARGE("Extra Large (150%)", 1.5f),
}

@Immutable
@Serializable
enum class ColorBlindMode(val displayName: String) {
    NONE("None"),
    PROTANOPIA("Protanopia (Red-weak)"),
    DEUTERANOPIA("Deuteranopia (Green-weak)"),
    TRITANOPIA("Tritanopia (Blue-weak)"),
}

@Immutable
@Serializable
enum class HandMode(val displayName: String) {
    RIGHT("Right-handed (default)"),
    LEFT("Left-handed"),
}

@Immutable
@Serializable
data class DownloadScheduleWindow(
    val startHour: Int = 0,
    val endHour: Int = 6,
    val wifiOnly: Boolean = true,
)
