package com.raulshma.jellyplay.feature.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.ColorBlindMode
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.DownloadScheduleWindow
import com.raulshma.jellyplay.core.model.HandMode
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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject

@Immutable
data class StorageBreakdown(
    val cacheMb: Long = 0,
    val downloadsMb: Long = 0,
    val imagesMb: Long = 0,
    val totalMb: Long = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesStore: UserPreferencesStore,
    private val authRepository: AuthRepository,
    private val downloadRepository: DownloadRepository,
    private val mediaRepository: MediaRepository,
    private val apiClient: com.raulshma.jellyplay.core.network.JellyfinApiClient,
    private val notificationScheduler: NotificationScheduler,
    private val autoDownloadScheduler: com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler,
    private val tvWatchNextScheduler: com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler,
    private val audioStreamCache: com.raulshma.jellyplay.core.data.playback.AudioStreamCache,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    var preferences by composeState(UserPreferences())
        private set

    var currentUserName by composeState("")
        private set

    var cacheSizeMb by composeState(0L)
        private set

    var storageBreakdown by composeState(StorageBreakdown())
        private set

    var cacheError by composeState<String?>(null)
        private set

    var libraryError by composeState<String?>(null)
        private set

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
                } else {
                    stopSessionAutoRefresh()
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
            libraryError = null
            mediaRepository.getLibraryFolders()
                .onSuccess { folders ->
                    libraryFolders = folders.filter { it.collectionType != "music" }
                }
                .onFailure { error -> libraryError = error.message ?: error::class.simpleName }
            isLoadingLibraries = false
        }
    }

    private fun loadSessions() {
        launch {
            isLoadingSessions = true
            apiClient.getSessions()
                .onSuccess { sessions -> activeSessions = sessions.filterActiveSessions() }
            isLoadingSessions = false
        }
    }

    /**
     * Starts polling `/Sessions` every 30s for admin users. Should be tied to
     * screen visibility (STARTED) by the caller via [stopSessionAutoRefresh] on
     * exit so polling does not run while settings is in the back stack.
     */
    fun startSessionAutoRefresh() {
        sessionRefreshJob?.cancel()
        sessionRefreshJob = launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                apiClient.getSessions()
                    .onSuccess { sessions -> activeSessions = sessions.filterActiveSessions() }
            }
        }
    }

    /** Stops the session auto-refresh loop started by [startSessionAutoRefresh]. */
    fun stopSessionAutoRefresh() {
        sessionRefreshJob?.cancel()
        sessionRefreshJob = null
    }

    /**
     * Keeps only active, non-server Jellyfin sessions: drops the headless
     * "Jellyfin Server" entry and any session inactive for more than 5 minutes
     * (unless it is currently playing). An unparseable `lastActivityDate`
     * resolves to [java.time.Instant.MIN], which predates the cutoff and so
     * excludes the session — deliberate, since a session with no resolvable
     * activity timestamp should not appear as live.
     */
    private fun List<com.raulshma.jellyplay.core.model.SessionInfo>.filterActiveSessions(): List<com.raulshma.jellyplay.core.model.SessionInfo> {
        val cutoff = java.time.Instant.now().minusSeconds(5 * 60)
        return filter {
            val lastActivity = try { java.time.Instant.parse(it.lastActivityDate) } catch (_: Exception) { java.time.Instant.MIN }
            it.isActive && it.client.isNotBlank() && it.deviceName.isNotBlank() && it.client != "Jellyfin Server" &&
                (it.nowPlayingItem != null || lastActivity.isAfter(cutoff))
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
        editor.edit { setTrailerAutoplay(enabled) }
    }

    fun setCinemaModeEnabled(enabled: Boolean) {
        editor.edit { setCinemaModeEnabled(enabled) }
    }

    fun setPreferredAudioLanguage(language: String?) {
        editor.edit { setPreferredAudioLanguage(language) }
    }

    fun setPreferredSubtitleLanguage(language: String?) = editor.setPreferredSubtitleLanguage(language)

    fun setStreamingQuality(quality: StreamingQuality) = editor.setStreamingQuality(quality)

    fun setWifiOnlyDownloads(enabled: Boolean) {
        editor.edit { setWifiOnlyDownloads(enabled) }
    }

    fun setDownloadConnections(count: Int) {
        editor.edit { setDownloadConnections(count) }
    }

    fun setMaxConcurrentDownloads(count: Int) {
        editor.edit { setMaxConcurrentDownloads(count) }
    }

    fun setMaxCacheSize(sizeMb: Int) {
        editor.edit { setMaxCacheSize(sizeMb) }
    }

    fun setAutoDeleteCache(enabled: Boolean) {
        editor.edit { setAutoDeleteCache(enabled) }
    }

    fun clearCache() {
        launch {
            cacheError = null
            try {
                context.cacheDir.deleteRecursively()
                val externalCache = context.externalCacheDir
                if (externalCache != null && externalCache.exists()) {
                    externalCache.deleteRecursively()
                }
            } catch (error: Exception) {
                cacheError = error.message ?: error::class.simpleName
            } finally {
                calculateCacheSize()
            }
        }
    }

    private fun calculateCacheSize() {
        launch {
            // Five independent recursive FS walks — collapse into a single IO
            // context-switch and run the walks concurrently rather than one
            // after another. Each walk can take seconds on large directories.
            val (cacheSize, externalCacheSize, downloadsSize, imagesSize) = withContext(Dispatchers.IO) {
                val cacheAsync = async { getDirSize(context.cacheDir) }
                val extAsync = async { context.externalCacheDir?.let { getDirSize(it) } ?: 0L }
                val dlAsync = async {
                    val prefs = preferencesStore.preferences.value
                    val location = prefs.downloadStorageLocation
                    val downloadsDir = if (location == "EXTERNAL" && context.getExternalFilesDir(null) != null) {
                        context.getExternalFilesDir(null)!!
                    } else {
                        context.filesDir
                    }
                    getDirSize(downloadsDir)
                }
                val imgAsync = async {
                    val imageDir = File(context.cacheDir, "image_cache")
                    if (imageDir.exists()) getDirSize(imageDir) else 0L
                }
                QuadLongs(cacheAsync.await(), extAsync.await(), dlAsync.await(), imgAsync.await())
            }

            cacheSizeMb = (cacheSize + externalCacheSize) / (1024 * 1024)
            val downloadsMb = downloadsSize / (1024 * 1024)
            val imagesMb = imagesSize / (1024 * 1024)
            val total = cacheSizeMb + downloadsMb + imagesMb
            storageBreakdown = StorageBreakdown(
                cacheMb = cacheSizeMb,
                downloadsMb = downloadsMb,
                imagesMb = imagesMb,
                totalMb = total,
            )
        }
    }

    /** 4-tuple of `Long` for destructuring the four parallel FS-walk results. */
    private class QuadLongs(
        val first: Long,
        val second: Long,
        val third: Long,
        val fourth: Long,
    ) {
        operator fun component1(): Long = first
        operator fun component2(): Long = second
        operator fun component3(): Long = third
        operator fun component4(): Long = fourth
    }

    private fun getDirSize(dir: File): Long {
        var size = 0L
        // (file, depth, isRoot) — the root is never skipped on symlink, so a
        // symlinked cache/downloads location still reports its size.
        val stack = ArrayDeque<Triple<File, Int, Boolean>>()
        stack.addLast(Triple(dir, 0, true))
        val maxDepth = 10
        while (stack.isNotEmpty()) {
            val (current, depth, isRoot) = stack.removeLast()
            if (!isRoot && java.nio.file.Files.isSymbolicLink(current.toPath())) continue
            if (current.isDirectory) {
                if (depth >= maxDepth) continue
                current.listFiles()?.forEach { file -> stack.addLast(Triple(file, depth + 1, false)) }
            } else if (current.isFile) {
                size += current.length()
            }
        }
        return size
    }



    fun setPinLockEnabled(enabled: Boolean) = editor.setPinLockEnabled(enabled)

    fun setPin(pin: String) {
        editor.edit { setPin(pin) }
    }

    fun clearPin() {
        editor.edit { clearPin() }
    }

    suspend fun verifyPin(pin: String): Boolean = preferencesStore.verifyPinOffMainThread(pin)

    fun setBiometricLockEnabled(enabled: Boolean) = editor.setBiometricLockEnabled(enabled)

    fun setUsePinForPlayerLock(enabled: Boolean) = editor.setUsePinForPlayerLock(enabled)

    fun setShowAdvancedSettings(enabled: Boolean) {
        editor.edit { setShowAdvancedSettings(enabled) }
    }

    fun setExperimentalFeatureEnabled(feature: ExperimentalFeature, enabled: Boolean) {
        val current = preferences.enabledExperimentalFeatures
        val updated = if (enabled) current + feature else current - feature
        editor.edit { setEnabledExperimentalFeatures(updated) }
    }

    fun setAutoLockTimerMs(ms: Long) = editor.setAutoLockTimerMs(ms)

    fun setDialogueBoostEnabled(enabled: Boolean) {
        editor.edit { setDialogueBoostEnabled(enabled) }
    }

    fun setDialogueBoostStrength(strength: EffectStrength) {
        editor.edit { setDialogueBoostStrength(strength) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        editor.edit { setEqualizerEnabled(enabled) }
    }

    fun setNightModeEnabled(enabled: Boolean) {
        editor.edit { setNightModeEnabled(enabled) }
    }

    fun setNightModeStrength(strength: EffectStrength) {
        editor.edit { setNightModeStrength(strength) }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        editor.edit { setBassBoostEnabled(enabled) }
    }

    fun setBassBoostStrength(strength: EffectStrength) {
        editor.edit { setBassBoostStrength(strength) }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        editor.edit { setVirtualizerEnabled(enabled) }
    }

    fun setVirtualizerStrength(strength: Int) {
        editor.edit { setVirtualizerStrength(strength) }
    }

    fun setReverbPreset(preset: com.raulshma.jellyplay.core.model.ReverbPreset) {
        editor.edit { setReverbPreset(preset) }
    }

    fun setAutoEqByGenre(enabled: Boolean) {
        editor.edit { setAutoEqByGenre(enabled) }
    }

    fun setDecoderMode(mode: DecoderMode) {
        editor.edit { setDecoderMode(mode) }
    }

    fun setAudioPassthrough(enabled: Boolean) {
        editor.edit { setAudioPassthrough(enabled) }
    }

    fun setFrameRateMatching(enabled: Boolean) {
        editor.edit { setFrameRateMatching(enabled) }
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
        editor.edit { setVideoControlsTimeoutMs(ms) }
    }

    fun setVideoGesturesEnabled(enabled: Boolean) = editor.setVideoGesturesEnabled(enabled)

    fun setVideoSkipBackOnResumeMs(ms: Long) {
        editor.edit { setVideoSkipBackOnResumeMs(ms) }
    }

    fun setVideoPassOutProtectionHours(hours: Int) {
        editor.edit { setVideoPassOutProtectionHours(hours) }
    }

    fun setVideoDefaultSpeed(speed: Float) {
        editor.edit { setVideoDefaultSpeed(speed) }
    }

    fun setVideoHoldSpeedEnabled(enabled: Boolean) {
        editor.edit { setVideoHoldSpeedEnabled(enabled) }
    }

    fun setVideoHoldSpeedMultiplier(multiplier: Float) {
        editor.edit { setVideoHoldSpeedMultiplier(multiplier) }
    }

    fun setVideoDefaultAspectRatio(ratio: String) {
        editor.edit { setVideoDefaultAspectRatio(ratio) }
    }

    fun setVideoAutoplayNext(enabled: Boolean) = editor.setVideoAutoplayNext(enabled)

    fun setVideoSwipeSeekMaxMs(ms: Long) {
        editor.edit { setVideoSwipeSeekMaxMs(ms) }
    }

    fun setVideoRememberBrightness(enabled: Boolean) {
        editor.edit { setVideoRememberBrightness(enabled) }
    }

    fun setVideoBrightnessLevel(level: Float) {
        editor.edit { setVideoBrightnessLevel(level) }
    }

    fun setAudioDefaultSpeed(speed: Float) = editor.setAudioDefaultSpeed(speed)

    fun setAudioNightModeVolume(volume: Float) {
        editor.edit { setAudioNightModeVolume(volume) }
    }

    fun setAudioNightModeGain(gain: Int) {
        editor.edit { setAudioNightModeGain(gain) }
    }

    fun setAudioSkipPreviousThresholdMs(ms: Long) {
        editor.edit { setAudioSkipPreviousThresholdMs(ms) }
    }

    fun setAudioAutoplayNext(enabled: Boolean) = editor.setAudioAutoplayNext(enabled)

    fun setAudioDelayMs(ms: Long) {
        editor.edit { setAudioDelay(ms) }
    }

    fun setHomeMode(mode: HomeMode) = editor.setHomeMode(mode)

    fun setSubtitleStyle(style: SubtitleStyle) = editor.setSubtitleStyle(style)

    fun setEqualizerSettings(settings: EqualizerSettings) {
        editor.edit { setEqualizerSettings(settings) }
    }

    fun setTrickplayEnabled(enabled: Boolean) {
        editor.edit { setTrickplayEnabled(enabled) }
    }

    fun setTrickplayOnSeekGesture(enabled: Boolean) {
        editor.edit { setTrickplayOnSeekGesture(enabled) }
    }

    fun setVideoPreloadBufferSize(size: com.raulshma.jellyplay.core.model.PreloadBufferSize) {
        editor.edit { setVideoPreloadBufferSize(size) }
    }

    fun setAudioPreloadBufferSize(size: com.raulshma.jellyplay.core.model.PreloadBufferSize) {
        editor.edit { setAudioPreloadBufferSize(size) }
    }

    fun setSegmentBehavior(
        type: com.raulshma.jellyplay.core.model.MediaSegmentType,
        behavior: com.raulshma.jellyplay.core.model.SegmentBehavior,
    ) {
        editor.edit { setSegmentBehavior(type, behavior) }
    }

    fun setVideoEpisodeBrowserEnabled(enabled: Boolean) {
        editor.edit { setVideoEpisodeBrowserEnabled(enabled) }
    }

    fun setVideoShowPlaybackMetadata(enabled: Boolean) {
        editor.edit { setVideoShowPlaybackMetadata(enabled) }
    }

    fun setGaplessEnabled(enabled: Boolean) = editor.setGaplessEnabled(enabled)

    fun setCrossfadeDurationMs(ms: Long) = editor.setCrossfadeDurationMs(ms)

    fun setAudioCachingEnabled(enabled: Boolean) {
        editor.edit { setAudioCachingEnabled(enabled) }
    }

    fun setAudioCacheSizeMb(sizeMb: Int) {
        editor.edit { setAudioCacheSizeMb(sizeMb) }
    }

    fun setAudioPrefetchLookahead(lookahead: Int) {
        editor.edit { setAudioPrefetchLookahead(lookahead) }
    }

    fun setAudioPrefetchBackfill(backfill: Int) {
        editor.edit { setAudioPrefetchBackfill(backfill) }
    }

    fun setAudioCacheNetworkPolicy(policy: com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy) {
        editor.edit { setAudioCacheNetworkPolicy(policy) }
    }

    fun setAudioCacheCellularMonthlyCapMb(capMb: Int) {
        editor.edit { setAudioCacheCellularMonthlyCapMb(capMb) }
    }

    fun clearAudioCache() {
        launch { audioStreamCache.clear() }
    }

    fun setAudioNormalizationMode(mode: AudioNormalizationMode) {
        editor.edit { setAudioNormalizationMode(mode) }
    }

    fun setReplayGainPreAmpDb(db: Float) {
        editor.edit { setReplayGainPreAmpDb(db) }
    }

    fun setDreamImageCategories(categories: Set<DreamImageCategory>) {
        editor.edit { setDreamImageCategories(categories) }
    }

    fun setDreamSlideshowIntervalMs(ms: Long) {
        editor.edit { setDreamSlideshowIntervalMs(ms) }
    }

    fun setDreamKenBurnsEnabled(enabled: Boolean) {
        editor.edit { setDreamKenBurnsEnabled(enabled) }
    }

    fun setDreamTransitionStyle(style: DreamTransitionStyle) {
        editor.edit { setDreamTransitionStyle(style) }
    }

    fun setDreamShowTitle(enabled: Boolean) {
        editor.edit { setDreamShowTitle(enabled) }
    }

    fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) = editor.setEnabledHomeSectionTypes(types)

    fun setHomeSectionOrder(order: List<HomeSectionType>) {
        editor.edit { setHomeSectionOrder(order) }
    }

    fun toggleHomeSectionType(type: HomeSectionType, enabled: Boolean) {
        val current = preferences.enabledHomeSectionTypes.toMutableSet()
        if (enabled) current.add(type) else current.remove(type)
        setEnabledHomeSectionTypes(current)
    }

    // Pinned home sections (collections / playlists / favorites / genres /
    // studios pinned to the home screen).

    /** A browseable, pinnable option surfaced in the "Add pinned section" picker. */
    data class PinnableOption(
        val sourceId: String,
        val title: String,
        val subtitle: String? = null,
    )

    var pinnedBrowseOptions by composeState<List<PinnableOption>>(emptyList())
        private set

    var pinnedBrowseLoading by composeState(false)
        private set

    var pinnedBrowseError by composeState<String?>(null)
        private set

    private var pinnedBrowseJob: Job? = null

    val pinnedHomeSections: List<com.raulshma.jellyplay.core.model.PinnedHomeSection>
        get() = preferences.pinnedHomeSections

    fun addPinnedHomeSection(section: com.raulshma.jellyplay.core.model.PinnedHomeSection) {
        editor.edit { addPinnedHomeSection(section) }
    }

    fun removePinnedHomeSection(sectionId: String) {
        editor.edit { removePinnedHomeSection(sectionId) }
    }

    fun setPinnedHomeSections(sections: List<com.raulshma.jellyplay.core.model.PinnedHomeSection>) {
        editor.edit { setPinnedHomeSections(sections) }
    }

    /** Moves a pinned section from [from] to [to], clamped to valid bounds. */
    fun movePinnedHomeSection(from: Int, to: Int) {
        val current = preferences.pinnedHomeSections.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        val moved = current.removeAt(from)
        current.add(to, moved)
        setPinnedHomeSections(current)
    }

    /**
     * Loads the browseable list of pinnable sources for the given [type]. For
     * FAVORITES the list is a single sentinel option (favorites is a server-side
     * filter, not a discrete item) so the picker can confirm in one tap.
     */
    fun loadPinnableOptions(type: com.raulshma.jellyplay.core.model.PinnedSectionType) {
        pinnedBrowseJob?.cancel()
        pinnedBrowseJob = launch {
            pinnedBrowseLoading = true
            pinnedBrowseError = null
            val result = runCatching {
                when (type) {
                    com.raulshma.jellyplay.core.model.PinnedSectionType.COLLECTION ->
                        mediaRepository.getMediaItems(
                            mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.COLLECTION),
                            limit = 100,
                        ).getOrDefault(
                            com.raulshma.jellyplay.core.model.SearchResult(emptyList(), 0, 0)
                        ).items.map { PinnableOption(it.id, it.name) }

                    com.raulshma.jellyplay.core.model.PinnedSectionType.PLAYLIST ->
                        mediaRepository.getPlaylists(limit = 100).getOrDefault(emptyList())
                            .map { PinnableOption(it.id, it.name, "${it.itemCount} items") }

                    com.raulshma.jellyplay.core.model.PinnedSectionType.GENRE ->
                        mediaRepository.getGenres().getOrDefault(emptyList())
                            .map { PinnableOption(it.id, it.name) }

                    com.raulshma.jellyplay.core.model.PinnedSectionType.STUDIO ->
                        mediaRepository.getStudios().getOrDefault(emptyList())
                            .map { PinnableOption(it.id, it.name) }

                    com.raulshma.jellyplay.core.model.PinnedSectionType.FAVORITES ->
                        listOf(PinnableOption(
                            com.raulshma.jellyplay.core.model.PinnedHomeSection.FAVORITES_SOURCE_ID,
                            "Favorites",
                            "All your favorited items",
                        ))
                }
            }
            result.onSuccess {
                pinnedBrowseOptions = it
                pinnedBrowseLoading = false
            }.onFailure { throwable ->
                pinnedBrowseOptions = emptyList()
                pinnedBrowseError = throwable.message ?: throwable::class.simpleName
                pinnedBrowseLoading = false
            }
        }
    }

    fun clearPinnedBrowse() {
        pinnedBrowseJob?.cancel()
        pinnedBrowseOptions = emptyList()
        pinnedBrowseLoading = false
        pinnedBrowseError = null
    }

    // Home layout presets (save / load / import / export / reset).

    private val presetJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    private val importExportJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val homeLayoutPresets: List<com.raulshma.jellyplay.core.model.HomeLayoutPreset>
        get() = preferences.homeLayoutPresets

    var presetImportError by composeState<String?>(null)
        private set

    /** Snapshots the current home-screen layout into a named preset and saves it. */
    fun saveCurrentLayoutAsPreset(name: String, idOverride: String? = null) {
        val config = com.raulshma.jellyplay.core.model.HomeLayoutConfig(
            enabledHomeSectionTypes = preferences.enabledHomeSectionTypes,
            homeSectionOrder = preferences.homeSectionOrder,
            libraryHomeSectionOverrides = preferences.libraryHomeSectionOverrides,
            mergeContinueWatchingAndNextUp = preferences.mergeContinueWatchingAndNextUp,
            nextUpMaxDays = preferences.nextUpMaxDays,
            nextUpRewatching = preferences.nextUpRewatching,
            pinnedHomeSections = preferences.pinnedHomeSections,
            homeHeroEnabled = preferences.homeHeroEnabled,
            continueWatchingClickBehavior = preferences.continueWatchingClickBehavior,
        )
        val preset = com.raulshma.jellyplay.core.model.HomeLayoutPreset(
            id = idOverride ?: java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Preset" },
            config = config,
        )
        editor.edit { saveHomeLayoutPreset(preset) }
    }

    /** Applies a preset's layout to the current preferences. */
    fun applyPreset(config: com.raulshma.jellyplay.core.model.HomeLayoutConfig) {
        editor.edit {
            setEnabledHomeSectionTypes(config.enabledHomeSectionTypes)
            setHomeSectionOrder(config.homeSectionOrder)
            setLibraryHomeSectionOverrides(config.libraryHomeSectionOverrides)
            setMergeContinueWatchingAndNextUp(config.mergeContinueWatchingAndNextUp)
            setNextUpMaxDays(config.nextUpMaxDays)
            setNextUpRewatching(config.nextUpRewatching)
            setPinnedHomeSections(config.pinnedHomeSections)
            setHomeHeroEnabled(config.homeHeroEnabled)
            setContinueWatchingClickBehavior(config.continueWatchingClickBehavior)
        }
    }

    fun deleteHomeLayoutPreset(presetId: String) {
        editor.edit { deleteHomeLayoutPreset(presetId) }
    }

    /** Serializes a preset to a shareable pretty-printed JSON string. */
    fun exportPresetJson(preset: com.raulshma.jellyplay.core.model.HomeLayoutPreset): String =
        presetJson.encodeToString(com.raulshma.jellyplay.core.model.HomeLayoutPreset.serializer(), preset)

    /** Serializes the *current* layout (without saving) for quick sharing. */
    fun exportCurrentLayoutJson(): String {
        val config = com.raulshma.jellyplay.core.model.HomeLayoutConfig(
            enabledHomeSectionTypes = preferences.enabledHomeSectionTypes,
            homeSectionOrder = preferences.homeSectionOrder,
            libraryHomeSectionOverrides = preferences.libraryHomeSectionOverrides,
            mergeContinueWatchingAndNextUp = preferences.mergeContinueWatchingAndNextUp,
            nextUpMaxDays = preferences.nextUpMaxDays,
            nextUpRewatching = preferences.nextUpRewatching,
            pinnedHomeSections = preferences.pinnedHomeSections,
            homeHeroEnabled = preferences.homeHeroEnabled,
            continueWatchingClickBehavior = preferences.continueWatchingClickBehavior,
        )
        return presetJson.encodeToString(com.raulshma.jellyplay.core.model.HomeLayoutConfig.serializer(), config)
    }

    /**
     * Parses pasted/imported JSON. Accepts either a full [HomeLayoutPreset] or
     * a bare [HomeLayoutConfig]. Returns the parsed config (and optional name
     * when a full preset was supplied).
     */
    fun importPresetFromJson(
        raw: String,
        onResult: (Result<Pair<com.raulshma.jellyplay.core.model.HomeLayoutConfig, String?>>) -> Unit,
    ) {
        launch {
            val result = runCatching {
                val text = raw.trim()
                val parser = importExportJson
                if (text.contains("\"config\"")) {
                    val preset = parser.decodeFromString<com.raulshma.jellyplay.core.model.HomeLayoutPreset>(text)
                    preset.config to preset.name
                } else {
                    val config = parser.decodeFromString<com.raulshma.jellyplay.core.model.HomeLayoutConfig>(text)
                    config to null
                }
            }
            presetImportError = result.exceptionOrNull()?.message
            onResult(result)
        }
    }

    fun clearPresetImportError() {
        presetImportError = null
    }

    /** Resets the home layout to factory defaults. */
    fun resetHomeLayout() {
        applyPreset(com.raulshma.jellyplay.core.model.HomeLayoutConfig.DEFAULT)
    }

    /** Resets all preferences in a specific category to their default values. */
    fun resetCategory(category: PreferenceResetCategory) {
        editor.resetCategory(category)
    }

    /** Clears all preferences and resets to factory defaults. */
    fun clearAllPreferences() {
        editor.clearAllPreferences()
    }

    fun setNavBarShowLabels(show: Boolean) = editor.setNavBarShowLabels(show)

    fun setHideBottomNavOnScroll(hide: Boolean) {
        editor.edit { setHideBottomNavOnScroll(hide) }
    }

    fun setHomeHeroEnabled(enabled: Boolean) = editor.setHomeHeroEnabled(enabled)

    fun setPerformanceMode(enabled: Boolean) = editor.setPerformanceMode(enabled)

    fun setLibraryViewMode(mode: LibraryViewMode) {
        editor.edit { setLibraryViewMode(mode) }
    }

    fun setAudioVisualizerEnabled(enabled: Boolean) {
        editor.edit { setAudioVisualizerEnabled(enabled) }
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        editor.edit { setEqualizerPreset(preset) }
    }

    fun setChannelMixEnabled(enabled: Boolean) {
        editor.edit { setChannelMixEnabled(enabled) }
    }

    fun setChannelMixMode(mode: ChannelMixMode) {
        editor.edit { setChannelMixMode(mode) }
    }

    fun setSleepTimerDurationMs(ms: Long) {
        editor.edit { setSleepTimerDurationMs(ms) }
    }

    fun setLrBalance(balance: Float) {
        editor.edit { setLrBalance(balance) }
    }

    fun setSyncPlayJoinBehavior(behavior: SyncPlayJoinBehavior) {
        editor.edit { setSyncPlayJoinBehavior(behavior) }
    }

    fun setSyncPlayToleranceMs(ms: Long) {
        editor.edit { setSyncPlayToleranceMs(ms) }
    }

    fun setSyncPlayAutoAcceptInvites(enabled: Boolean) {
        editor.edit { setSyncPlayAutoAcceptInvites(enabled) }
    }

    fun setDefaultCastingStrategy(strategy: CastingStrategy) {
        editor.edit { setDefaultCastingStrategy(strategy) }
    }

    fun setBackgroundCastingEnabled(enabled: Boolean) {
        editor.edit { setBackgroundCastingEnabled(enabled) }
    }

    fun setPreferredRenderer(renderer: String?) {
        editor.edit { setPreferredRenderer(renderer) }
    }

    fun setDvrPrePaddingMinutes(minutes: Int) {
        editor.edit { setDvrPrePaddingMinutes(minutes) }
    }

    fun setDvrPostPaddingMinutes(minutes: Int) {
        editor.edit { setDvrPostPaddingMinutes(minutes) }
    }

    fun setDvrRecordingQuality(quality: String) {
        editor.edit { setDvrRecordingQuality(quality) }
    }

    fun setFavoriteChannels(channels: Set<String>) {
        editor.edit { setFavoriteChannels(channels) }
    }

    fun setEnabledNewsletterSections(sections: Set<NewsletterSectionType>) {
        editor.edit { setEnabledNewsletterSections(sections) }
    }

    fun setNewsletterSectionOrder(order: List<NewsletterSectionType>) {
        editor.edit { setNewsletterSectionOrder(order) }
    }

    fun setNewsletterEnabled(enabled: Boolean) {
        editor.edit { setNewsletterEnabled(enabled) }
    }

    fun setNewsletterDayOfWeek(day: Int) {
        editor.edit { setNewsletterDayOfWeek(day) }
    }

    fun setManualOffline(enabled: Boolean) {
        editor.edit { setManualOffline(enabled) }
    }

    fun setAutoOfflineEnabled(enabled: Boolean) {
        editor.edit { setAutoOfflineEnabled(enabled) }
    }

    fun setManualBandwidthCap(cap: Long) {
        editor.edit { setManualBandwidthCap(cap) }
    }

    fun setMeteredNetworkBehavior(behavior: MeteredNetworkBehavior) {
        editor.edit { setMeteredNetworkBehavior(behavior) }
    }

    fun setAdaptiveBitrateEnabled(enabled: Boolean) {
        editor.edit { setAdaptiveBitrateEnabled(enabled) }
    }

    fun setPitchSemitones(semitones: Float) {
        editor.edit { setPitchSemitones(semitones) }
    }


    fun setBackgroundVideoAudioEnabled(enabled: Boolean) {
        editor.edit { setBackgroundVideoAudioEnabled(enabled) }
    }

    fun setAutoPlayCountdownSec(sec: Int) {
        editor.edit { setAutoPlayCountdownSec(sec) }
    }

    fun setShowUnwatchedBadge(enabled: Boolean) {
        editor.edit { setShowUnwatchedBadge(enabled) }
    }

    fun setHideWatchedItems(enabled: Boolean) {
        editor.edit { setHideWatchedItems(enabled) }
    }

    fun setHideEpisodeThumbnails(enabled: Boolean) {
        editor.edit { setHideEpisodeThumbnails(enabled) }
    }

    fun setSkipSpecials(enabled: Boolean) {
        editor.edit { setSkipSpecials(enabled) }
    }

    fun setCellularDownloadSizeWarningMb(sizeMb: Int) {
        editor.edit { setCellularDownloadSizeWarningMb(sizeMb) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        editor.edit { setHapticsEnabled(enabled) }
    }

    fun setDateFormatPreference(preference: DateFormatPreference) {
        editor.edit { setDateFormatPreference(preference) }
    }

    fun setAppFontScale(scale: AppFontScale) {
        editor.edit { setAppFontScale(scale) }
    }

    fun setScheduledThemeStartHour(hour: Int) {
        editor.edit { setScheduledThemeStartHour(hour) }
    }

    fun setScheduledThemeEndHour(hour: Int) {
        editor.edit { setScheduledThemeEndHour(hour) }
    }

    fun setColorBlindMode(mode: ColorBlindMode) {
        editor.edit { setColorBlindMode(mode) }
    }

    fun setHandMode(mode: HandMode) {
        editor.edit { setHandMode(mode) }
    }

    fun setDownloadScheduleEnabled(enabled: Boolean) {
        editor.edit { setDownloadScheduleEnabled(enabled) }
    }

    fun setDownloadScheduleWindow(window: DownloadScheduleWindow) {
        editor.edit { setDownloadScheduleWindow(window) }
    }

    fun setCellularStreamingQuality(quality: StreamingQuality) {
        editor.edit { setCellularStreamingQuality(quality) }
    }

    fun setShowWatchedCheckmark(enabled: Boolean) {
        editor.edit { setShowWatchedCheckmark(enabled) }
    }

    fun setDefaultLibrarySortOrder(libraryId: String, order: String) {
        editor.edit { setDefaultLibrarySortOrder(libraryId, order) }
    }

    fun setKeepScreenOnDuringVideo(enabled: Boolean) {
        editor.edit { setKeepScreenOnDuringVideo(enabled) }
    }

    fun setDownloadQuality(quality: DownloadQuality) {
        editor.edit { setDownloadQuality(quality) }
    }

    fun setSmartDownloadsEnabled(enabled: Boolean) {
        editor.edit { setSmartDownloadsEnabled(enabled) }
    }

    fun setAutoDownloadNewEpisodes(enabled: Boolean) {
        editor.edit {
            setAutoDownloadNewEpisodes(enabled)
            autoDownloadScheduler.sync()
        }
    }

    fun setIncognitoModeEnabled(enabled: Boolean) {
        editor.edit { setIncognitoModeEnabled(enabled) }
    }

    fun setShowTimeRemaining(enabled: Boolean) {
        editor.edit { setShowTimeRemaining(enabled) }
    }

    fun setShowClockOnHome(enabled: Boolean) {
        editor.edit { setShowClockOnHome(enabled) }
    }

    fun setShowClockInPlayer(enabled: Boolean) {
        editor.edit { setShowClockInPlayer(enabled) }
    }

    fun setPauseOnAudioFocusLoss(enabled: Boolean) {
        editor.edit { setPauseOnAudioFocusLoss(enabled) }
    }

    fun setDuckOnTransientFocusLoss(enabled: Boolean) {
        editor.edit { setDuckOnTransientFocusLoss(enabled) }
    }

    fun setVolumeBoostEnabled(enabled: Boolean) {
        editor.edit { setVolumeBoostEnabled(enabled) }
    }

    fun setVolumeBoostGain(gain: Int) {
        editor.edit { setVolumeBoostGain(gain) }
    }

    fun setShowShareMediaOption(enabled: Boolean) {
        editor.edit { setShowShareMediaOption(enabled) }
    }

    fun setShowExternalRatings(enabled: Boolean) {
        editor.edit { setShowExternalRatings(enabled) }
    }

    fun setMpvConfig(config: MpvEngineConfig) {
        editor.edit { setMpvConfig(config) }
    }

    fun setLibVlcConfig(config: LibVlcEngineConfig) {
        editor.edit { setLibVlcConfig(config) }
    }

    fun setExoPlayerConfig(config: ExoPlayerEngineConfig) {
        editor.edit { setExoPlayerConfig(config) }
    }

    fun setDataSaverEnabled(enabled: Boolean) {
        editor.edit { setDataSaverEnabled(enabled) }
    }

    fun setVerboseNetworkLogging(enabled: Boolean) {
        editor.edit { setVerboseNetworkLogging(enabled) }
    }

    fun setNetworkTimeoutPreset(preset: com.raulshma.jellyplay.core.model.NetworkTimeoutPreset) {
        editor.edit { setNetworkTimeoutPreset(preset) }
    }

    fun setContinueWatchingClickBehavior(behavior: com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior) {
        editor.edit { setContinueWatchingClickBehavior(behavior) }
    }

    fun setMergeContinueWatchingAndNextUp(enabled: Boolean) {
        editor.edit { setMergeContinueWatchingAndNextUp(enabled) }
    }

    fun unhideAllCwItems() {
        editor.edit { unhideAllCwItems() }
    }

    fun setNextUpMaxDays(days: Int) {
        editor.edit { setNextUpMaxDays(days) }
    }

    fun setNextUpRewatching(enabled: Boolean) {
        editor.edit { setNextUpRewatching(enabled) }
    }

    fun setAppLanguage(language: String?) {
        launch {
            editor.edit { setAppLanguage(language) }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
                localeManager?.applicationLocales = if (language != null) {
                    android.os.LocaleList.forLanguageTags(language)
                } else {
                    android.os.LocaleList.getEmptyLocaleList()
                }
            } else {
                com.raulshma.jellyplay.core.ui.components.LocaleApplier.apply(context, language)
            }
        }
    }

    fun setPgsSubtitleDirectPlay(enabled: Boolean) {
        editor.edit { setPgsSubtitleDirectPlay(enabled) }
    }

    fun setBackdropThemeMusicEnabled(enabled: Boolean) {
        editor.edit { setBackdropThemeMusicEnabled(enabled) }
    }

    fun setHiddenNavItems(items: Set<String>) {
        editor.edit { setHiddenNavItems(items) }
    }

    fun setNavItemOrder(order: List<String>) {
        editor.edit { setNavItemOrder(order) }
    }

    fun setSelfUpdateCheckEnabled(enabled: Boolean) {
        editor.edit { setSelfUpdateCheckEnabled(enabled) }
    }

    fun setHdrSubtitleStyleEnabled(enabled: Boolean) {
        editor.edit { setHdrSubtitleStyleEnabled(enabled) }
    }

    fun setHdrSubtitleStyle(style: com.raulshma.jellyplay.core.model.SubtitleStyle) {
        editor.edit { setHdrSubtitleStyle(style) }
    }

    fun authorizeQuickConnect(code: String, onResult: (success: Boolean, error: String?) -> Unit) {
        launch {
            authRepository.authorizeQuickConnect(code)
                .onSuccess { authorized ->
                    if (authorized) {
                        onResult(true, null)
                    } else {
                        onResult(false, "Code not found or already used")
                    }
                }
                .onFailure { e ->
                    onResult(false, e.message ?: "Authorization failed")
                }
        }
    }

    fun setReduceMotionEnabled(enabled: Boolean) {
        editor.edit { setReduceMotionEnabled(enabled) }
    }

    fun setPreferAudioDescription(enabled: Boolean) {
        editor.edit { setPreferAudioDescription(enabled) }
    }

    fun setHighContrastSubtitles(enabled: Boolean) {
        editor.edit { setHighContrastSubtitles(enabled) }
    }

    fun setSubtitlesForcedOnly(enabled: Boolean) {
        editor.edit { setSubtitlesForcedOnly(enabled) }
    }

    fun setHideSearchHistory(enabled: Boolean) {
        editor.edit { setHideSearchHistory(enabled) }
    }

    fun setBlueLightFilterEnabled(enabled: Boolean) {
        editor.edit { setBlueLightFilterEnabled(enabled) }
    }

    fun setBlueLightFilterStrength(strength: Float) {
        editor.edit { setBlueLightFilterStrength(strength) }
    }

    fun setTvZoomModePercent(percent: Float) {
        editor.edit { setTvZoomModePercent(percent) }
    }

    fun setRemoteControlEnabled(enabled: Boolean) {
        editor.edit { setRemoteControlEnabled(enabled) }
    }

    fun setMaxDownloadStorageGb(gb: Int) {
        editor.edit { setMaxDownloadStorageGb(gb) }
    }

    fun setDownloadStorageLocation(location: String) {
        editor.edit { setDownloadStorageLocation(location) }
    }

    fun setAndroidTvWatchNextEnabled(enabled: Boolean) {
        editor.edit {
            setAndroidTvWatchNextEnabled(enabled)
            tvWatchNextScheduler.scheduleRefresh()
        }
    }

    fun setUserDataSyncEnabled(enabled: Boolean) {
        editor.edit { setUserDataSyncEnabled(enabled) }
    }

    fun setSynthwaveMode(enabled: Boolean) {
        editor.edit { setSynthwaveMode(enabled) }
    }

    fun setSynthwaveAccent(accent: String) {
        editor.edit { setSynthwaveAccent(accent) }
    }

    fun setSoothingMode(enabled: Boolean) {
        editor.edit { setSoothingMode(enabled) }
    }

    fun setSoothingAccent(accent: String) {
        editor.edit { setSoothingAccent(accent) }
    }

    fun setMonochromeMode(enabled: Boolean) {
        editor.edit { setMonochromeMode(enabled) }
    }

    fun updateNotificationPreferences(transform: (NotificationPreferences) -> NotificationPreferences) {
        editor.edit {
            updateNotificationPreferences(transform)
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
                val jsonString = com.raulshma.jellyplay.core.datastore.PreferencesJson.fullPreferences
                    .encodeToString(UserPreferences.serializer(), prefs)
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
                val imported = com.raulshma.jellyplay.core.datastore.PreferencesJson.import
                    .decodeFromString(UserPreferences.serializer(), jsonString)
                editor.edit { restorePreferences(imported) }
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
