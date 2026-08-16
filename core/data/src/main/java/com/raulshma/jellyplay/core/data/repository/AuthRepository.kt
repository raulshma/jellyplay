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

    /**
     * Reachability probe against one explicit address (a primary or an
     * alternate). Purely a health check — never connects the client or
     * persists anything, unlike [addServer].
     */
    suspend fun probeServer(address: String): Result<ServerInfo>

    suspend fun removeServer(serverId: String)

    suspend fun switchServer(serverId: String): Result<Unit>

    suspend fun addServerAddress(serverId: String, address: String): Result<Unit>

    suspend fun removeServerAddress(serverId: String, address: String): Result<Unit>

    suspend fun switchServerAddress(serverId: String, address: String): Result<Unit>

    suspend fun login(serverAddress: String, username: String, password: String): Result<UserInfo>

    suspend fun isQuickConnectEnabled(): Result<Boolean>

    suspend fun initiateQuickConnect(): Result<QuickConnectInfo>

    suspend fun pollQuickConnect(secret: String): Result<QuickConnectState>

    suspend fun loginWithQuickConnect(serverAddress: String, secret: String): Result<UserInfo>

    suspend fun authorizeQuickConnect(code: String): Result<Boolean>

    suspend fun restoreSession(): Result<Unit>

    /**
     * Re-fetches the current user's policy from the server and updates the
     * cached [UserInfo] (and its encrypted persistence). Used to catch a
     * server-side admin demotion without forcing re-login.
     *
     * Failure handling:
     *  - HTTP 401/403 → the cached user is treated as no longer authorized
     *    (admin status cleared); the server is the ultimate authority.
     *  - Any other failure (network, 5xx) → the cached value is preserved so
     *    a flaky connection can't lock an admin out; the server still 403s
     *    on the actual privileged call as a backstop.
     */
    suspend fun refreshCurrentUser(): Result<UserInfo>

    suspend fun logout()

    /**
     * Logs out the current session on the server (revokes the active access token)
     * and clears local state. Despite the historical Jellyfin naming of the
     * underlying endpoint, this affects only the current device's session — it
     * does not revoke other active sessions for the same user.
     */
    suspend fun revokeServerSession()

    suspend fun switchUser(userId: String): Result<Unit>

    suspend fun removeUser(userId: String)

    suspend fun getUsersForServer(serverId: String): List<UserInfo>
}
