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
 * Manual override for the adaptive window-size layout (issue #166). AUTO
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
