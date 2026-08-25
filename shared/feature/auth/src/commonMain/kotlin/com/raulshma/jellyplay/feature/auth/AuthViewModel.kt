package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_auth
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_check_availability
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_initiate
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_not_enabled
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_polling
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_timeout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

sealed class QuickConnectUiState {
    data object Idle : QuickConnectUiState()
    data object Initiating : QuickConnectUiState()
    data class WaitingForApproval(val code: String, val secret: String) : QuickConnectUiState()
    data object Authenticating : QuickConnectUiState()
    data object Success : QuickConnectUiState()
    data class Error(val message: AuthMessage) : QuickConnectUiState()
}

class AuthViewModel(
    private val authRepository: AuthRepository,
) : JellyPlayViewModel() {

    val servers = authRepository.servers
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _serverHealth = stateFlow<Map<String, ServerHealth>>(emptyMap())
    val serverHealth = _serverHealth.flow

    val currentServerUsers = authRepository.currentServerUsers
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = stateFlow(false)
    val isLoading = _isLoading.flow

    private val _quickConnectState = stateFlow<QuickConnectUiState>(QuickConnectUiState.Idle)
    val quickConnectState = _quickConnectState.flow

    private var quickConnectPollingJob: Job? = null

    fun addServer(address: String, onResult: (Result<ServerInfo>) -> Unit) {
        launch {
            _isLoading.set(true)
            val result = authRepository.addServer(address)
            _isLoading.set(false)
            onResult(result)
        }
    }

    fun removeServer(serverId: String) {
        launch {
            authRepository.removeServer(serverId)
        }
    }

    /**
     * Pings each saved server once and publishes per-server [ServerHealth]
     * into [serverHealth] (keyed by the server's primary address, which the
     * server list renders). A server counts as reachable when ANY of its
     * addresses answers — primary first, then alternates — mirroring the
     * app's address failover. Safe to call repeatedly; concurrent calls are
     * guarded by cancelling any in-flight batch.
     */
    private var healthCheckJob: Job? = null
    fun checkServersHealth(servers: List<ServerInfo>) {
        healthCheckJob?.cancel()
        if (servers.isEmpty()) {
            _serverHealth.set(emptyMap())
            return
        }
        // Immediately mark everything as Checking so the UI can render a dot.
        _serverHealth.set(servers.associate { it.address to ServerHealth.Checking })
        healthCheckJob = launch {
            servers.forEach { server ->
                val startTime = System.currentTimeMillis()
                val addresses = listOf(server.address) + server.alternateAddresses
                val reachable = addresses.any { address ->
                    authRepository.probeServer(address).isSuccess
                }
                val latency = System.currentTimeMillis() - startTime
                val health = if (reachable) {
                    ServerHealth.Healthy(latencyMs = latency)
                } else {
                    ServerHealth.Unreachable
                }
                _serverHealth.update { it + (server.address to health) }
            }
        }
    }

    fun login(serverAddress: String, username: String, password: String, onResult: (Result<Unit>) -> Unit) {
        launch {
            val result = authRepository.login(serverAddress, username, password).map {}
            onResult(result)
        }
    }

    fun switchUser(userId: String, onResult: (Result<Unit>) -> Unit) {
        launch {
            val result = authRepository.switchUser(userId)
            onResult(result)
        }
    }

    fun removeUser(userId: String) {
        launch {
            authRepository.removeUser(userId)
        }
    }

    fun getUsersForServer(serverId: String, onResult: (List<UserInfo>) -> Unit) {
        launch {
            val users = authRepository.getUsersForServer(serverId)
            onResult(users)
        }
    }

    fun startQuickConnect(serverAddress: String) {
        quickConnectPollingJob?.cancel()
        _quickConnectState.set(QuickConnectUiState.Initiating)

        launch {
            val enabledResult = authRepository.isQuickConnectEnabled()
            if (enabledResult.isFailure) {
                _quickConnectState.set(
                    QuickConnectUiState.Error(
                        enabledResult.exceptionOrNull()?.message?.let { AuthMessage.Raw(it) }
                            ?: AuthMessage.Resource(Res.string.auth_qc_error_check_availability)
                    )
                )
                return@launch
            }
            if (enabledResult.getOrNull() != true) {
                _quickConnectState.set(
                    QuickConnectUiState.Error(
                        AuthMessage.Resource(Res.string.auth_qc_error_not_enabled)
                    )
                )
                return@launch
            }

            val initiateResult = authRepository.initiateQuickConnect()
            if (initiateResult.isFailure) {
                _quickConnectState.set(
                    QuickConnectUiState.Error(
                        initiateResult.exceptionOrNull()?.message?.let { AuthMessage.Raw(it) }
                            ?: AuthMessage.Resource(Res.string.auth_qc_error_initiate)
                    )
                )
                return@launch
            }

            val qcInfo = initiateResult.getOrNull()!!
            _quickConnectState.set(
                QuickConnectUiState.WaitingForApproval(
                    code = qcInfo.code,
                    secret = qcInfo.secret,
                )
            )

            quickConnectPollingJob = launch {
                var attempts = 0
                val maxAttempts = 40
                while (attempts < maxAttempts) {
                    delay(3_000)
                    attempts++

                    val pollResult = authRepository.pollQuickConnect(qcInfo.secret)
                    if (pollResult.isFailure) {
                        _quickConnectState.set(
                            QuickConnectUiState.Error(
                                pollResult.exceptionOrNull()?.message?.let { AuthMessage.Raw(it) }
                                    ?: AuthMessage.Resource(Res.string.auth_qc_error_polling)
                            )
                        )
                        return@launch
                    }

                    val state = pollResult.getOrNull()!!
                    if (state.authenticated) {
                        _quickConnectState.set(QuickConnectUiState.Authenticating)
                        val loginResult = authRepository.loginWithQuickConnect(
                            serverAddress, qcInfo.secret
                        )
                        if (loginResult.isSuccess) {
                            _quickConnectState.set(QuickConnectUiState.Success)
                        } else {
                            _quickConnectState.set(
                                QuickConnectUiState.Error(
                                    loginResult.exceptionOrNull()?.message?.let { AuthMessage.Raw(it) }
                                        ?: AuthMessage.Resource(Res.string.auth_qc_error_auth)
                                )
                            )
                        }
                        return@launch
                    }
                }
                _quickConnectState.set(
                    QuickConnectUiState.Error(
                        AuthMessage.Resource(Res.string.auth_qc_error_timeout)
                    )
                )
            }
        }
    }

    fun cancelQuickConnect() {
        quickConnectPollingJob?.cancel()
        quickConnectPollingJob = null
        _quickConnectState.set(QuickConnectUiState.Idle)
    }

    fun resetQuickConnectState() {
        quickConnectPollingJob?.cancel()
        quickConnectPollingJob = null
        _quickConnectState.set(QuickConnectUiState.Idle)
    }

    override fun onCleared() {
        super.onCleared()
        quickConnectPollingJob?.cancel()
        healthCheckJob?.cancel()
    }
}
