package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.NetworkLog
import com.raulshma.jellyplay.core.network.RetryPolicy
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
    private val addressRouter: ServerAddressRouter,
) : AuthApiClient {

    override val currentServer: Flow<ServerInfo?> = engine.currentServer
    override val currentUser: Flow<UserInfo?> = engine.currentUser
    override val session: Flow<ActiveSession?> = engine.session

    /**
     * Probes exactly [address] via the router's dedicated probe client. This
     * bypasses the failover interceptor on purpose: reachability checks must
     * test THE address, never be silently rerouted to the active alternate.
     */
    private suspend fun probeServerInfo(address: String): ServerInfo {
        val probe = addressRouter.probe(address)
        if (!probe.reachable) {
            throw probe.error ?: java.io.IOException("Server at $address is unreachable")
        }
        return ServerInfo(
            id = probe.serverId ?: java.util.UUID.randomUUID().toString(),
            name = probe.serverName ?: "Jellyfin Server",
            address = address,
        )
    }

    override suspend fun connectToServer(address: String): Result<ServerInfo> {
        val normalizedAddress = address.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else "https://$it"
        }
        // Wrap the discovery call in RetryPolicy (max 2 retries) — every other
        // read path uses apiResultWithRetry, but discovery previously used bare
        // runCatching. A single transient socket timeout during server
        // discovery — exactly when users are most likely on a flaky LAN —
        // surfaced as a hard failure and prompted re-taps that each fired N
        // independent discovery HTTP calls instead of one call with backoff.
        return RetryPolicy.executeWithRetry(maxRetries = 2) {
            runCatching {
                withContext(Dispatchers.IO) {
                    try {
                        val info = probeServerInfo(normalizedAddress)
                        // Atomically adopt the probed server AND drop any signed-in
                        // user: this path is reachable while authenticated
                        // (Settings → Server Management → Add Server), and a
                        // single-sided updateServer would publish a synthetic
                        // (newServer, oldUser) ActiveSession — driving HomeSession
                        // to emit a ServerSwitched and clear Room under the wrong
                        // identity. The follow-up login republishes a real pair.
                        engine.authMutex.withLock { engine.updateSession(info, null) }
                        info
                    } catch (e: Exception) {
                        NetworkLog.e("JellyfinApi", "connectToServer failed for $normalizedAddress", e)
                        throw e
                    }
                }
            }
        }
    }

    override suspend fun getServerInfo(address: String): Result<ServerInfo> {
        val normalizedAddress = address.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else "https://$it"
        }
        return runCatching {
            withContext(Dispatchers.IO) { probeServerInfo(normalizedAddress) }
        }
    }

    /**
     * Re-runs endpoint selection for the active server: probes the primary
     * first, then alternates, and routes all traffic to the first reachable
     * address. Callers use this at session restore so an unreachable primary
     * (user away from home) never becomes the client's base URL. Returns the
     * selected address, or null when no server is configured.
     */
    override suspend fun selectReachableAddress(): String? {
        if (!addressRouter.hasAlternates) {
            return engine.activeServerAddress
        }
        addressRouter.reselectActiveEndpoint()
        return engine.activeServerAddress
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
        atomicLogin(serverInfo, fallbackName = username) { client ->
            client.userApi.authenticateUserByName(
                AuthenticateUserByName(
                    username = username,
                    pw = password,
                )
            ).content
        }
    }

    override suspend fun setServer(serverInfo: ServerInfo) {
        engine.authMutex.withLock { engine.updateServer(serverInfo) }
    }

    override suspend fun setUser(userInfo: UserInfo) {
        engine.authMutex.withLock {
            val server = engine.currentServer.value ?: return
            // `currentUser` drives the authenticated UI (including Home's
            // initial fetch), so do not publish it until requests can use this
            // user's API client. Publishing first made a user switch issue its
            // first request with the previous user's client or no client.
            // Build against the router's active endpoint — after address
            // selection this is the reachable address, not necessarily the
            // server's primary one.
            engine.updateApi(engine.jellyfin.createApi(
                baseUrl = engine.activeServerAddress ?: server.address,
                accessToken = userInfo.accessToken,
            ))
            engine.updateUser(userInfo)
        }
    }

    override suspend fun disconnect() {
        // One atomic publish of the cleared pair (matches the atomic publish
        // discipline of the login paths) — session observers see stable → null
        // in a single step. updateApi stays INSIDE the lock: a failover's
        // rebuildApiFor (mutex-held) that already passed its `_api == null`
        // early-return could otherwise resurrect a live client on top of the
        // null-out below, leaving an authenticated client with a null session.
        engine.authMutex.withLock {
            engine.updateApi(null)
            engine.updateSession(null, null)
        }
        // No manual cross-module cache clears here: every in-memory cache in
        // this module (favorite flags, the home hot-path sub-caches) is keyed
        // by the engine's atomic session identity, so a previous identity's
        // entries miss by construction.
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
        atomicLogin(serverInfo, fallbackName = "") { client ->
            client.userApi.authenticateWithQuickConnect(
                QuickConnectDto(secret = secret)
            ).content
        }
    }

    /**
     * Shared capture/adopt/try/publish/restore spine for both login paths
     * (name/password, Quick Connect), so the session discipline can't drift
     * between them. Capture and pre-auth adopt happen in ONE critical
     * section: the capture is atomic with everything [restoreSession] might
     * undo, so a concurrent identity write between the two can't be silently
     * clobbered by the restore. The adopt runs only when NO session is
     * established — adopting over a working session would publish
     * SignedOut(previous), and identity observers react destructively to that
     * (cache drop + previous identity's SWR snapshot clear): a failed login
     * attempt would wipe the signed-in user's cached home. The round-trip
     * ([authenticate] lambda) runs unlocked; success replaces the pair
     * atomically ([publishAuthenticatedSession]), and failure restores the
     * captured values only if no other flow published in between
     * ([restoreSession] re-checks under the mutex — a concurrent publish wins
     * over the restore). RetryPolicy re-runs the caller's block against the
     * same captured state, so the capture is idempotent across attempts.
     */
    private suspend fun atomicLogin(
        serverInfo: ServerInfo,
        fallbackName: String,
        authenticate: suspend (org.jellyfin.sdk.api.client.ApiClient) -> org.jellyfin.sdk.model.api.AuthenticationResult,
    ): UserInfo {
        val (previousSession, previousApi) = engine.authMutex.withLock {
            val session = engine.session.value
            val api = engine.api
            if (session == null) {
                // Signed-out path: point currentServer at the target server.
                // Identity stays null → no transition fires, nothing to wipe;
                // the follow-up login republishes a real pair.
                engine.updateSession(serverInfo, null)
            }
            session to api
        }
        return try {
            val client = engine.jellyfin.createApi(serverInfo.address)
            val authResult = authenticate(client)
            val accessTokenValue = authResult.accessToken ?: throw Exception("No access token")
            val authenticatedClient = engine.jellyfin.createApi(
                baseUrl = serverInfo.address,
                accessToken = accessTokenValue,
            )
            val userDto = authResult.user ?: throw Exception("Authentication failed")
            val userInfo = userDto.toUserInfo(serverInfo.address, accessTokenValue, fallbackName = fallbackName)
            publishAuthenticatedSession(serverInfo, userInfo, authenticatedClient)
            userInfo
        } catch (t: Throwable) {
            restoreSession(previousSession, previousApi)
            throw t
        }
    }

    /**
     * Maps an authentication response's user DTO to the model [UserInfo].
     * Shared by every auth path (name/password, Quick Connect) so policy
     * fields can't drift between them.
     */
    private fun org.jellyfin.sdk.model.api.UserDto.toUserInfo(
        serverAddress: String,
        accessToken: String,
        fallbackName: String,
    ): UserInfo {
        val policy = policy
        return UserInfo(
            id = id.toString(),
            name = name ?: fallbackName,
            serverAddress = serverAddress,
            accessToken = accessToken,
            isAdmin = policy?.isAdministrator ?: false,
            canDeleteContent = policy?.enableContentDeletion ?: false,
            maxParentalAgeRating = policy?.maxParentalRating,
            primaryImageTag = primaryImageTag,
            enabledFolderIds = if (policy?.enableAllFolders == false) {
                policy.enabledFolders?.map { it.toString() } ?: emptyList()
            } else emptyList(),
        )
    }

    /**
     * Single atomic publish of the authenticated (server, user) pair — one
     * critical-section step, so session observers never see the server
     * connected but its user missing (or vice versa). The API client is
     * re-pointed INSIDE the same lock: a failover's rebuildApiFor (mutex-held)
     * landing between an unguarded updateApi and this publish would rebuild
     * the client from the OLD user's token while the session claims the new
     * one — requests then spoke as the wrong user until something rebuilt.
     */
    private suspend fun publishAuthenticatedSession(
        serverInfo: ServerInfo,
        userInfo: UserInfo,
        authenticatedClient: org.jellyfin.sdk.api.client.ApiClient,
    ) {
        engine.authMutex.withLock {
            engine.updateApi(authenticatedClient)
            engine.updateSession(
                serverInfo.copy(
                    userId = userInfo.id,
                    accessToken = userInfo.accessToken,
                    isConnected = true,
                ),
                userInfo,
            )
        }
    }

    /**
     * Puts a captured pre-auth session back after a failed login attempt.
     * Pairs with the pre-auth adopt in the login paths: without this, a wrong
     * password (or a declined Quick Connect) would leave the app signed out
     * and the previous identity's cached home cleared. The login round-trip
     * runs with authMutex RELEASED, so a concurrent auth flow may have
     * published its own session in the meantime — the restore only runs when
     * the session still equals the captured value (checked under the mutex);
     * otherwise the newer publish wins and both the session and the API
     * client are left untouched. Runs under
     * [kotlinx.coroutines.NonCancellable] so the restore survives caller
     * cancellation, and re-points the API client at the captured one in case
     * the failure landed after a client swap had already happened.
     */
    private suspend fun restoreSession(
        previousSession: ActiveSession?,
        previousApi: org.jellyfin.sdk.api.client.ApiClient?,
    ) {
        withContext(NonCancellable) {
            engine.authMutex.withLock {
                if (engine.session.value != previousSession) return@withLock
                engine.updateApi(previousApi)
                engine.updateSession(previousSession?.server, previousSession?.user)
            }
        }
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

    override fun getServerUrl(): String? = engine.activeServerAddress

    override fun getAccessToken(): String? = engine.currentUser.value?.accessToken
}
