package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val servers: Flow<List<ServerInfo>>

    val currentServer: Flow<ServerInfo?>

    val currentUser: Flow<UserInfo?>

    val isAuthenticated: Flow<Boolean>

    val currentServerUsers: Flow<List<UserInfo>>

    suspend fun addServer(address: String): Result<ServerInfo>

    suspend fun removeServer(serverId: String)

    suspend fun switchServer(serverId: String): Result<Unit>

    suspend fun login(serverAddress: String, username: String, password: String): Result<UserInfo>

    suspend fun isQuickConnectEnabled(): Result<Boolean>

    suspend fun initiateQuickConnect(): Result<QuickConnectInfo>

    suspend fun pollQuickConnect(secret: String): Result<QuickConnectState>

    suspend fun loginWithQuickConnect(serverAddress: String, secret: String): Result<UserInfo>

    suspend fun authorizeQuickConnect(code: String): Result<Boolean>

    suspend fun restoreSession(): Result<Unit>

    suspend fun logout()

    suspend fun switchUser(userId: String): Result<Unit>

    suspend fun removeUser(userId: String)

    suspend fun getUsersForServer(serverId: String): List<UserInfo>
}
