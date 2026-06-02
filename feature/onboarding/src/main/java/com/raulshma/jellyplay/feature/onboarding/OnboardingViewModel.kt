package com.raulshma.jellyplay.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val preferencesStore: UserPreferencesStore,
    val seerrPreferencesStore: SeerrPreferencesStore,
) : ViewModel() {

    val preferences = preferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    val seerrPreferences = seerrPreferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.raulshma.jellyplay.core.model.seerr.SeerrPreferences())

    private val _currentStep = MutableStateFlow(0)
    val currentStep = _currentStep.asStateFlow()

    fun setStep(step: Int) {
        _currentStep.value = step.coerceIn(0, OnboardingStep.count - 1)
    }

    fun nextStep() {
        setStep(_currentStep.value + 1)
    }

    fun skipOnboarding() {
        completeOnboarding()
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesStore.setOnboardingCompleted(true)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesStore.setThemeMode(mode) }
    }

    fun setDynamicTheming(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setDynamicTheming(enabled) }
    }

    fun setOledMode(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setOledMode(enabled) }
    }

    fun setContrastLevel(level: ContrastLevel) {
        viewModelScope.launch { preferencesStore.setContrastLevel(level) }
    }

    fun setPerformanceMode(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setPerformanceMode(enabled) }
    }

    fun setHomeMode(mode: HomeMode) {
        viewModelScope.launch { preferencesStore.setHomeMode(mode) }
    }

    fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) {
        viewModelScope.launch { preferencesStore.setEnabledHomeSectionTypes(types) }
    }

    fun setNavBarShowLabels(show: Boolean) {
        viewModelScope.launch { preferencesStore.setNavBarShowLabels(show) }
    }

    fun setPreferredPlayer(playerType: PlayerType) {
        viewModelScope.launch { preferencesStore.setPreferredPlayer(playerType) }
    }

    fun setStreamingQuality(quality: StreamingQuality) {
        viewModelScope.launch { preferencesStore.setStreamingQuality(quality) }
    }

    fun setVideoSeekDurationMs(ms: Long) {
        viewModelScope.launch { preferencesStore.setVideoSeekDurationMs(ms) }
    }

    fun setVideoGesturesEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setVideoGesturesEnabled(enabled) }
    }

    fun setVideoDefaultOrientation(mode: OrientationMode) {
        viewModelScope.launch { preferencesStore.setVideoDefaultOrientation(mode) }
    }

    fun setVideoAutoplayNext(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setVideoAutoplayNext(enabled) }
    }

    fun setAudioDefaultSpeed(speed: Float) {
        viewModelScope.launch { preferencesStore.setAudioDefaultSpeed(speed) }
    }

    fun setGaplessEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setGaplessEnabled(enabled) }
    }

    fun setCrossfadeDurationMs(ms: Long) {
        viewModelScope.launch { preferencesStore.setCrossfadeDurationMs(ms) }
    }

    fun setAudioNormalizationEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setAudioNormalizationEnabled(enabled) }
    }

    fun setAudioAutoplayNext(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setAudioAutoplayNext(enabled) }
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        viewModelScope.launch { preferencesStore.setSubtitleStyle(style) }
    }

    fun setPreferredSubtitleLanguage(language: String?) {
        viewModelScope.launch { preferencesStore.setPreferredSubtitleLanguage(language) }
    }

    fun setPinLockEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setPinLockEnabled(enabled) }
    }

    fun setPinHash(hash: String?) {
        viewModelScope.launch { preferencesStore.setPinHash(hash) }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setBiometricLockEnabled(enabled) }
    }

    fun setAutoLockTimerMs(ms: Long) {
        viewModelScope.launch { preferencesStore.setAutoLockTimerMs(ms) }
    }

    fun hashPin(pin: String): String = preferencesStore.hashPin(pin)

    fun setSeerrServerUrl(url: String) {
        viewModelScope.launch { seerrPreferencesStore.setServerUrl(url) }
    }

    fun setSeerrApiKey(key: String) {
        viewModelScope.launch { seerrPreferencesStore.setApiKey(key) }
    }

    fun setSeerrEnabled(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setEnabled(enabled) }
    }

    fun setSeerrSearchEnabled(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setSearchEnabled(enabled) }
    }

    fun setSeerrRecommendationsEnabled(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setRecommendationsEnabled(enabled) }
    }

    fun setSeerrDiscoverEnabled(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setDiscoverEnabled(enabled) }
    }

    fun setSeerrStreamingRegion(region: String) {
        viewModelScope.launch { seerrPreferencesStore.setStreamingRegion(region) }
    }

    fun setSeerrDiscoverRegion(region: String) {
        viewModelScope.launch { seerrPreferencesStore.setDiscoverRegion(region) }
    }

    fun seerrDisconnect() {
        viewModelScope.launch { seerrPreferencesStore.disconnect() }
    }
}
