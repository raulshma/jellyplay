package com.raulshma.jellyplay.core.datastore.appearance

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.ColorBlindMode
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.HandMode
import com.raulshma.jellyplay.core.model.LayoutMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.TvOverscan
import com.raulshma.jellyplay.core.datastore.spec.PreferencePlatformRule

/**
 * The appearance &amp; accessibility domain's single preference declaration:
 * one [PreferenceSpec] row per key `AppearanceStore` owns — the canonical
 * persisted key name (which is ALSO the legacy pre-typed-era wire name,
 * declared exactly once), the default, the reset category, and the read/write
 * encoding ([PreferenceSpec.plainBoolean] / [plainInt] / [plainFloat] /
 * [enumRow] / [derived]).
 *
 * `AppearanceStore` is derived from these rows: the `Keys` members rebuild
 * each row's typed key from its wire name, the `read` projection delegates
 * each slice field to its row encoding, the single-key setters and `restore`
 * delegate to the rows' derived writes, and `resetKeysFor` filters the rows by
 * reset category — so a row cannot drift from the machinery that reads or
 * writes it. The one migration-carrying read — `theme_variant`'s legacy
 * synthwave/soothing/monochrome boolean fallback — lives in the
 * [THEME_VARIANT] row's derived lambda, documented in place below.
 *
 * Not derivable by design (the accepted residuals, the videoplayer/playback
 * precedent):
 *  - the lowercase normalization of `theme_variant` writes stays at the
 *    setter/restore call sites — the setter is the clamp owner (the
 *    `plainFloat` transform precedent: the derived write stores the raw
 *    value, the row's [THEME_VARIANT] encode is the identity);
 *  - the variant-name → accent-row dispatch in `setVariantAccent` stays
 *    hand-written in the store (a cross-row routing table, not a row
 *    encoding).
 *
 * Adding a preference: one row here, one [AppearanceSlice] property, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings and reset lists,
 * not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object AppearancePreferenceSpecs {

    /**
     * Route kind shared by every settings-search row that deep-links into the
     * appearance settings screen — the plain id the feature layer maps to
     * `Route.AppearanceSettings()`.
     */
    const val ROUTE_APPEARANCE_SETTINGS = "appearance_settings"

    // ------------------------------------------------------------------
    // Theme engine
    // ------------------------------------------------------------------

    val THEME_MODE: PreferenceSpec<ThemeMode> = PreferenceSpec.enumRow(
        keyName = "theme_mode",
        default = ThemeMode.SYSTEM,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "theme_mode",
            titleKey = "ss_theme_mode_title",
            subtitleKey = "ss_theme_mode_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("theme", "mode", "light", "dark", "system", "black"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
        ),
    )

    val CONTRAST_LEVEL: PreferenceSpec<ContrastLevel> = PreferenceSpec.enumRow(
        keyName = "contrast_level",
        default = ContrastLevel.DEFAULT,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "contrast",
            titleKey = "ss_contrast_title",
            subtitleKey = "ss_contrast_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("contrast", "accessibility", "legibility", "readability"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    val DYNAMIC_THEMING: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "dynamic_theming",
        default = true,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "dynamic_theming",
            titleKey = "ss_dynamic_theming_title",
            subtitleKey = "ss_dynamic_theming_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("dynamic", "artwork", "colors", "theme", "wallpaper"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
        ),
    )

    val OLED_MODE: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "oled_mode",
        default = false,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "oled_mode",
            titleKey = "ss_oled_mode_title",
            subtitleKey = "ss_oled_mode_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("oled", "black", "amoled", "pure black", "battery"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
        ),
    )

    val PERFORMANCE_MODE: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "performance_mode",
        default = false,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "performance_mode",
            titleKey = "ss_performance_mode_title",
            subtitleKey = "ss_performance_mode_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("performance", "speed", "lag", "battery", "animations"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    // ------------------------------------------------------------------
    // Accents + theme style variant (with the legacy boolean pairing)
    // ------------------------------------------------------------------

    val ACCENT_COLOR_SWATCH: PreferenceSpec<String> = PreferenceSpec.derived(
        keyName = "accent_color_swatch",
        default = "dynamic",
        resetCategory = PreferenceResetCategory.APPEARANCE,
        read = { _, raw -> raw ?: "dynamic" },
        encode = { it },
        search = PreferenceSearchSpec(
            // The search hit reuses the enum's core_ui_* faces (the fold):
            // the catalog row declares no ss_*_title twin.
            titleKey = "core_ui_accent_color_title",
            id = "accent_color",
            subtitleKey = "core_ui_accent_color_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("accent", "color", "theme", "swatch", "palette", "customize"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
        ),
    )

    val COLOR_STYLE: PreferenceSpec<ColorStyle> = PreferenceSpec.enumRow(
        keyName = "color_style",
        default = ColorStyle.TONAL_SPOT,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            // The search hit reuses the enum's core_ui_* faces (the fold):
            // the catalog row declares no ss_*_title twin.
            titleKey = "core_ui_color_style_title",
            id = "color_style",
            subtitleKey = "core_ui_color_style_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("color style", "palette", "vibe", "generated", "mood", "theme"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
        ),
    )

    /**
     * The active theme style (a raw lowercase [com.raulshma.jellyplay.core
     * .designsystem.theme.ThemeVariant] name — the picker offers literal
     * variant strings, not an enum). Reads the stored name; on absence falls
     * back to the legacy single-purpose booleans ([SYNTHWAVE_MODE] /
     * [SOOTHING_MODE] / [MONOCHROME_MODE], declared beside this row) so
     * upgrades and old backups keep the user's theme with no migration
     * write. The legacy booleans are never written again; they remain in the
     * APPEARANCE reset list.
     *
     * The derived write is the identity — the lowercase normalization of
     * `setThemeVariant` / `restore` stays at those call sites (the setter is
     * the clamp owner; the persisted canonical form must stay lowercase for
     * the raw-string comparisons downstream).
     */
    val THEME_VARIANT: PreferenceSpec<String> = PreferenceSpec.derived(
        keyName = "theme_variant",
        default = "standard",
        resetCategory = PreferenceResetCategory.APPEARANCE,
        read = { prefs, raw ->
            raw ?: when {
                SYNTHWAVE_MODE.readFrom(prefs) -> "synthwave"
                SOOTHING_MODE.readFrom(prefs) -> "soothing"
                MONOCHROME_MODE.readFrom(prefs) -> "monochrome"
                else -> "standard"
            }
        },
        encode = { it },
        search = PreferenceSearchSpec(
            // The search hit restates the row's screen title (the fold): the
            // catalog row declares no ss_*_title twin.
            titleKey = "settings_theme_style",
            id = "theme_style",
            subtitleKey = "ss_theme_style_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("theme", "style", "variant", "synthwave", "soothing", "monochrome", "vivid", "aurora", "sakura", "vector", "pop", "pastel", "neon", "retro", "look"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
        ),
    )

    /**
     * The legacy `synthwave_mode` boolean surface. Not an [AppearanceSlice]
     * field — since the [THEME_VARIANT] raw string owns the projection, this
     * key is read only by the [THEME_VARIANT] row's migration (and reset with
     * the rest of the appearance keys); declared so its wire name, default
     * and reset participation live with the rows instead of as magic literals
     * (the playback domain's FORCE_DIRECT_PLAY precedent).
     */
    val SYNTHWAVE_MODE: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "synthwave_mode",
        default = false,
        resetCategory = PreferenceResetCategory.APPEARANCE,
    )

    val SYNTHWAVE_ACCENT: PreferenceSpec<String> = PreferenceSpec.derived(
        keyName = "synthwave_accent",
        default = "magenta",
        resetCategory = PreferenceResetCategory.APPEARANCE,
        read = { _, raw -> raw ?: "magenta" },
        encode = { it },
    )

    /** Legacy single-purpose boolean — see [SYNTHWAVE_MODE]. */
    val SOOTHING_MODE: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "soothing_mode",
        default = false,
        resetCategory = PreferenceResetCategory.APPEARANCE,
    )

    val SOOTHING_ACCENT: PreferenceSpec<String> = PreferenceSpec.derived(
        keyName = "soothing_accent",
        default = "ocean",
        resetCategory = PreferenceResetCategory.APPEARANCE,
        read = { _, raw -> raw ?: "ocean" },
        encode = { it },
    )

    /** Legacy single-purpose boolean — see [SYNTHWAVE_MODE]. */
    val MONOCHROME_MODE: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "monochrome_mode",
        default = false,
        resetCategory = PreferenceResetCategory.APPEARANCE,
    )

    val VIVID_ACCENT: PreferenceSpec<String> = PreferenceSpec.derived(
        keyName = "vivid_accent",
        default = "punch",
        resetCategory = PreferenceResetCategory.APPEARANCE,
        read = { _, raw -> raw ?: "punch" },
        encode = { it },
    )

    val AURORA_ACCENT: PreferenceSpec<String> = PreferenceSpec.derived(
        keyName = "aurora_accent",
        default = "emerald",
        resetCategory = PreferenceResetCategory.APPEARANCE,
        read = { _, raw -> raw ?: "emerald" },
        encode = { it },
    )

    val SAKURA_ACCENT: PreferenceSpec<String> = PreferenceSpec.derived(
        keyName = "sakura_accent",
        default = "rose",
        resetCategory = PreferenceResetCategory.APPEARANCE,
        read = { _, raw -> raw ?: "rose" },
        encode = { it },
    )

    val VECTOR_POP_ACCENT: PreferenceSpec<String> = PreferenceSpec.derived(
        keyName = "vector_pop_accent",
        default = "cobalt",
        resetCategory = PreferenceResetCategory.APPEARANCE,
        read = { _, raw -> raw ?: "cobalt" },
        encode = { it },
    )

    // ------------------------------------------------------------------
    // Accessibility + comfort toggles
    // ------------------------------------------------------------------

    val REDUCE_MOTION_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "reduce_motion_enabled",
        default = false,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "reduce_motion",
            titleKey = "ss_reduce_motion_title",
            subtitleKey = "ss_reduce_motion_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("motion", "reduce", "animations", "parallax", "effects"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    val BLUE_LIGHT_FILTER_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "blue_light_filter_enabled",
        default = false,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "blue_light_filter",
            titleKey = "ss_blue_light_filter_title",
            subtitleKey = "ss_blue_light_filter_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("blue light", "amber", "eye care", "night", "filter", "tint"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    val BLUE_LIGHT_FILTER_STRENGTH: PreferenceSpec<Float> = PreferenceSpec.plainFloat(
        keyName = "blue_light_filter_strength",
        default = 0.3f,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "blue_light_strength",
            titleKey = "ss_blue_light_strength_title",
            subtitleKey = "ss_blue_light_strength_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("blue light", "strength", "amber", "intensity", "overlay"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    val BACKDROP_THEME_MUSIC_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "backdrop_theme_music_enabled",
        default = false,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "theme_music",
            titleKey = "ss_theme_music_title",
            subtitleKey = "ss_theme_music_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("theme", "music", "backdrop", "ambience", "song", "score"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    /**
     * Haptics reset under `MISC_APP`, not `APPEARANCE` — the one row of this
     * store in another category (the drift the row declaration pins: the
     * pre-spec hand-written reset list had already lost it once).
     */
    val HAPTICS_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "haptics_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.MISC_APP,
        search = PreferenceSearchSpec(
            id = "haptics_enabled",
            titleKey = "ss_haptics_enabled_title",
            subtitleKey = "ss_haptics_enabled_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("haptic", "vibration", "feedback", "vibrate", "touch"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    /**
     * Not an APPEARANCE reset row — deliberately declared with a `null` reset
     * category: the EXPERIMENTAL reset LIST entry for this key is owned by
     * `ExperimentalStore` (the reset-owner ledger; the
     * `ExperimentalPreferenceSpecs.APP_LANGUAGE` split precedent). This store
     * owns the key's read/write surface; its reset contribution stays empty,
     * exactly as the hand-written predecessor's category lists did.
     */
    val SHOW_ADVANCED_SETTINGS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "show_advanced_settings",
        default = false,
        resetCategory = null,
    )

    // ------------------------------------------------------------------
    // Locale-facing + layout knobs
    // ------------------------------------------------------------------

    val DATE_FORMAT_PREFERENCE: PreferenceSpec<DateFormatPreference> = PreferenceSpec.enumRow(
        keyName = "date_format_preference",
        default = DateFormatPreference.SYSTEM,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "date_format",
            titleKey = "ss_date_format_title",
            subtitleKey = "ss_date_format_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("date", "format", "time", "calendar", "day", "month", "year", "display"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    val APP_FONT_SCALE: PreferenceSpec<AppFontScale> = PreferenceSpec.enumRow(
        keyName = "app_font_scale",
        default = AppFontScale.DEFAULT,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "font_scale",
            titleKey = "ss_font_scale_title",
            subtitleKey = "ss_font_scale_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("font", "size", "text", "scale", "accessibility", "readability", "large", "small"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    val SCHEDULED_THEME_START_HOUR: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "scheduled_theme_start_hour",
        default = 22,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "scheduled_start",
            titleKey = "ss_scheduled_start_title",
            subtitleKey = "ss_scheduled_start_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("theme", "schedule", "start", "hour", "day", "auto"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    val SCHEDULED_THEME_END_HOUR: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "scheduled_theme_end_hour",
        default = 7,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "scheduled_end",
            titleKey = "ss_scheduled_end_title",
            subtitleKey = "ss_scheduled_end_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("theme", "schedule", "end", "hour", "night", "auto"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    val COLOR_BLIND_MODE: PreferenceSpec<ColorBlindMode> = PreferenceSpec.enumRow(
        keyName = "color_blind_mode",
        default = ColorBlindMode.NONE,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "color_blind_mode",
            titleKey = "ss_color_blind_mode_title",
            subtitleKey = "ss_color_blind_mode_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("color", "blind", "daltonize", "accessibility", "protanopia", "deuteranopia", "tritanopia", "vision"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    val HAND_MODE: PreferenceSpec<HandMode> = PreferenceSpec.enumRow(
        keyName = "hand_mode",
        default = HandMode.RIGHT,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "hand_mode",
            titleKey = "ss_hand_mode_title",
            subtitleKey = "ss_hand_mode_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("hand", "left", "right", "handed", "accessibility", "mirror", "one-handed"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    val LAYOUT_MODE: PreferenceSpec<LayoutMode> = PreferenceSpec.enumRow(
        keyName = "layout_mode",
        default = LayoutMode.AUTO,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "layout_mode",
            titleKey = "ss_layout_mode_title",
            subtitleKey = "ss_layout_mode_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("layout", "tablet", "phone", "adaptive", "two pane", "window", "compact", "expanded"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            isAdvanced = true,
        ),
    )

    /**
     * The TV overscan safe-area calibration — the layout-override's
     * form-factor twin: a TV-only display knob, so its search entry is tagged
     * [PreferencePlatformRule.ANDROID_ONLY] (form factor is the runtime
     * `LocalTvMode` axis, not a platform) while the row's screen-side gate is
     * the feature layer's `RowAdmission.Tv`. The default is the guideline 5%.
     */
    val TV_OVERSCAN: PreferenceSpec<TvOverscan> = PreferenceSpec.enumRow(
        keyName = "tv_overscan",
        default = TvOverscan.FIVE,
        resetCategory = PreferenceResetCategory.APPEARANCE,
        search = PreferenceSearchSpec(
            id = "screen_fit",
            titleKey = "ss_screen_fit_title",
            subtitleKey = "ss_screen_fit_subtitle",
            categoryKey = "ss_cat_appearance",
            keywords = listOf("screen fit", "overscan", "safe area", "tv", "edge", "cut off", "calibration", "display", "border"),
            routeKind = ROUTE_APPEARANCE_SETTINGS,
            platformRule = PreferencePlatformRule.ANDROID_ONLY,
        ),
    )

    // ------------------------------------------------------------------
    // Settings-search standalone entries: catalog facts without a single
    // backing knob of this store.
    // ------------------------------------------------------------------

    /**
     * The theme-scheduler highlight-alias row: its screen face IS the
     * theme_mode row (the deep-link highlights through the feature's
     * alias table), so this is a catalog fact, not a knob declaration.
     */
    private val searchThemeScheduler = PreferenceSearchSpec(
        id = "theme_scheduler",
        titleKey = "ss_theme_scheduler_title",
        subtitleKey = "ss_theme_scheduler_subtitle",
        categoryKey = "ss_cat_appearance",
        keywords = listOf("theme", "scheduler", "day", "night", "auto", "time", "scheduled", "dark", "light"),
        routeKind = ROUTE_APPEARANCE_SETTINGS,
        isAdvanced = true,
    )

    /**
     * The per-variant accent picker row: it dispatches to the active
     * variant's accent row ([SYNTHWAVE_ACCENT] / [SOOTHING_ACCENT] /
     * [VIVID_ACCENT] / …) — a cross-row routing owned by the store, not a
     * knob of its own.
     */
    private val searchStyleAccent = PreferenceSearchSpec(
        id = "style_accent",
        titleKey = "ss_style_accent_title",
        subtitleKey = "ss_style_accent_subtitle",
        categoryKey = "ss_cat_appearance",
        keywords = listOf("accent", "color", "swatch", "synthwave", "soothing", "vivid", "aurora", "sakura", "vector", "neon", "theme"),
        routeKind = ROUTE_APPEARANCE_SETTINGS,
        isAdvanced = true,
    )

    /**
     * Every row this store declares, in [AppearanceSlice] property order
     * (with the legacy-only boolean rows beside the [THEME_VARIANT] row that
     * reads them). The reset derivation below and the derivation-integrity
     * test both iterate this — a row declared but forgotten here falls out of
     * reset coverage and is caught by the JVM reset-coverage guard.
     */
    val all: List<PreferenceSpec<*>> = listOf(
        DYNAMIC_THEMING,
        THEME_MODE,
        CONTRAST_LEVEL,
        OLED_MODE,
        PERFORMANCE_MODE,
        ACCENT_COLOR_SWATCH,
        COLOR_STYLE,
        THEME_VARIANT,
        SYNTHWAVE_MODE,
        SYNTHWAVE_ACCENT,
        SOOTHING_MODE,
        SOOTHING_ACCENT,
        MONOCHROME_MODE,
        VIVID_ACCENT,
        AURORA_ACCENT,
        SAKURA_ACCENT,
        VECTOR_POP_ACCENT,
        SHOW_ADVANCED_SETTINGS,
        REDUCE_MOTION_ENABLED,
        BLUE_LIGHT_FILTER_ENABLED,
        BLUE_LIGHT_FILTER_STRENGTH,
        BACKDROP_THEME_MUSIC_ENABLED,
        HAPTICS_ENABLED,
        DATE_FORMAT_PREFERENCE,
        APP_FONT_SCALE,
        SCHEDULED_THEME_START_HOUR,
        SCHEDULED_THEME_END_HOUR,
        COLOR_BLIND_MODE,
        HAND_MODE,
        LAYOUT_MODE,
        TV_OVERSCAN,
    )

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — `APPEARANCE` for every
     * key but [HAPTICS_ENABLED] (`MISC_APP`), while [SHOW_ADVANCED_SETTINGS]
     * carries no category here (its EXPERIMENTAL list entry is
     * `ExperimentalStore`'s — see that row's KDoc). The facade aggregates
     * these lists instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, in the settings feature's
     * catalog order (the order the retired hand records carried): every row
     * above that carries a [PreferenceSearchSpec], plus the two standalone
     * entries ([searchThemeScheduler], [searchStyleAccent]). The settings
     * feature derives its spec-backed catalog rows from this list over its
     * ordered record spine; the theme group's order here is the theme
     * group's catalog order (library_view_mode and nav_labels stay
     * feature-side residuals — their knobs live in stores without spec
     * machinery).
     */
    val searchEntries: List<PreferenceSearchSpec> = listOf(
        DATE_FORMAT_PREFERENCE.searchEntry(),
        APP_FONT_SCALE.searchEntry(),
        COLOR_BLIND_MODE.searchEntry(),
        HAND_MODE.searchEntry(),
        searchThemeScheduler,
        THEME_MODE.searchEntry(),
        THEME_VARIANT.searchEntry(),
        searchStyleAccent,
        DYNAMIC_THEMING.searchEntry(),
        OLED_MODE.searchEntry(),
        CONTRAST_LEVEL.searchEntry(),
        LAYOUT_MODE.searchEntry(),
        TV_OVERSCAN.searchEntry(),
        BACKDROP_THEME_MUSIC_ENABLED.searchEntry(),
        ACCENT_COLOR_SWATCH.searchEntry(),
        COLOR_STYLE.searchEntry(),
        SCHEDULED_THEME_START_HOUR.searchEntry(),
        SCHEDULED_THEME_END_HOUR.searchEntry(),
        HAPTICS_ENABLED.searchEntry(),
        PERFORMANCE_MODE.searchEntry(),
        REDUCE_MOTION_ENABLED.searchEntry(),
        BLUE_LIGHT_FILTER_ENABLED.searchEntry(),
        BLUE_LIGHT_FILTER_STRENGTH.searchEntry(),
    )

    /** The projection hook for [searchEntries]: a row's declared search entry. */
    private fun PreferenceSpec<*>.searchEntry(): PreferenceSearchSpec =
        requireNotNull(search) { "row '$keyName' declares no search entry" }
}
