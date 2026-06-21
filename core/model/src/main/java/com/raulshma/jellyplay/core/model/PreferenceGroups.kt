package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class VideoPlayerPreferences(
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val audioPassthrough: Boolean = false,
    val frameRateMatching: Boolean = false,
    val videoSeekDurationMs: Long = 10_000L,
    val videoDefaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val videoControlsTimeoutMs: Long = 5_000L,
    val videoGesturesEnabled: Boolean = true,
    val videoHoldSpeedEnabled: Boolean = true,
    val videoHoldSpeedMultiplier: Float = 2.0f,
    val videoDefaultSpeed: Float = 1.0f,
    val videoDefaultAspectRatio: String = "AUTO",
    val videoAutoplayNext: Boolean = false,
    val trailerAutoplay: Boolean = true,
    val videoSwipeSeekMaxMs: Long = 120_000L,
    val videoRememberBrightness: Boolean = false,
    val videoBrightnessLevel: Float = 0.5f,
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
    val videoEpisodeBrowserEnabled: Boolean = true,
    val videoShowPlaybackMetadata: Boolean = true,
    val videoPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val keepScreenOnDuringVideo: Boolean = true,
    val showTimeRemaining: Boolean = false,
    val pauseOnAudioFocusLoss: Boolean = true,
    val volumeBoostEnabled: Boolean = false,
    val volumeBoostGain: Int = 0,
    val backgroundVideoAudioEnabled: Boolean = false,
    val autoPlayCountdownSec: Int = 10,
    val reduceMotionEnabled: Boolean = false,
    val preferAudioDescription: Boolean = false,
    val highContrastSubtitles: Boolean = false,
    val blueLightFilterEnabled: Boolean = false,
    val blueLightFilterStrength: Float = 0.3f,
    val tvZoomModePercent: Float = 0f,
    val mpvConfig: MpvEngineConfig = MpvEngineConfig(),
    val libVlcConfig: LibVlcEngineConfig = LibVlcEngineConfig(),
    val exoPlayerConfig: ExoPlayerEngineConfig = ExoPlayerEngineConfig(),
)

@Immutable
@Serializable
data class AudioPlayerPreferences(
    val audioDefaultSpeed: Float = 1.0f,
    val audioNightModeVolume: Float = 0.4f,
    val audioNightModeGain: Int = 1200,
    val audioSkipPreviousThresholdMs: Long = 3_000L,
    val audioAutoplayNext: Boolean = true,
    val audioPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val audioNormalizationEnabled: Boolean = false,
    val replayGainPreAmpDb: Float = 0f,
    val channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    val channelMixEnabled: Boolean = false,
    val audioGaplessEnabled: Boolean = true,
    val audioCrossfadeDurationMs: Long = 0L,
    val equalizerEnabled: Boolean = false,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 500,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val lrBalance: Float = 0f,
    val autoEqByGenre: Boolean = false,
    val pitchSemitones: Float = 0f,
    val audioDelayMs: Long = 0L,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val audioVisualizerEnabled: Boolean = false,
)

@Immutable
@Serializable
data class SubtitlePreferences(
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val preferredSubtitleLanguage: String? = null,
    val preferredAudioLanguage: String? = null,
)

@Immutable
@Serializable
data class SecurityPreferences(
    val pinLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val biometricLockEnabled: Boolean = false,
    val autoLockTimerMs: Long = 30_000L,
    val incognitoModeEnabled: Boolean = false,
    val remoteControlEnabled: Boolean = true,
)

@Immutable
@Serializable
data class DownloadPreferences(
    val wifiOnlyDownloads: Boolean = true,
    val downloadConnections: Int = 4,
    val downloadQuality: DownloadQuality = DownloadQuality.ORIGINAL,
    val smartDownloadsEnabled: Boolean = false,
    val autoDownloadNewEpisodes: Boolean = false,
    val maxDownloadStorageGb: Int = 0,
    val downloadStorageLocation: String = "INTERNAL",
    val manualOfflineEnabled: Boolean = false,
    val autoOfflineEnabled: Boolean = true,
)

@Immutable
@Serializable
data class SyncPlayPreferences(
    val syncPlayJoinBehavior: SyncPlayJoinBehavior = SyncPlayJoinBehavior.ASK,
    val syncPlayToleranceMs: Long = 100L,
    val syncPlayAutoAcceptInvites: Boolean = false,
)

@Immutable
@Serializable
data class AppearancePreferences(
    val dynamicTheming: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val oledMode: Boolean = false,
    val accentColorSwatch: String = "dynamic",
    val colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    val navBarShowLabels: Boolean = true,
    val homeHeroEnabled: Boolean = true,
    val performanceMode: Boolean = false,
    val synthwaveMode: Boolean = false,
    val synthwaveAccent: String = "magenta",
    val soothingMode: Boolean = false,
    val soothingAccent: String = "ocean",
    val monochromeMode: Boolean = false,
    val libraryViewMode: LibraryViewMode = LibraryViewMode.GRID,
)

val UserPreferences.videoPlayer: VideoPlayerPreferences
    get() = VideoPlayerPreferences(
        preferredPlayer = preferredPlayer,
        decoderMode = decoderMode,
        audioPassthrough = audioPassthrough,
        frameRateMatching = frameRateMatching,
        videoSeekDurationMs = videoSeekDurationMs,
        videoDefaultOrientation = videoDefaultOrientation,
        videoControlsTimeoutMs = videoControlsTimeoutMs,
        videoGesturesEnabled = videoGesturesEnabled,
        videoHoldSpeedEnabled = videoHoldSpeedEnabled,
        videoHoldSpeedMultiplier = videoHoldSpeedMultiplier,
        videoDefaultSpeed = videoDefaultSpeed,
        videoDefaultAspectRatio = videoDefaultAspectRatio,
        videoAutoplayNext = videoAutoplayNext,
        trailerAutoplay = trailerAutoplay,
        videoSwipeSeekMaxMs = videoSwipeSeekMaxMs,
        videoRememberBrightness = videoRememberBrightness,
        videoBrightnessLevel = videoBrightnessLevel,
        trickplayEnabled = trickplayEnabled,
        trickplayOnSeekGesture = trickplayOnSeekGesture,
        videoEpisodeBrowserEnabled = videoEpisodeBrowserEnabled,
        videoShowPlaybackMetadata = videoShowPlaybackMetadata,
        videoPreloadBufferSize = videoPreloadBufferSize,
        keepScreenOnDuringVideo = keepScreenOnDuringVideo,
        showTimeRemaining = showTimeRemaining,
        pauseOnAudioFocusLoss = pauseOnAudioFocusLoss,
        volumeBoostEnabled = volumeBoostEnabled,
        volumeBoostGain = volumeBoostGain,
        backgroundVideoAudioEnabled = backgroundVideoAudioEnabled,
        autoPlayCountdownSec = autoPlayCountdownSec,
        reduceMotionEnabled = reduceMotionEnabled,
        preferAudioDescription = preferAudioDescription,
        highContrastSubtitles = highContrastSubtitles,
        blueLightFilterEnabled = blueLightFilterEnabled,
        blueLightFilterStrength = blueLightFilterStrength,
        tvZoomModePercent = tvZoomModePercent,
        mpvConfig = mpvConfig,
        libVlcConfig = libVlcConfig,
        exoPlayerConfig = exoPlayerConfig,
    )

val UserPreferences.audioPlayer: AudioPlayerPreferences
    get() = AudioPlayerPreferences(
        audioDefaultSpeed = audioDefaultSpeed,
        audioNightModeVolume = audioNightModeVolume,
        audioNightModeGain = audioNightModeGain,
        audioSkipPreviousThresholdMs = audioSkipPreviousThresholdMs,
        audioAutoplayNext = audioAutoplayNext,
        audioPreloadBufferSize = audioPreloadBufferSize,
        audioNormalizationMode = audioNormalizationMode,
        audioNormalizationEnabled = audioNormalizationEnabled,
        replayGainPreAmpDb = replayGainPreAmpDb,
        channelMixMode = channelMixMode,
        channelMixEnabled = channelMixEnabled,
        audioGaplessEnabled = audioGaplessEnabled,
        audioCrossfadeDurationMs = audioCrossfadeDurationMs,
        equalizerEnabled = equalizerEnabled,
        equalizerSettings = equalizerSettings,
        equalizerPreset = equalizerPreset,
        bassBoostEnabled = bassBoostEnabled,
        bassBoostStrength = bassBoostStrength,
        virtualizerEnabled = virtualizerEnabled,
        virtualizerStrength = virtualizerStrength,
        reverbPreset = reverbPreset,
        lrBalance = lrBalance,
        autoEqByGenre = autoEqByGenre,
        pitchSemitones = pitchSemitones,
        audioDelayMs = audioDelayMs,
        dialogueBoostEnabled = dialogueBoostEnabled,
        dialogueBoostStrength = dialogueBoostStrength,
        nightModeEnabled = nightModeEnabled,
        nightModeStrength = nightModeStrength,
        audioVisualizerEnabled = audioVisualizerEnabled,
    )

val UserPreferences.subtitle: SubtitlePreferences
    get() = SubtitlePreferences(
        subtitleStyle = subtitleStyle,
        preferredSubtitleLanguage = preferredSubtitleLanguage,
        preferredAudioLanguage = preferredAudioLanguage,
    )

val UserPreferences.security: SecurityPreferences
    get() = SecurityPreferences(
        pinLockEnabled = pinLockEnabled,
        pinHash = pinHash,
        biometricLockEnabled = biometricLockEnabled,
        autoLockTimerMs = autoLockTimerMs,
        incognitoModeEnabled = incognitoModeEnabled,
        remoteControlEnabled = remoteControlEnabled,
    )

val UserPreferences.download: DownloadPreferences
    get() = DownloadPreferences(
        wifiOnlyDownloads = wifiOnlyDownloads,
        downloadConnections = downloadConnections,
        downloadQuality = downloadQuality,
        smartDownloadsEnabled = smartDownloadsEnabled,
        autoDownloadNewEpisodes = autoDownloadNewEpisodes,
        maxDownloadStorageGb = maxDownloadStorageGb,
        downloadStorageLocation = downloadStorageLocation,
        manualOfflineEnabled = manualOfflineEnabled,
        autoOfflineEnabled = autoOfflineEnabled,
    )

val UserPreferences.syncPlay: SyncPlayPreferences
    get() = SyncPlayPreferences(
        syncPlayJoinBehavior = syncPlayJoinBehavior,
        syncPlayToleranceMs = syncPlayToleranceMs,
        syncPlayAutoAcceptInvites = syncPlayAutoAcceptInvites,
    )

val UserPreferences.appearance: AppearancePreferences
    get() = AppearancePreferences(
        dynamicTheming = dynamicTheming,
        themeMode = themeMode,
        contrastLevel = contrastLevel,
        oledMode = oledMode,
        accentColorSwatch = accentColorSwatch,
        colorStyle = colorStyle,
        navBarShowLabels = navBarShowLabels,
        homeHeroEnabled = homeHeroEnabled,
        performanceMode = performanceMode,
        synthwaveMode = synthwaveMode,
        synthwaveAccent = synthwaveAccent,
        soothingMode = soothingMode,
        soothingAccent = soothingAccent,
        monochromeMode = monochromeMode,
        libraryViewMode = libraryViewMode,
    )
