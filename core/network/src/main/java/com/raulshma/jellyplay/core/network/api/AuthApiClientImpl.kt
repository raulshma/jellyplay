package com.raulshma.jellyplay.core.network.api

import android.util.Log
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : AuthApiClient {

    override val currentServer: Flow<ServerInfo?> = engine.currentServer
    override val currentUser: Flow<UserInfo?> = engine.currentUser

    override suspend fun connectToServer(address: String): Result<ServerInfo> = runCatching {
        val normalizedAddress = address.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else "https://$it"
        }
        withContext(Dispatchers.IO) {
            try {
                val client = engine.jellyfin.createApi(normalizedAddress)
                val systemInfo = client.systemApi.getPublicSystemInfo().content
                val info = ServerInfo(
                    id = systemInfo.id?.toString() ?: java.util.UUID.randomUUID().toString(),
                    name = systemInfo.serverName ?: "Jellyfin Server",
                    address = normalizedAddress,
                )
                engine.authMutex.withLock { engine.updateServer(info) }
                info
            } catch (e: Exception) {
                Log.e("JellyfinApi", "connectToServer failed for $normalizedAddress", e)
                throw e
            }
        }
    }

    override suspend fun getServerInfo(address: String): Result<ServerInfo> = runCatching {
        val normalizedAddress = address.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else "https://$it"
        }
        withContext(Dispatchers.IO) {
            val client = engine.jellyfin.createApi(normalizedAddress)
            val systemInfo = client.systemApi.getPublicSystemInfo().content
            ServerInfo(
                id = systemInfo.id?.toString() ?: java.util.UUID.randomUUID().toString(),
                name = systemInfo.serverName ?: "Jellyfin Server",
                address = normalizedAddress,
            )
        }
    }

    override suspend fun authenticateUser(
        serverAddress: String,
        username: String,
        password: String,
    ): Result<UserInfo> = authenticateUser(
        serverInfo = engine.currentServer.value ?: connectToServer(serverAddress).getOrThrow(),
        username = username,
        password = password,
    )

    override suspend fun authenticateUser(
        serverInfo: ServerInfo,
        username: String,
        password: String,
    ): Result<UserInfo> = engine.apiResultWithRetry {
        engine.authMutex.withLock { engine.updateServer(serverInfo) }
        val client = engine.jellyfin.createApi(serverInfo.address)
        val authResult = client.userApi.authenticateUserByName(
            AuthenticateUserByName(
                username = username,
                pw = password,
            )
        ).content
        val accessTokenValue = authResult.accessToken ?: throw Exception("No access token")
        val authenticatedClient = engine.jellyfin.createApi(
            baseUrl = serverInfo.address,
            accessToken = accessTokenValue,
        )
        engine.updateApi(authenticatedClient)
        val userDto = authResult.user ?: throw Exception("Authentication failed")
        val policy = userDto.policy
        val userInfo = UserInfo(
            id = userDto.id.toString(),
            name = userDto.name ?: username,
            serverAddress = serverInfo.address,
            accessToken = accessTokenValue,
            isAdmin = policy?.isAdministrator ?: false,
            canDeleteContent = policy?.enableContentDeletion ?: false,
            maxParentalAgeRating = policy?.maxParentalRating,
            primaryImageTag = userDto.primaryImageTag,
            enabledFolderIds = if (policy?.enableAllFolders == false) {
                policy.enabledFolders?.map { it.toString() } ?: emptyList()
            } else emptyList(),
        )
        engine.authMutex.withLock {
            engine.updateUser(userInfo)
            engine.updateServer(serverInfo.copy(
                userId = userInfo.id,
                accessToken = userInfo.accessToken,
                isConnected = true,
            ))
        }
        userInfo
    }

    override suspend fun setServer(serverInfo: ServerInfo) {
        engine.authMutex.withLock { engine.updateServer(serverInfo) }
    }

    override suspend fun setUser(userInfo: UserInfo) {
        engine.authMutex.withLock { engine.updateUser(userInfo) }
        val server = engine.currentServer.value ?: return
        engine.updateApi(engine.jellyfin.createApi(
            baseUrl = server.address,
            accessToken = userInfo.accessToken,
        ))
    }

    override suspend fun disconnect() {
        engine.updateApi(null)
        engine.authMutex.withLock {
            engine.updateUser(null)
            engine.updateServer(null)
        }
    }

    override suspend fun isQuickConnectEnabled(): Result<Boolean> = engine.apiResultWithRetry {
        val server = engine.currentServer.value ?: throw IllegalStateException("Not connected to server")
        val client = engine.api ?: engine.jellyfin.createApi(server.address)
        client.quickConnectApi.getQuickConnectEnabled().content
    }

    override suspend fun initiateQuickConnect(): Result<QuickConnectInfo> = engine.apiResultWithRetry {
        val server = engine.currentServer.value ?: throw IllegalStateException("Not connected to server")
        val client = engine.api ?: engine.jellyfin.createApi(server.address)
        val result = client.quickConnectApi.initiateQuickConnect().content
        QuickConnectInfo(
            secret = result.secret,
            code = result.code,
        )
    }

    override suspend fun getQuickConnectState(secret: String): Result<QuickConnectState> = engine.apiResultWithRetry {
        val server = engine.currentServer.value ?: throw IllegalStateException("Not connected to server")
        val client = engine.api ?: engine.jellyfin.createApi(server.address)
        val result = client.quickConnectApi.getQuickConnectState(secret).content
        QuickConnectState(
            authenticated = result.authenticated,
            secret = result.secret,
        )
    }

    override suspend fun authenticateWithQuickConnect(
        serverInfo: ServerInfo,
        secret: String,
    ): Result<UserInfo> = engine.apiResultWithRetry {
        engine.authMutex.withLock { engine.updateServer(serverInfo) }
        val client = engine.jellyfin.createApi(serverInfo.address)
        val authResult = client.userApi.authenticateWithQuickConnect(
            QuickConnectDto(secret = secret)
        ).content
        val accessTokenValue = authResult.accessToken ?: throw Exception("No access token")
        val authenticatedClient = engine.jellyfin.createApi(
            baseUrl = serverInfo.address,
            accessToken = accessTokenValue,
        )
        engine.updateApi(authenticatedClient)
        val userDto = authResult.user ?: throw Exception("Quick Connect authentication failed")
        val policy = userDto.policy
        val userInfo = UserInfo(
            id = userDto.id.toString(),
            name = userDto.name ?: "",
            serverAddress = serverInfo.address,
            accessToken = accessTokenValue,
            isAdmin = policy?.isAdministrator ?: false,
            maxParentalAgeRating = policy?.maxParentalRating,
            primaryImageTag = userDto.primaryImageTag,
            enabledFolderIds = if (policy?.enableAllFolders == false) {
                policy.enabledFolders?.map { it.toString() } ?: emptyList()
            } else emptyList(),
        )
        engine.authMutex.withLock {
            engine.updateUser(userInfo)
            engine.updateServer(serverInfo.copy(
                userId = userInfo.id,
                accessToken = userInfo.accessToken,
                isConnected = true,
            ))
        }
        userInfo
    }

    override suspend fun postCapabilities(): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().sessionApi.postFullCapabilities(data = engine.cachedCapabilities)
    }

    override suspend fun revokeServerSession(): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().request(
            method = HttpMethod.POST,
            pathTemplate = "Sessions/Logout",
        )
    }

    override suspend fun authorizeQuickConnect(code: String): Result<Boolean> = engine.apiResultWithRetry {
        engine.requireApi().quickConnectApi.authorizeQuickConnect(code = code).content
    }

    override fun getServerUrl(): String? = engine.currentServer.value?.address

    override fun getAccessToken(): String? = engine.currentUser.value?.accessToken
}
