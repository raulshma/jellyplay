package com.raulshma.jellyplay.feature.onboarding

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationSlice
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The ~27 preference fields the onboarding wizard's per-step screens read,
 * projected off the owning store slices instead of the legacy aggregate. The
 * fields span 8 domains (appearance, home discovery, navigation, playback,
 * video player, audio, subtitle, security) and are combined into one holder so
 * the screen keeps its single `preferences.field` read pattern.
 */
@Immutable
data class OnboardingPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicTheming: Boolean = true,
    val oledMode: Boolean = false,
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val accentColorSwatch: String = "dynamic",
    val colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    val homeHeroEnabled: Boolean = true,
    val performanceMode: Boolean = false,
    val homeMode: HomeMode = HomeMode.VIDEO,
    val navBarShowLabels: Boolean = true,
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val videoSeekDurationMs: Long = 10_000L,
    val videoGesturesEnabled: Boolean = true,
    val videoDefaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val videoAutoplayNext: Boolean = true,
    val audioDefaultSpeed: Float = 1.0f,
    val audioGaplessEnabled: Boolean = true,
    val audioCrossfadeDurationMs: Long = 0L,
    val audioNormalizationEnabled: Boolean = false,
    val audioAutoplayNext: Boolean = true,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val pinLockEnabled: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val autoLockTimerMs: Long = 30_000L,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appearanceStore: AppearanceStore,
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val navigationStore: NavigationStore,
    private val playbackStore: PlaybackStore,
    private val videoPlayerStore: VideoPlayerStore,
    private val audioStore: AudioStore,
    private val subtitleLanguageStore: SubtitleLanguageStore,
    private val securityStore: SecurityStore,
    val seerrPreferencesStore: SeerrPreferencesStore,
    private val seerrSecureCredentialsStore: SeerrSecureCredentialsStore,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    val preferences = combine(
        appearanceStore.appearance,
        homeDiscoveryStore.homeDiscovery,
        navigationStore.navigation,
        playbackStore.playback,
        videoPlayerStore.videoPlayer,
        audioStore.audio,
        subtitleLanguageStore.subtitle,
        securityStore.security,
    ) { slices ->
        @Suppress("UNCHECKED_CAST")
        val appearance = slices[0] as AppearanceSlice
        @Suppress("UNCHECKED_CAST")
        val home = slices[1] as HomeDiscoverySlice
        @Suppress("UNCHECKED_CAST")
        val nav = slices[2] as NavigationSlice
        @Suppress("UNCHECKED_CAST")
        val playback = slices[3] as PlaybackSlice
        @Suppress("UNCHECKED_CAST")
        val videoPlayer = slices[4] as VideoPlayerSlice
        @Suppress("UNCHECKED_CAST")
        val audio = slices[5] as AudioSlice
        @Suppress("UNCHECKED_CAST")
        val subtitle = slices[6] as SubtitleSlice
        @Suppress("UNCHECKED_CAST")
        val security = slices[7] as SecuritySlice
        OnboardingPreferences(
            themeMode = appearance.themeMode,
            dynamicTheming = appearance.dynamicTheming,
            oledMode = appearance.oledMode,
            contrastLevel = appearance.contrastLevel,
            accentColorSwatch = appearance.accentColorSwatch,
            colorStyle = appearance.colorStyle,
            homeHeroEnabled = home.homeHeroEnabled,
            performanceMode = appearance.performanceMode,
            homeMode = home.homeMode,
            navBarShowLabels = nav.navBarShowLabels,
            enabledHomeSectionTypes = home.enabledHomeSectionTypes,
            preferredPlayer = playback.preferredPlayer,
            streamingQuality = playback.streamingQuality,
            videoSeekDurationMs = videoPlayer.videoSeekDurationMs,
            videoGesturesEnabled = videoPlayer.videoGesturesEnabled,
            videoDefaultOrientation = videoPlayer.videoDefaultOrientation,
            videoAutoplayNext = videoPlayer.videoAutoplayNext,
            audioDefaultSpeed = audio.audioDefaultSpeed,
            audioGaplessEnabled = audio.audioGaplessEnabled,
            audioCrossfadeDurationMs = audio.audioCrossfadeDurationMs,
            audioNormalizationEnabled = audio.audioNormalizationEnabled,
            audioAutoplayNext = audio.audioAutoplayNext,
            subtitleStyle = subtitle.subtitleStyle,
            pinLockEnabled = security.pinLockEnabled,
            biometricLockEnabled = security.biometricLockEnabled,
            autoLockTimerMs = security.autoLockTimerMs,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), OnboardingPreferences())

    val seerrPreferences = seerrPreferencesStore.preferences

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
        editor.edit { appRuntimeState.setOnboardingCompleted(true) }
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
