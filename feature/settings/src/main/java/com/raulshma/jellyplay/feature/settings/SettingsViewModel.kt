package com.raulshma.jellyplay.feature.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesStore: UserPreferencesStore,
    private val authRepository: AuthRepository,
    private val downloadRepository: DownloadRepository,
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
            }
        }
        viewModelScope.launch {
            authRepository.currentServerUsers.collect { users ->
                currentServerUsers = users
                isLoadingUsers = false
            }
        }
        calculateCacheSize()
    }

    fun setDynamicTheming(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setDynamicTheming(enabled) }
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
            val cacheSize = getDirSize(context.cacheDir)
            val externalCacheSize = context.externalCacheDir?.let { getDirSize(it) } ?: 0L
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

    fun setKidsModeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setKidsModeEnabled(enabled) }
    }

    fun setKidsModeMaxRating(rating: String) {
        viewModelScope.launch { preferencesStore.setKidsModeMaxRating(rating) }
    }

    fun setDialogueBoostEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setDialogueBoostEnabled(enabled) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setEqualizerEnabled(enabled) }
    }

    fun setNightModeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setNightModeEnabled(enabled) }
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

    fun setSkipIntroEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setSkipIntroEnabled(enabled) }
    }

    fun setSkipOutroEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setSkipOutroEnabled(enabled) }
    }

    fun setAutoSkipIntro(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setAutoSkipIntro(enabled) }
    }

    fun setAutoSkipOutro(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setAutoSkipOutro(enabled) }
    }

    fun setVideoEpisodeBrowserEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setVideoEpisodeBrowserEnabled(enabled) }
    }

    fun setSyncPlayProgressReportingMode(mode: String) {
        viewModelScope.launch { preferencesStore.setSyncPlayProgressReportingMode(mode) }
    }

    fun setSyncPlayAutoJoinLastGroup(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setSyncPlayAutoJoinLastGroup(enabled) }
    }

    fun setSyncPlayNotifyUserJoinLeave(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setSyncPlayNotifyUserJoinLeave(enabled) }
    }

    fun setSyncPlayNotifyChatMessages(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setSyncPlayNotifyChatMessages(enabled) }
    }

    fun setSyncPlayNotifySyncIssues(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setSyncPlayNotifySyncIssues(enabled) }
    }

    fun setSyncPlayDefaultIgnoreWait(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setSyncPlayDefaultIgnoreWait(enabled) }
    }
}
