package com.raulshma.jellyplay.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class QuickConnectUiState {
    data object Idle : QuickConnectUiState()
    data object Initiating : QuickConnectUiState()
    data class WaitingForApproval(val code: String, val secret: String) : QuickConnectUiState()
    data object Authenticating : QuickConnectUiState()
    data object Success : QuickConnectUiState()
    data class Error(val message: String) : QuickConnectUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val servers = authRepository.servers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentServerUsers = authRepository.currentServerUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _quickConnectState = MutableStateFlow<QuickConnectUiState>(QuickConnectUiState.Idle)
    val quickConnectState = _quickConnectState.asStateFlow()

    private var quickConnectPollingJob: Job? = null

    fun addServer(address: String, onResult: (Result<ServerInfo>) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = authRepository.addServer(address)
            _isLoading.value = false
            onResult(result)
        }
    }

    fun removeServer(serverId: String) {
        viewModelScope.launch {
            authRepository.removeServer(serverId)
        }
    }

    fun login(serverAddress: String, username: String, password: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.login(serverAddress, username, password).map {}
            onResult(result)
        }
    }

    fun switchUser(userId: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.switchUser(userId)
            onResult(result)
        }
    }

    fun removeUser(userId: String) {
        viewModelScope.launch {
            authRepository.removeUser(userId)
        }
    }

    fun getUsersForServer(serverId: String, onResult: (List<UserInfo>) -> Unit) {
        viewModelScope.launch {
            val users = authRepository.getUsersForServer(serverId)
            onResult(users)
        }
    }

    fun startQuickConnect(serverAddress: String) {
        quickConnectPollingJob?.cancel()
        _quickConnectState.value = QuickConnectUiState.Initiating

        viewModelScope.launch {
            // Check if Quick Connect is enabled
            val enabledResult = authRepository.isQuickConnectEnabled()
            if (enabledResult.isFailure) {
                _quickConnectState.value = QuickConnectUiState.Error(
                    enabledResult.exceptionOrNull()?.message ?: "Failed to check Quick Connect availability"
                )
                return@launch
            }
            if (enabledResult.getOrNull() != true) {
                _quickConnectState.value = QuickConnectUiState.Error(
                    "Quick Connect is not enabled on this server"
                )
                return@launch
            }

            // Initiate Quick Connect
            val initiateResult = authRepository.initiateQuickConnect()
            if (initiateResult.isFailure) {
                _quickConnectState.value = QuickConnectUiState.Error(
                    initiateResult.exceptionOrNull()?.message ?: "Failed to initiate Quick Connect"
                )
                return@launch
            }

            val qcInfo = initiateResult.getOrNull()!!
            _quickConnectState.value = QuickConnectUiState.WaitingForApproval(
                code = qcInfo.code,
                secret = qcInfo.secret,
            )

            // Start polling
            quickConnectPollingJob = viewModelScope.launch {
                var attempts = 0
                val maxAttempts = 40 // ~2 minutes at 3s interval
                while (attempts < maxAttempts) {
                    delay(3_000)
                    attempts++

                    val pollResult = authRepository.pollQuickConnect(qcInfo.secret)
                    if (pollResult.isFailure) {
                        _quickConnectState.value = QuickConnectUiState.Error(
                            pollResult.exceptionOrNull()?.message ?: "Quick Connect polling failed"
                        )
                        return@launch
                    }

                    val state = pollResult.getOrNull()!!
                    if (state.authenticated) {
                        _quickConnectState.value = QuickConnectUiState.Authenticating
                        val loginResult = authRepository.loginWithQuickConnect(
                            serverAddress, qcInfo.secret
                        )
                        if (loginResult.isSuccess) {
                            _quickConnectState.value = QuickConnectUiState.Success
                        } else {
                            _quickConnectState.value = QuickConnectUiState.Error(
                                loginResult.exceptionOrNull()?.message ?: "Quick Connect authentication failed"
                            )
                        }
                        return@launch
                    }
                }
                // Timed out
                _quickConnectState.value = QuickConnectUiState.Error(
                    "Quick Connect timed out. Please try again."
                )
            }
        }
    }

    fun cancelQuickConnect() {
        quickConnectPollingJob?.cancel()
        quickConnectPollingJob = null
        _quickConnectState.value = QuickConnectUiState.Idle
    }

    fun resetQuickConnectState() {
        quickConnectPollingJob?.cancel()
        quickConnectPollingJob = null
        _quickConnectState.value = QuickConnectUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        quickConnectPollingJob?.cancel()
    }
}
