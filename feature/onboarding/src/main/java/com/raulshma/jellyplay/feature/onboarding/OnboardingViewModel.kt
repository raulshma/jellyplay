package com.raulshma.jellyplay.feature.onboarding

import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.ThemeMode
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
        completeOnboarding()
    }

    fun completeOnboarding() {
        launch {
            preferencesStore.setOnboardingCompleted(true)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        launch { preferencesStore.setThemeMode(mode) }
    }

    fun setDynamicTheming(enabled: Boolean) {
        launch { preferencesStore.setDynamicTheming(enabled) }
    }

    fun setOledMode(enabled: Boolean) {
        launch { preferencesStore.setOledMode(enabled) }
    }

    fun setContrastLevel(level: ContrastLevel) {
        launch { preferencesStore.setContrastLevel(level) }
    }

    fun setPerformanceMode(enabled: Boolean) {
        launch { preferencesStore.setPerformanceMode(enabled) }
    }

    fun setHomeHeroEnabled(enabled: Boolean) {
        launch { preferencesStore.setHomeHeroEnabled(enabled) }
    }

    fun setHomeMode(mode: HomeMode) {
        launch { preferencesStore.setHomeMode(mode) }
    }

    fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) {
        launch { preferencesStore.setEnabledHomeSectionTypes(types) }
    }

    fun setNavBarShowLabels(show: Boolean) {
        launch { preferencesStore.setNavBarShowLabels(show) }
    }

    fun setPreferredPlayer(playerType: PlayerType) {
        launch { preferencesStore.setPreferredPlayer(playerType) }
    }

    fun setStreamingQuality(quality: StreamingQuality) {
        launch { preferencesStore.setStreamingQuality(quality) }
    }

    fun setVideoSeekDurationMs(ms: Long) {
        launch { preferencesStore.setVideoSeekDurationMs(ms) }
    }

    fun setVideoGesturesEnabled(enabled: Boolean) {
        launch { preferencesStore.setVideoGesturesEnabled(enabled) }
    }

    fun setVideoDefaultOrientation(mode: OrientationMode) {
        launch { preferencesStore.setVideoDefaultOrientation(mode) }
    }

    fun setVideoAutoplayNext(enabled: Boolean) {
        launch { preferencesStore.setVideoAutoplayNext(enabled) }
    }

    fun setAudioDefaultSpeed(speed: Float) {
        launch { preferencesStore.setAudioDefaultSpeed(speed) }
    }

    fun setGaplessEnabled(enabled: Boolean) {
        launch { preferencesStore.setGaplessEnabled(enabled) }
    }

    fun setCrossfadeDurationMs(ms: Long) {
        launch { preferencesStore.setCrossfadeDurationMs(ms) }
    }

    fun setAudioNormalizationEnabled(enabled: Boolean) {
        launch { preferencesStore.setAudioNormalizationEnabled(enabled) }
    }

    fun setAudioAutoplayNext(enabled: Boolean) {
        launch { preferencesStore.setAudioAutoplayNext(enabled) }
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        launch { preferencesStore.setSubtitleStyle(style) }
    }

    fun setPreferredSubtitleLanguage(language: String?) {
        launch { preferencesStore.setPreferredSubtitleLanguage(language) }
    }

    fun setPinLockEnabled(enabled: Boolean) {
        launch { preferencesStore.setPinLockEnabled(enabled) }
    }

    fun setPinHash(hash: String?) {
        launch { preferencesStore.setPinHash(hash) }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        launch { preferencesStore.setBiometricLockEnabled(enabled) }
    }

    fun setAutoLockTimerMs(ms: Long) {
        launch { preferencesStore.setAutoLockTimerMs(ms) }
    }

    fun hashPin(pin: String): String = preferencesStore.hashPin(pin)

    fun setSeerrServerUrl(url: String) {
        launch { seerrPreferencesStore.setServerUrl(url) }
    }

    fun setSeerrApiKey(key: String) {
        launch { seerrPreferencesStore.setApiKey(key) }
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
