package com.raulshma.jellyplay.feature.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
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
                kotlinx.coroutines.delay(10_000)
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

    fun setDynamicTheming(enabled: Boolean) {
        launch { preferencesStore.setDynamicTheming(enabled) }
    }

    fun setAccentColorSwatch(swatch: String) {
        launch { preferencesStore.setAccentColorSwatch(swatch) }
    }

    fun setColorStyle(style: ColorStyle) {
        launch { preferencesStore.setColorStyle(style) }
    }

    fun setThemeMode(mode: ThemeMode) {
        launch { preferencesStore.setThemeMode(mode) }
    }

    fun setContrastLevel(level: ContrastLevel) {
        launch { preferencesStore.setContrastLevel(level) }
    }

    fun setOledMode(enabled: Boolean) {
        launch { preferencesStore.setOledMode(enabled) }
    }

    fun setPreferredPlayer(playerType: PlayerType) {
        launch { preferencesStore.setPreferredPlayer(playerType) }
    }

    fun setTrailerAutoplay(enabled: Boolean) {
        launch { preferencesStore.setTrailerAutoplay(enabled) }
    }

    fun setPreferredAudioLanguage(language: String?) {
        launch { preferencesStore.setPreferredAudioLanguage(language) }
    }

    fun setPreferredSubtitleLanguage(language: String?) {
        launch { preferencesStore.setPreferredSubtitleLanguage(language) }
    }

    fun setStreamingQuality(quality: StreamingQuality) {
        launch { preferencesStore.setStreamingQuality(quality) }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        launch { preferencesStore.setWifiOnlyDownloads(enabled) }
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

    fun setPinLockEnabled(enabled: Boolean) {
        launch { preferencesStore.setPinLockEnabled(enabled) }
    }

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

    fun setBiometricLockEnabled(enabled: Boolean) {
        launch { preferencesStore.setBiometricLockEnabled(enabled) }
    }

    fun setShowAdvancedSettings(enabled: Boolean) {
        launch { preferencesStore.setShowAdvancedSettings(enabled) }
    }

    fun setAutoLockTimerMs(ms: Long) {
        launch { preferencesStore.setAutoLockTimerMs(ms) }
    }

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

    fun setVideoSeekDurationMs(ms: Long) {
        launch { preferencesStore.setVideoSeekDurationMs(ms) }
    }

    fun setVideoDefaultOrientation(mode: OrientationMode) {
        launch { preferencesStore.setVideoDefaultOrientation(mode) }
    }

    fun setVideoControlsTimeoutMs(ms: Long) {
        launch { preferencesStore.setVideoControlsTimeoutMs(ms) }
    }

    fun setVideoGesturesEnabled(enabled: Boolean) {
        launch { preferencesStore.setVideoGesturesEnabled(enabled) }
    }

    fun setVideoDefaultSpeed(speed: Float) {
        launch { preferencesStore.setVideoDefaultSpeed(speed) }
    }

    fun setVideoDefaultAspectRatio(ratio: String) {
        launch { preferencesStore.setVideoDefaultAspectRatio(ratio) }
    }

    fun setVideoAutoplayNext(enabled: Boolean) {
        launch { preferencesStore.setVideoAutoplayNext(enabled) }
    }

    fun setVideoSwipeSeekMaxMs(ms: Long) {
        launch { preferencesStore.setVideoSwipeSeekMaxMs(ms) }
    }

    fun setVideoRememberBrightness(enabled: Boolean) {
        launch { preferencesStore.setVideoRememberBrightness(enabled) }
    }

    fun setAudioDefaultSpeed(speed: Float) {
        launch { preferencesStore.setAudioDefaultSpeed(speed) }
    }

    fun setAudioNightModeVolume(volume: Float) {
        launch { preferencesStore.setAudioNightModeVolume(volume) }
    }

    fun setAudioNightModeGain(gain: Int) {
        launch { preferencesStore.setAudioNightModeGain(gain) }
    }

    fun setAudioSkipPreviousThresholdMs(ms: Long) {
        launch { preferencesStore.setAudioSkipPreviousThresholdMs(ms) }
    }

    fun setAudioAutoplayNext(enabled: Boolean) {
        launch { preferencesStore.setAudioAutoplayNext(enabled) }
    }

    fun setAudioDelayMs(ms: Long) {
        launch { preferencesStore.setAudioDelay(ms) }
    }

    fun setHomeMode(mode: HomeMode) {
        launch { preferencesStore.setHomeMode(mode) }
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        launch { preferencesStore.setSubtitleStyle(style) }
    }

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

    fun setGaplessEnabled(enabled: Boolean) {
        launch { preferencesStore.setGaplessEnabled(enabled) }
    }

    fun setCrossfadeDurationMs(ms: Long) {
        launch { preferencesStore.setCrossfadeDurationMs(ms) }
    }

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

    fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) {
        launch { preferencesStore.setEnabledHomeSectionTypes(types) }
    }

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

    fun setNavBarShowLabels(show: Boolean) {
        launch { preferencesStore.setNavBarShowLabels(show) }
    }

    fun setHomeHeroEnabled(enabled: Boolean) {
        launch { preferencesStore.setHomeHeroEnabled(enabled) }
    }

    fun setPerformanceMode(enabled: Boolean) {
        launch { preferencesStore.setPerformanceMode(enabled) }
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
