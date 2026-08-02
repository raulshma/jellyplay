package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : JellyPlayViewModel() {

    val currentUser: StateFlow<UserInfo?> = authRepository.currentUser
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private val _currentServerUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val currentServerUsers: StateFlow<List<UserInfo>> = _currentServerUsers.asStateFlow()

    private val _isLoadingUsers = MutableStateFlow(false)
    val isLoadingUsers: StateFlow<Boolean> = _isLoadingUsers.asStateFlow()

    init {
        launch {
            authRepository.currentServerUsers.collect { users ->
                _currentServerUsers.value = users
                _isLoadingUsers.value = false
            }
        }
    }

    fun switchUser(userId: String, onComplete: () -> Unit) {
        launch {
            authRepository.switchUser(userId)
            onComplete()
        }
    }

    fun removeUser(userId: String) {
        launch { authRepository.removeUser(userId) }
    }
}
