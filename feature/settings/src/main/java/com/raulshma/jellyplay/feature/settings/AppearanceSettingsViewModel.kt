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
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val store: UserPreferencesStore,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    val preferences: StateFlow<UserPreferences> = store.preferences
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    val showAdvancedSettings: StateFlow<Boolean> = store.preferences
        .map { it.showAdvancedSettings }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { setShowAdvancedSettings(enabled) }

    fun setThemeMode(mode: ThemeMode) = editor.setThemeMode(mode)
    fun setDynamicTheming(enabled: Boolean) = editor.setDynamicTheming(enabled)
    fun setOledMode(enabled: Boolean) = editor.setOledMode(enabled)
    fun setContrastLevel(level: ContrastLevel) = editor.setContrastLevel(level)
    fun setPerformanceMode(enabled: Boolean) = editor.setPerformanceMode(enabled)
    fun setColorStyle(style: ColorStyle) = editor.setColorStyle(style)
    fun setAccentColorSwatch(swatch: String) = editor.setAccentColorSwatch(swatch)
    fun setSynthwaveMode(enabled: Boolean) =
        editor.edit { setSynthwaveMode(enabled) }
    fun setSynthwaveAccent(accent: String) =
        editor.edit { setSynthwaveAccent(accent) }
    fun setSoothingMode(enabled: Boolean) =
        editor.edit { setSoothingMode(enabled) }
    fun setSoothingAccent(accent: String) =
        editor.edit { setSoothingAccent(accent) }
    fun setMonochromeMode(enabled: Boolean) =
        editor.edit { setMonochromeMode(enabled) }
    fun setBlueLightFilterEnabled(enabled: Boolean) =
        editor.edit { setBlueLightFilterEnabled(enabled) }
    fun setBlueLightFilterStrength(strength: Float) =
        editor.edit { setBlueLightFilterStrength(strength) }
    fun setColorBlindMode(mode: ColorBlindMode) = editor.setColorBlindMode(mode)
    fun setScheduledThemeStartHour(hour: Int) = editor.setScheduledThemeStartHour(hour)
    fun setScheduledThemeEndHour(hour: Int) = editor.setScheduledThemeEndHour(hour)
    fun setHandMode(mode: HandMode) = editor.setHandMode(mode)
    fun setReduceMotionEnabled(enabled: Boolean) =
        editor.edit { setReduceMotionEnabled(enabled) }
    fun setAppFontScale(scale: AppFontScale) = editor.setAppFontScale(scale)
    fun setDateFormatPreference(preference: DateFormatPreference) =
        editor.setDateFormatPreference(preference)
    fun setHomeMode(mode: HomeMode) = editor.setHomeMode(mode)
    fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) =
        editor.setEnabledHomeSectionTypes(types)
    fun setHomeSectionOrder(order: List<HomeSectionType>) =
        editor.edit { setHomeSectionOrder(order) }
    fun setHomeHeroEnabled(enabled: Boolean) = editor.setHomeHeroEnabled(enabled)
    fun setNavBarShowLabels(show: Boolean) = editor.setNavBarShowLabels(show)
    fun setHideSearchHistory(enabled: Boolean) =
        editor.edit { setHideSearchHistory(enabled) }
    fun setShowUnwatchedBadge(enabled: Boolean) =
        editor.edit { setShowUnwatchedBadge(enabled) }
    fun setShowWatchedCheckmark(enabled: Boolean) =
        editor.edit { setShowWatchedCheckmark(enabled) }
    fun setHideWatchedItems(enabled: Boolean) =
        editor.edit { setHideWatchedItems(enabled) }
    fun setHideEpisodeThumbnails(enabled: Boolean) =
        editor.edit { setHideEpisodeThumbnails(enabled) }
    fun setSkipSpecials(enabled: Boolean) =
        editor.edit { setSkipSpecials(enabled) }
    fun setLibraryViewMode(mode: LibraryViewMode) =
        editor.edit { setLibraryViewMode(mode) }
    fun setShowShareMediaOption(enabled: Boolean) =
        editor.edit { setShowShareMediaOption(enabled) }
    fun setShowExternalRatings(enabled: Boolean) =
        editor.edit { setShowExternalRatings(enabled) }
    fun setShowClockOnHome(enabled: Boolean) =
        editor.edit { setShowClockOnHome(enabled) }
    fun setContinueWatchingClickBehavior(behavior: ContinueWatchingClickBehavior) =
        editor.edit { setContinueWatchingClickBehavior(behavior) }
    fun setMergeContinueWatchingAndNextUp(enabled: Boolean) =
        editor.edit { setMergeContinueWatchingAndNextUp(enabled) }
    fun unhideAllCwItems() =
        editor.edit { unhideAllCwItems() }
    fun setNextUpMaxDays(days: Int) =
        editor.edit { setNextUpMaxDays(days) }
    fun setNextUpRewatching(enabled: Boolean) =
        editor.edit { setNextUpRewatching(enabled) }
    fun setEnabledNewsletterSections(sections: Set<NewsletterSectionType>) =
        editor.edit { setEnabledNewsletterSections(sections) }
    fun setNewsletterSectionOrder(order: List<NewsletterSectionType>) =
        editor.edit { setNewsletterSectionOrder(order) }
    fun setNewsletterEnabled(enabled: Boolean) =
        editor.edit { setNewsletterEnabled(enabled) }
    fun setNewsletterDayOfWeek(day: Int) =
        editor.edit { setNewsletterDayOfWeek(day) }
    fun setHapticsEnabled(enabled: Boolean) = editor.setHapticsEnabled(enabled)
    fun setBackdropThemeMusicEnabled(enabled: Boolean) =
        editor.edit { setBackdropThemeMusicEnabled(enabled) }
    fun setDreamShowTitle(enabled: Boolean) =
        editor.edit { setDreamShowTitle(enabled) }
    fun setDreamImageCategories(categories: Set<DreamImageCategory>) =
        editor.edit { setDreamImageCategories(categories) }
    fun setDreamSlideshowIntervalMs(ms: Long) =
        editor.edit { setDreamSlideshowIntervalMs(ms) }
    fun setDreamKenBurnsEnabled(enabled: Boolean) =
        editor.edit { setDreamKenBurnsEnabled(enabled) }
    fun setDreamTransitionStyle(style: DreamTransitionStyle) =
        editor.edit { setDreamTransitionStyle(style) }
    fun setHideBottomNavOnScroll(hide: Boolean) =
        editor.edit { setHideBottomNavOnScroll(hide) }
    fun setHiddenNavItems(items: Set<String>) =
        editor.edit { setHiddenNavItems(items) }
    fun setNavItemOrder(order: List<String>) =
        editor.edit { setNavItemOrder(order) }

    fun resetCategory(category: PreferenceResetCategory) = editor.resetCategory(category)
}
