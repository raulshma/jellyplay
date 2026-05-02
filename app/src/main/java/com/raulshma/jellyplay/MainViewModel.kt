package com.raulshma.jellyplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val isAuthenticated = authRepository.isAuthenticated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
