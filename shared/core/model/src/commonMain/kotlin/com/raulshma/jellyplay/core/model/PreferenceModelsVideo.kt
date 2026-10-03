package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The video-player preference enums: engine choice, transport/decode
 * defaults, and the touch-gesture vocabulary. The display-label seam for the
 * gesture/orientation/indicator rows lives in `core:ui`
 * (`PreferenceEnumNames`) — these enums deliberately carry no
 * `displayName`/`constant` pair: the labels were hardcoded English (and the
 * "constant" a dead second wire vocabulary; persistence is the enum NAME).
 */

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
enum class DecoderMode(override val displayName: String) : HasDisplayName {
    HW_PREFERRED("Hardware (Preferred)"),
    HW_ONLY("Hardware Only"),
    SW_ONLY("Software Only"),
}

/**
 * The default screen-orientation lock. Labels resolve through the
 * `PreferenceEnumNames` seam — this enum used to carry a hardcoded-English
 * `displayName` plus a raw wire `constant` that leaked verbatim into the
 * orientation picker's subtitles; both are gone.
 */
@Immutable
@Serializable
enum class OrientationMode {
    SENSOR_LANDSCAPE,
    SENSOR_PORTRAIT,
    SENSOR,
    LOCKED_LANDSCAPE,
    LOCKED_PORTRAIT,
}

/**
 * Where the brightness/volume indicator bar renders relative to the
 * gesture that triggered it. Gesture sides are fixed (left = brightness,
 * right = volume); this only controls the indicator placement. Labels resolve
 * through the `PreferenceEnumNames` seam.
 */
@Immutable
@Serializable
enum class GestureIndicatorSide {
    OPPOSITE,
    SAME,
}

/**
 * Which touch-gesture tiers the video player responds to. [TAP_ONLY] keeps
 * taps, double-tap seek, long-press hold-speed and pinch-zoom active while
 * disabling the single-finger swipe surface (swipe seek / brightness /
 * volume / edge swipe) — the granular split the legacy all-or-nothing
 * `video_gestures_enabled` boolean could not express. Labels resolve through
 * the `PreferenceEnumNames` seam; the datastore persists the enum NAME.
 */
@Immutable
@Serializable
enum class GestureMode {
    ALL,
    TAP_ONLY,
    NONE;

    /** Tap tier: taps, double-tap seek, long-press hold-speed, pinch-zoom. */
    val tapsEnabled: Boolean get() = this != NONE

    /** Swipe tier: single-finger seek / brightness / volume / edge swipe. */
    val swipesEnabled: Boolean get() = this == ALL
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
