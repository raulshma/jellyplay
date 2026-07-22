package com.raulshma.jellyplay.feature.onboarding

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val preferencesStore: UserPreferencesStore,
    val seerrPreferencesStore: SeerrPreferencesStore,
    private val seerrSecureCredentialsStore: SeerrSecureCredentialsStore,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    val preferences = preferencesStore.preferences
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    val seerrPreferences = seerrPreferencesStore.preferences
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.seerr.SeerrPreferences())

    private val _currentStep = stateFlow(0)
    val currentStep = _currentStep.flow

    fun setStep(step: Int) {
        _currentStep.set(step.coerceIn(0, OnboardingStep.count - 1))
    }

    fun nextStep() {
        setStep(_currentStep.value + 1)
    }

    fun skipOnboarding() {
        // Skip jumps to the final step rather than silently completing from page 1,
        // which previously permanently dismissed the wizard (Skip == Complete). The
        // user lands on the review/finish step and can complete (or step back) from
        // there, so an accidental tap never throws away the chance to configure.
        setStep(OnboardingStep.count - 1)
    }

    fun completeOnboarding() {
        editor.edit { setOnboardingCompleted(true) }
    }

    fun setThemeMode(mode: com.raulshma.jellyplay.core.model.ThemeMode) = editor.setThemeMode(mode)
    fun setDynamicTheming(enabled: Boolean) = editor.setDynamicTheming(enabled)
    fun setOledMode(enabled: Boolean) = editor.setOledMode(enabled)
    fun setContrastLevel(level: com.raulshma.jellyplay.core.model.ContrastLevel) = editor.setContrastLevel(level)
    fun setAccentColorSwatch(swatch: String) = editor.setAccentColorSwatch(swatch)
    fun setColorStyle(style: com.raulshma.jellyplay.core.model.ColorStyle) = editor.setColorStyle(style)
    fun setPerformanceMode(enabled: Boolean) = editor.setPerformanceMode(enabled)
    fun setHomeHeroEnabled(enabled: Boolean) = editor.setHomeHeroEnabled(enabled)
    fun setHomeMode(mode: com.raulshma.jellyplay.core.model.HomeMode) = editor.setHomeMode(mode)
    fun setEnabledHomeSectionTypes(types: Set<com.raulshma.jellyplay.core.model.HomeSectionType>) = editor.setEnabledHomeSectionTypes(types)
    fun setNavBarShowLabels(show: Boolean) = editor.setNavBarShowLabels(show)
    fun setPreferredPlayer(playerType: com.raulshma.jellyplay.core.model.PlayerType) = editor.setPreferredPlayer(playerType)
    fun setStreamingQuality(quality: com.raulshma.jellyplay.core.model.StreamingQuality) = editor.setStreamingQuality(quality)
    fun setVideoSeekDurationMs(ms: Long) = editor.setVideoSeekDurationMs(ms)
    fun setVideoGesturesEnabled(enabled: Boolean) = editor.setVideoGesturesEnabled(enabled)
    fun setVideoDefaultOrientation(mode: com.raulshma.jellyplay.core.model.OrientationMode) = editor.setVideoDefaultOrientation(mode)
    fun setVideoAutoplayNext(enabled: Boolean) = editor.setVideoAutoplayNext(enabled)
    fun setAudioDefaultSpeed(speed: Float) = editor.setAudioDefaultSpeed(speed)
    fun setGaplessEnabled(enabled: Boolean) = editor.setGaplessEnabled(enabled)
    fun setCrossfadeDurationMs(ms: Long) = editor.setCrossfadeDurationMs(ms)
    fun setAudioNormalizationEnabled(enabled: Boolean) = editor.setAudioNormalizationEnabled(enabled)
    fun setAudioAutoplayNext(enabled: Boolean) = editor.setAudioAutoplayNext(enabled)
    fun setSubtitleStyle(style: com.raulshma.jellyplay.core.model.SubtitleStyle) = editor.setSubtitleStyle(style)
    fun setPreferredSubtitleLanguage(language: String?) = editor.setPreferredSubtitleLanguage(language)
    fun setPinLockEnabled(enabled: Boolean) = editor.setPinLockEnabled(enabled)
    fun setPinHash(hash: String?) = editor.setPinHash(hash)
    fun setBiometricLockEnabled(enabled: Boolean) = editor.setBiometricLockEnabled(enabled)
    fun setAutoLockTimerMs(ms: Long) = editor.setAutoLockTimerMs(ms)
    fun hashPin(pin: String): String = editor.hashPin(pin)

    fun setSeerrServerUrl(url: String) {
        launch { seerrPreferencesStore.setServerUrl(url) }
    }

    fun setSeerrApiKey(key: String) {
        launch { seerrSecureCredentialsStore.setApiKey(key) }
    }

    fun setSeerrAuthMethod(method: SeerrAuthMethod) {
        launch { seerrPreferencesStore.setAuthMethod(method) }
    }

    fun setSeerrUsername(username: String) {
        launch { seerrPreferencesStore.setUsername(username) }
    }

    fun setSeerrEmail(email: String) {
        launch { seerrPreferencesStore.setEmail(email) }
    }

    fun setSeerrPassword(password: String) {
        launch { seerrSecureCredentialsStore.setPassword(password) }
    }

    fun setSeerrEnabled(enabled: Boolean) {
        launch { seerrPreferencesStore.setEnabled(enabled) }
    }

    fun setSeerrSearchEnabled(enabled: Boolean) {
        launch { seerrPreferencesStore.setSearchEnabled(enabled) }
    }

    fun setSeerrRecommendationsEnabled(enabled: Boolean) {
        launch { seerrPreferencesStore.setRecommendationsEnabled(enabled) }
    }

    fun setSeerrDiscoverEnabled(enabled: Boolean) {
        launch { seerrPreferencesStore.setDiscoverEnabled(enabled) }
    }

    fun setSeerrStreamingRegion(region: String) {
        launch { seerrPreferencesStore.setStreamingRegion(region) }
    }

    fun setSeerrDiscoverRegion(region: String) {
        launch { seerrPreferencesStore.setDiscoverRegion(region) }
    }

    fun seerrDisconnect() {
        launch { seerrPreferencesStore.disconnect() }
    }
}
