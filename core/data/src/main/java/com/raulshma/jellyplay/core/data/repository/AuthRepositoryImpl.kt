package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import com.raulshma.jellyplay.core.database.entity.UserEntity
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import android.util.Log
import androidx.collection.LruCache
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val database: JellyPlayDatabase,
    private val serverDao: ServerDao,
    private val userDao: UserDao,
    private val preferencesStore: UserPreferencesStore,
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        private val folderIdsCache = LruCache<String, List<String>>(16)
    }

    override val servers: Flow<List<ServerInfo>> = serverDao.getAllServers().map { entities ->
        entities.map { it.toServerInfo() }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override val currentServer: Flow<ServerInfo?> = apiClient.currentServer

    override val currentUser: Flow<UserInfo?> = apiClient.currentUser

    override val isAuthenticated: Flow<Boolean> = combine(
        apiClient.currentServer,
        apiClient.currentUser,
    ) { server, user -> server != null && user != null }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    override val currentServerUsers: Flow<List<UserInfo>> =
        apiClient.currentServer.flatMapLatest { server ->
            server?.id?.let { sid ->
                userDao.getUsersForServer(sid).map { list ->
                    list.map { it.toUserInfo(server.address) }
                }
            } ?: flowOf(emptyList())
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        database.withTransaction {
            userDao.deleteUsersForServer(serverId)
            serverDao.deleteServerById(serverId)
        }
    }

    override suspend fun switchServer(serverId: String): Result<Unit> = runCatching {
        val serverEntity = serverDao.getServerById(serverId) ?: return Result.success(Unit)
        val server = serverEntity.toServerInfo()
        apiClient.disconnect()
        apiClient.setServer(server)

        val userEntity = userDao.getMostRecentUserForServer(serverId)
            ?: serverEntity.userId?.let { userDao.getUserById(it) }

        if (userEntity != null) {
            val user = userEntity.toUserInfo(server.address)
            apiClient.setUser(user)
            preferencesStore.setActiveServer(serverId)
            preferencesStore.setActiveUser(userEntity.userId)
            database.withTransaction {
                serverDao.updateServer(serverEntity.copy(lastConnected = System.currentTimeMillis()))
                userDao.updateUser(userEntity.copy(lastConnected = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun login(
        serverAddress: String,
        username: String,
        password: String,
    ): Result<UserInfo> {
        val normalizedAddress = serverAddress.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else "https://$it"
        }
        val serverFromDb = serverDao.getServerByAddress(normalizedAddress)

        val existingServerInfo = serverFromDb?.toServerInfo()

        return if (existingServerInfo != null) {
            apiClient.authenticateUser(existingServerInfo, username, password)
        } else {
            apiClient.authenticateUser(serverAddress, username, password)
        }.onSuccess { user ->
            val server = apiClient.currentServer.first()
            if (server != null) {
                persistSession(server, user, username)
            }
        }
    }

    override suspend fun isQuickConnectEnabled(): Result<Boolean> {
        return apiClient.isQuickConnectEnabled()
    }

    override suspend fun initiateQuickConnect(): Result<QuickConnectInfo> {
        return apiClient.initiateQuickConnect()
    }

    override suspend fun pollQuickConnect(secret: String): Result<QuickConnectState> {
        return apiClient.getQuickConnectState(secret)
    }

    override suspend fun loginWithQuickConnect(
        serverAddress: String,
        secret: String,
    ): Result<UserInfo> {
        val normalizedAddress = serverAddress.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else "https://$it"
        }
        val serverFromDb = serverDao.getServerByAddress(normalizedAddress)

        val existingServerInfo = serverFromDb?.toServerInfo()

        val serverInfo = existingServerInfo
            ?: apiClient.currentServer.first()
            ?: return Result.failure(Exception("Not connected to server"))

        return apiClient.authenticateWithQuickConnect(serverInfo, secret).onSuccess { user ->
            val server = apiClient.currentServer.first()
            if (server != null) {
                persistSession(server, user)
            }
        }
    }

    override suspend fun restoreSession(): Result<Unit> = runCatching {
        val serverId = preferencesStore.activeServerId.first()
        val userId = preferencesStore.activeUserId.first()
        if (serverId != null && userId != null) {
            val serverEntity = serverDao.getServerById(serverId)
            if (serverEntity == null) {
                Log.w("AuthRepository", "restoreSession: server not found in DB, id=$serverId")
                preferencesStore.setActiveServer("")
                preferencesStore.setActiveUser("")
                return@runCatching
            }
            val server = serverEntity.toServerInfo()
            apiClient.setServer(server)

            val userEntity = userDao.getUserById(userId)
            if (userEntity == null) {
                Log.w("AuthRepository", "restoreSession: user not found in DB, id=$userId")
                preferencesStore.setActiveUser("")
                return@runCatching
            }
            val token = userEntity.accessToken
            if (token != null) {
                apiClient.setUser(
                    UserInfo(
                        id = userId,
                        name = userEntity.name,
                        serverAddress = server.address,
                        accessToken = token,
                        serverId = serverId,
                        isAdmin = userEntity.isAdmin,
                        maxParentalAgeRating = userEntity.maxParentalAgeRating,
                        primaryImageTag = userEntity.primaryImageTag,
                        enabledFolderIds = userEntity.enabledFolderIds?.let {
                            try {
                                json.decodeFromString<List<String>>(it)
                            } catch (_: Exception) { emptyList() }
                        } ?: emptyList(),
                    )
                )
            } else {
                Log.w("AuthRepository", "restoreSession: no access token for user id=$userId")
            }
        }
    }.onFailure { e ->
        Log.e("AuthRepository", "restoreSession failed", e)
    }

    override suspend fun logout() {
        apiClient.disconnect()
        preferencesStore.clearAll()
    }

    override suspend fun switchUser(userId: String): Result<Unit> = runCatching {
        val userEntity = userDao.getUserById(userId) ?: return Result.success(Unit)
        val server = serverDao.getServerById(userEntity.serverId) ?: return Result.success(Unit)
        apiClient.disconnect()
        apiClient.setServer(server.toServerInfo())
        apiClient.setUser(userEntity.toUserInfo(server.address))
        preferencesStore.setActiveServer(server.id)
        preferencesStore.setActiveUser(userId)
        database.withTransaction {
            userDao.updateUser(userEntity.copy(lastConnected = System.currentTimeMillis()))
            serverDao.updateServer(
                server.copy(
                    userId = userId,
                    accessToken = userEntity.accessToken,
                    lastConnected = System.currentTimeMillis(),
                )
            )
        }
    }

    override suspend fun removeUser(userId: String) {
        userDao.deleteUserById(userId)
        val currentUserId = apiClient.currentUser.first()?.id
        if (currentUserId == userId) {
            apiClient.disconnect()
            preferencesStore.setActiveUser("")
        }
    }

    override suspend fun getUsersForServer(serverId: String): List<UserInfo> {
        return userDao.getUsersForServer(serverId).first().map { it.toUserInfo() }
    }

    private suspend fun persistSession(server: ServerInfo, user: UserInfo, fallbackUsername: String = "") {
        val existingServer = serverDao.getServerById(server.id)
        if (existingServer == null) {
            serverDao.insertServer(
                ServerEntity(
                    id = server.id,
                    name = server.name,
                    address = server.address,
                )
            )
        }
        val userEntity = UserEntity(
            userId = user.id,
            serverId = server.id,
            name = user.name.ifBlank { fallbackUsername },
            accessToken = user.accessToken,
            primaryImageTag = user.primaryImageTag,
            maxParentalAgeRating = user.maxParentalAgeRating,
            isAdmin = user.isAdmin,
            enabledFolderIds = json.encodeToString(user.enabledFolderIds),
            lastConnected = System.currentTimeMillis(),
        )
        database.withTransaction {
            userDao.insertUser(userEntity)
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
        }
        preferencesStore.setActiveServer(server.id)
        preferencesStore.setActiveUser(user.id)
    }

    private fun ServerEntity.toServerInfo() = ServerInfo(
        id = id,
        name = name,
        address = address,
        userId = userId,
        accessToken = accessToken,
        isConnected = accessToken != null,
    )

    private fun UserEntity.toUserInfo(serverAddress: String = "") = UserInfo(
        id = userId,
        name = name,
        serverAddress = serverAddress,
        accessToken = accessToken,
        serverId = serverId,
        isAdmin = isAdmin,
        maxParentalAgeRating = maxParentalAgeRating,
        primaryImageTag = primaryImageTag,
        enabledFolderIds = enabledFolderIds?.let { raw ->
            folderIdsCache[raw] ?: try {
                json.decodeFromString<List<String>>(raw).also { folderIdsCache.put(raw, it) }
            } catch (_: Exception) { emptyList() }
        } ?: emptyList(),
    )
}
