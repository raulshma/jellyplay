package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.ColorBlindMode
import com.raulshma.jellyplay.core.model.DownloadScheduleWindow
import com.raulshma.jellyplay.core.model.HandMode
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single auditable write seam over [UserPreferencesStore]. Every preference
 * mutation from a ViewModel flows through here so cross-cutting write concerns
 * (logging, validation, batching) have one place to live.
 *
 * [edit] is the general-purpose escape hatch for any store mutation; the named
 * methods below cover the appearance / home / playback setters shared by the
 * onboarding and settings flows. Writes that require a post-write side effect
 * on a scheduler (auto-download, TV watch-next, notifications) belong to the
 * layer that owns those scheduler dependencies — they call [edit] for the
 * store write and then trigger the scheduler themselves.
 */
@Singleton
class PreferencesEditor @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val store: UserPreferencesStore,
    private val securityStore: SecurityStore,
) {
    /** Fire-and-forget launch over the application scope. */
    private fun run(block: suspend () -> Unit) = scope.launch { block() }

    /**
     * Runs [block] against the store on the application scope. Use this for any
     * store mutation that does not need a post-write side effect.
     */
    fun edit(block: suspend UserPreferencesStore.() -> Unit) = run { store.block() }

    // ----- Named convenience setters (appearance / home / playback) --------

    fun setThemeMode(mode: ThemeMode) = edit { setThemeMode(mode) }
    fun setDynamicTheming(enabled: Boolean) = edit { setDynamicTheming(enabled) }
    fun setOledMode(enabled: Boolean) = edit { setOledMode(enabled) }
    fun setContrastLevel(level: ContrastLevel) = edit { setContrastLevel(level) }
    fun setAccentColorSwatch(swatch: String) = edit { setAccentColorSwatch(swatch) }
    fun setColorStyle(style: ColorStyle) = edit { setColorStyle(style) }
    fun setPerformanceMode(enabled: Boolean) = edit { setPerformanceMode(enabled) }
    fun setHomeHeroEnabled(enabled: Boolean) = edit { setHomeHeroEnabled(enabled) }
    fun setHomeBackdropEnabled(enabled: Boolean) = edit { setHomeBackdropEnabled(enabled) }
    fun setShowSettingsInHomeSearch(enabled: Boolean) = edit { setShowSettingsInHomeSearch(enabled) }
    fun setHomeMode(mode: HomeMode) = edit { setHomeMode(mode) }
    fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) = edit { setEnabledHomeSectionTypes(types) }
    fun setLibraryHomeSectionOverrides(overrides: Map<String, Set<HomeSectionType>>) =
        edit { setLibraryHomeSectionOverrides(overrides) }
    fun setNavBarShowLabels(show: Boolean) = edit { setNavBarShowLabels(show) }
    fun setPreferredPlayer(playerType: PlayerType) = edit { setPreferredPlayer(playerType) }
    fun setStreamingQuality(quality: StreamingQuality) = edit { setStreamingQuality(quality) }
    fun setLiveStreamOption(option: LiveStreamOption) = edit { setLiveStreamOption(option) }
    fun setVideoSeekDurationMs(ms: Long) = edit { setVideoSeekDurationMs(ms) }
    fun setVideoGesturesEnabled(enabled: Boolean) = edit { setVideoGesturesEnabled(enabled) }
    fun setVideoDefaultOrientation(mode: OrientationMode) = edit { setVideoDefaultOrientation(mode) }
    fun setVideoAutoplayNext(enabled: Boolean) = edit { setVideoAutoplayNext(enabled) }
    fun setAudioDefaultSpeed(speed: Float) = edit { setAudioDefaultSpeed(speed) }
    fun setGaplessEnabled(enabled: Boolean) = edit { setGaplessEnabled(enabled) }
    fun setCrossfadeDurationMs(ms: Long) = edit { setCrossfadeDurationMs(ms) }
    fun setAudioNormalizationEnabled(enabled: Boolean) = edit { setAudioNormalizationEnabled(enabled) }
    fun setAudioAutoplayNext(enabled: Boolean) = edit { setAudioAutoplayNext(enabled) }
    fun setSubtitleStyle(style: SubtitleStyle) = edit { setSubtitleStyle(style) }
    fun setPreferredSubtitleLanguage(language: String?) = edit { setPreferredSubtitleLanguage(language) }
    fun setSubtitlesForcedOnly(enabled: Boolean) = edit { setSubtitlesForcedOnly(enabled) }
    // ----- Security / PIN: composite ops live in SecurityStore, which owns
    // hashing + verification (PinHasher) and rate-limit escalation
    // (PinRateLimiter) as collaborators.
    fun setPinLockEnabled(enabled: Boolean) = run { securityStore.setPinLockEnabled(enabled) }
    fun setPinHash(hash: String?) = run { securityStore.setPinHash(hash) }
    fun setPin(pin: String) = run { securityStore.setPin(pin) }
    fun clearPin() = run { securityStore.clearPin() }
    fun setBiometricLockEnabled(enabled: Boolean) = run { securityStore.setBiometricLockEnabled(enabled) }
    fun setUsePinForPlayerLock(enabled: Boolean) = run { securityStore.setUsePinForPlayerLock(enabled) }
    fun setDuckOnTransientFocusLoss(enabled: Boolean) = edit { setDuckOnTransientFocusLoss(enabled) }
    fun setAutoLockTimerMs(ms: Long) = run { securityStore.setAutoLockTimerMs(ms) }
    fun setRemoteControlEnabled(enabled: Boolean) = run { securityStore.setRemoteControlEnabled(enabled) }
    fun hashPin(pin: String): String = securityStore.hashPin(pin)
    suspend fun verifyPin(pin: String): Boolean = securityStore.verifyPinOffMainThread(pin)

    /** Resets all preferences in a specific category to their default values. */
    fun resetCategory(category: PreferenceResetCategory) = edit { resetCategory(category) }

    /**
     * Clears the preferences DataStore only (preferences reset to defaults).
     * Does **not** sign out the user or delete downloaded media, cache, or DB.
     */
    fun clearAllPreferences() = edit { clearAllPreferencesOnly() }

    fun setHapticsEnabled(enabled: Boolean) = edit { setHapticsEnabled(enabled) }
    fun setDateFormatPreference(preference: DateFormatPreference) = edit { setDateFormatPreference(preference) }
    fun setAppFontScale(scale: AppFontScale) = edit { setAppFontScale(scale) }
    fun setScheduledThemeStartHour(hour: Int) = edit { setScheduledThemeStartHour(hour) }
    fun setScheduledThemeEndHour(hour: Int) = edit { setScheduledThemeEndHour(hour) }
    fun setColorBlindMode(mode: ColorBlindMode) = edit { setColorBlindMode(mode) }
    fun setHandMode(mode: HandMode) = edit { setHandMode(mode) }
    fun setDownloadScheduleEnabled(enabled: Boolean) = edit { setDownloadScheduleEnabled(enabled) }
    fun setDownloadScheduleWindow(window: DownloadScheduleWindow) = edit { setDownloadScheduleWindow(window) }
}
