package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PreferencesEditor(
    private val scope: CoroutineScope,
    private val store: UserPreferencesStore,
) {
    fun setThemeMode(mode: ThemeMode) = scope.launch { store.setThemeMode(mode) }
    fun setDynamicTheming(enabled: Boolean) = scope.launch { store.setDynamicTheming(enabled) }
    fun setOledMode(enabled: Boolean) = scope.launch { store.setOledMode(enabled) }
    fun setContrastLevel(level: ContrastLevel) = scope.launch { store.setContrastLevel(level) }
    fun setAccentColorSwatch(swatch: String) = scope.launch { store.setAccentColorSwatch(swatch) }
    fun setColorStyle(style: ColorStyle) = scope.launch { store.setColorStyle(style) }
    fun setPerformanceMode(enabled: Boolean) = scope.launch { store.setPerformanceMode(enabled) }
    fun setHomeHeroEnabled(enabled: Boolean) = scope.launch { store.setHomeHeroEnabled(enabled) }
    fun setHomeMode(mode: HomeMode) = scope.launch { store.setHomeMode(mode) }
    fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) = scope.launch { store.setEnabledHomeSectionTypes(types) }
    fun setNavBarShowLabels(show: Boolean) = scope.launch { store.setNavBarShowLabels(show) }
    fun setPreferredPlayer(playerType: PlayerType) = scope.launch { store.setPreferredPlayer(playerType) }
    fun setStreamingQuality(quality: StreamingQuality) = scope.launch { store.setStreamingQuality(quality) }
    fun setVideoSeekDurationMs(ms: Long) = scope.launch { store.setVideoSeekDurationMs(ms) }
    fun setVideoGesturesEnabled(enabled: Boolean) = scope.launch { store.setVideoGesturesEnabled(enabled) }
    fun setVideoDefaultOrientation(mode: OrientationMode) = scope.launch { store.setVideoDefaultOrientation(mode) }
    fun setVideoAutoplayNext(enabled: Boolean) = scope.launch { store.setVideoAutoplayNext(enabled) }
    fun setAudioDefaultSpeed(speed: Float) = scope.launch { store.setAudioDefaultSpeed(speed) }
    fun setGaplessEnabled(enabled: Boolean) = scope.launch { store.setGaplessEnabled(enabled) }
    fun setCrossfadeDurationMs(ms: Long) = scope.launch { store.setCrossfadeDurationMs(ms) }
    fun setAudioNormalizationEnabled(enabled: Boolean) = scope.launch { store.setAudioNormalizationEnabled(enabled) }
    fun setAudioAutoplayNext(enabled: Boolean) = scope.launch { store.setAudioAutoplayNext(enabled) }
    fun setSubtitleStyle(style: SubtitleStyle) = scope.launch { store.setSubtitleStyle(style) }
    fun setPreferredSubtitleLanguage(language: String?) = scope.launch { store.setPreferredSubtitleLanguage(language) }
    fun setSubtitlesForcedOnly(enabled: Boolean) = scope.launch { store.setSubtitlesForcedOnly(enabled) }
    fun setPinLockEnabled(enabled: Boolean) = scope.launch { store.setPinLockEnabled(enabled) }
    fun setPinHash(hash: String?) = scope.launch { store.setPinHash(hash) }
    fun setBiometricLockEnabled(enabled: Boolean) = scope.launch { store.setBiometricLockEnabled(enabled) }
    fun setAutoLockTimerMs(ms: Long) = scope.launch { store.setAutoLockTimerMs(ms) }
    fun hashPin(pin: String): String = store.hashPin(pin)
}
