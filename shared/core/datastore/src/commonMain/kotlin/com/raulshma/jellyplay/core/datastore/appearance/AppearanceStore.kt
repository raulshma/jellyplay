package com.raulshma.jellyplay.core.datastore.appearance

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.ColorBlindMode
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.HandMode
import com.raulshma.jellyplay.core.model.LayoutMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

/**
 * Deep module owning the **appearance &amp; accessibility** preference domain:
 * theme/contrast/oled/dynamic/accent/color-style, the theme style variant
 * (standard/synthwave/soothing/monochrome/vivid/aurora/sakura/vector_pop) with
 * its per-variant accents, reduce-motion, blue-light filter,
 * font scale, date format, colour-blind mode, hand mode, haptics, scheduled
 * theme hours, backdrop theme music, performance mode, and the advanced-settings
 * toggle.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, setters, read projection, and reset list end-to-end. Mirrors the
 * `PlaybackStore` / `ServerIdentityStore` shape.
 *
 * **Spec derivation**: every key this store owns is declared exactly once as a row in
 * [AppearancePreferenceSpecs] (wire name, default, reset category, read/write
 * encoding) and the machinery below is derived from those rows — each [Keys]
 * member rebuilds its row's typed key from the row's wire name, each [read]
 * projection row delegates to its row encoding, the single-key setters and
 * [restore] delegate to the rows' derived writes, and [resetKeysFor] filters
 * the rows by reset category. The migration semantics (the legacy
 * synthwave/soothing/monochrome booleans → [AppearanceSlice.themeVariant]
 * fallback) live in the [AppearancePreferenceSpecs.THEME_VARIANT] row's
 * encoding now, next to the key it applies to.
 *
 * **Cross-key invariants owned here (hand-written by decision):**
 *  - [setThemeVariant] / `restore` normalize the variant to lowercase before
 *    writing the raw slot (the setter is the clamp owner).
 *  - [setVariantAccent] routes a variant name to the right accent row — a
 *    cross-row dispatch table, not a row encoding.
 *
 * **Theme style invariant:** a single `theme_variant` key selects the active
 * variant — variants are inherently mutually exclusive. The legacy
 * `synthwave_mode` / `soothing_mode` / `monochrome_mode` booleans are no longer
 * written but still drive the `themeVariant` derivation so existing installs
 * (and old backups) keep their theme; they remain in the reset list.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 *
 * **Residual (accepted):** the slice plumbing stays hand-written by the
 * no-reflection rule — adding a preference still means one
 * [AppearancePreferenceSpecs] row + one [AppearanceSlice] property + one
 * [read] / [restore] row (and its setter) + one write-through test line. See
 * the spec KDoc.
 */
class AppearanceStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    /**
     * The store's DataStore keys, each derived from its
     * [AppearancePreferenceSpecs] row — the member rebuilds the row's typed
     * key from the row's single-declared wire name (`Preferences.Key`
     * equality is name-based, so these interoperate with any hand-built key
     * of the same name). Kept as a plain object rather than folded into the
     * rows because it is the reflection anchor for the JVM reset-coverage
     * guard.
     */
    internal object Keys {
        val THEME_MODE = stringPreferencesKey(AppearancePreferenceSpecs.THEME_MODE.keyName)
        val CONTRAST_LEVEL = stringPreferencesKey(AppearancePreferenceSpecs.CONTRAST_LEVEL.keyName)
        val DYNAMIC_THEMING = booleanPreferencesKey(AppearancePreferenceSpecs.DYNAMIC_THEMING.keyName)
        val OLED_MODE = booleanPreferencesKey(AppearancePreferenceSpecs.OLED_MODE.keyName)
        val ACCENT_COLOR_SWATCH = stringPreferencesKey(AppearancePreferenceSpecs.ACCENT_COLOR_SWATCH.keyName)
        val COLOR_STYLE = stringPreferencesKey(AppearancePreferenceSpecs.COLOR_STYLE.keyName)
        val PERFORMANCE_MODE = booleanPreferencesKey(AppearancePreferenceSpecs.PERFORMANCE_MODE.keyName)
        val REDUCE_MOTION_ENABLED = booleanPreferencesKey(AppearancePreferenceSpecs.REDUCE_MOTION_ENABLED.keyName)
        val THEME_VARIANT = stringPreferencesKey(AppearancePreferenceSpecs.THEME_VARIANT.keyName)
        val SYNTHWAVE_MODE = booleanPreferencesKey(AppearancePreferenceSpecs.SYNTHWAVE_MODE.keyName)
        val SYNTHWAVE_ACCENT = stringPreferencesKey(AppearancePreferenceSpecs.SYNTHWAVE_ACCENT.keyName)
        val SOOTHING_MODE = booleanPreferencesKey(AppearancePreferenceSpecs.SOOTHING_MODE.keyName)
        val SOOTHING_ACCENT = stringPreferencesKey(AppearancePreferenceSpecs.SOOTHING_ACCENT.keyName)
        val MONOCHROME_MODE = booleanPreferencesKey(AppearancePreferenceSpecs.MONOCHROME_MODE.keyName)
        val VIVID_ACCENT = stringPreferencesKey(AppearancePreferenceSpecs.VIVID_ACCENT.keyName)
        val AURORA_ACCENT = stringPreferencesKey(AppearancePreferenceSpecs.AURORA_ACCENT.keyName)
        val SAKURA_ACCENT = stringPreferencesKey(AppearancePreferenceSpecs.SAKURA_ACCENT.keyName)
        val VECTOR_POP_ACCENT = stringPreferencesKey(AppearancePreferenceSpecs.VECTOR_POP_ACCENT.keyName)
        val BACKDROP_THEME_MUSIC_ENABLED = booleanPreferencesKey(AppearancePreferenceSpecs.BACKDROP_THEME_MUSIC_ENABLED.keyName)
        val BLUE_LIGHT_FILTER_ENABLED = booleanPreferencesKey(AppearancePreferenceSpecs.BLUE_LIGHT_FILTER_ENABLED.keyName)
        val BLUE_LIGHT_FILTER_STRENGTH = floatPreferencesKey(AppearancePreferenceSpecs.BLUE_LIGHT_FILTER_STRENGTH.keyName)
        val DATE_FORMAT_PREFERENCE = stringPreferencesKey(AppearancePreferenceSpecs.DATE_FORMAT_PREFERENCE.keyName)
        val APP_FONT_SCALE = stringPreferencesKey(AppearancePreferenceSpecs.APP_FONT_SCALE.keyName)
        val SCHEDULED_THEME_START_HOUR = intPreferencesKey(AppearancePreferenceSpecs.SCHEDULED_THEME_START_HOUR.keyName)
        val SCHEDULED_THEME_END_HOUR = intPreferencesKey(AppearancePreferenceSpecs.SCHEDULED_THEME_END_HOUR.keyName)
        val COLOR_BLIND_MODE = stringPreferencesKey(AppearancePreferenceSpecs.COLOR_BLIND_MODE.keyName)
        val HAND_MODE = stringPreferencesKey(AppearancePreferenceSpecs.HAND_MODE.keyName)
        val LAYOUT_MODE = stringPreferencesKey(AppearancePreferenceSpecs.LAYOUT_MODE.keyName)
        val HAPTICS_ENABLED = booleanPreferencesKey(AppearancePreferenceSpecs.HAPTICS_ENABLED.keyName)
        val SHOW_ADVANCED_SETTINGS = booleanPreferencesKey(AppearancePreferenceSpecs.SHOW_ADVANCED_SETTINGS.keyName)
    }

    val appearance: StateFlow<AppearanceSlice> =
        dataStore.sliceStateFlow(scope, seed = AppearanceSlice(), read = ::read)

    /**
     * Whether the settings screens expose their advanced sections. An
     * appearance-domain field surfaced independently so settings sub-screens
     * can read it without collecting the whole preference aggregate.
     */
    val showAdvancedSettings: StateFlow<Boolean> = appearance
        .map { it.showAdvancedSettings }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * The manual layout override (issue #166). Surfaced independently so the
     * desktop shell can rewire its adaptive locals without collecting (and
     * recomposing on) the whole appearance aggregate.
     */
    val layoutMode: StateFlow<LayoutMode> = appearance
        .map { it.layoutMode }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), LayoutMode.AUTO)

    /**
     * Pure read of the appearance fields from a raw [Preferences] snapshot,
     * each field delegated to its [AppearancePreferenceSpecs] row encoding
     * (including the theme-variant legacy-boolean fallback — the row's derived
     * read). Exposed so the facade can fold these into the whole
     * preference projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): AppearanceSlice = AppearanceSlice(
        dynamicTheming = AppearancePreferenceSpecs.DYNAMIC_THEMING.readFrom(prefs),
        themeMode = AppearancePreferenceSpecs.THEME_MODE.readFrom(prefs),
        contrastLevel = AppearancePreferenceSpecs.CONTRAST_LEVEL.readFrom(prefs),
        oledMode = AppearancePreferenceSpecs.OLED_MODE.readFrom(prefs),
        performanceMode = AppearancePreferenceSpecs.PERFORMANCE_MODE.readFrom(prefs),
        accentColorSwatch = AppearancePreferenceSpecs.ACCENT_COLOR_SWATCH.readFrom(prefs),
        colorStyle = AppearancePreferenceSpecs.COLOR_STYLE.readFrom(prefs),
        synthwaveAccent = AppearancePreferenceSpecs.SYNTHWAVE_ACCENT.readFrom(prefs),
        soothingAccent = AppearancePreferenceSpecs.SOOTHING_ACCENT.readFrom(prefs),
        themeVariant = AppearancePreferenceSpecs.THEME_VARIANT.readFrom(prefs),
        vividAccent = AppearancePreferenceSpecs.VIVID_ACCENT.readFrom(prefs),
        auroraAccent = AppearancePreferenceSpecs.AURORA_ACCENT.readFrom(prefs),
        sakuraAccent = AppearancePreferenceSpecs.SAKURA_ACCENT.readFrom(prefs),
        vectorPopAccent = AppearancePreferenceSpecs.VECTOR_POP_ACCENT.readFrom(prefs),
        showAdvancedSettings = AppearancePreferenceSpecs.SHOW_ADVANCED_SETTINGS.readFrom(prefs),
        reduceMotionEnabled = AppearancePreferenceSpecs.REDUCE_MOTION_ENABLED.readFrom(prefs),
        blueLightFilterEnabled = AppearancePreferenceSpecs.BLUE_LIGHT_FILTER_ENABLED.readFrom(prefs),
        blueLightFilterStrength = AppearancePreferenceSpecs.BLUE_LIGHT_FILTER_STRENGTH.readFrom(prefs),
        backdropThemeMusicEnabled = AppearancePreferenceSpecs.BACKDROP_THEME_MUSIC_ENABLED.readFrom(prefs),
        hapticsEnabled = AppearancePreferenceSpecs.HAPTICS_ENABLED.readFrom(prefs),
        dateFormatPreference = AppearancePreferenceSpecs.DATE_FORMAT_PREFERENCE.readFrom(prefs),
        appFontScale = AppearancePreferenceSpecs.APP_FONT_SCALE.readFrom(prefs),
        scheduledThemeStartHour = AppearancePreferenceSpecs.SCHEDULED_THEME_START_HOUR.readFrom(prefs),
        scheduledThemeEndHour = AppearancePreferenceSpecs.SCHEDULED_THEME_END_HOUR.readFrom(prefs),
        colorBlindMode = AppearancePreferenceSpecs.COLOR_BLIND_MODE.readFrom(prefs),
        handMode = AppearancePreferenceSpecs.HAND_MODE.readFrom(prefs),
        layoutMode = AppearancePreferenceSpecs.LAYOUT_MODE.readFrom(prefs),
    )

    // ------------------------------------------------------------------
    // Setters — single-key setters delegate to their row's derived write
    // (the encoding is the row's, not re-declared here); the lowercase
    // normalization and the per-variant accent routing live below,
    // hand-written.
    // ------------------------------------------------------------------

    suspend fun setDynamicTheming(enabled: Boolean) {
        dataStore.edit { AppearancePreferenceSpecs.DYNAMIC_THEMING.writeTo(it, enabled) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { AppearancePreferenceSpecs.THEME_MODE.writeTo(it, mode) }
    }

    suspend fun setContrastLevel(level: ContrastLevel) {
        dataStore.edit { AppearancePreferenceSpecs.CONTRAST_LEVEL.writeTo(it, level) }
    }

    suspend fun setOledMode(enabled: Boolean) {
        dataStore.edit { AppearancePreferenceSpecs.OLED_MODE.writeTo(it, enabled) }
    }

    suspend fun setAccentColorSwatch(swatch: String) {
        dataStore.edit { AppearancePreferenceSpecs.ACCENT_COLOR_SWATCH.writeTo(it, swatch) }
    }

    suspend fun setColorStyle(style: ColorStyle) {
        dataStore.edit { AppearancePreferenceSpecs.COLOR_STYLE.writeTo(it, style) }
    }

    suspend fun setPerformanceMode(enabled: Boolean) {
        dataStore.edit { AppearancePreferenceSpecs.PERFORMANCE_MODE.writeTo(it, enabled) }
    }

    suspend fun setReduceMotionEnabled(enabled: Boolean) {
        dataStore.edit { AppearancePreferenceSpecs.REDUCE_MOTION_ENABLED.writeTo(it, enabled) }
    }

    /**
     * Selects the active theme style (a [com.raulshma.jellyplay.core.designsystem.theme.ThemeVariant]
     * name in lowercase: "standard", "synthwave", "soothing", "monochrome",
     * "vivid", "aurora", "sakura", "vector_pop"). A single key — variants are
     * inherently mutually exclusive. Normalizes to lowercase so the persisted
     * canonical form stays stable for the raw-string comparisons downstream.
     */
    suspend fun setThemeVariant(variant: String) {
        dataStore.edit { AppearancePreferenceSpecs.THEME_VARIANT.writeTo(it, variant.lowercase()) }
    }

    suspend fun setSynthwaveAccent(accent: String) {
        dataStore.edit { AppearancePreferenceSpecs.SYNTHWAVE_ACCENT.writeTo(it, accent) }
    }

    suspend fun setSoothingAccent(accent: String) {
        dataStore.edit { AppearancePreferenceSpecs.SOOTHING_ACCENT.writeTo(it, accent) }
    }

    /** Persists the accent for the given themed variant; unknown variants are ignored. */
    suspend fun setVariantAccent(variant: String, accent: String) {
        val row = when (variant.lowercase()) {
            "synthwave" -> AppearancePreferenceSpecs.SYNTHWAVE_ACCENT
            "soothing" -> AppearancePreferenceSpecs.SOOTHING_ACCENT
            "vivid" -> AppearancePreferenceSpecs.VIVID_ACCENT
            "aurora" -> AppearancePreferenceSpecs.AURORA_ACCENT
            "sakura" -> AppearancePreferenceSpecs.SAKURA_ACCENT
            "vector_pop" -> AppearancePreferenceSpecs.VECTOR_POP_ACCENT
            else -> return
        }
        dataStore.edit { row.writeTo(it, accent) }
    }

    suspend fun setShowAdvancedSettings(enabled: Boolean) {
        dataStore.edit { AppearancePreferenceSpecs.SHOW_ADVANCED_SETTINGS.writeTo(it, enabled) }
    }

    suspend fun setBlueLightFilterEnabled(enabled: Boolean) {
        dataStore.edit { AppearancePreferenceSpecs.BLUE_LIGHT_FILTER_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setBlueLightFilterStrength(strength: Float) {
        dataStore.edit { AppearancePreferenceSpecs.BLUE_LIGHT_FILTER_STRENGTH.writeTo(it, strength) }
    }

    suspend fun setBackdropThemeMusicEnabled(enabled: Boolean) {
        dataStore.edit { AppearancePreferenceSpecs.BACKDROP_THEME_MUSIC_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { AppearancePreferenceSpecs.HAPTICS_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setDateFormatPreference(preference: DateFormatPreference) {
        dataStore.edit { AppearancePreferenceSpecs.DATE_FORMAT_PREFERENCE.writeTo(it, preference) }
    }

    suspend fun setAppFontScale(scale: AppFontScale) {
        dataStore.edit { AppearancePreferenceSpecs.APP_FONT_SCALE.writeTo(it, scale) }
    }

    suspend fun setScheduledThemeStartHour(hour: Int) {
        dataStore.edit { AppearancePreferenceSpecs.SCHEDULED_THEME_START_HOUR.writeTo(it, hour) }
    }

    suspend fun setScheduledThemeEndHour(hour: Int) {
        dataStore.edit { AppearancePreferenceSpecs.SCHEDULED_THEME_END_HOUR.writeTo(it, hour) }
    }

    suspend fun setColorBlindMode(mode: ColorBlindMode) {
        dataStore.edit { AppearancePreferenceSpecs.COLOR_BLIND_MODE.writeTo(it, mode) }
    }

    suspend fun setHandMode(mode: HandMode) {
        dataStore.edit { AppearancePreferenceSpecs.HAND_MODE.writeTo(it, mode) }
    }

    suspend fun setLayoutMode(mode: LayoutMode) {
        dataStore.edit { AppearancePreferenceSpecs.LAYOUT_MODE.writeTo(it, mode) }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * keeps this list from drifting (the hand-written predecessor had already
     * lost [Keys.HAPTICS_ENABLED], which resets under `MISC_APP`).
     * [Keys.SHOW_ADVANCED_SETTINGS] is not in this store's category lists — it
     * resets under `EXPERIMENTAL` via ExperimentalStore's list instead (see the
     * spec row's KDoc).
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category] — the [AppearancePreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. `APPEARANCE` for every
     * key but the haptics row (`MISC_APP`), while the advanced-settings row
     * deliberately contributes to no category here. The facade aggregates
     * these lists instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        AppearancePreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore via its row's derived write (the same encoding the row reads
     * with) — including the gap keys the legacy facade-level
     * `restorePreferences` omitted (the theme-variant + accent keys, haptics,
     * date format, font scale, scheduled-theme hours, and color-blind/hand
     * mode).
     */
    suspend fun restore(slice: AppearanceSlice) {
        dataStore.edit { prefs ->
            AppearancePreferenceSpecs.DYNAMIC_THEMING.writeTo(prefs, slice.dynamicTheming)
            AppearancePreferenceSpecs.THEME_MODE.writeTo(prefs, slice.themeMode)
            AppearancePreferenceSpecs.CONTRAST_LEVEL.writeTo(prefs, slice.contrastLevel)
            AppearancePreferenceSpecs.OLED_MODE.writeTo(prefs, slice.oledMode)
            AppearancePreferenceSpecs.ACCENT_COLOR_SWATCH.writeTo(prefs, slice.accentColorSwatch)
            AppearancePreferenceSpecs.COLOR_STYLE.writeTo(prefs, slice.colorStyle)
            AppearancePreferenceSpecs.PERFORMANCE_MODE.writeTo(prefs, slice.performanceMode)
            AppearancePreferenceSpecs.SHOW_ADVANCED_SETTINGS.writeTo(prefs, slice.showAdvancedSettings)
            AppearancePreferenceSpecs.REDUCE_MOTION_ENABLED.writeTo(prefs, slice.reduceMotionEnabled)
            AppearancePreferenceSpecs.BLUE_LIGHT_FILTER_ENABLED.writeTo(prefs, slice.blueLightFilterEnabled)
            AppearancePreferenceSpecs.BLUE_LIGHT_FILTER_STRENGTH.writeTo(prefs, slice.blueLightFilterStrength)
            AppearancePreferenceSpecs.BACKDROP_THEME_MUSIC_ENABLED.writeTo(prefs, slice.backdropThemeMusicEnabled)
            AppearancePreferenceSpecs.SYNTHWAVE_ACCENT.writeTo(prefs, slice.synthwaveAccent)
            AppearancePreferenceSpecs.SOOTHING_ACCENT.writeTo(prefs, slice.soothingAccent)
            // Same normalization as setThemeVariant: backup JSON can carry any
            // casing, and downstream legacy-boolean derivation compares raw
            // lowercase strings.
            AppearancePreferenceSpecs.THEME_VARIANT.writeTo(prefs, slice.themeVariant.lowercase())
            AppearancePreferenceSpecs.VIVID_ACCENT.writeTo(prefs, slice.vividAccent)
            AppearancePreferenceSpecs.AURORA_ACCENT.writeTo(prefs, slice.auroraAccent)
            AppearancePreferenceSpecs.SAKURA_ACCENT.writeTo(prefs, slice.sakuraAccent)
            AppearancePreferenceSpecs.VECTOR_POP_ACCENT.writeTo(prefs, slice.vectorPopAccent)
            AppearancePreferenceSpecs.HAPTICS_ENABLED.writeTo(prefs, slice.hapticsEnabled)
            AppearancePreferenceSpecs.DATE_FORMAT_PREFERENCE.writeTo(prefs, slice.dateFormatPreference)
            AppearancePreferenceSpecs.APP_FONT_SCALE.writeTo(prefs, slice.appFontScale)
            AppearancePreferenceSpecs.SCHEDULED_THEME_START_HOUR.writeTo(prefs, slice.scheduledThemeStartHour)
            AppearancePreferenceSpecs.SCHEDULED_THEME_END_HOUR.writeTo(prefs, slice.scheduledThemeEndHour)
            AppearancePreferenceSpecs.COLOR_BLIND_MODE.writeTo(prefs, slice.colorBlindMode)
            AppearancePreferenceSpecs.HAND_MODE.writeTo(prefs, slice.handMode)
            AppearancePreferenceSpecs.LAYOUT_MODE.writeTo(prefs, slice.layoutMode)
        }
    }
}

/**
 * The appearance &amp; accessibility preference slice. Plain data class.
 * Defaults mirror the projection defaults in [AppearanceStore.read] (declared
 * on the [AppearancePreferenceSpecs] rows).
 */
@Immutable
@Serializable
data class AppearanceSlice(
    val dynamicTheming: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val oledMode: Boolean = false,
    val performanceMode: Boolean = false,
    val accentColorSwatch: String = "dynamic",
    val colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    val themeVariant: String = "standard",
    val synthwaveAccent: String = "magenta",
    val soothingAccent: String = "ocean",
    val vividAccent: String = "punch",
    val auroraAccent: String = "emerald",
    val sakuraAccent: String = "rose",
    val vectorPopAccent: String = "cobalt",
    val showAdvancedSettings: Boolean = false,
    val reduceMotionEnabled: Boolean = false,
    val blueLightFilterEnabled: Boolean = false,
    val blueLightFilterStrength: Float = 0.3f,
    val backdropThemeMusicEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val dateFormatPreference: DateFormatPreference = DateFormatPreference.SYSTEM,
    val appFontScale: AppFontScale = AppFontScale.DEFAULT,
    val scheduledThemeStartHour: Int = 22,
    val scheduledThemeEndHour: Int = 7,
    val colorBlindMode: ColorBlindMode = ColorBlindMode.NONE,
    val handMode: HandMode = HandMode.RIGHT,
    val layoutMode: LayoutMode = LayoutMode.AUTO,
)
