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
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

    init {
        viewModelScope.launch {
            preferencesStore.preferences.collect { prefs ->
                preferences = prefs
            }
        }
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                currentUserName = user?.name ?: ""
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
}
