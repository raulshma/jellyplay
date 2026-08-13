package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.ColorBlindMode
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.HandMode
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val store: UserPreferencesStore,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    /** Appearance-screen slice — recomposes this screen only on appearance-field writes. */
    val preferences: StateFlow<AppearanceScreenPreferences> = projections.appearanceScreenPreferences

    /** Navigation-customization slice, consumed by the embedded `NavigationCustomizationGroup`. */
    val navigationCustomizationPreferences: StateFlow<NavigationCustomizationPreferences> =
        projections.navigationCustomizationPreferences

    val showAdvancedSettings: StateFlow<Boolean> = appearanceStore.showAdvancedSettings

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { appearance.setShowAdvancedSettings(enabled) }

    fun setThemeMode(mode: ThemeMode) = editor.setThemeMode(mode)
    fun setDynamicTheming(enabled: Boolean) = editor.setDynamicTheming(enabled)
    fun setOledMode(enabled: Boolean) = editor.setOledMode(enabled)
    fun setContrastLevel(level: ContrastLevel) = editor.setContrastLevel(level)
    fun setPerformanceMode(enabled: Boolean) = editor.setPerformanceMode(enabled)
    fun setColorStyle(style: ColorStyle) = editor.setColorStyle(style)
    fun setAccentColorSwatch(swatch: String) = editor.setAccentColorSwatch(swatch)
    fun setSynthwaveMode(enabled: Boolean) =
        editor.edit { appearance.setSynthwaveMode(enabled) }
    fun setSynthwaveAccent(accent: String) =
        editor.edit { appearance.setSynthwaveAccent(accent) }
    fun setSoothingMode(enabled: Boolean) =
        editor.edit { appearance.setSoothingMode(enabled) }
    fun setSoothingAccent(accent: String) =
        editor.edit { appearance.setSoothingAccent(accent) }
    fun setMonochromeMode(enabled: Boolean) =
        editor.edit { appearance.setMonochromeMode(enabled) }
    fun setBlueLightFilterEnabled(enabled: Boolean) =
        editor.edit { appearance.setBlueLightFilterEnabled(enabled) }
    fun setBlueLightFilterStrength(strength: Float) =
        editor.edit { appearance.setBlueLightFilterStrength(strength) }
    fun setColorBlindMode(mode: ColorBlindMode) = editor.setColorBlindMode(mode)
    fun setScheduledThemeStartHour(hour: Int) = editor.setScheduledThemeStartHour(hour)
    fun setScheduledThemeEndHour(hour: Int) = editor.setScheduledThemeEndHour(hour)
    fun setHandMode(mode: HandMode) = editor.setHandMode(mode)
    fun setReduceMotionEnabled(enabled: Boolean) =
        editor.edit { appearance.setReduceMotionEnabled(enabled) }
    fun setAppFontScale(scale: AppFontScale) = editor.setAppFontScale(scale)
    fun setDateFormatPreference(preference: DateFormatPreference) =
        editor.setDateFormatPreference(preference)
    fun setHomeMode(mode: HomeMode) = editor.setHomeMode(mode)
    fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) =
        editor.setEnabledHomeSectionTypes(types)
    fun setHomeSectionOrder(order: List<HomeSectionType>) =
        editor.edit { homeDiscovery.setHomeSectionOrder(order) }
    fun setHomeHeroEnabled(enabled: Boolean) = editor.setHomeHeroEnabled(enabled)
    fun setHomeBackdropEnabled(enabled: Boolean) = editor.setHomeBackdropEnabled(enabled)
    fun setNavBarShowLabels(show: Boolean) = editor.setNavBarShowLabels(show)
    fun setHideSearchHistory(enabled: Boolean) =
        editor.edit { experimental.setHideSearchHistory(enabled) }
    fun setShowUnwatchedBadge(enabled: Boolean) =
        editor.edit { homeDiscovery.setShowUnwatchedBadge(enabled) }
    fun setShowWatchedCheckmark(enabled: Boolean) =
        editor.edit { homeDiscovery.setShowWatchedCheckmark(enabled) }
    fun setHideWatchedItems(enabled: Boolean) =
        editor.edit { homeDiscovery.setHideWatchedItems(enabled) }
    fun setHideEpisodeThumbnails(enabled: Boolean) =
        editor.edit { library.setHideEpisodeThumbnails(enabled) }
    fun setSkipSpecials(enabled: Boolean) =
        editor.edit { library.setSkipSpecials(enabled) }
    fun setCompactEpisodeList(enabled: Boolean) =
        editor.edit { library.setCompactEpisodeList(enabled) }
    fun setConfirmLibraryReset(enabled: Boolean) =
        editor.edit { library.setConfirmLibraryReset(enabled) }
    fun setLibraryViewMode(mode: LibraryViewMode) =
        editor.edit { library.setLibraryViewMode(mode) }
    fun setShowShareMediaOption(enabled: Boolean) =
        editor.edit { experimental.setShowShareMediaOption(enabled) }
    fun setShowExternalRatings(enabled: Boolean) =
        editor.edit { homeDiscovery.setShowExternalRatings(enabled) }
    fun setShowClockOnHome(enabled: Boolean) =
        editor.edit { homeDiscovery.setShowClockOnHome(enabled) }
    fun setShowSettingsInHomeSearch(enabled: Boolean) = editor.setShowSettingsInHomeSearch(enabled)
    fun setHideTopHeaderOnScroll(enabled: Boolean) =
        editor.edit { homeDiscovery.setHideTopHeaderOnScroll(enabled) }
    fun setContinueWatchingClickBehavior(behavior: ContinueWatchingClickBehavior) =
        editor.edit { homeDiscovery.setContinueWatchingClickBehavior(behavior) }
    fun setMergeContinueWatchingAndNextUp(enabled: Boolean) =
        editor.edit { homeDiscovery.setMergeContinueWatchingAndNextUp(enabled) }
    fun unhideAllCwItems() =
        editor.edit { homeDiscovery.unhideAllCwItems() }
    fun setNextUpMaxDays(days: Int) =
        editor.edit { homeDiscovery.setNextUpMaxDays(days) }
    fun setNextUpRewatching(enabled: Boolean) =
        editor.edit { homeDiscovery.setNextUpRewatching(enabled) }
    fun setEnabledNewsletterSections(sections: Set<NewsletterSectionType>) =
        editor.edit { notification.setEnabledNewsletterSections(sections) }
    fun setNewsletterSectionOrder(order: List<NewsletterSectionType>) =
        editor.edit { notification.setNewsletterSectionOrder(order) }
    fun setNewsletterEnabled(enabled: Boolean) =
        editor.edit { notification.setNewsletterEnabled(enabled) }
    fun setNewsletterDayOfWeek(day: Int) =
        editor.edit { notification.setNewsletterDayOfWeek(day) }
    fun setHapticsEnabled(enabled: Boolean) = editor.setHapticsEnabled(enabled)
    fun setBackdropThemeMusicEnabled(enabled: Boolean) =
        editor.edit { appearance.setBackdropThemeMusicEnabled(enabled) }
    fun setDreamShowTitle(enabled: Boolean) =
        editor.edit { screensaver.setDreamShowTitle(enabled) }
    fun setDreamImageCategories(categories: Set<DreamImageCategory>) =
        editor.edit { screensaver.setDreamImageCategories(categories) }
    fun setDreamSlideshowIntervalMs(ms: Long) =
        editor.edit { screensaver.setDreamSlideshowIntervalMs(ms) }
    fun setDreamKenBurnsEnabled(enabled: Boolean) =
        editor.edit { screensaver.setDreamKenBurnsEnabled(enabled) }
    fun setDreamTransitionStyle(style: DreamTransitionStyle) =
        editor.edit { screensaver.setDreamTransitionStyle(style) }

    fun setHideBottomNavOnScroll(hide: Boolean) =
        editor.edit { navigation.setHideBottomNavOnScroll(hide) }
    fun setHiddenNavItems(items: Set<String>) =
        editor.edit { navigation.setHiddenNavItems(items) }
    fun setNavItemOrder(order: List<String>) =
        editor.edit { navigation.setNavItemOrder(order) }

    fun resetCategory(category: PreferenceResetCategory) = editor.resetCategory(category)
}
