package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val servers: Flow<List<ServerInfo>>

    val currentServer: Flow<ServerInfo?>

    val currentUser: Flow<UserInfo?>

    val isAuthenticated: Flow<Boolean>

    suspend fun addServer(address: String): Result<ServerInfo>

    suspend fun removeServer(serverId: String)

    suspend fun switchServer(serverId: String): Result<Unit>

    suspend fun login(serverAddress: String, username: String, password: String): Result<UserInfo>

    suspend fun restoreSession(): Result<Unit>

    suspend fun logout()
}
