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
import com.raulshma.jellyplay.core.network.failover.ProbeOutcome
import com.raulshma.jellyplay.core.network.failover.probeResolvedAddress
import com.raulshma.jellyplay.core.network.failover.selectPreferredAddress
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.network.auth.AuthSessionCore
import com.raulshma.jellyplay.core.network.auth.AuthenticateByNameRequestDto
import com.raulshma.jellyplay.core.network.auth.AuthenticationResultDto
import com.raulshma.jellyplay.core.network.auth.NoOpAuthSessionSideEffects
import com.raulshma.jellyplay.core.network.auth.PublicSystemInfoDto
import com.raulshma.jellyplay.core.network.auth.QuickConnectAuthRequestDto
import com.raulshma.jellyplay.core.network.auth.QuickConnectResultDto
import com.raulshma.jellyplay.core.network.auth.RawLoginOutcome
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 *  chunk 1: the wasmJs [AuthApiClient] — a hand-rolled Ktor
 * replacement for the jvmShared `AuthApiClientImpl` (Jellyfin SDK + OkHttp),
 * mirroring its session discipline and wire behavior.
 *
 * Session semantics are the ones `JellyfinApiEngine`/
 * `AuthApiClientImpl` establish, carried over verbatim where the platforms
 * allow:
 *  - ONE atomic [ActiveSession] publish per critical section (login /
 *    switchUser / disconnect); a missing side collapses to null. Never a
 *    synthetic `(newServer, oldUser)` intermediate.
 *  - The capture/adopt/try/publish/restore spine of the login paths is the
 *    shared commonMain [AuthSessionCore] (single home of the invariant
 *    KDoc), injected with this client's [authMutex] + session state and
 *    the inert side-effects port below — the declared no-client-to-swap
 *    divergence.
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
 *    `selectReachableAddress` runs the SAME primary-then-alternates-keep-
 *    primary decision as the JVM router through the commonMain FailoverPolicy
 *    core, but probes strictly sequentially (the router's primary-alone-first
 *    + concurrent alternate fan-out is an OkHttp-bound jvmShared transport —
 *    a declared divergence; see FailoverPolicy.kt for the full list).
 *  - No API-client object to swap on auth transitions — requests derive the
 *    base URL + token from the session state per call, so `setUser`'s
 *    build-client-before-publish ordering collapses to publish-inside-lock.
 *  - The device id is random PER BOOT (no persisted identity on wasm v1);
 *    the server will list each browser session as a new device until 
 *    persistence lands.
 *  - postCapabilities omits the DeviceProfile (no codec profile on wasm yet
 *    — HtmlVideoEngine lands in a later chunk).
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

    /**
     * The shared commonMain spine (capture/adopt/try/publish/restore,
     * connect-adopt, setServer, disconnect) over THIS client's [authMutex]
     * and the shared session state — same lock, so setUser's
     * publish-in-lock still excludes against every folded path. No
     * side-effects port: wasm derives base URL + token per request from the
     * session state (the declared divergence in the class KDoc), so there
     * is no client object to swap on auth transitions.
     */
    private val sessionCore = AuthSessionCore(
        mutex = authMutex,
        state = sessionState,
        sideEffects = NoOpAuthSessionSideEffects,
    )

    override val currentServer: Flow<ServerInfo?> get() = sessionState.currentServer
    override val currentUser: Flow<UserInfo?> get() = sessionState.currentUser
    override val session: Flow<ActiveSession?> get() = sessionState.session

    // ── Probe / discovery ─────────────────────────────────────────────────

    /**
     * The Ktor transport of ONE reachability probe, producing the common
     * failover core's [ProbeOutcome] (the latency the JVM router tracks is a
     * declared JVM divergence — nothing on wasm scores it). Any HTTP response
     * — including a non-2xx — means reachable; only transport failures mean
     * unreachable. Cancellation is NOT classified: caller cancellation must
     * keep propagating through the retry path (transport-side contract — the
     * decision core never catches).
     */
    private suspend fun probeHttp(address: String): ProbeOutcome = try {
        val response: HttpResponse = probeHttpClient.get("$address/System/Info/Public")
        val bodyText = if (response.status.isSuccess()) response.bodyAsText() else null
        val dto = bodyText?.let { decodePublicInfo(it) }
        ProbeOutcome(reachable = true, serverId = dto?.id, serverName = dto?.serverName)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ProbeOutcome(reachable = false, error = e)
    }

    /**
     * Pure JSON decode guard — non-suspend on purpose (the ratchet keeps bare
     * runCatching out of suspend bodies): an unparseable public-info body
     * degrades to a reachable-without-identity outcome, same as the JVM probe.
     */
    private fun decodePublicInfo(bodyText: String): PublicSystemInfoDto? =
        runCatching { wireJson.decodeFromString<PublicSystemInfoDto>(bodyText) }.getOrNull()

    /**
     * Probes exactly [address] through the common
     * [probeResolvedAddress][com.raulshma.jellyplay.core.network.failover.probeResolvedAddress]
     * ladder — the identical normalize → probe → legacy `/emby`/`/mediabrowser`
     * strip-retry-once → adopt-the-stripped-address-only-on-a-real-identity
     * table the JVM router's `probe` runs (formerly a prose-mirrored private
     * ladder here; the mirror is the shared core now). Semantics mirror
     * `AuthApiClientImpl.probeServerInfo`: unreachable → throw the transport
     * error (retryable via the wasm classifier; a synthetic retryable
     * ApiException when there is no cause), reachable → [ServerInfo] with the
     * public id/name or their fallbacks, persisted in the RESOLVED address
     * form when the probe had to strip a legacy prefix.
     */
    private suspend fun probeServerInfo(address: String): ServerInfo {
        val probe = probeResolvedAddress(address, ::probeHttp)
        if (!probe.reachable) {
            throw probe.error ?: ApiException(
                isRetryable = true,
                message = "Server at $address is unreachable",
            )
        }
        return infoFrom(probe, probe.resolvedAddress ?: address)
    }

    private fun infoFrom(probe: ProbeOutcome, address: String): ServerInfo =
        PublicSystemInfoDto(id = probe.serverId, serverName = probe.serverName)
            .toServerInfo(address = address, fallbackServerId = randomUuidV4())

    override suspend fun connectToServer(address: String): Result<ServerInfo> {
        val normalizedAddress = normalizeServerAddress(address)
        // Same RetryPolicy wrap (max 2 retries) the JVM discovery path uses —
        // one call with backoff instead of re-taps each firing fresh probes.
        return RetryPolicy.executeWithRetry(maxRetries = 2) {
            runCatchingRethrowingCancellation {
                try {
                    val info = probeServerInfo(normalizedAddress)
                    // Atomically adopt the probed server AND drop any signed-in
                    // user (verbatim reason: this path is reachable while
                    // authenticated; a single-sided updateServer would publish
                    // a synthetic (newServer, oldUser) ActiveSession).
                    sessionCore.adoptServer(info)
                    info
                } catch (e: Exception) {
                    NetworkLog.e("JellyfinApi", "connectToServer failed for $normalizedAddress", e)
                    throw e
                }
            }
        }
    }

    override suspend fun getServerInfo(address: String): Result<ServerInfo> {
        val normalizedAddress = normalizeServerAddress(address)
        return runCatchingRethrowingCancellation { probeServerInfo(normalizedAddress) }
    }

    override suspend fun selectReachableAddress(): String? {
        val server = sessionState.currentServer.value ?: return null
        if (server.alternateAddresses.isEmpty()) return server.address
        // The common primary-then-alternates selection (prefer the healthy
        // primary; keep the primary when nothing answers) — shared with the
        // JVM router. Sequential probing on wasm is the declared divergence;
        // the primary is normalized like the alternates — a stored address
        // with a trailing '/' or missing scheme must not fail fetch and
        // wrongly skip to the alternates.
        val normalizedPrimary = normalizeServerAddress(server.address)
        return selectPreferredAddress(
            primary = normalizedPrimary,
            alternates = server.alternateAddresses.map(::normalizeServerAddress),
            probe = ::probeHttp,
        )
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
        // The spine (capture/adopt/round-trip/publish/restore) is
        // [AuthSessionCore.atomicLogin]'s; this lambda is the wasm wire leg —
        // the Ktor round-trip plus the wire-DTO → model mapping. The user
        // maps only when the token survived, matching the pre-fold order
        // (token validated first, then the user).
        sessionCore.atomicLogin(serverInfo) {
            val authResult = postForJson<AuthenticationResultDto>(
                url = apiUrl(serverInfo.address, "/Users/AuthenticateByName"),
                accessToken = null,
                bodyText = encodeBody(AuthenticateByNameRequestDto(username = username, pw = password)),
            )
            RawLoginOutcome(
                accessToken = authResult.accessToken,
                userInfo = authResult.accessToken?.let { token ->
                    authResult.user?.toUserInfo(
                        serverAddress = serverInfo.address,
                        accessToken = token,
                        fallbackName = username,
                    )
                },
            )
        }
    }

    override suspend fun setServer(serverInfo: ServerInfo) {
        sessionCore.setServer(serverInfo)
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
        // lock for the same ordering reasons as the JVM path (the shared
        // [AuthSessionCore] — here with an inert side-effects port, so the
        // whole critical section is the session write alone).
        sessionCore.disconnect()
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
        // Same shape as authenticateUser above: the shared spine, with the
        // Quick Connect round-trip + mapping as the wasm wire leg.
        sessionCore.atomicLogin(serverInfo) {
            val authResult = postForJson<AuthenticationResultDto>(
                url = apiUrl(serverInfo.address, "/Users/AuthenticateWithQuickConnect"),
                accessToken = null,
                bodyText = encodeBody(QuickConnectAuthRequestDto(secret = secret)),
            )
            RawLoginOutcome(
                accessToken = authResult.accessToken,
                userInfo = authResult.accessToken?.let { token ->
                    authResult.user?.toUserInfo(
                        serverAddress = serverInfo.address,
                        accessToken = token,
                        fallbackName = "",
                    )
                },
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
