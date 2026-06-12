package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val oledMode: Boolean = false,
    val dynamicTheming: Boolean = true,
    val accentColorSwatch: String = "dynamic",
    val colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    val navBarShowLabels: Boolean = true,
    val homeHeroEnabled: Boolean = true,
    val synthwaveMode: Boolean = false,
    val synthwaveAccent: String = "magenta",
    val soothingMode: Boolean = false,
    val soothingAccent: String = "ocean",
    val monochromeMode: Boolean = false,
)

/**
 * Focused facade over [UserPreferencesStore] that exposes only
 * theme/appearance settings. Backed by the same DataStore (no data
 * migration) but provides a cleaner, narrower API and a dedicated
 * [StateFlow] so subscribers don't re-render on unrelated preference
 * changes.
 */
@Singleton
class AppearanceSettingsStore @Inject constructor(
    private val userPreferencesStore: UserPreferencesStore,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    val settings: StateFlow<AppearanceSettings> = userPreferencesStore.preferences
        .map { prefs ->
            AppearanceSettings(
                themeMode = prefs.themeMode,
                contrastLevel = prefs.contrastLevel,
                oledMode = prefs.oledMode,
                dynamicTheming = prefs.dynamicTheming,
                accentColorSwatch = prefs.accentColorSwatch,
                colorStyle = prefs.colorStyle,
                navBarShowLabels = prefs.navBarShowLabels,
                homeHeroEnabled = prefs.homeHeroEnabled,
                synthwaveMode = prefs.synthwaveMode,
                synthwaveAccent = prefs.synthwaveAccent,
                soothingMode = prefs.soothingMode,
                soothingAccent = prefs.soothingAccent,
                monochromeMode = prefs.monochromeMode,
            )
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, AppearanceSettings())

    suspend fun setThemeMode(mode: ThemeMode) = userPreferencesStore.setThemeMode(mode)

    suspend fun setContrastLevel(level: ContrastLevel) = userPreferencesStore.setContrastLevel(level)

    suspend fun setOledMode(enabled: Boolean) = userPreferencesStore.setOledMode(enabled)

    suspend fun setDynamicTheming(enabled: Boolean) = userPreferencesStore.setDynamicTheming(enabled)

    suspend fun setAccentColorSwatch(swatch: String) = userPreferencesStore.setAccentColorSwatch(swatch)

    suspend fun setColorStyle(style: ColorStyle) = userPreferencesStore.setColorStyle(style)

    suspend fun setNavBarShowLabels(enabled: Boolean) = userPreferencesStore.setNavBarShowLabels(enabled)

    suspend fun setHomeHeroEnabled(enabled: Boolean) = userPreferencesStore.setHomeHeroEnabled(enabled)

    suspend fun setSynthwaveMode(enabled: Boolean) = userPreferencesStore.setSynthwaveMode(enabled)

    suspend fun setSynthwaveAccent(accent: String) = userPreferencesStore.setSynthwaveAccent(accent)

    suspend fun setSoothingMode(enabled: Boolean) = userPreferencesStore.setSoothingMode(enabled)

    suspend fun setSoothingAccent(accent: String) = userPreferencesStore.setSoothingAccent(accent)

    suspend fun setMonochromeMode(enabled: Boolean) = userPreferencesStore.setMonochromeMode(enabled)
}
