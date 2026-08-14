package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import com.raulshma.jellyplay.core.database.entity.UserEntity
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
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
    private val serverIdentityStore: ServerIdentityStore,
    private val tokenCipher: TokenCipher,
    private val json: Json,
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object {
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
        // Prefer the primary when reachable, else fail over to an alternate so
        // a server tapped while away from its LAN address still connects.
        // Defensive: a probe crash must not fail the switch — the primary
        // address still works as before.
        runCatching { apiClient.selectReachableAddress() }

        val userEntity = userDao.getMostRecentUserForServer(serverId)
            ?: serverEntity.userId?.let { userDao.getUserById(it) }

        if (userEntity != null) {
            val user = userEntity.toUserInfo(server.address)
            apiClient.setUser(user)
            serverIdentityStore.setActiveSession(serverId, userEntity.userId)
            database.withTransaction {
                serverDao.updateServer(serverEntity.copy(lastConnected = System.currentTimeMillis()))
                userDao.updateUser(userEntity.copy(lastConnected = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun addServerAddress(serverId: String, address: String): Result<Unit> = runCatching {
        val normalizedAddress = address.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else "https://$it"
        }
        val serverEntity = serverDao.getServerById(serverId)
            ?: return Result.failure(Exception("Server not found"))
        if (serverEntity.address == normalizedAddress) {
            return Result.failure(Exception("Address is already the primary address"))
        }
        val currentAlternates = serverEntity.alternateAddresses?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
        if (normalizedAddress in currentAlternates) {
            return Result.failure(Exception("Address is already an alternate"))
        }
        val info = apiClient.getServerInfo(normalizedAddress).getOrElse {
            return Result.failure(Exception("Could not connect to server at $normalizedAddress"))
        }
        if (info.id != serverId) {
            return Result.failure(Exception("This address points to a different server"))
        }
        val updated = currentAlternates + normalizedAddress
        serverDao.updateServer(
            serverEntity.copy(alternateAddresses = json.encodeToString(updated))
        )
    }

    override suspend fun removeServerAddress(serverId: String, address: String): Result<Unit> = runCatching {
        val serverEntity = serverDao.getServerById(serverId)
            ?: return Result.failure(Exception("Server not found"))
        val currentAlternates = serverEntity.alternateAddresses?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
        val updated = currentAlternates - address
        serverDao.updateServer(
            serverEntity.copy(
                alternateAddresses = if (updated.isEmpty()) null else json.encodeToString(updated)
            )
        )
    }

    override suspend fun switchServerAddress(serverId: String, address: String): Result<Unit> = runCatching {
        val serverEntity = serverDao.getServerById(serverId)
            ?: return Result.failure(Exception("Server not found"))
        val normalizedAddress = address.trim().trimEnd('/')
        val currentAlternates = serverEntity.alternateAddresses?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
        if (normalizedAddress !in currentAlternates) {
            return Result.failure(Exception("Address not found in alternate addresses"))
        }
        val oldPrimary = serverEntity.address
        val newAlternates = (currentAlternates - normalizedAddress) + oldPrimary
        serverDao.updateServer(
            serverEntity.copy(
                address = normalizedAddress,
                alternateAddresses = json.encodeToString(newAlternates),
            )
        )
        val updatedServer = serverEntity.copy(
            address = normalizedAddress,
            alternateAddresses = json.encodeToString(newAlternates),
        ).toServerInfo()
        apiClient.setServer(updatedServer)
        val currentUser = apiClient.currentUser.first()
        if (currentUser != null) {
            apiClient.setUser(currentUser.copy(serverAddress = normalizedAddress))
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

    override suspend fun authorizeQuickConnect(code: String): Result<Boolean> =
        apiClient.authorizeQuickConnect(code)

    override suspend fun restoreSession(): Result<Unit> = runCatching {
        val serverId: String? = serverIdentityStore.activeServerId.first()
        val userId: String? = serverIdentityStore.activeUserId.first()
        if (serverId != null && userId != null) {
            val serverEntity = serverDao.getServerById(serverId)
            if (serverEntity == null) {
                // Don't log the raw server/user GUIDs — they survive into
                // release builds and can be used for cross-session correlation.
                Log.w("AuthRepository", "restoreSession: server not found in DB")
                serverIdentityStore.setActiveSession("", "")
                return@runCatching
            }
            val server = serverEntity.toServerInfo()
            apiClient.setServer(server)
            // Address failover: if the primary (e.g. a LAN URL) is unreachable
            // but an alternate answers (e.g. the outside URL), route all
            // traffic to the alternate BEFORE the API client is created, so
            // the very first request already uses a working address. When no
            // alternates exist this is a no-op and offline/cached use is
            // unchanged. Re-selection runs again periodically (health monitor)
            // and switches back to the primary once it is reachable.
            runCatching { apiClient.selectReachableAddress() }

            val userEntity = userDao.getUserById(userId)
            if (userEntity == null) {
                Log.w("AuthRepository", "restoreSession: user not found in DB")
                serverIdentityStore.setActiveUser("")
                return@runCatching
            }
            val token = tokenCipher.decrypt(userEntity.accessToken)
            if (token != null) {
                apiClient.setUser(
                    UserInfo(
                        id = userId,
                        name = userEntity.name,
                        serverAddress = server.address,
                        accessToken = token,
                        serverId = serverId,
                        isAdmin = userEntity.isAdmin,
                        canDeleteContent = userEntity.canDeleteContent,
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
                Log.w("AuthRepository", "restoreSession: no access token for active user")
            }
        }
    }.onFailure { e ->
        Log.e("AuthRepository", "restoreSession failed", e)
    }

    override suspend fun refreshCurrentUser(): Result<UserInfo> {
        val cached = apiClient.currentUser.first()
            ?: return Result.failure(Exception("No active user to refresh"))

        val result = apiClient.getCurrentUser()
        return result.fold(
            onSuccess = { managed ->
                val refreshed = cached.copy(
                    isAdmin = managed.policy.isAdministrator,
                    canDeleteContent = managed.policy.enableContentDeletion,
                )
                apiClient.setUser(refreshed)
                persistRefreshedFlags(refreshed)
                Result.success(refreshed)
            },
            onFailure = { e ->
                // 401/403 = the server has revoked/demoted this user. Clear
                // admin status so the admin area is blocked immediately; the
                // server remains the ultimate authority on the next call.
                if (e is com.raulshma.jellyplay.core.network.api.ApiException && e.isAccessDenied) {
                    val demoted = cached.copy(isAdmin = false, canDeleteContent = false)
                    apiClient.setUser(demoted)
                    persistRefreshedFlags(demoted)
                    Result.success(demoted)
                } else {
                    // Transient/non-access failure — keep the cached value so a
                    // flaky network can't lock an admin out. The server still
                    // 403s on the real privileged call as a backstop.
                    Result.failure(e)
                }
            },
        )
    }

    /**
     * Persists just the refreshed permission flags ([UserEntity.isAdmin] and
     * [UserEntity.canDeleteContent]) for the active user without rewriting the
     * whole entity (avoids clobbering the encrypted token, image tag, etc.).
     */
    private suspend fun persistRefreshedFlags(user: UserInfo) {
        val existing = userDao.getUserById(user.id) ?: return
        if (existing.isAdmin == user.isAdmin && existing.canDeleteContent == user.canDeleteContent) return
        userDao.updateUser(
            existing.copy(
                isAdmin = user.isAdmin,
                canDeleteContent = user.canDeleteContent,
            )
        )
    }

    override suspend fun logout() {
        apiClient.disconnect()
        // Clear only the active session selection — preserve the stable
        // device id and all user preferences (theme/player/EQ/onboarding/…)
        // so re-login does not orphan server-side sessions or reset settings.
        serverIdentityStore.clearSession()
    }

    override suspend fun revokeServerSession() {
        val currentUserId = apiClient.currentUser.first()?.id
        try {
            apiClient.revokeServerSession()
        } catch (_: Exception) {
            // Even if the server call fails, we should still clear local state
        }
        if (currentUserId != null) {
            removeUser(currentUserId)
        }
        apiClient.disconnect()
        serverIdentityStore.clearSession()
    }

    override suspend fun switchUser(userId: String): Result<Unit> = runCatching {
        val userEntity = userDao.getUserById(userId) ?: return Result.success(Unit)
        val server = serverDao.getServerById(userEntity.serverId) ?: return Result.success(Unit)
        apiClient.disconnect()
        apiClient.setServer(server.toServerInfo())
        // Re-run endpoint selection so the user's first request does not race
        // a dead primary address.
        runCatching { apiClient.selectReachableAddress() }
        apiClient.setUser(userEntity.toUserInfo(server.address))
        serverIdentityStore.setActiveSession(server.id, userId)
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
        database.withTransaction {
            userDao.deleteUserById(userId)
            // Single bulk UPDATE replaces the previous per-row read-modify-
            // write loop (an N+1 write that issued a separate UPDATE per
            // matching server). Same effect — every server row bound to this
            // user has its userId + accessToken cleared.
            serverDao.clearUserFromServers(userId)
        }
        val currentUserId = apiClient.currentUser.first()?.id
        if (currentUserId == userId) {
            apiClient.disconnect()
            serverIdentityStore.setActiveUser("")
        }
    }

    override suspend fun getUsersForServer(serverId: String): List<UserInfo> {
        // Use the one-shot DAO query instead of `.first()` on the Flow, which
        // would cancel a fresh Flow emission (full query cost) on every call.
        return userDao.getUsersForServerOnce(serverId).map { it.toUserInfo() }
    }

    private suspend fun persistSession(server: ServerInfo, user: UserInfo, fallbackUsername: String = "") {
        val existingServer = serverDao.getServerById(server.id)
        val preservedAlternateAddresses = existingServer?.alternateAddresses
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
            accessToken = tokenCipher.encrypt(user.accessToken) ?: "",
            primaryImageTag = user.primaryImageTag,
            maxParentalAgeRating = user.maxParentalAgeRating,
            isAdmin = user.isAdmin,
            canDeleteContent = user.canDeleteContent,
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
                    accessToken = tokenCipher.encrypt(user.accessToken),
                    lastConnected = System.currentTimeMillis(),
                    alternateAddresses = preservedAlternateAddresses,
                )
            )
        }
        serverIdentityStore.setActiveSession(server.id, user.id)
    }

    private fun ServerEntity.toServerInfo() = ServerInfo(
        id = id,
        name = name,
        address = address,
        userId = userId,
        accessToken = tokenCipher.decrypt(accessToken),
        isConnected = accessToken != null,
        alternateAddresses = alternateAddresses?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList())
        } ?: emptyList(),
    )

    private fun UserEntity.toUserInfo(serverAddress: String = "") = UserInfo(
        id = userId,
        name = name,
        serverAddress = serverAddress,
        accessToken = tokenCipher.decrypt(accessToken) ?: "",
        serverId = serverId,
        isAdmin = isAdmin,
        canDeleteContent = canDeleteContent,
        maxParentalAgeRating = maxParentalAgeRating,
        primaryImageTag = primaryImageTag,
        enabledFolderIds = enabledFolderIds?.let { raw ->
            folderIdsCache[raw] ?: try {
                json.decodeFromString<List<String>>(raw).also { folderIdsCache.put(raw, it) }
            } catch (_: Exception) { emptyList() }
        } ?: emptyList(),
    )
}
