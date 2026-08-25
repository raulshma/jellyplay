package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.NetworkLog
import com.raulshma.jellyplay.core.network.RetryPolicy
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.network.auth.AuthenticateByNameRequestDto
import com.raulshma.jellyplay.core.network.auth.AuthenticationResultDto
import com.raulshma.jellyplay.core.network.auth.PublicSystemInfoDto
import com.raulshma.jellyplay.core.network.auth.QuickConnectAuthRequestDto
import com.raulshma.jellyplay.core.network.auth.QuickConnectResultDto
import com.raulshma.jellyplay.core.network.auth.defaultClientCapabilities
import com.raulshma.jellyplay.core.network.auth.toServerInfo
import com.raulshma.jellyplay.core.network.auth.toUserInfo
import com.raulshma.jellyplay.core.network.randomUuidV4
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Phase W chunk 1: the wasmJs [AuthApiClient] — a hand-rolled Ktor
 * replacement for the jvmShared `AuthApiClientImpl` (Jellyfin SDK + OkHttp),
 * mirroring its session discipline and wire behavior.
 *
 * Session semantics are the ones `JellyfinApiEngine`/
 * `AuthApiClientImpl` establish, carried over verbatim where the platforms
 * allow:
 *  - ONE atomic [ActiveSession] publish per critical section (login /
 *    switchUser / disconnect); a missing side collapses to null. Never a
 *    synthetic `(newServer, oldUser)` intermediate.
 *  - The capture/adopt/try/publish/restore spine of `atomicLogin`: pre-auth
 *    adopt only when signed out, the round-trip runs unlocked, success
 *    republishes atomically, failure restores the captured session only if
 *    nothing newer published in between (under [authMutex], inside
 *    NonCancellable).
 *
 * Since chunk 2 the request plumbing (URL join, Authorization header, JSON
 * helpers, error mapping, apiResult/apiResultWithRetry) lives in the shared
 * [WasmApiSupport] base together with the library/playback clients, and the
 * session state is EXTERNALLY shareable: the auth client publishes into the
 * [AtomicSessionState] the DI module hands to every wasm API client, which
 * is how they derive per-request base URL + token without their own SDK
 * client to swap on auth transitions.
 *
 * wasm v1 deltas vs the JVM impl (all documented, none affect JVM):
 *  - No failover router: `getServerUrl` returns the server's primary address;
 *    `selectReachableAddress` probes primary then alternates sequentially and
 *    falls back to the primary when nothing answers (the router's concurrent
 *    probe/failover machinery is OkHttp-bound jvmShared code).
 *  - No API-client object to swap on auth transitions — requests derive the
 *    base URL + token from the session state per call, so `setUser`'s
 *    build-client-before-publish ordering collapses to publish-inside-lock.
 *  - The device id is random PER BOOT (no persisted identity on wasm v1);
 *    the server will list each browser session as a new device until Phase W
 *    persistence lands.
 *  - postCapabilities omits the DeviceProfile (no codec profile on wasm yet
 *    — HtmlVideoEngine lands in a later Phase W chunk).
 *  - No Dispatchers.IO hop (no such dispatcher on wasm; the fetch engine is
 *    non-blocking).
 */
class KtorWasmAuthApiClient(
    /** App API client ([com.raulshma.jellyplay.core.network.createWasmHttpClient]). */
    httpClient: HttpClient,
    /**
     * Dedicated short-timeout probe client
     * ([com.raulshma.jellyplay.core.network.createWasmProbeHttpClient]) —
     * mirrors the jvmShared ServerAddressRouter's probe client rationale.
     */
    private val probeHttpClient: HttpClient,
    identity: WasmClientIdentity,
    /**
     * Session state shared with the other wasm API clients (chunk 2);
     * defaults to a private instance so standalone construction keeps the
     * chunk-1 behavior. Accessible to this class via the inherited protected
     * `sessionState` from [WasmApiSupport].
     */
    sharedSessionState: AtomicSessionState = AtomicSessionState(),
) : WasmApiSupport(httpClient, sharedSessionState, identity), AuthApiClient {

    private val authMutex = Mutex()

    override val currentServer: Flow<ServerInfo?> get() = sessionState.currentServer
    override val currentUser: Flow<UserInfo?> get() = sessionState.currentUser
    override val session: Flow<ActiveSession?> get() = sessionState.session

    // ── Probe / discovery ─────────────────────────────────────────────────

    /**
     * Outcome of a reachability probe against one address, mirroring
     * `ServerAddressRouter.AddressProbeResult` (minus the latency the JVM
     * router tracks for its own scoring). Any HTTP response — including a
     * non-2xx — means reachable; only transport failures mean unreachable.
     */
    private data class ProbeResult(
        val reachable: Boolean,
        val serverId: String? = null,
        val serverName: String? = null,
        val error: Exception? = null,
    )

    private suspend fun probeHttp(address: String): ProbeResult = try {
        val response: HttpResponse = probeHttpClient.get("$address/System/Info/Public")
        val bodyText = if (response.status.isSuccess()) response.bodyAsText() else null
        val dto = bodyText?.let {
            runCatching { wireJson.decodeFromString<PublicSystemInfoDto>(it) }.getOrNull()
        }
        ProbeResult(reachable = true, serverId = dto?.id, serverName = dto?.serverName)
    } catch (e: CancellationException) {
        // Caller cancellation must keep propagating through the retry path —
        // never classified as an unreachable endpoint.
        throw e
    } catch (e: Exception) {
        ProbeResult(reachable = false, error = e)
    }

    /**
     * Probes exactly [address]. Semantics mirror
     * `AuthApiClientImpl.probeServerInfo`: unreachable → throw the transport
     * error (retryable via the wasm classifier; a synthetic retryable
     * ApiException when there is no cause), reachable → [ServerInfo] with the
     * public id/name or their fallbacks.
     */
    private suspend fun probeServerInfo(address: String): ServerInfo {
        val probe = probeHttp(address)
        if (!probe.reachable) {
            throw probe.error ?: ApiException(
                isRetryable = true,
                message = "Server at $address is unreachable",
            )
        }
        return PublicSystemInfoDto(id = probe.serverId, serverName = probe.serverName)
            .toServerInfo(address = address, fallbackServerId = randomUuidV4())
    }

    /** Address normalization, verbatim from `AuthApiClientImpl`. */
    private fun normalizeAddress(address: String): String = address.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it
        else "https://$it"
    }

    override suspend fun connectToServer(address: String): Result<ServerInfo> {
        val normalizedAddress = normalizeAddress(address)
        // Same RetryPolicy wrap (max 2 retries) the JVM discovery path uses —
        // one call with backoff instead of re-taps each firing fresh probes.
        return RetryPolicy.executeWithRetry(maxRetries = 2) {
            runCatching {
                try {
                    val info = probeServerInfo(normalizedAddress)
                    // Atomically adopt the probed server AND drop any signed-in
                    // user (verbatim reason: this path is reachable while
                    // authenticated; a single-sided updateServer would publish
                    // a synthetic (newServer, oldUser) ActiveSession).
                    authMutex.withLock { sessionState.updateSession(info, null) }
                    info
                } catch (e: Exception) {
                    NetworkLog.e("JellyfinApi", "connectToServer failed for $normalizedAddress", e)
                    throw e
                }
            }
        }
    }

    override suspend fun getServerInfo(address: String): Result<ServerInfo> {
        val normalizedAddress = normalizeAddress(address)
        return runCatching { probeServerInfo(normalizedAddress) }
    }

    override suspend fun selectReachableAddress(): String? {
        val server = sessionState.currentServer.value ?: return null
        if (server.alternateAddresses.isEmpty()) return server.address
        // Mirror the router's selection order: prefer the (healthy common
        // case) primary; only when it is down try the alternates; keep the
        // primary when nothing answers. Sequential on wasm — no probe fan-out.
        // The primary is normalized like the alternates — a stored address
        // with a trailing '/' or missing scheme must not fail fetch and
        // wrongly skip to the alternates.
        val normalizedPrimary = normalizeAddress(server.address)
        if (probeHttp(normalizedPrimary).reachable) return normalizedPrimary
        for (alternate in server.alternateAddresses) {
            val normalized = normalizeAddress(alternate)
            if (probeHttp(normalized).reachable) return normalized
        }
        return normalizedPrimary
    }

    // ── Login / session management ────────────────────────────────────────

    override suspend fun authenticateUser(
        serverAddress: String,
        username: String,
        password: String,
    ): Result<UserInfo> = authenticateUser(
        serverInfo = sessionState.currentServer.value ?: connectToServer(serverAddress).getOrThrow(),
        username = username,
        password = password,
    )

    override suspend fun authenticateUser(
        serverInfo: ServerInfo,
        username: String,
        password: String,
    ): Result<UserInfo> = apiResultWithRetry {
        atomicLogin(serverInfo, fallbackName = username) {
            postForJson<AuthenticationResultDto>(
                url = apiUrl(serverInfo.address, "/Users/AuthenticateByName"),
                accessToken = null,
                bodyText = encodeBody(AuthenticateByNameRequestDto(username = username, pw = password)),
            )
        }
    }

    override suspend fun setServer(serverInfo: ServerInfo) {
        authMutex.withLock { sessionState.updateServer(serverInfo) }
    }

    override suspend fun setUser(userInfo: UserInfo) {
        authMutex.withLock {
            val server = sessionState.currentServer.value ?: return
            // JVM builds the user's API client before publishing so the first
            // authenticated request can't fire token-less; on wasm requests
            // read the token from the session per call, so publish-in-lock is
            // the whole transaction.
            sessionState.updateUser(userInfo)
        }
    }

    override suspend fun disconnect() {
        // One atomic publish of the cleared pair; everything stays inside the
        // lock for the same ordering reasons as the JVM path.
        authMutex.withLock { sessionState.updateSession(null, null) }
    }

    /**
     * Shared capture/adopt/try/publish/restore spine for both login paths,
     * mirroring `AuthApiClientImpl.atomicLogin` minus the SDK client swap
     * (none exists here). See the class KDoc for the invariant each step
     * protects; the comments there apply verbatim.
     */
    private suspend fun atomicLogin(
        serverInfo: ServerInfo,
        fallbackName: String,
        authenticate: suspend () -> AuthenticationResultDto,
    ): UserInfo {
        val previousSession = authMutex.withLock {
            val session = sessionState.session.value
            if (session == null) {
                // Signed-out path: point currentServer at the target server;
                // identity stays null → no transition fires, nothing to wipe.
                sessionState.updateSession(serverInfo, null)
            }
            session
        }
        return try {
            val authResult = authenticate()
            val accessTokenValue = authResult.accessToken ?: throw Exception("No access token")
            val userDto = authResult.user ?: throw Exception("Authentication failed")
            val userInfo = userDto.toUserInfo(
                serverAddress = serverInfo.address,
                accessToken = accessTokenValue,
                fallbackName = fallbackName,
            )
            publishAuthenticatedSession(serverInfo, userInfo)
            userInfo
        } catch (t: Throwable) {
            restoreSession(previousSession)
            throw t
        }
    }

    /**
     * Single atomic publish of the authenticated (server, user) pair — one
     * critical-section step, mirroring
     * `AuthApiClientImpl.publishAuthenticatedSession` (minus the API-client
     * re-point, which has no wasm equivalent).
     */
    private suspend fun publishAuthenticatedSession(
        serverInfo: ServerInfo,
        userInfo: UserInfo,
    ) {
        authMutex.withLock {
            sessionState.updateSession(
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
     * Puts a captured pre-auth session back after a failed login attempt —
     * same contract as `AuthApiClientImpl.restoreSession`: runs under
     * NonCancellable, and only when the session still equals the captured
     * value (checked under the mutex) so a concurrent publish wins.
     */
    private suspend fun restoreSession(previousSession: ActiveSession?) {
        withContext(NonCancellable) {
            authMutex.withLock {
                if (sessionState.session.value != previousSession) return@withLock
                sessionState.updateSession(previousSession?.server, previousSession?.user)
            }
        }
    }

    // ── Quick Connect ─────────────────────────────────────────────────────

    override suspend fun isQuickConnectEnabled(): Result<Boolean> = apiResultWithRetry {
        val server = requireConnectedServer()
        getJson<Boolean>(
            url = apiUrl(server.address, "/QuickConnect/Enabled"),
            accessToken = getAccessToken(),
        )
    }

    override suspend fun initiateQuickConnect(): Result<QuickConnectInfo> = apiResultWithRetry {
        val server = requireConnectedServer()
        val result = postForJson<QuickConnectResultDto>(
            url = apiUrl(server.address, "/QuickConnect/Initiate"),
            accessToken = getAccessToken(),
        )
        QuickConnectInfo(
            secret = result.secret,
            code = result.code ?: "",
        )
    }

    override suspend fun getQuickConnectState(secret: String): Result<QuickConnectState> = apiResultWithRetry {
        val server = requireConnectedServer()
        val result = getJson<QuickConnectResultDto>(
            url = apiUrl(server.address, "/QuickConnect/Connect"),
            accessToken = getAccessToken(),
            query = listOf("secret" to secret),
        )
        QuickConnectState(
            authenticated = result.authenticated,
            secret = result.secret,
        )
    }

    override suspend fun authenticateWithQuickConnect(
        serverInfo: ServerInfo,
        secret: String,
    ): Result<UserInfo> = apiResultWithRetry {
        atomicLogin(serverInfo, fallbackName = "") {
            postForJson<AuthenticationResultDto>(
                url = apiUrl(serverInfo.address, "/Users/AuthenticateWithQuickConnect"),
                accessToken = null,
                bodyText = encodeBody(QuickConnectAuthRequestDto(secret = secret)),
            )
        }
    }

    override suspend fun authorizeQuickConnect(code: String): Result<Boolean> = apiResultWithRetry {
        val server = requireConnectedServer()
        postForJson<Boolean>(
            url = apiUrl(server.address, "/QuickConnect/Authorize"),
            accessToken = getAccessToken(),
            query = listOf("code" to code),
        )
    }

    // ── Session capabilities / revocation ─────────────────────────────────

    override suspend fun postCapabilities(): Result<Unit> = apiResultWithRetry {
        val server = requireConnectedServer()
        postStatusOnly(
            // Mirror of JellyfinApiEngine.cachedCapabilities minus the
            // DeviceProfile (wasm cut — see class KDoc).
            url = apiUrl(server.address, "/Sessions/Capabilities/Full"),
            accessToken = getAccessToken(),
            bodyText = encodeBody(defaultClientCapabilities()),
        )
    }

    override suspend fun revokeServerSession(): Result<Unit> = apiResultWithRetry {
        val server = requireConnectedServer()
        postStatusOnly(
            url = apiUrl(server.address, "/Sessions/Logout"),
            accessToken = getAccessToken(),
        )
    }

    override fun getServerUrl(): String? = sessionState.currentServer.value?.address

    override fun getAccessToken(): String? = sessionState.currentUser.value?.accessToken
}
