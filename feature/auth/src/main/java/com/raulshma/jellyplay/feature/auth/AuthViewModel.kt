package com.raulshma.jellyplay.feature.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.ServerInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val servers = authRepository.servers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

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
}
