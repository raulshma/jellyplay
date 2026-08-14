package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.flow.Flow

interface AuthApiClient {
    val currentServer: Flow<ServerInfo?>
    val currentUser: Flow<UserInfo?>

    suspend fun connectToServer(address: String): Result<ServerInfo>

    suspend fun getServerInfo(address: String): Result<ServerInfo>

    /**
     * Re-runs endpoint selection for the active server (probe the primary
     * first, then alternates; first reachable wins, falling back to the
     * current selection when nothing answers). Returns the selected address,
     * or null when no server is configured.
     */
    suspend fun selectReachableAddress(): String?

    suspend fun authenticateUser(
        serverAddress: String,
        username: String,
        password: String,
    ): Result<UserInfo>

    suspend fun authenticateUser(
        serverInfo: ServerInfo,
        username: String,
        password: String,
    ): Result<UserInfo>

    suspend fun setServer(serverInfo: ServerInfo)
    suspend fun setUser(userInfo: UserInfo)
    suspend fun disconnect()

    suspend fun isQuickConnectEnabled(): Result<Boolean>
    suspend fun initiateQuickConnect(): Result<QuickConnectInfo>
    suspend fun getQuickConnectState(secret: String): Result<QuickConnectState>

    suspend fun authenticateWithQuickConnect(
        serverInfo: ServerInfo,
        secret: String,
    ): Result<UserInfo>

    suspend fun authorizeQuickConnect(code: String): Result<Boolean>

    suspend fun postCapabilities(): Result<Unit>

    suspend fun revokeServerSession(): Result<Unit>

    fun getServerUrl(): String?
    fun getAccessToken(): String?
}
