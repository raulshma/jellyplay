package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val serverDao: ServerDao,
    private val preferencesStore: UserPreferencesStore,
    private val coroutineScope: CoroutineScope,
) : AuthRepository {

    override val servers: Flow<List<ServerInfo>> = serverDao.getAllServers().map { entities ->
        entities.map { it.toServerInfo() }
    }

    override val currentServer: Flow<ServerInfo?> = apiClient.currentServer

    override val currentUser: Flow<UserInfo?> = apiClient.currentUser

    override val isAuthenticated: Flow<Boolean> = combine(
        apiClient.currentServer,
        apiClient.currentUser,
    ) { server, user -> server != null && user != null }

    override suspend fun addServer(address: String): Result<ServerInfo> {
        return apiClient.connectToServer(address).onSuccess { serverInfo ->
            serverDao.insertServer(
                ServerEntity(
                    id = serverInfo.id,
                    name = serverInfo.name,
                    address = serverInfo.address,
                )
            )
        }
    }

    override suspend fun removeServer(serverId: String) {
        serverDao.deleteServerById(serverId)
    }

    override suspend fun login(
        serverAddress: String,
        username: String,
        password: String,
    ): Result<UserInfo> {
        return apiClient.authenticateUser(serverAddress, username, password).onSuccess { user ->
            val server = apiClient.currentServer as? ServerInfo
            if (server != null) {
                serverDao.updateServer(
                    ServerEntity(
                        id = server.id,
                        name = server.name,
                        address = server.address,
                        userId = user.id,
                        accessToken = user.accessToken,
                        lastConnected = System.currentTimeMillis(),
                    )
                )
                preferencesStore.setActiveServer(server.id)
                preferencesStore.setActiveUser(user.id)
            }
        }
    }

    override suspend fun restoreSession(): Result<Unit> = runCatching {
        val serverId = preferencesStore.activeServerId.first()
        val userId = preferencesStore.activeUserId.first()
        if (serverId != null && userId != null) {
            val serverEntity = serverDao.getServerById(serverId) ?: return Result.success(Unit)
            val server = serverEntity.toServerInfo()
            apiClient.setServer(server)
            val token = serverEntity.accessToken
            if (token != null) {
                apiClient.setUser(
                    UserInfo(
                        id = userId,
                        name = "",
                        serverAddress = server.address,
                        accessToken = token,
                    )
                )
            }
        }
    }

    override suspend fun logout() {
        apiClient.disconnect()
        preferencesStore.clearAll()
    }

    private fun ServerEntity.toServerInfo() = ServerInfo(
        id = id,
        name = name,
        address = address,
        userId = userId,
        accessToken = accessToken,
        isConnected = accessToken != null,
    )
}
