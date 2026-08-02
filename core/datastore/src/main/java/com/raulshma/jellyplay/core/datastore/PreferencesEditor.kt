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
 * Single auditable write seam over the 18 domain stores +
 * [AppRuntimeStateStore]. Every preference mutation from a ViewModel flows
 * through here so cross-cutting write concerns (logging, validation, batching)
 * have one place to live.
 *
 * [edit] is the general-purpose escape hatch: its receiver is a
 * [PreferencesEditScope] exposing each owning store, so a call looks like
 * `editor.edit { appearance.setThemeMode(mode) }`. The named methods below
 * cover the appearance / home / playback setters shared by the onboarding and
 * settings flows. Writes that require a post-write side effect on a scheduler
 * (auto-download, TV watch-next, notifications) belong to the layer that owns
 * those scheduler dependencies — they call [edit] for the store write and then
 * trigger the scheduler themselves.
 *
 * The legacy `UserPreferencesStore` is retained only for the reset machinery
 * ([resetCategory], [clearAllPreferences]) and the v0/v1 backup import path.
 */
@Singleton
class PreferencesEditor @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val editScope: PreferencesEditScope,
    // Retained for the reset / clear machinery that still lives on the facade.
    private val store: UserPreferencesStore,
) {
    /** Fire-and-forget launch over the application scope. */
    private fun run(block: suspend () -> Unit) = scope.launch { block() }

    private val securityStore: SecurityStore get() = editScope.security

    /**
     * Runs [block] against the [PreferencesEditScope] on the application scope.
     * Use this for any store mutation that does not need a post-write side
     * effect. Reach the owning store directly: `appearance.setThemeMode(mode)`.
     */
    fun edit(block: suspend PreferencesEditScope.() -> Unit) = run { editScope.block() }

    // ----- Named convenience setters (appearance / home / playback) --------

    fun setThemeMode(mode: ThemeMode) = edit { appearance.setThemeMode(mode) }
    fun setDynamicTheming(enabled: Boolean) = edit { appearance.setDynamicTheming(enabled) }
    fun setOledMode(enabled: Boolean) = edit { appearance.setOledMode(enabled) }
    fun setContrastLevel(level: ContrastLevel) = edit { appearance.setContrastLevel(level) }
    fun setAccentColorSwatch(swatch: String) = edit { appearance.setAccentColorSwatch(swatch) }
    fun setColorStyle(style: ColorStyle) = edit { appearance.setColorStyle(style) }
    fun setPerformanceMode(enabled: Boolean) = edit { appearance.setPerformanceMode(enabled) }
    fun setHomeHeroEnabled(enabled: Boolean) = edit { homeDiscovery.setHomeHeroEnabled(enabled) }
    fun setHomeBackdropEnabled(enabled: Boolean) = edit { homeDiscovery.setHomeBackdropEnabled(enabled) }
    fun setShowSettingsInHomeSearch(enabled: Boolean) = edit { homeDiscovery.setShowSettingsInHomeSearch(enabled) }
    fun setHomeMode(mode: HomeMode) = edit { homeDiscovery.setHomeMode(mode) }
    fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) = edit { homeDiscovery.setEnabledHomeSectionTypes(types) }
    fun setLibraryHomeSectionOverrides(overrides: Map<String, Set<HomeSectionType>>) =
        edit { homeDiscovery.setLibraryHomeSectionOverrides(overrides) }
    fun setNavBarShowLabels(show: Boolean) = edit { navigation.setNavBarShowLabels(show) }
    fun setPreferredPlayer(playerType: PlayerType) = edit { playback.setPreferredPlayer(playerType) }
    fun setStreamingQuality(quality: StreamingQuality) = edit { playback.setStreamingQuality(quality) }
    fun setLiveStreamOption(option: LiveStreamOption) = edit { playback.setLiveStreamOption(option) }
    fun setVideoSeekDurationMs(ms: Long) = edit { videoPlayer.setVideoSeekDurationMs(ms) }
    fun setVideoGesturesEnabled(enabled: Boolean) = edit { videoPlayer.setVideoGesturesEnabled(enabled) }
    fun setVideoDefaultOrientation(mode: OrientationMode) = edit { videoPlayer.setVideoDefaultOrientation(mode) }
    fun setVideoAutoplayNext(enabled: Boolean) = edit { videoPlayer.setVideoAutoplayNext(enabled) }
    fun setAudioDefaultSpeed(speed: Float) = edit { audio.setAudioDefaultSpeed(speed) }
    fun setGaplessEnabled(enabled: Boolean) = edit { audio.setAudioGaplessEnabled(enabled) }
    fun setCrossfadeDurationMs(ms: Long) = edit { audio.setAudioCrossfadeDurationMs(ms) }
    fun setAudioNormalizationEnabled(enabled: Boolean) = edit { audio.setAudioNormalizationEnabled(enabled) }
    fun setAudioAutoplayNext(enabled: Boolean) = edit { audio.setAudioAutoplayNext(enabled) }
    fun setSubtitleStyle(style: SubtitleStyle) = edit { subtitle.setSubtitleStyle(style) }
    fun setPreferredSubtitleLanguage(language: String?) = edit { subtitle.setPreferredSubtitleLanguage(language) }
    fun setSubtitlesForcedOnly(enabled: Boolean) = edit { subtitle.setSubtitlesForcedOnly(enabled) }
    // ----- Security / PIN: composite ops live in SecurityStore, which owns
    // hashing + verification (PinHasher) and rate-limit escalation
    // (PinRateLimiter) as collaborators.
    fun setPinLockEnabled(enabled: Boolean) = run { securityStore.setPinLockEnabled(enabled) }
    fun setPinHash(hash: String?) = run { securityStore.setPinHash(hash) }
    fun setPin(pin: String) = run { securityStore.setPin(pin) }
    fun clearPin() = run { securityStore.clearPin() }
    fun setBiometricLockEnabled(enabled: Boolean) = run { securityStore.setBiometricLockEnabled(enabled) }
    fun setUsePinForPlayerLock(enabled: Boolean) = run { securityStore.setUsePinForPlayerLock(enabled) }
    fun setDuckOnTransientFocusLoss(enabled: Boolean) = edit { playback.setDuckOnTransientFocusLoss(enabled) }
    fun setAutoLockTimerMs(ms: Long) = run { securityStore.setAutoLockTimerMs(ms) }
    fun setRemoteControlEnabled(enabled: Boolean) = run { securityStore.setRemoteControlEnabled(enabled) }
    fun hashPin(pin: String): String = securityStore.hashPin(pin)
    suspend fun verifyPin(pin: String): Boolean = securityStore.verifyPinOffMainThread(pin)

    /** Resets all preferences in a specific category to their default values. */
    fun resetCategory(category: PreferenceResetCategory) = run { store.resetCategory(category) }

    /**
     * Clears the preferences DataStore only (preferences reset to defaults).
     * Does **not** sign out the user or delete downloaded media, cache, or DB.
     */
    fun clearAllPreferences() = run { store.clearAllPreferencesOnly() }

    fun setHapticsEnabled(enabled: Boolean) = edit { appearance.setHapticsEnabled(enabled) }
    fun setDateFormatPreference(preference: DateFormatPreference) = edit { appearance.setDateFormatPreference(preference) }
    fun setAppFontScale(scale: AppFontScale) = edit { appearance.setAppFontScale(scale) }
    fun setScheduledThemeStartHour(hour: Int) = edit { appearance.setScheduledThemeStartHour(hour) }
    fun setScheduledThemeEndHour(hour: Int) = edit { appearance.setScheduledThemeEndHour(hour) }
    fun setColorBlindMode(mode: ColorBlindMode) = edit { appearance.setColorBlindMode(mode) }
    fun setHandMode(mode: HandMode) = edit { appearance.setHandMode(mode) }
    fun setDownloadScheduleEnabled(enabled: Boolean) = edit { downloads.setDownloadScheduleEnabled(enabled) }
    fun setDownloadScheduleWindow(window: DownloadScheduleWindow) = edit { downloads.setDownloadScheduleWindow(window) }
}
