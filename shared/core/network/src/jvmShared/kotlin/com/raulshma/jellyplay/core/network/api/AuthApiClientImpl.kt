package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.normalizeServerAddress
import com.raulshma.jellyplay.core.network.NetworkLog
import com.raulshma.jellyplay.core.network.RetryPolicy
import com.raulshma.jellyplay.core.network.auth.AuthSessionCore
import com.raulshma.jellyplay.core.network.auth.AuthSessionSideEffects
import com.raulshma.jellyplay.core.network.auth.RawLoginOutcome
import com.raulshma.jellyplay.core.network.config.isTlsTrustFailure
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
    private val addressRouter: ServerAddressRouter,
) : AuthApiClient {

    /**
     * Non-retryable probe signal: wraps a TLS-trust failure out of
     * `connectToServer`'s probe so [RetryPolicy]'s classifier (which treats
     * every IOException — SSLException included — as retryable) stops after
     * the FIRST attempt. A plain `RuntimeException` by design: not an
     * IOException, and its message matches no "HTTP ###" pattern, so no
     * classifier branch can mistake it for retryable. The original failure
     * rides along as the [cause] for upstream root-cause classifiers (the Add
     * Server trust dialog). Probe-scoped only — the global classifier
     * semantics are unchanged.
     */
    private class ProbeTlsTrustFailure(address: String, cause: Throwable) :
        RuntimeException("TLS trust failure probing $address", cause)

    override val currentServer: Flow<ServerInfo?> = engine.currentServer
    override val currentUser: Flow<UserInfo?> = engine.currentUser
    override val session: Flow<ActiveSession?> = engine.session

    /**
     * The shared auth session spine (capture/adopt/try/publish/restore,
     * connect-adopt, setServer, disconnect) — commonMain [AuthSessionCore]
     * owns the invariant KDoc. Injected with the ENGINE's mutex and state so
     * the folded paths exclude against setUser and failover's rebuildApiFor
     * exactly like the private spine they replace. The side-effects port is
     * the engine's ApiClient swap — the one platform side-channel, which
     * must stay inside the spine's critical sections (a failover's
     * mutex-held rebuildApiFor could otherwise rebuild from the old user's
     * token on top of a new session; see AuthSessionCore's class KDoc).
     */
    private val sessionCore = AuthSessionCore<ApiClient?>(
        mutex = engine.authMutex,
        state = engine,
        sideEffects = object : AuthSessionSideEffects<ApiClient?> {
            override fun captureSide(): ApiClient? = engine.api
            override fun publishSide(serverInfo: ServerInfo, userInfo: UserInfo) {
                engine.updateApi(
                    engine.jellyfin.createApi(
                        baseUrl = serverInfo.address,
                        accessToken = userInfo.accessToken,
                    )
                )
            }
            override fun restoreSide(previous: ApiClient?) = engine.updateApi(previous)
            override fun clearSide() = engine.updateApi(null)
        },
    )

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
            // A legacy /emby (or /mediabrowser) address that only answered
            // after the prefix was stripped is persisted in its resolved
            // form, so the next connect and session restore probe the
            // working address directly.
            address = probe.resolvedAddress ?: address,
        )
    }

    override suspend fun connectToServer(address: String): Result<ServerInfo> {
        val normalizedAddress = normalizeServerAddress(address)
        // Wrap the discovery call in RetryPolicy (max 2 retries) — every other
        // read path uses apiResultWithRetry, but discovery previously used bare
        // runCatching. A single transient socket timeout during server
        // discovery — exactly when users are most likely on a flaky LAN —
        // surfaced as a hard failure and prompted re-taps that each fired N
        // independent discovery HTTP calls instead of one call with backoff.
        //
        // TLS-trust failures are exempt FROM THIS PROBE ONLY (review
        // round): no retry can fix an untrusted certificate, so retrying just
        // burned 3 probe rounds before the Add Server trust dialog could
        // appear. RetryPolicy's classifier treats any IOException as
        // retryable, so the block rethrows TLS-trust failures wrapped in a
        // plain [ProbeTlsTrustFailure] (a RuntimeException — deliberately NOT
        // an IOException, and its message matches no "HTTP ###" pattern), which
        // the classifier passes through as non-retryable after exactly ONE
        // attempt. The original cause chain is preserved for upstream
        // classifiers (the Add Server dialog walks to the root cause). The
        // global classifier semantics are untouched — non-TLS network failures
        // below retry exactly as before.
        return RetryPolicy.executeWithRetry(maxRetries = 2) {
            runCatchingRethrowingCancellation {
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
                        sessionCore.adoptServer(info)
                        info
                    } catch (e: Exception) {
                        NetworkLog.e("JellyfinApi", "connectToServer failed for $normalizedAddress", e)
                        if (isTlsTrustFailure(e)) throw ProbeTlsTrustFailure(normalizedAddress, e)
                        throw e
                    }
                }
            }
        }
    }

    override suspend fun getServerInfo(address: String): Result<ServerInfo> {
        val normalizedAddress = normalizeServerAddress(address)
        return runCatchingRethrowingCancellation {
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
        // The spine (capture/adopt/round-trip/publish/restore) is
        // [AuthSessionCore.atomicLogin]'s; this lambda is the JVM wire leg —
        // the round-trip on a fresh unauthenticated client plus the SDK-DTO →
        // model mapping. The user maps only when the token survived, matching
        // the pre-fold order (token validated first, then the user).
        sessionCore.atomicLogin(serverInfo) {
            val client = engine.jellyfin.createApi(serverInfo.address)
            val authResult = client.userApi.authenticateUserByName(
                AuthenticateUserByName(
                    username = username,
                    pw = password,
                )
            ).content
            RawLoginOutcome(
                accessToken = authResult.accessToken,
                userInfo = authResult.accessToken?.let { token ->
                    authResult.user?.toUserInfo(serverInfo.address, token, fallbackName = username)
                },
            )
        }
    }

    override suspend fun setServer(serverInfo: ServerInfo) {
        sessionCore.setServer(serverInfo)
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
        // in a single step, and the engine's API-client null-out rides the
        // same lock (the failover rebuildApiFor ordering — see
        // [AuthSessionCore]'s class KDoc).
        sessionCore.disconnect()
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
        val result = client.quickConnectApi.getQuickConnectState(secret = secret).content
        QuickConnectState(
            authenticated = result.authenticated,
            secret = result.secret,
        )
    }

    override suspend fun authenticateWithQuickConnect(
        serverInfo: ServerInfo,
        secret: String,
    ): Result<UserInfo> = engine.apiResultWithRetry {
        // Same shape as authenticateUser above: the shared spine, with the
        // Quick Connect round-trip + mapping as the JVM wire leg.
        sessionCore.atomicLogin(serverInfo) {
            val client = engine.jellyfin.createApi(serverInfo.address)
            val authResult = client.userApi.authenticateWithQuickConnect(
                QuickConnectDto(secret = secret)
            ).content
            RawLoginOutcome(
                accessToken = authResult.accessToken,
                userInfo = authResult.accessToken?.let { token ->
                    authResult.user?.toUserInfo(serverInfo.address, token, fallbackName = "")
                },
            )
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
