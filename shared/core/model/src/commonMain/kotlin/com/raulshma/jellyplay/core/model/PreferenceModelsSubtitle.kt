package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The subtitle preference models: the user style (shared verbatim by the
 * Language and HDR subtitle settings) and its supporting enums.
 */

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
