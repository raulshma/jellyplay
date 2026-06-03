package com.raulshma.jellyplay.feature.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesStore: UserPreferencesStore,
    private val authRepository: AuthRepository,
    private val downloadRepository: DownloadRepository,
    private val mediaRepository: MediaRepository,
    private val apiClient: com.raulshma.jellyplay.core.network.JellyfinApiClient,
) : ViewModel() {

    var preferences by mutableStateOf(UserPreferences())
        private set

    var currentUserName by mutableStateOf("")
        private set

    var cacheSizeMb by mutableStateOf(0L)
        private set

    val appVersion: String by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    }

    var currentUser by mutableStateOf<UserInfo?>(null)
        private set

    var currentServerUsers by mutableStateOf<List<UserInfo>>(emptyList())
        private set

    var isLoadingUsers by mutableStateOf(false)
        private set

    var libraryFolders by mutableStateOf<List<LibraryFolder>>(emptyList())
        private set

    var isLoadingLibraries by mutableStateOf(false)
        private set

    val currentServerAddress = authRepository.currentServer
        .map { it?.address ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    var activeSessions by mutableStateOf<List<com.raulshma.jellyplay.core.model.SessionInfo>>(emptyList())
        private set

    var isLoadingSessions by mutableStateOf(false)
        private set

    var messageSentEvent by mutableStateOf<String?>(null)
        private set

    private var sessionRefreshJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            preferencesStore.preferences.collect { prefs ->
                preferences = prefs
            }
        }
        viewModelScope.launch {
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
        viewModelScope.launch {
            authRepository.currentServerUsers.collect { users ->
                currentServerUsers = users
                isLoadingUsers = false
            }
        }
        calculateCacheSize()
        loadLibraryFolders()
    }

    private fun loadLibraryFolders() {
        viewModelScope.launch {
            isLoadingLibraries = true
            mediaRepository.getLibraryFolders()
                .onSuccess { folders ->
                    libraryFolders = folders.filter { it.collectionType != "music" }
                }
            isLoadingLibraries = false
        }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            isLoadingSessions = true
            apiClient.getSessions()
                .onSuccess { sessions ->
                    val cutoff = java.time.Instant.now().minusSeconds(5 * 60)
                    activeSessions = sessions.filter { 
                        val lastActivity = try { java.time.Instant.parse(it.lastActivityDate) } catch (e: Exception) { java.time.Instant.MIN }
                        it.isActive && it.client.isNotBlank() && it.deviceName.isNotBlank() && it.client != "Jellyfin Server" &&
                        (it.nowPlayingItem != null || lastActivity.isAfter(cutoff))
                    }
                }
            isLoadingSessions = false
        }
    }

    private fun startSessionAutoRefresh() {
        sessionRefreshJob?.cancel()
        sessionRefreshJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)
                apiClient.getSessions()
                    .onSuccess { sessions ->
                        val cutoff = java.time.Instant.now().minusSeconds(5 * 60)
                        activeSessions = sessions.filter { 
                            val lastActivity = try { java.time.Instant.parse(it.lastActivityDate) } catch (e: Exception) { java.time.Instant.MIN }
                            it.isActive && it.client.isNotBlank() && it.deviceName.isNotBlank() && it.client != "Jellyfin Server" &&
                            (it.nowPlayingItem != null || lastActivity.isAfter(cutoff))
                        }
                    }
            }
        }
    }

    fun sendMessageToSession(sessionId: String, header: String, text: String) {
        viewModelScope.launch {
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
        viewModelScope.launch { preferencesStore.setDynamicTheming(enabled) }
    }

    fun setAccentColorSwatch(swatch: String) {
        viewModelScope.launch { preferencesStore.setAccentColorSwatch(swatch) }
    }

    fun setColorStyle(style: ColorStyle) {
        viewModelScope.launch { preferencesStore.setColorStyle(style) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesStore.setThemeMode(mode) }
    }

    fun setContrastLevel(level: ContrastLevel) {
        viewModelScope.launch { preferencesStore.setContrastLevel(level) }
    }

    fun setOledMode(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setOledMode(enabled) }
    }

    fun setPreferredPlayer(playerType: PlayerType) {
        viewModelScope.launch { preferencesStore.setPreferredPlayer(playerType) }
    }

    fun setPreferredAudioLanguage(language: String?) {
        viewModelScope.launch { preferencesStore.setPreferredAudioLanguage(language) }
    }

    fun setPreferredSubtitleLanguage(language: String?) {
        viewModelScope.launch { preferencesStore.setPreferredSubtitleLanguage(language) }
    }

    fun setStreamingQuality(quality: StreamingQuality) {
        viewModelScope.launch { preferencesStore.setStreamingQuality(quality) }
    }

    fun setMaxCacheSize(sizeMb: Int) {
        viewModelScope.launch { preferencesStore.setMaxCacheSize(sizeMb) }
    }

    fun setAutoDeleteCache(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setAutoDeleteCache(enabled) }
    }

    fun clearCache() {
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch { authRepository.logout() }
    }

    fun setPinLockEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setPinLockEnabled(enabled) }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            val hash = preferencesStore.hashPin(pin)
            preferencesStore.setPinHash(hash)
            preferencesStore.setPinLockEnabled(true)
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            preferencesStore.setPinLockEnabled(false)
            preferencesStore.setPinHash(null)
        }
    }

    fun verifyPin(pin: String): Boolean {
        return preferencesStore.verifyPin(pin, preferences.pinHash)
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setBiometricLockEnabled(enabled) }
    }

    fun setAutoLockTimerMs(ms: Long) {
        viewModelScope.launch { preferencesStore.setAutoLockTimerMs(ms) }
    }

    fun setDialogueBoostEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setDialogueBoostEnabled(enabled) }
    }

    fun setDialogueBoostStrength(strength: EffectStrength) {
        viewModelScope.launch { preferencesStore.setDialogueBoostStrength(strength) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setEqualizerEnabled(enabled) }
    }

    fun setNightModeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setNightModeEnabled(enabled) }
    }

    fun setNightModeStrength(strength: EffectStrength) {
        viewModelScope.launch { preferencesStore.setNightModeStrength(strength) }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setBassBoostEnabled(enabled) }
    }

    fun setBassBoostStrength(strength: EffectStrength) {
        viewModelScope.launch { preferencesStore.setBassBoostStrength(strength) }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setVirtualizerEnabled(enabled) }
    }

    fun setVirtualizerStrength(strength: Int) {
        viewModelScope.launch { preferencesStore.setVirtualizerStrength(strength) }
    }

    fun setReverbPreset(preset: com.raulshma.jellyplay.core.model.ReverbPreset) {
        viewModelScope.launch { preferencesStore.setReverbPreset(preset) }
    }

    fun setAutoEqByGenre(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setAutoEqByGenre(enabled) }
    }

    fun setDecoderMode(mode: DecoderMode) {
        viewModelScope.launch { preferencesStore.setDecoderMode(mode) }
    }

    fun setAudioPassthrough(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setAudioPassthrough(enabled) }
    }

    fun setFrameRateMatching(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setFrameRateMatching(enabled) }
    }

    fun switchUser(userId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.switchUser(userId)
            onComplete()
        }
    }

    fun removeUser(userId: String) {
        viewModelScope.launch {
            authRepository.removeUser(userId)
        }
    }

    fun setVideoSeekDurationMs(ms: Long) {
        viewModelScope.launch { preferencesStore.setVideoSeekDurationMs(ms) }
    }

    fun setVideoDefaultOrientation(mode: OrientationMode) {
        viewModelScope.launch { preferencesStore.setVideoDefaultOrientation(mode) }
    }

    fun setVideoControlsTimeoutMs(ms: Long) {
        viewModelScope.launch { preferencesStore.setVideoControlsTimeoutMs(ms) }
    }

    fun setVideoGesturesEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setVideoGesturesEnabled(enabled) }
    }

    fun setVideoDefaultSpeed(speed: Float) {
        viewModelScope.launch { preferencesStore.setVideoDefaultSpeed(speed) }
    }

    fun setVideoDefaultAspectRatio(ratio: String) {
        viewModelScope.launch { preferencesStore.setVideoDefaultAspectRatio(ratio) }
    }

    fun setVideoAutoplayNext(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setVideoAutoplayNext(enabled) }
    }

    fun setVideoSwipeSeekMaxMs(ms: Long) {
        viewModelScope.launch { preferencesStore.setVideoSwipeSeekMaxMs(ms) }
    }

    fun setVideoRememberBrightness(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setVideoRememberBrightness(enabled) }
    }

    fun setAudioDefaultSpeed(speed: Float) {
        viewModelScope.launch { preferencesStore.setAudioDefaultSpeed(speed) }
    }

    fun setAudioNightModeVolume(volume: Float) {
        viewModelScope.launch { preferencesStore.setAudioNightModeVolume(volume) }
    }

    fun setAudioNightModeGain(gain: Int) {
        viewModelScope.launch { preferencesStore.setAudioNightModeGain(gain) }
    }

    fun setAudioSkipPreviousThresholdMs(ms: Long) {
        viewModelScope.launch { preferencesStore.setAudioSkipPreviousThresholdMs(ms) }
    }

    fun setAudioAutoplayNext(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setAudioAutoplayNext(enabled) }
    }

    fun setAudioDelayMs(ms: Long) {
        viewModelScope.launch { preferencesStore.setAudioDelay(ms) }
    }

    fun setHomeMode(mode: HomeMode) {
        viewModelScope.launch { preferencesStore.setHomeMode(mode) }
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        viewModelScope.launch { preferencesStore.setSubtitleStyle(style) }
    }

    fun setEqualizerSettings(settings: EqualizerSettings) {
        viewModelScope.launch { preferencesStore.setEqualizerSettings(settings) }
    }

    fun setTrickplayEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setTrickplayEnabled(enabled) }
    }

    fun setTrickplayOnSeekGesture(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setTrickplayOnSeekGesture(enabled) }
    }

    fun setVideoPreloadBufferSize(size: com.raulshma.jellyplay.core.model.PreloadBufferSize) {
        viewModelScope.launch { preferencesStore.setVideoPreloadBufferSize(size) }
    }

    fun setAudioPreloadBufferSize(size: com.raulshma.jellyplay.core.model.PreloadBufferSize) {
        viewModelScope.launch { preferencesStore.setAudioPreloadBufferSize(size) }
    }

    fun setSegmentBehavior(
        type: com.raulshma.jellyplay.core.model.MediaSegmentType,
        behavior: com.raulshma.jellyplay.core.model.SegmentBehavior,
    ) {
        viewModelScope.launch { preferencesStore.setSegmentBehavior(type, behavior) }
    }

    fun setVideoEpisodeBrowserEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setVideoEpisodeBrowserEnabled(enabled) }
    }

    fun setGaplessEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setGaplessEnabled(enabled) }
    }

    fun setCrossfadeDurationMs(ms: Long) {
        viewModelScope.launch { preferencesStore.setCrossfadeDurationMs(ms) }
    }

    fun setAudioNormalizationMode(mode: AudioNormalizationMode) {
        viewModelScope.launch { preferencesStore.setAudioNormalizationMode(mode) }
    }

    fun setReplayGainPreAmpDb(db: Float) {
        viewModelScope.launch { preferencesStore.setReplayGainPreAmpDb(db) }
    }

    fun setDreamImageCategories(categories: Set<DreamImageCategory>) {
        viewModelScope.launch { preferencesStore.setDreamImageCategories(categories) }
    }

    fun setDreamSlideshowIntervalMs(ms: Long) {
        viewModelScope.launch { preferencesStore.setDreamSlideshowIntervalMs(ms) }
    }

    fun setDreamKenBurnsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setDreamKenBurnsEnabled(enabled) }
    }

    fun setDreamTransitionStyle(style: DreamTransitionStyle) {
        viewModelScope.launch { preferencesStore.setDreamTransitionStyle(style) }
    }

    fun setDreamShowTitle(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setDreamShowTitle(enabled) }
    }

    fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) {
        viewModelScope.launch { preferencesStore.setEnabledHomeSectionTypes(types) }
    }

    fun setHomeSectionOrder(order: List<HomeSectionType>) {
        viewModelScope.launch { preferencesStore.setHomeSectionOrder(order) }
    }

    fun setHiddenLibrarySectionIds(ids: Set<String>) {
        viewModelScope.launch { preferencesStore.setHiddenLibrarySectionIds(ids) }
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
        viewModelScope.launch { preferencesStore.setNavBarShowLabels(show) }
    }

    fun setHomeHeroEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setHomeHeroEnabled(enabled) }
    }

    fun setPerformanceMode(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setPerformanceMode(enabled) }
    }

    fun setMpvConfig(config: MpvEngineConfig) {
        viewModelScope.launch { preferencesStore.setMpvConfig(config) }
    }

    fun setLibVlcConfig(config: LibVlcEngineConfig) {
        viewModelScope.launch { preferencesStore.setLibVlcConfig(config) }
    }

    fun setExoPlayerConfig(config: ExoPlayerEngineConfig) {
        viewModelScope.launch { preferencesStore.setExoPlayerConfig(config) }
    }
}
