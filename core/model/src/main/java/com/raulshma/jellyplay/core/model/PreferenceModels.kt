package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class MediaStreamSelection(
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
)

@Immutable
@Serializable
enum class HomeMode {
    VIDEO,
    MUSIC,
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
)

@Immutable
@Serializable
enum class DecoderMode(val displayName: String) {
    HW_PREFERRED("Hardware (Preferred)"),
    HW_ONLY("Hardware Only"),
    SW_ONLY("Software Only"),
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
enum class StreamingQuality(val displayName: String) {
    AUTO("Auto"),
    LOW_360P("360p"),
    SD_480P("480p"),
    HD_720P("720p"),
    FHD_1080P("1080p"),
    UHD_4K("4K"),
}

@Immutable
@Serializable
enum class PlaybackMode(val displayName: String) {
    AUTO("Auto"),
    FORCE_DIRECT_PLAY("Force Direct Play"),
    FORCE_TRANSCODE("Force Transcode"),
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
