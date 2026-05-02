package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesStore: UserPreferencesStore,
    private val authRepository: AuthRepository,
) : ViewModel() {

    var preferences by mutableStateOf(UserPreferences())
        private set

    var currentUserName by mutableStateOf("")
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

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
