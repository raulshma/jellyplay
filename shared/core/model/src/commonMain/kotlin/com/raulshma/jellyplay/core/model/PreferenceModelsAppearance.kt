package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The appearance preference enums: theme shape (mode, style, contrast) and
 * the accessibility vocabulary (font scale, date format, color-blind mode,
 * handedness, window-class override).
 */

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

/**
 * Manual override for the adaptive window-size layout. AUTO
 * keeps the measured [com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass];
 * PHONE / TABLET clamp it so phones can opt into the expanded two-pane shell
 * and tablets into the compact single-pane one, as many apps allow.
 */
@Immutable
@Serializable
enum class LayoutMode(override val displayName: String) : HasDisplayName {
    AUTO("Auto"),
    PHONE("Phone"),
    TABLET("Tablet"),
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

/**
 * The TV overscan safe-area calibration: the percentage padding the
 * TV shell applies on every edge so rows/rails/text never clip at panels that
 * cut into the picture (the Android TV "overscan" guidance —
 * developer.android.com/design/tv). Each edge insets by [percent] of its
 * screen dimension (width for the horizontal pair, height for the vertical
 * one — see core:ui's `TvOverscan.overscanSafeAreaPadding`). Only ever
 * consumed on the TV form factor; the default is the guideline 5%.
 */
@Immutable
@Serializable
enum class TvOverscan(val displayName: String, val percent: Int) {
    OFF("Off", 0),
    FIVE("5%", 5),
    TEN("10%", 10),
}

/**
 * The TV shell's UI scale: a multiplier applied to the composition density on
 * the TV form factor, so EVERY dp-sized surface (cards, rows, padding,
 * chrome) and every sp-sized text shrinks together — the density-normalization
 * lever for TVs whose stock density makes the layout read oversized
 * (1080p panels running a 1.5x+ density are the common case). Applied once in
 * `JellyPlayPreferenceTheme` (the single channel both activity hosts share);
 * inert off the TV form factor. The shipped default is [SMALL] — the stock
 * TV density renders the layout noticeably oversized at 1080p — and
 * [DEFAULT] keeps the pre-scale behavior. Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class TvUiScale(val displayName: String, val factor: Float) {
    COMPACT("Compact (75%)", 0.75f),
    SMALL("Small (85%)", 0.85f),
    DEFAULT("Default (100%)", 1f),
    LARGE("Large (115%)", 1.15f),
}

/**
 * The detail screen's people-display density: the cast rail's item geometry
 * (media-detail header) and the Cast & Crew grid's column minimum share the
 * same three-step vocabulary. Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class DetailDensity(val displayName: String) {
    COMPACT("Compact"),
    DEFAULT("Default"),
    LARGE("Large"),
}

/**
 * App-wide backdrop dim level: a MULTIPLIER over each backdrop surface's own
 * scrim alpha (detail backdrop, home backdrop), so DEFAULT (1.0) reproduces
 * the shipped look and the other steps scale it — NONE removes the dim
 * entirely, HEAVIER deepens it for bright rooms. Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class BackdropDimLevel(val displayName: String, val multiplier: Float) {
    NONE("None", 0f),
    LIGHTER("Lighter", 0.6f),
    DEFAULT("Default", 1f),
    HEAVIER("Heavier", 1.4f),
}

/**
 * Confirmation-haptic strength remap applied by the shared haptic accessors:
 * LIGHT downgrades the confirm haptic to the lighter selection tick, STRONG
 * upgrades it to the heavier long-press buzz; MEDIUM is the shipped mapping.
 * Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class HapticsIntensity(val displayName: String) {
    LIGHT("Light"),
    MEDIUM("Medium"),
    STRONG("Strong"),
}

/**
 * Server image-request quality override applied by the shared media-image
 * loader (Jellyfin's `quality` query parameter): ORIGINAL sends no parameter
 * (the shipped behavior), the other steps cap the returned JPEG quality to
 * cut bandwidth and decode memory. Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class ImageQualityPreset(val displayName: String, val quality: Int?) {
    ORIGINAL("Original", null),
    HIGH_90("High (90%)", 90),
    BALANCED_70("Balanced (70%)", 70),
    LOW_50("Data saver (50%)", 50),
}

/**
 * Settings/list row density: a vertical-padding scale over the shared
 * SettingListItem family. Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class ListDensity(val displayName: String) {
    COMPACT("Compact"),
    DEFAULT("Default"),
    ROOMY("Roomy"),
}

/**
 * Press-feedback style for the app's clickable surfaces, carried by
 * `LocalIndication` at the theme root: DEFAULT keeps the Material ripple,
 * SUBTLE swaps it for a low-alpha tint wash, NONE removes the press
 * feedback entirely (a reduced-motion companion). Persistence is the enum
 * NAME.
 */
@Immutable
@Serializable
enum class PressEffectStyle(val displayName: String) {
    DEFAULT("Default ripple"),
    SUBTLE("Subtle tint"),
    NONE("None"),
}

/**
 * Scrollable-container overscroll effect: PLATFORM keeps the system
 * stretch/glow, NONE disables it app-wide. Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class OverscrollStyle(val displayName: String) {
    PLATFORM("Platform"),
    NONE("None"),
}

/**
 * The detail body's entrance-stagger speed: the per-section delay step the
 * shared entrance driver subtracts per stagger index. DEFAULT (0.045f) is the
 * shipped step ([DETAIL_STAGGER_STEP] in feature:details); FAST tightens the
 * cascade, SLOW stretches it. Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class DetailStaggerStyle(val displayName: String, val step: Float) {
    FAST("Fast", 0.03f),
    DEFAULT("Default", 0.045f),
    SLOW("Slow", 0.06f),
}

/**
 * The detail hero backdrop's decode target: the fixed 1920x1080 full-bleed
 * decode stepped down (smaller screens, memory) or up (4K TV crispness).
 * Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class DetailBackdropResolution(val displayName: String, val decodeWidth: Int, val decodeHeight: Int) {
    HD_1280("720p", 1280, 720),
    FHD_1920("1080p", 1920, 1080),
    QHD_2560("1440p", 2560, 1440),
}

/**
 * The chapter thumbnail row's card size on the detail body: a multiplier over
 * the shipped fixed card geometry (180 dp wide, 104 dp thumbnail). Persistence
 * is the enum NAME.
 */
@Immutable
@Serializable
enum class DetailChapterCardSize(val displayName: String, val scale: Float) {
    COMPACT("Compact", 0.8f),
    DEFAULT("Default", 1f),
    LARGE("Large", 1.2f),
}

/**
 * The person screen's filmography ordering. DEFAULT is the shipped
 * newest-first year sort; the other steps are explicit client-side orders over
 * the already-loaded credits. Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class FilmographySort(val displayName: String) {
    DEFAULT("Default (newest first)"),
    YEAR_DESC("Newest first"),
    YEAR_ASC("Oldest first"),
    ALPHABETICAL("Alphabetical"),
}

/**
 * The smart-play dispatch's candidate preference: which episode a Play press
 * targets first. DEFAULT is the shipped resume > next-up > replay order; the
 * other steps reorder the same candidates (the resolution still never lands on
 * a virtual episode). Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class SmartPlayPriority(val displayName: String) {
    DEFAULT("Default (resume first)"),
    NEXT_UP_FIRST("Next up first"),
    REPLAY_FIRST("Replay first"),
}

/**
 * Which preset of the vertical-options menu's entries the landscape/TV detail
 * rail promotes. DEFAULT is the shipped fixed set (download, mark watched,
 * trailer, watch party); COMPACT keeps the two offline/watch-state anchors;
 * VIEWING drops the download entry for stream-only users. The rail still only
 * renders entries the menu actually carries for the current item, and the
 * menu suppresses exactly the promoted ids (no dupes). Persistence is the
 * enum NAME.
 */
@Immutable
@Serializable
enum class PromotedRailSource(val displayName: String) {
    DEFAULT("Default"),
    COMPACT("Compact"),
    VIEWING("Viewing only"),
}
