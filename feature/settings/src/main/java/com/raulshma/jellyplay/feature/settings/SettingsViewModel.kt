package com.raulshma.jellyplay.feature.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesStore: UserPreferencesStore,
    private val authRepository: AuthRepository,
    private val downloadRepository: DownloadRepository,
    private val mediaRepository: MediaRepository,
    private val apiClient: com.raulshma.jellyplay.core.network.JellyfinApiClient,
    private val notificationScheduler: NotificationScheduler,
) : JellyPlayViewModel() {

    private val editor = PreferencesEditor(scope, preferencesStore)

    var preferences by composeState(UserPreferences())
        private set

    var currentUserName by composeState("")
        private set

    var cacheSizeMb by composeState(0L)
        private set

    val appVersion: String by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    }

    var currentUser by composeState<UserInfo?>(null)
        private set

    var currentServerUsers by composeState<List<UserInfo>>(emptyList())
        private set

    var isLoadingUsers by composeState(false)
        private set

    var libraryFolders by composeState<List<LibraryFolder>>(emptyList())
        private set

    var isLoadingLibraries by composeState(false)
        private set

    val currentServerAddress = authRepository.currentServer
        .map { it?.address ?: "" }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), "")

    var activeSessions by composeState<List<com.raulshma.jellyplay.core.model.SessionInfo>>(emptyList())
        private set

    var isLoadingSessions by composeState(false)
        private set

    var messageSentEvent by composeState<String?>(null)
        private set

    private var sessionRefreshJob: Job? = null

    init {
        launch {
            preferencesStore.preferences.collect { prefs ->
                preferences = prefs
            }
        }
        launch {
            authRepository.currentUser.collect { user ->
                currentUser = user
                currentUserName = user?.name ?: ""
                if (user?.isAdmin == true) {
                    loadSessions()
                    startSessionAutoRefresh()
                } else {
                    sessionRefreshJob?.cancel()
                    activeSessions = emptyList()
                }
            }
        }
        launch {
            authRepository.currentServerUsers.collect { users ->
                currentServerUsers = users
                isLoadingUsers = false
            }
        }
        calculateCacheSize()
        loadLibraryFolders()
    }

    private fun loadLibraryFolders() {
        launch {
            isLoadingLibraries = true
            mediaRepository.getLibraryFolders()
                .onSuccess { folders ->
                    libraryFolders = folders.filter { it.collectionType != "music" }
                }
            isLoadingLibraries = false
        }
    }

    private fun loadSessions() {
        launch {
            isLoadingSessions = true
            apiClient.getSessions()
                .onSuccess { sessions ->
                    val cutoff = java.time.Instant.now().minusSeconds(5 * 60)
                    activeSessions = sessions.filter {
                        val lastActivity = try { java.time.Instant.parse(it.lastActivityDate) } catch (_: Exception) { java.time.Instant.MIN }
                        it.isActive && it.client.isNotBlank() && it.deviceName.isNotBlank() && it.client != "Jellyfin Server" &&
                        (it.nowPlayingItem != null || lastActivity.isAfter(cutoff))
                    }
                }
            isLoadingSessions = false
        }
    }

    private fun startSessionAutoRefresh() {
        sessionRefreshJob?.cancel()
        sessionRefreshJob = launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                apiClient.getSessions()
                    .onSuccess { sessions ->
                        val cutoff = java.time.Instant.now().minusSeconds(5 * 60)
                        activeSessions = sessions.filter {
                            val lastActivity = try { java.time.Instant.parse(it.lastActivityDate) } catch (_: Exception) { java.time.Instant.MIN }
                            it.isActive && it.client.isNotBlank() && it.deviceName.isNotBlank() && it.client != "Jellyfin Server" &&
                            (it.nowPlayingItem != null || lastActivity.isAfter(cutoff))
                        }
                    }
            }
        }
    }

    fun sendMessageToSession(sessionId: String, header: String, text: String) {
        launch {
            apiClient.sendMessageToSession(sessionId, header, text)
                .onSuccess {
                    messageSentEvent = "Message sent successfully"
                }
                .onFailure {
                    messageSentEvent = "Failed to send message"
                }
        }
    }

    fun clearMessageEvent() {
        messageSentEvent = null
    }

    override fun onCleared() {
        super.onCleared()
        sessionRefreshJob?.cancel()
    }

    fun setDynamicTheming(enabled: Boolean) = editor.setDynamicTheming(enabled)

    fun setAccentColorSwatch(swatch: String) = editor.setAccentColorSwatch(swatch)

    fun setColorStyle(style: ColorStyle) = editor.setColorStyle(style)

    fun setThemeMode(mode: ThemeMode) = editor.setThemeMode(mode)

    fun setContrastLevel(level: ContrastLevel) = editor.setContrastLevel(level)

    fun setOledMode(enabled: Boolean) = editor.setOledMode(enabled)

    fun setPreferredPlayer(playerType: PlayerType) = editor.setPreferredPlayer(playerType)

    fun setTrailerAutoplay(enabled: Boolean) {
        launch { preferencesStore.setTrailerAutoplay(enabled) }
    }

    fun setPreferredAudioLanguage(language: String?) {
        launch { preferencesStore.setPreferredAudioLanguage(language) }
    }

    fun setPreferredSubtitleLanguage(language: String?) = editor.setPreferredSubtitleLanguage(language)

    fun setStreamingQuality(quality: StreamingQuality) = editor.setStreamingQuality(quality)

    fun setWifiOnlyDownloads(enabled: Boolean) {
        launch { preferencesStore.setWifiOnlyDownloads(enabled) }
    }

    fun setDownloadConnections(count: Int) {
        launch { preferencesStore.setDownloadConnections(count) }
    }

    fun setMaxCacheSize(sizeMb: Int) {
        launch { preferencesStore.setMaxCacheSize(sizeMb) }
    }

    fun setAutoDeleteCache(enabled: Boolean) {
        launch { preferencesStore.setAutoDeleteCache(enabled) }
    }

    fun clearCache() {
        launch {
            try {
                context.cacheDir.deleteRecursively()
                val externalCache = context.externalCacheDir
                if (externalCache != null && externalCache.exists()) {
                    externalCache.deleteRecursively()
                }
                calculateCacheSize()
            } catch (_: Exception) {}
        }
    }

    private fun calculateCacheSize() {
        launch {
            val cacheSize = withContext(Dispatchers.IO) { getDirSize(context.cacheDir) }
            val externalCacheSize = withContext(Dispatchers.IO) {
                context.externalCacheDir?.let { getDirSize(it) } ?: 0L
            }
            cacheSizeMb = (cacheSize + externalCacheSize) / (1024 * 1024)
        }
    }

    private fun getDirSize(dir: File): Long {
        var size = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getDirSize(file) else file.length()
            }
        } else if (dir.isFile) {
            size = dir.length()
        }
        return size
    }

    fun logout() {
        launch { authRepository.logout() }
    }

    fun setPinLockEnabled(enabled: Boolean) = editor.setPinLockEnabled(enabled)

    fun setPin(pin: String) {
        launch {
            val hash = preferencesStore.hashPin(pin)
            preferencesStore.setPinHash(hash)
            preferencesStore.setPinLockEnabled(true)
        }
    }

    fun clearPin() {
        launch {
            preferencesStore.setPinLockEnabled(false)
            preferencesStore.setPinHash(null)
        }
    }

    fun verifyPin(pin: String): Boolean {
        return preferencesStore.verifyPin(pin, preferences.pinHash)
    }

    fun setBiometricLockEnabled(enabled: Boolean) = editor.setBiometricLockEnabled(enabled)

    fun setShowAdvancedSettings(enabled: Boolean) {
        launch { preferencesStore.setShowAdvancedSettings(enabled) }
    }

    fun setAutoLockTimerMs(ms: Long) = editor.setAutoLockTimerMs(ms)

    fun setDialogueBoostEnabled(enabled: Boolean) {
        launch { preferencesStore.setDialogueBoostEnabled(enabled) }
    }

    fun setDialogueBoostStrength(strength: EffectStrength) {
        launch { preferencesStore.setDialogueBoostStrength(strength) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        launch { preferencesStore.setEqualizerEnabled(enabled) }
    }

    fun setNightModeEnabled(enabled: Boolean) {
        launch { preferencesStore.setNightModeEnabled(enabled) }
    }

    fun setNightModeStrength(strength: EffectStrength) {
        launch { preferencesStore.setNightModeStrength(strength) }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        launch { preferencesStore.setBassBoostEnabled(enabled) }
    }

    fun setBassBoostStrength(strength: EffectStrength) {
        launch { preferencesStore.setBassBoostStrength(strength) }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        launch { preferencesStore.setVirtualizerEnabled(enabled) }
    }

    fun setVirtualizerStrength(strength: Int) {
        launch { preferencesStore.setVirtualizerStrength(strength) }
    }

    fun setReverbPreset(preset: com.raulshma.jellyplay.core.model.ReverbPreset) {
        launch { preferencesStore.setReverbPreset(preset) }
    }

    fun setAutoEqByGenre(enabled: Boolean) {
        launch { preferencesStore.setAutoEqByGenre(enabled) }
    }

    fun setDecoderMode(mode: DecoderMode) {
        launch { preferencesStore.setDecoderMode(mode) }
    }

    fun setAudioPassthrough(enabled: Boolean) {
        launch { preferencesStore.setAudioPassthrough(enabled) }
    }

    fun setFrameRateMatching(enabled: Boolean) {
        launch { preferencesStore.setFrameRateMatching(enabled) }
    }

    fun switchUser(userId: String, onComplete: () -> Unit) {
        launch {
            authRepository.switchUser(userId)
            onComplete()
        }
    }

    fun removeUser(userId: String) {
        launch {
            authRepository.removeUser(userId)
        }
    }

    fun setVideoSeekDurationMs(ms: Long) = editor.setVideoSeekDurationMs(ms)

    fun setVideoDefaultOrientation(mode: OrientationMode) = editor.setVideoDefaultOrientation(mode)

    fun setVideoControlsTimeoutMs(ms: Long) {
        launch { preferencesStore.setVideoControlsTimeoutMs(ms) }
    }

    fun setVideoGesturesEnabled(enabled: Boolean) = editor.setVideoGesturesEnabled(enabled)

    fun setVideoDefaultSpeed(speed: Float) {
        launch { preferencesStore.setVideoDefaultSpeed(speed) }
    }

    fun setVideoDefaultAspectRatio(ratio: String) {
        launch { preferencesStore.setVideoDefaultAspectRatio(ratio) }
    }

    fun setVideoAutoplayNext(enabled: Boolean) = editor.setVideoAutoplayNext(enabled)

    fun setVideoSwipeSeekMaxMs(ms: Long) {
        launch { preferencesStore.setVideoSwipeSeekMaxMs(ms) }
    }

    fun setVideoRememberBrightness(enabled: Boolean) {
        launch { preferencesStore.setVideoRememberBrightness(enabled) }
    }

    fun setVideoBrightnessLevel(level: Float) {
        launch { preferencesStore.setVideoBrightnessLevel(level) }
    }

    fun setAudioDefaultSpeed(speed: Float) = editor.setAudioDefaultSpeed(speed)

    fun setAudioNightModeVolume(volume: Float) {
        launch { preferencesStore.setAudioNightModeVolume(volume) }
    }

    fun setAudioNightModeGain(gain: Int) {
        launch { preferencesStore.setAudioNightModeGain(gain) }
    }

    fun setAudioSkipPreviousThresholdMs(ms: Long) {
        launch { preferencesStore.setAudioSkipPreviousThresholdMs(ms) }
    }

    fun setAudioAutoplayNext(enabled: Boolean) = editor.setAudioAutoplayNext(enabled)

    fun setAudioDelayMs(ms: Long) {
        launch { preferencesStore.setAudioDelay(ms) }
    }

    fun setHomeMode(mode: HomeMode) = editor.setHomeMode(mode)

    fun setSubtitleStyle(style: SubtitleStyle) = editor.setSubtitleStyle(style)

    fun setEqualizerSettings(settings: EqualizerSettings) {
        launch { preferencesStore.setEqualizerSettings(settings) }
    }

    fun setTrickplayEnabled(enabled: Boolean) {
        launch { preferencesStore.setTrickplayEnabled(enabled) }
    }

    fun setTrickplayOnSeekGesture(enabled: Boolean) {
        launch { preferencesStore.setTrickplayOnSeekGesture(enabled) }
    }

    fun setVideoPreloadBufferSize(size: com.raulshma.jellyplay.core.model.PreloadBufferSize) {
        launch { preferencesStore.setVideoPreloadBufferSize(size) }
    }

    fun setAudioPreloadBufferSize(size: com.raulshma.jellyplay.core.model.PreloadBufferSize) {
        launch { preferencesStore.setAudioPreloadBufferSize(size) }
    }

    fun setSegmentBehavior(
        type: com.raulshma.jellyplay.core.model.MediaSegmentType,
        behavior: com.raulshma.jellyplay.core.model.SegmentBehavior,
    ) {
        launch { preferencesStore.setSegmentBehavior(type, behavior) }
    }

    fun setVideoEpisodeBrowserEnabled(enabled: Boolean) {
        launch { preferencesStore.setVideoEpisodeBrowserEnabled(enabled) }
    }

    fun setVideoShowPlaybackMetadata(enabled: Boolean) {
        launch { preferencesStore.setVideoShowPlaybackMetadata(enabled) }
    }

    fun setGaplessEnabled(enabled: Boolean) = editor.setGaplessEnabled(enabled)

    fun setCrossfadeDurationMs(ms: Long) = editor.setCrossfadeDurationMs(ms)

    fun setAudioNormalizationMode(mode: AudioNormalizationMode) {
        launch { preferencesStore.setAudioNormalizationMode(mode) }
    }

    fun setReplayGainPreAmpDb(db: Float) {
        launch { preferencesStore.setReplayGainPreAmpDb(db) }
    }

    fun setDreamImageCategories(categories: Set<DreamImageCategory>) {
        launch { preferencesStore.setDreamImageCategories(categories) }
    }

    fun setDreamSlideshowIntervalMs(ms: Long) {
        launch { preferencesStore.setDreamSlideshowIntervalMs(ms) }
    }

    fun setDreamKenBurnsEnabled(enabled: Boolean) {
        launch { preferencesStore.setDreamKenBurnsEnabled(enabled) }
    }

    fun setDreamTransitionStyle(style: DreamTransitionStyle) {
        launch { preferencesStore.setDreamTransitionStyle(style) }
    }

    fun setDreamShowTitle(enabled: Boolean) {
        launch { preferencesStore.setDreamShowTitle(enabled) }
    }

    fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) = editor.setEnabledHomeSectionTypes(types)

    fun setHomeSectionOrder(order: List<HomeSectionType>) {
        launch { preferencesStore.setHomeSectionOrder(order) }
    }

    fun setHiddenLibrarySectionIds(ids: Set<String>) {
        launch { preferencesStore.setHiddenLibrarySectionIds(ids) }
    }

    fun toggleHomeSectionType(type: HomeSectionType, enabled: Boolean) {
        val current = preferences.enabledHomeSectionTypes.toMutableSet()
        if (enabled) current.add(type) else current.remove(type)
        setEnabledHomeSectionTypes(current)
    }

    fun toggleLibrarySection(libraryId: String, visible: Boolean) {
        val current = preferences.hiddenLibrarySectionIds.toMutableSet()
        if (visible) current.remove(libraryId) else current.add(libraryId)
        setHiddenLibrarySectionIds(current)
    }

    fun setNavBarShowLabels(show: Boolean) = editor.setNavBarShowLabels(show)

    fun setHomeHeroEnabled(enabled: Boolean) = editor.setHomeHeroEnabled(enabled)

    fun setPerformanceMode(enabled: Boolean) = editor.setPerformanceMode(enabled)

    fun setLibraryViewMode(mode: LibraryViewMode) {
        launch { preferencesStore.setLibraryViewMode(mode) }
    }

    fun setAudioVisualizerEnabled(enabled: Boolean) {
        launch { preferencesStore.setAudioVisualizerEnabled(enabled) }
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        launch { preferencesStore.setEqualizerPreset(preset) }
    }

    fun setChannelMixEnabled(enabled: Boolean) {
        launch { preferencesStore.setChannelMixEnabled(enabled) }
    }

    fun setChannelMixMode(mode: ChannelMixMode) {
        launch { preferencesStore.setChannelMixMode(mode) }
    }

    fun setSleepTimerDurationMs(ms: Long) {
        launch { preferencesStore.setSleepTimerDurationMs(ms) }
    }

    fun setLrBalance(balance: Float) {
        launch { preferencesStore.setLrBalance(balance) }
    }

    fun setSyncPlayJoinBehavior(behavior: SyncPlayJoinBehavior) {
        launch { preferencesStore.setSyncPlayJoinBehavior(behavior) }
    }

    fun setSyncPlayToleranceMs(ms: Long) {
        launch { preferencesStore.setSyncPlayToleranceMs(ms) }
    }

    fun setSyncPlayAutoAcceptInvites(enabled: Boolean) {
        launch { preferencesStore.setSyncPlayAutoAcceptInvites(enabled) }
    }

    fun setDefaultCastingStrategy(strategy: CastingStrategy) {
        launch { preferencesStore.setDefaultCastingStrategy(strategy) }
    }

    fun setBackgroundCastingEnabled(enabled: Boolean) {
        launch { preferencesStore.setBackgroundCastingEnabled(enabled) }
    }

    fun setPreferredRenderer(renderer: String?) {
        launch { preferencesStore.setPreferredRenderer(renderer) }
    }

    fun setDvrPrePaddingMinutes(minutes: Int) {
        launch { preferencesStore.setDvrPrePaddingMinutes(minutes) }
    }

    fun setDvrPostPaddingMinutes(minutes: Int) {
        launch { preferencesStore.setDvrPostPaddingMinutes(minutes) }
    }

    fun setDvrRecordingQuality(quality: String) {
        launch { preferencesStore.setDvrRecordingQuality(quality) }
    }

    fun setFavoriteChannels(channels: Set<String>) {
        launch { preferencesStore.setFavoriteChannels(channels) }
    }

    fun setEnabledNewsletterSections(sections: Set<NewsletterSectionType>) {
        launch { preferencesStore.setEnabledNewsletterSections(sections) }
    }

    fun setNewsletterSectionOrder(order: List<NewsletterSectionType>) {
        launch { preferencesStore.setNewsletterSectionOrder(order) }
    }

    fun setNewsletterEnabled(enabled: Boolean) {
        launch { preferencesStore.setNewsletterEnabled(enabled) }
    }

    fun setNewsletterDayOfWeek(day: Int) {
        launch { preferencesStore.setNewsletterDayOfWeek(day) }
    }

    fun setManualOffline(enabled: Boolean) {
        launch { preferencesStore.setManualOffline(enabled) }
    }

    fun setAutoOfflineEnabled(enabled: Boolean) {
        launch { preferencesStore.setAutoOfflineEnabled(enabled) }
    }

    fun setManualBandwidthCap(cap: Long) {
        launch { preferencesStore.setManualBandwidthCap(cap) }
    }

    fun setMeteredNetworkBehavior(behavior: MeteredNetworkBehavior) {
        launch { preferencesStore.setMeteredNetworkBehavior(behavior) }
    }

    fun setAdaptiveBitrateEnabled(enabled: Boolean) {
        launch { preferencesStore.setAdaptiveBitrateEnabled(enabled) }
    }

    fun setPitchSemitones(semitones: Float) {
        launch { preferencesStore.setPitchSemitones(semitones) }
    }


    fun setBackgroundVideoAudioEnabled(enabled: Boolean) {
        launch { preferencesStore.setBackgroundVideoAudioEnabled(enabled) }
    }

    fun setAutoPlayCountdownSec(sec: Int) {
        launch { preferencesStore.setAutoPlayCountdownSec(sec) }
    }

    fun setShowUnwatchedBadge(enabled: Boolean) {
        launch { preferencesStore.setShowUnwatchedBadge(enabled) }
    }

    fun setHideWatchedItems(enabled: Boolean) {
        launch { preferencesStore.setHideWatchedItems(enabled) }
    }

    fun setCellularStreamingQuality(quality: StreamingQuality) {
        launch { preferencesStore.setCellularStreamingQuality(quality) }
    }

    fun setShowWatchedCheckmark(enabled: Boolean) {
        launch { preferencesStore.setShowWatchedCheckmark(enabled) }
    }

    fun setDefaultLibrarySortOrder(libraryId: String, order: String) {
        launch { preferencesStore.setDefaultLibrarySortOrder(libraryId, order) }
    }

    fun setKeepScreenOnDuringVideo(enabled: Boolean) {
        launch { preferencesStore.setKeepScreenOnDuringVideo(enabled) }
    }

    fun setDownloadQuality(quality: DownloadQuality) {
        launch { preferencesStore.setDownloadQuality(quality) }
    }

    fun setSmartDownloadsEnabled(enabled: Boolean) {
        launch { preferencesStore.setSmartDownloadsEnabled(enabled) }
    }

    fun setAutoDownloadNewEpisodes(enabled: Boolean) {
        launch { preferencesStore.setAutoDownloadNewEpisodes(enabled) }
    }

    fun setIncognitoModeEnabled(enabled: Boolean) {
        launch { preferencesStore.setIncognitoModeEnabled(enabled) }
    }

    fun setShowTimeRemaining(enabled: Boolean) {
        launch { preferencesStore.setShowTimeRemaining(enabled) }
    }

    fun setPauseOnAudioFocusLoss(enabled: Boolean) {
        launch { preferencesStore.setPauseOnAudioFocusLoss(enabled) }
    }

    fun setVolumeBoostEnabled(enabled: Boolean) {
        launch { preferencesStore.setVolumeBoostEnabled(enabled) }
    }

    fun setVolumeBoostGain(gain: Int) {
        launch { preferencesStore.setVolumeBoostGain(gain) }
    }

    fun setShowShareMediaOption(enabled: Boolean) {
        launch { preferencesStore.setShowShareMediaOption(enabled) }
    }

    fun setShowExternalRatings(enabled: Boolean) {
        launch { preferencesStore.setShowExternalRatings(enabled) }
    }

    fun setMpvConfig(config: MpvEngineConfig) {
        launch { preferencesStore.setMpvConfig(config) }
    }

    fun setLibVlcConfig(config: LibVlcEngineConfig) {
        launch { preferencesStore.setLibVlcConfig(config) }
    }

    fun setExoPlayerConfig(config: ExoPlayerEngineConfig) {
        launch { preferencesStore.setExoPlayerConfig(config) }
    }

    fun setDataSaverEnabled(enabled: Boolean) {
        launch { preferencesStore.setDataSaverEnabled(enabled) }
    }

    fun setReduceMotionEnabled(enabled: Boolean) {
        launch { preferencesStore.setReduceMotionEnabled(enabled) }
    }

    fun setPreferAudioDescription(enabled: Boolean) {
        launch { preferencesStore.setPreferAudioDescription(enabled) }
    }

    fun setHighContrastSubtitles(enabled: Boolean) {
        launch { preferencesStore.setHighContrastSubtitles(enabled) }
    }

    fun setHideSearchHistory(enabled: Boolean) {
        launch { preferencesStore.setHideSearchHistory(enabled) }
    }

    fun setBlueLightFilterEnabled(enabled: Boolean) {
        launch { preferencesStore.setBlueLightFilterEnabled(enabled) }
    }

    fun setBlueLightFilterStrength(strength: Float) {
        launch { preferencesStore.setBlueLightFilterStrength(strength) }
    }

    fun setTvZoomModePercent(percent: Float) {
        launch { preferencesStore.setTvZoomModePercent(percent) }
    }

    fun setRemoteControlEnabled(enabled: Boolean) {
        launch { preferencesStore.setRemoteControlEnabled(enabled) }
    }

    fun setMaxDownloadStorageGb(gb: Int) {
        launch { preferencesStore.setMaxDownloadStorageGb(gb) }
    }

    fun setDownloadStorageLocation(location: String) {
        launch { preferencesStore.setDownloadStorageLocation(location) }
    }

    fun setKidsModeEnabled(enabled: Boolean) {
        launch { preferencesStore.setKidsModeEnabled(enabled) }
    }

    fun setKidsModeMaxRating(rating: String) {
        launch { preferencesStore.setKidsModeMaxRating(rating) }
    }

    fun setSynthwaveMode(enabled: Boolean) {
        launch { preferencesStore.setSynthwaveMode(enabled) }
    }

    fun setSynthwaveAccent(accent: String) {
        launch { preferencesStore.setSynthwaveAccent(accent) }
    }

    fun setSoothingMode(enabled: Boolean) {
        launch { preferencesStore.setSoothingMode(enabled) }
    }

    fun setSoothingAccent(accent: String) {
        launch { preferencesStore.setSoothingAccent(accent) }
    }

    fun setMonochromeMode(enabled: Boolean) {
        launch { preferencesStore.setMonochromeMode(enabled) }
    }

    fun updateNotificationPreferences(transform: (NotificationPreferences) -> NotificationPreferences) {
        launch {
            preferencesStore.updateNotificationPreferences(transform)
            notificationScheduler.scheduleOrUpdate()
        }
    }

    var backupRestoreStatus by composeState<String?>(null)
        private set

    fun exportSettings(uri: Uri) {
        launch {
            backupRestoreStatus = null
            runCatching {
                val prefs = preferences
                val json = Json { prettyPrint = true; encodeDefaults = true }
                val jsonString = json.encodeToString(UserPreferences.serializer(), prefs)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.writer().use { it.write(jsonString) }
                    } ?: throw IOException("Cannot open output stream")
                }
                backupRestoreStatus = "Settings exported successfully"
            }.onFailure {
                backupRestoreStatus = "Export failed: ${it.message}"
            }
        }
    }

    fun importSettings(uri: Uri) {
        launch {
            backupRestoreStatus = null
            runCatching {
                val jsonString = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.reader().use { it.readText() }
                    } ?: throw IOException("Cannot open input stream")
                }
                val json = Json { ignoreUnknownKeys = true }
                val imported = json.decodeFromString(UserPreferences.serializer(), jsonString)
                preferencesStore.restorePreferences(imported)
                backupRestoreStatus = "Settings imported successfully"
            }.onFailure {
                backupRestoreStatus = "Import failed: ${it.message}"
            }
        }
    }

    fun clearBackupRestoreStatus() {
        backupRestoreStatus = null
    }
}
