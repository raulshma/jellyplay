package com.raulshma.jellyplay.core.datastore.appearance

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.ColorBlindMode
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.HandMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

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
 * **Theme style invariant:** a single `theme_variant` key selects the active
 * variant — variants are inherently mutually exclusive. The legacy
 * `synthwave_mode` / `soothing_mode` / `monochrome_mode` booleans are no longer
 * written but still drive [readThemeVariant] derivation so existing installs
 * (and old backups) keep their theme; they remain in the reset list.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 */
@Singleton
class AppearanceStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    internal object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CONTRAST_LEVEL = stringPreferencesKey("contrast_level")
        val DYNAMIC_THEMING = booleanPreferencesKey("dynamic_theming")
        val OLED_MODE = booleanPreferencesKey("oled_mode")
        val ACCENT_COLOR_SWATCH = stringPreferencesKey("accent_color_swatch")
        val COLOR_STYLE = stringPreferencesKey("color_style")
        val PERFORMANCE_MODE = booleanPreferencesKey("performance_mode")
        val REDUCE_MOTION_ENABLED = booleanPreferencesKey("reduce_motion_enabled")
        val THEME_VARIANT = stringPreferencesKey("theme_variant")
        val SYNTHWAVE_MODE = booleanPreferencesKey("synthwave_mode")
        val SYNTHWAVE_ACCENT = stringPreferencesKey("synthwave_accent")
        val SOOTHING_MODE = booleanPreferencesKey("soothing_mode")
        val SOOTHING_ACCENT = stringPreferencesKey("soothing_accent")
        val MONOCHROME_MODE = booleanPreferencesKey("monochrome_mode")
        val VIVID_ACCENT = stringPreferencesKey("vivid_accent")
        val AURORA_ACCENT = stringPreferencesKey("aurora_accent")
        val SAKURA_ACCENT = stringPreferencesKey("sakura_accent")
        val VECTOR_POP_ACCENT = stringPreferencesKey("vector_pop_accent")
        val BACKDROP_THEME_MUSIC_ENABLED = booleanPreferencesKey("backdrop_theme_music_enabled")
        val BLUE_LIGHT_FILTER_ENABLED = booleanPreferencesKey("blue_light_filter_enabled")
        val BLUE_LIGHT_FILTER_STRENGTH = floatPreferencesKey("blue_light_filter_strength")
        val DATE_FORMAT_PREFERENCE = stringPreferencesKey("date_format_preference")
        val APP_FONT_SCALE = stringPreferencesKey("app_font_scale")
        val SCHEDULED_THEME_START_HOUR = intPreferencesKey("scheduled_theme_start_hour")
        val SCHEDULED_THEME_END_HOUR = intPreferencesKey("scheduled_theme_end_hour")
        val COLOR_BLIND_MODE = stringPreferencesKey("color_blind_mode")
        val HAND_MODE = stringPreferencesKey("hand_mode")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val SHOW_ADVANCED_SETTINGS = booleanPreferencesKey("show_advanced_settings")
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    val appearance: StateFlow<AppearanceSlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, AppearanceSlice())

    /**
     * Whether the settings screens expose their advanced sections. An
     * appearance-domain field surfaced independently so settings sub-screens
     * can read it without collecting the whole preference aggregate.
     */
    val showAdvancedSettings: StateFlow<Boolean> = appearance
        .map { it.showAdvancedSettings }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    internal fun read(prefs: Preferences): AppearanceSlice = AppearanceSlice(
        dynamicTheming = PreferenceCodec.readBool(prefs, Keys.DYNAMIC_THEMING, "dynamic_theming", true),
        themeMode = readThemeMode(prefs),
        contrastLevel = readContrastLevel(prefs),
        oledMode = PreferenceCodec.readBool(prefs, Keys.OLED_MODE, "oled_mode", false),
        performanceMode = PreferenceCodec.readBool(prefs, Keys.PERFORMANCE_MODE, "performance_mode", false),
        accentColorSwatch = prefs[Keys.ACCENT_COLOR_SWATCH] ?: "dynamic",
        colorStyle = readColorStyle(prefs),
        synthwaveAccent = prefs[Keys.SYNTHWAVE_ACCENT] ?: "magenta",
        soothingAccent = prefs[Keys.SOOTHING_ACCENT] ?: "ocean",
        themeVariant = readThemeVariant(prefs),
        vividAccent = prefs[Keys.VIVID_ACCENT] ?: "punch",
        auroraAccent = prefs[Keys.AURORA_ACCENT] ?: "emerald",
        sakuraAccent = prefs[Keys.SAKURA_ACCENT] ?: "rose",
        vectorPopAccent = prefs[Keys.VECTOR_POP_ACCENT] ?: "cobalt",
        showAdvancedSettings = PreferenceCodec.readBool(prefs, Keys.SHOW_ADVANCED_SETTINGS, "show_advanced_settings", false),
        reduceMotionEnabled = PreferenceCodec.readBool(prefs, Keys.REDUCE_MOTION_ENABLED, "reduce_motion_enabled", false),
        blueLightFilterEnabled = PreferenceCodec.readBool(prefs, Keys.BLUE_LIGHT_FILTER_ENABLED, "blue_light_filter_enabled", false),
        blueLightFilterStrength = PreferenceCodec.readFloat(prefs, Keys.BLUE_LIGHT_FILTER_STRENGTH, "blue_light_filter_strength", 0.3f),
        backdropThemeMusicEnabled = PreferenceCodec.readBool(prefs, Keys.BACKDROP_THEME_MUSIC_ENABLED, "backdrop_theme_music_enabled", false),
        hapticsEnabled = PreferenceCodec.readBool(prefs, Keys.HAPTICS_ENABLED, "haptics_enabled", true),
        dateFormatPreference = readDateFormat(prefs),
        appFontScale = readFontScale(prefs),
        scheduledThemeStartHour = PreferenceCodec.readInt(prefs, Keys.SCHEDULED_THEME_START_HOUR, "scheduled_theme_start_hour", 22),
        scheduledThemeEndHour = PreferenceCodec.readInt(prefs, Keys.SCHEDULED_THEME_END_HOUR, "scheduled_theme_end_hour", 7),
        colorBlindMode = readColorBlindMode(prefs),
        handMode = readHandMode(prefs),
    )

    private fun readThemeMode(prefs: Preferences): ThemeMode = try {
        ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
    } catch (_: Exception) {
        ThemeMode.SYSTEM
    }

    /**
     * Resolves the active theme style. Falls back to the legacy single-purpose
     * booleans when `theme_variant` hasn't been written yet, so upgrades (and
     * restores of old backups) keep the user's theme with no migration write.
     */
    private fun readThemeVariant(prefs: Preferences): String {
        prefs[Keys.THEME_VARIANT]?.let { return it }
        return when {
            PreferenceCodec.readBool(prefs, Keys.SYNTHWAVE_MODE, "synthwave_mode", false) -> "synthwave"
            PreferenceCodec.readBool(prefs, Keys.SOOTHING_MODE, "soothing_mode", false) -> "soothing"
            PreferenceCodec.readBool(prefs, Keys.MONOCHROME_MODE, "monochrome_mode", false) -> "monochrome"
            else -> "standard"
        }
    }

    private fun readContrastLevel(prefs: Preferences): ContrastLevel = try {
        ContrastLevel.valueOf(prefs[Keys.CONTRAST_LEVEL] ?: ContrastLevel.DEFAULT.name)
    } catch (_: Exception) {
        ContrastLevel.DEFAULT
    }

    private fun readColorStyle(prefs: Preferences): ColorStyle = try {
        ColorStyle.valueOf(prefs[Keys.COLOR_STYLE] ?: ColorStyle.TONAL_SPOT.name)
    } catch (_: Exception) {
        ColorStyle.TONAL_SPOT
    }

    private fun readDateFormat(prefs: Preferences): DateFormatPreference = try {
        DateFormatPreference.valueOf(prefs[Keys.DATE_FORMAT_PREFERENCE] ?: DateFormatPreference.SYSTEM.name)
    } catch (_: Exception) {
        DateFormatPreference.SYSTEM
    }

    private fun readFontScale(prefs: Preferences): AppFontScale = try {
        AppFontScale.valueOf(prefs[Keys.APP_FONT_SCALE] ?: AppFontScale.DEFAULT.name)
    } catch (_: Exception) {
        AppFontScale.DEFAULT
    }

    private fun readColorBlindMode(prefs: Preferences): ColorBlindMode = try {
        ColorBlindMode.valueOf(prefs[Keys.COLOR_BLIND_MODE] ?: ColorBlindMode.NONE.name)
    } catch (_: Exception) {
        ColorBlindMode.NONE
    }

    private fun readHandMode(prefs: Preferences): HandMode = try {
        HandMode.valueOf(prefs[Keys.HAND_MODE] ?: HandMode.RIGHT.name)
    } catch (_: Exception) {
        HandMode.RIGHT
    }

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setDynamicTheming(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_THEMING] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setContrastLevel(level: ContrastLevel) {
        dataStore.edit { it[Keys.CONTRAST_LEVEL] = level.name }
    }

    suspend fun setOledMode(enabled: Boolean) {
        dataStore.edit { it[Keys.OLED_MODE] = enabled }
    }

    suspend fun setAccentColorSwatch(swatch: String) {
        dataStore.edit { it[Keys.ACCENT_COLOR_SWATCH] = swatch }
    }

    suspend fun setColorStyle(style: ColorStyle) {
        dataStore.edit { it[Keys.COLOR_STYLE] = style.name }
    }

    suspend fun setPerformanceMode(enabled: Boolean) {
        dataStore.edit { it[Keys.PERFORMANCE_MODE] = enabled }
    }

    suspend fun setReduceMotionEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCE_MOTION_ENABLED] = enabled }
    }

    /**
     * Selects the active theme style (a [com.raulshma.jellyplay.core.designsystem.theme.ThemeVariant]
     * name in lowercase: "standard", "synthwave", "soothing", "monochrome",
     * "vivid", "aurora", "sakura", "vector_pop"). A single key — variants are
     * inherently mutually exclusive. Normalizes to lowercase so the persisted
     * canonical form stays stable for the raw-string comparisons downstream.
     */
    suspend fun setThemeVariant(variant: String) {
        dataStore.edit { it[Keys.THEME_VARIANT] = variant.lowercase() }
    }

    suspend fun setSynthwaveAccent(accent: String) {
        dataStore.edit { it[Keys.SYNTHWAVE_ACCENT] = accent }
    }

    suspend fun setSoothingAccent(accent: String) {
        dataStore.edit { it[Keys.SOOTHING_ACCENT] = accent }
    }

    /** Persists the accent for the given themed variant; unknown variants are ignored. */
    suspend fun setVariantAccent(variant: String, accent: String) {
        val key = when (variant.lowercase()) {
            "synthwave" -> Keys.SYNTHWAVE_ACCENT
            "soothing" -> Keys.SOOTHING_ACCENT
            "vivid" -> Keys.VIVID_ACCENT
            "aurora" -> Keys.AURORA_ACCENT
            "sakura" -> Keys.SAKURA_ACCENT
            "vector_pop" -> Keys.VECTOR_POP_ACCENT
            else -> return
        }
        dataStore.edit { it[key] = accent }
    }

    suspend fun setShowAdvancedSettings(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_ADVANCED_SETTINGS] = enabled }
    }

    suspend fun setBlueLightFilterEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BLUE_LIGHT_FILTER_ENABLED] = enabled }
    }

    suspend fun setBlueLightFilterStrength(strength: Float) {
        dataStore.edit { it[Keys.BLUE_LIGHT_FILTER_STRENGTH] = strength }
    }

    suspend fun setBackdropThemeMusicEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKDROP_THEME_MUSIC_ENABLED] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setDateFormatPreference(preference: DateFormatPreference) {
        dataStore.edit { it[Keys.DATE_FORMAT_PREFERENCE] = preference.name }
    }

    suspend fun setAppFontScale(scale: AppFontScale) {
        dataStore.edit { it[Keys.APP_FONT_SCALE] = scale.name }
    }

    suspend fun setScheduledThemeStartHour(hour: Int) {
        dataStore.edit { it[Keys.SCHEDULED_THEME_START_HOUR] = hour }
    }

    suspend fun setScheduledThemeEndHour(hour: Int) {
        dataStore.edit { it[Keys.SCHEDULED_THEME_END_HOUR] = hour }
    }

    suspend fun setColorBlindMode(mode: ColorBlindMode) {
        dataStore.edit { it[Keys.COLOR_BLIND_MODE] = mode.name }
    }

    suspend fun setHandMode(mode: HandMode) {
        dataStore.edit { it[Keys.HAND_MODE] = mode.name }
    }

    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.THEME_MODE, Keys.CONTRAST_LEVEL, Keys.DYNAMIC_THEMING, Keys.OLED_MODE,
        Keys.ACCENT_COLOR_SWATCH, Keys.COLOR_STYLE, Keys.PERFORMANCE_MODE,
        Keys.REDUCE_MOTION_ENABLED, Keys.THEME_VARIANT,
        Keys.SYNTHWAVE_MODE, Keys.SYNTHWAVE_ACCENT,
        Keys.SOOTHING_MODE, Keys.SOOTHING_ACCENT, Keys.MONOCHROME_MODE,
        Keys.VIVID_ACCENT, Keys.AURORA_ACCENT, Keys.SAKURA_ACCENT, Keys.VECTOR_POP_ACCENT,
        Keys.BACKDROP_THEME_MUSIC_ENABLED, Keys.BLUE_LIGHT_FILTER_ENABLED,
        Keys.BLUE_LIGHT_FILTER_STRENGTH, Keys.DATE_FORMAT_PREFERENCE, Keys.APP_FONT_SCALE,
        Keys.SCHEDULED_THEME_START_HOUR, Keys.SCHEDULED_THEME_END_HOUR,
        Keys.COLOR_BLIND_MODE, Keys.HAND_MODE,
    )

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category]. This store's keys all sit in `APPEARANCE`, so every other
     * category returns an empty list. The facade aggregates these lists instead
     * of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.APPEARANCE -> listOf(
            Keys.THEME_MODE, Keys.CONTRAST_LEVEL, Keys.DYNAMIC_THEMING, Keys.OLED_MODE,
            Keys.ACCENT_COLOR_SWATCH, Keys.COLOR_STYLE, Keys.PERFORMANCE_MODE,
            Keys.REDUCE_MOTION_ENABLED, Keys.THEME_VARIANT,
            Keys.SYNTHWAVE_MODE, Keys.SYNTHWAVE_ACCENT,
            Keys.SOOTHING_MODE, Keys.SOOTHING_ACCENT, Keys.MONOCHROME_MODE,
            Keys.VIVID_ACCENT, Keys.AURORA_ACCENT, Keys.SAKURA_ACCENT, Keys.VECTOR_POP_ACCENT,
            Keys.BACKDROP_THEME_MUSIC_ENABLED, Keys.BLUE_LIGHT_FILTER_ENABLED,
            Keys.BLUE_LIGHT_FILTER_STRENGTH, Keys.DATE_FORMAT_PREFERENCE, Keys.APP_FONT_SCALE,
            Keys.SCHEDULED_THEME_START_HOUR, Keys.SCHEDULED_THEME_END_HOUR,
            Keys.COLOR_BLIND_MODE, Keys.HAND_MODE,
        )
        PreferenceResetCategory.MISC_APP -> listOf(
            Keys.HAPTICS_ENABLED,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the appearance keys owned by this
     * store from a decoded [UserPreferences]. The facade calls this (and every
     * other store's hook) instead of writing these keys itself.
     *
     * Mirrors the legacy facade behaviour exactly: the mutually-exclusive
     * accent-theme toggles, font scale, date format, color-blind/hand mode and
     * scheduled-theme keys are not written back (the projection reads them
     * straight from their stored slots).
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { it ->
            it[Keys.DYNAMIC_THEMING] = userPreferences.dynamicTheming
            it[Keys.THEME_MODE] = userPreferences.themeMode.name
            it[Keys.CONTRAST_LEVEL] = userPreferences.contrastLevel.name
            it[Keys.OLED_MODE] = userPreferences.oledMode
            it[Keys.ACCENT_COLOR_SWATCH] = userPreferences.accentColorSwatch
            it[Keys.COLOR_STYLE] = userPreferences.colorStyle.name
            it[Keys.PERFORMANCE_MODE] = userPreferences.performanceMode
            it[Keys.SHOW_ADVANCED_SETTINGS] = userPreferences.showAdvancedSettings
            it[Keys.REDUCE_MOTION_ENABLED] = userPreferences.reduceMotionEnabled
            it[Keys.BLUE_LIGHT_FILTER_ENABLED] = userPreferences.blueLightFilterEnabled
            it[Keys.BLUE_LIGHT_FILTER_STRENGTH] = userPreferences.blueLightFilterStrength
            it[Keys.BACKDROP_THEME_MUSIC_ENABLED] = userPreferences.backdropThemeMusicEnabled
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences], plus the gap
     * keys [restorePreferences] omits (the theme-variant + accent keys,
     * haptics, date format, font scale, scheduled-theme hours, and
     * color-blind/hand mode).
     */
    suspend fun restore(slice: AppearanceSlice) {
        dataStore.edit { it ->
            it[Keys.DYNAMIC_THEMING] = slice.dynamicTheming
            it[Keys.THEME_MODE] = slice.themeMode.name
            it[Keys.CONTRAST_LEVEL] = slice.contrastLevel.name
            it[Keys.OLED_MODE] = slice.oledMode
            it[Keys.ACCENT_COLOR_SWATCH] = slice.accentColorSwatch
            it[Keys.COLOR_STYLE] = slice.colorStyle.name
            it[Keys.PERFORMANCE_MODE] = slice.performanceMode
            it[Keys.SHOW_ADVANCED_SETTINGS] = slice.showAdvancedSettings
            it[Keys.REDUCE_MOTION_ENABLED] = slice.reduceMotionEnabled
            it[Keys.BLUE_LIGHT_FILTER_ENABLED] = slice.blueLightFilterEnabled
            it[Keys.BLUE_LIGHT_FILTER_STRENGTH] = slice.blueLightFilterStrength
            it[Keys.BACKDROP_THEME_MUSIC_ENABLED] = slice.backdropThemeMusicEnabled
            it[Keys.SYNTHWAVE_ACCENT] = slice.synthwaveAccent
            it[Keys.SOOTHING_ACCENT] = slice.soothingAccent
            // Same normalization as setThemeVariant: backup JSON can carry any
            // casing, and downstream legacy-boolean derivation compares raw
            // lowercase strings.
            it[Keys.THEME_VARIANT] = slice.themeVariant.lowercase()
            it[Keys.VIVID_ACCENT] = slice.vividAccent
            it[Keys.AURORA_ACCENT] = slice.auroraAccent
            it[Keys.SAKURA_ACCENT] = slice.sakuraAccent
            it[Keys.VECTOR_POP_ACCENT] = slice.vectorPopAccent
            it[Keys.HAPTICS_ENABLED] = slice.hapticsEnabled
            it[Keys.DATE_FORMAT_PREFERENCE] = slice.dateFormatPreference.name
            it[Keys.APP_FONT_SCALE] = slice.appFontScale.name
            it[Keys.SCHEDULED_THEME_START_HOUR] = slice.scheduledThemeStartHour
            it[Keys.SCHEDULED_THEME_END_HOUR] = slice.scheduledThemeEndHour
            it[Keys.COLOR_BLIND_MODE] = slice.colorBlindMode.name
            it[Keys.HAND_MODE] = slice.handMode.name
        }
    }
}

/**
 * The appearance &amp; accessibility preference slice. Plain data class.
 * Defaults mirror the projection defaults in [AppearanceStore.read].
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
)
