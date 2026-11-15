package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.RetryPolicy
import com.raulshma.jellyplay.core.network.auth.AuthSessionStateStore
import com.raulshma.jellyplay.core.network.auth.AuthSessionStateStoreAdapter
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.model.api.ClientCapabilitiesDto
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.MediaType as SdkMediaType

class JellyfinApiEngine(
    // LazyProvider ctor params (the local seam in LazyProvider.kt — this used
    // to be dagger.Lazy, back when a Hilt graph constructed this class)
    // defer construction of both the Jellyfin SDK instance and the shared
    // OkHttpClient off the synchronous Koin graph: MainViewModel's
    // constructor chain resolves this engine on the main thread before
    // setContent, and the Jellyfin/OkHttpClient Koin definitions both do
    // real work (DataStore-backed device-id read, PackageManager binder
    // call, disk cache mkdirs). First .get() happens inside suspend
    // repository code well after ServerIdentityStore.identity (Eagerly-
    // started) has populated, so the ensureDeviceId() runBlocking fallbacks
    // in the platform network modules never fire on the main thread.
    private val jellyfinLazy: LazyProvider<Jellyfin>,
    private val okHttpClientLazy: LazyProvider<OkHttpClient>,
    private val deviceProfileProvider: DeviceProfileProvider,
    private val addressRouter: ServerAddressRouter,
) {
    val jellyfin: Jellyfin get() = jellyfinLazy.get()
    val okHttpClient: OkHttpClient get() = okHttpClientLazy.get()
    private val _currentServer = MutableStateFlow<ServerInfo?>(null)
    val currentServer: StateFlow<ServerInfo?> = _currentServer.asStateFlow()

    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    val currentUser: StateFlow<UserInfo?> = _currentUser.asStateFlow()

    /**
     * The [ActiveSession] server+user pair published as ONE atomic value.
     * `currentServer` and `currentUser` are separate StateFlows, but the
     * public write surface ([updateSession], [adoptUser]) publishes them as
     * one mutex-held step, so a `combine(currentServer, currentUser)`
     * downstream can never observe a synthetic mixed `(newServer, oldUser)`
     * intermediate — an identity that never existed. This flow is updated
     * inside the same critical sections so a session transition is always
     * observed as a single step from one stable session to the next, or
     * to/from `null`. `null` means "no fully-established identity" (either
     * side missing), matching the `if (server != null && user != null)`
     * projection consumers used to derive from the separate flows.
     */
    private val _session = MutableStateFlow<ActiveSession?>(null)
    val session: StateFlow<ActiveSession?> = _session.asStateFlow()

    /**
     * The spine's read (through the internal
     * [AuthSessionStateStoreAdapter] — see [asAuthSessionStateStore]):
     * reads the ATOMIC session value, never a combine of the two separate
     * flows.
     */
    internal fun currentSession(): ActiveSession? = session.value

    val authMutex = Mutex()

    @Volatile
    private var _api: ApiClient? = null

    // Internal — referenced only inside this
    // module (core/data reads currentServer/currentUser/okHttpClient, never the
    // raw ApiClient). The ported tests land in this module's jvmTest, where
    // internal stays visible.
    internal val api: ApiClient? get() = _api

    /**
     * The address all server traffic should use right now: the router's active
     * endpoint (primary when reachable, else an alternate), falling back to
     * the server's primary address when routing is not configured.
     */
    val activeServerAddress: String?
        get() = addressRouter.activeAddress.value ?: _currentServer.value?.address

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Rebuild the ApiClient whenever the router moves to another endpoint
        // (failover to an alternate, or back to the primary) so SDK-generated
        // URLs — REST, images via imageApi — follow the active address too.
        engineScope.launch {
            addressRouter.activeAddress.drop(1).collect { address ->
                // Under authMutex: rebuildApiFor republishes the refreshed
                // pair, and without the lock an address failover could
                // interleave with an authMutex-guarded session write —
                // publishing a mixed (currentServer, refreshedUser) pair the
                // atomic session exists to prevent.
                if (address != null) authMutex.withLock { rebuildApiFor(address) }
            }
        }
    }

    // Internal — see the note on [api].
    internal fun requireApi(): ApiClient =
        _api ?: throw IllegalStateException("Not connected to server")

    /**
     * The authenticated user's id, throwing when no session is established
     * (message-aligned: "Not authenticated"). Reads the ATOMIC [session]
     * value, never the separate [currentUser] flow alone: a user published
     * without a server is no identity (see [publishSession]), and the
     * hand-rolled `currentUser.value?.id ?: throw` guards this replaces
     * would have treated it as one. Internal like [requireApi] — referenced
     * only inside this module, and the ported tests land in its jvmTest
     * where internal stays visible.
     */
    internal fun requireUserId(): String =
        session.value?.user?.id ?: throw IllegalStateException("Not authenticated")

    /**
     * The authenticated user's id or null — the nullable pass-through the
     * clients' optional `userId` parameters want. Same atomic [session]
     * source and rationale as [requireUserId]; re-invoked per use, so
     * freshness is the caller's. Divergence, deliberate: the realtime
     * user-data channel keeps its own per-event `currentUser.value?.id`
     * read — its guards key on the user flow the websocket session was
     * opened under, not on the atomic session value.
     */
    internal fun currentUserId(): String? = session.value?.user?.id

    /**
     * THE public session write: atomically adopts BOTH sides of the session
     * in ONE [authMutex] critical section, so a [session] observer can never
     * see the mixed `(newServer, oldUser)` pair that two separate writes
     * would produce, and `_api` can never lag the published session.
     *
     * The critical section runs in a fixed order:
     *
     *  1. `_api` retarget — a fresh client is built from the published pair
     *     (`server.address` + `user.accessToken`) BEFORE the pair is
     *     published, mirroring the spine's publishSide ordering
     *     ([AuthSessionCore]'s class KDoc): no observer that acts on the new
     *     session can pick up a client still speaking as the old identity.
     *     A fully-cleared publish (`null, null`) drops the client — the
     *     disconnect shape. The DESIGNED intermediates leave the client
     *     alone: `(server, null)` is the login-in-flight state (the client
     *     retargets when the user lands — via this method's rebuild, or the
     *     address collector's self-healing retarget, see [rebuildApiFor]),
     *     and `(null, user)` collapses to no identity while a live client
     *     must not die merely because its server side is momentarily gone.
     *  2. The pair write + collapsed [session] publish + address router
     *     reconfiguration ([publishSessionState]).
     *
     * The shared auth spine ([AuthSessionCore]) does NOT go through this
     * method — its choreography (capture → adopt → round-trip → publish →
     * restore, the api swap riding the side-effects hooks) needs several
     * state moves inside ONE critical section, so it composes the internal
     * [publishSessionState] primitive under the SAME [authMutex] it is
     * injected with. This method is the public shape for callers that hold
     * no lock of their own.
     */
    suspend fun updateSession(server: ServerInfo?, user: UserInfo?) {
        authMutex.withLock {
            if (server != null && user != null) {
                _api = createApiClient(server.address, user.accessToken)
            } else if (server == null && user == null) {
                _api = null
            }
            publishSessionState(server, user)
        }
    }

    /**
     * The single-side USER adopt — the named intent behind
     * `AuthApiClientImpl.setUser` (session restore, switchServer/switchUser,
     * refreshCurrentUser): the server side is ALREADY established (setUser's
     * guard fails the call without one) and the address router has already
     * selected the endpoint, so this intent must NOT re-configure the router
     * nor move the base URL off the router's active endpoint onto the
     * server's primary — hence single-side instead of [updateSession].
     *
     * Under [authMutex], in the setUser order: the client is built and
     * installed BEFORE the user publish (`currentUser` drives the
     * authenticated UI — publishing first made a user switch issue its first
     * request with the previous user's client). A publish without a
     * configured server is a no-op, exactly as setUser's guard always did.
     */
    suspend fun adoptUser(user: UserInfo) {
        authMutex.withLock {
            val server = _currentServer.value ?: return
            _api = createApiClient(activeServerAddress ?: server.address, user.accessToken)
            _currentUser.value = user
            collapseSession(_currentServer.value, user)
        }
    }

    /**
     * The ONE ApiClient-construction seam (updateSession's rebuild,
     * adoptUser, rebuildApiFor, and the auth client's round-trip legs all
     * build clients through here). Arguments are passed EXPLICITLY —
     * clientInfo/deviceInfo resolved from the Jellyfin instance's options
     * and a fresh default [HttpClientOptions] — instead of riding the SDK's
     * default-args overload: the `$default` static evaluates
     * `options.clientInfo`/`options.deviceInfo` BEFORE dispatching to the
     * (stubbed) instance method, which NPEs whenever `options` is null —
     * the asynchronous-rebuild trap the test suites mock the address router
     * flat to avoid. For a real instance this resolves to EXACTLY the
     * arguments the SDK defaults would have used (the static reads
     * `options.clientInfo`/`options.deviceInfo` and constructs a fresh
     * `HttpClientOptions()`), so production behavior is unchanged.
     */
    internal fun createApiClient(baseUrl: String, accessToken: String?): ApiClient =
        jellyfin.createApi(
            baseUrl = baseUrl,
            accessToken = accessToken,
            clientInfo = jellyfin.options.clientInfo,
            deviceInfo = jellyfin.options.deviceInfo,
            httpClientOptions = HttpClientOptions(),
        )

    /**
     * The raw session publish primitive the shared auth spine composes —
     * both side flows + the collapsed [session] publish + the address router
     * dance, in one step. Caller MUST hold [authMutex] (the mutex injected
     * into [AuthSessionCore] — the spine's critical sections are the only
     * production caller; the public [updateSession] wraps this primitive
     * with the mutex itself). Module-internal so the spine (via
     * [asAuthSessionStateStore]) and this module's tests can drive it while
     * the public surface stays updateSession/adoptUser only.
     */
    internal fun publishSessionState(server: ServerInfo?, user: UserInfo?) {
        _currentServer.value = server
        _currentUser.value = user
        collapseSession(server, user)
        if (server == null) addressRouter.clear() else addressRouter.configure(server)
    }

    /** Collapses the pair into the combined session; a missing side → null. */
    private fun collapseSession(server: ServerInfo?, user: UserInfo?) {
        _session.value = if (server != null && user != null) ActiveSession(server, user) else null
    }

    /**
     * The raw `_api` swap — single-side by nature: the client is the
     * side-channel [AuthSessionCore]'s side-effects hooks keep atomic WITH
     * the session inside their critical sections, never published
     * independently. The legitimate callers are exactly those hooks'
     * shapes (AuthApiClientImpl's port): publishSide installs the fresh
     * client before the session write, restoreSide puts the CAPTURED client
     * instance back (rebuilding would mint a different object than the
     * capture — see [AuthSessionSideEffects.restoreSide]), clearSide drops
     * it on disconnect — plus [rebuildApiFor] and the test harnesses, which
     * install recording clients. Module-internal for the same reach as
     * [publishSessionState]; NOT part of the public surface.
     */
    internal fun updateApi(api: ApiClient?) {
        _api = api
    }

    /**
     * Swaps the ApiClient's base URL to [address], keeping the current user's
     * access token (both addresses belong to the same server, so the token
     * stays valid) and mirroring the address into the published user so
     * URL-building consumers see the active endpoint. Only retargets an
     * existing client — creating one from here would race the spine's
     * publishSide/adoptUser authoritative construction. Runs under
     * [authMutex] (the collector takes the lock): it republishes the
     * combined session, and an unguarded publish could interleave with an
     * authMutex-guarded session write.
     *
     * DECLARED window: with no user yet there is no token to preserve, so
     * the early return keeps the existing client rather than nulling it — a
     * live client must not die merely because an address flap raced the
     * session seeding. Until the user lands, `_api` can therefore still
     * speak the PREVIOUS address; the window self-heals on the next address
     * change after the user lands (this rebuild), or immediately when the
     * user lands through [updateSession]/[adoptUser], both of which build
     * the client inside their critical sections.
     */
    private fun rebuildApiFor(address: String) {
        if (_api == null) return
        // No user yet → no token to preserve; keep the existing client rather
        // than nulling it. A live client must not die merely because an
        // address flap raced the session seeding (the designed (server, null)
        // intermediate) — the next address change after the user lands
        // performs the retarget.
        val user = _currentUser.value ?: return
        _api = createApiClient(address, user.accessToken)
        // Republish the pair with the mirrored address — raw writes (not the
        // public updateSession: no rebuild wanted, the client is already
        // retargeted; not the router dance: this IS the router's collector).
        _currentUser.value = user.copy(serverAddress = address)
        collapseSession(_currentServer.value, _currentUser.value)
    }

    suspend fun <T> apiResult(block: suspend () -> T): Result<T> =
        // The shared helper rethrows CancellationException before the recovery
        // mapping sees it, so structured cancellation is never masked as a
        // Result.failure. The recoverCatching's only remaining job is the typed
        // wrap: an ApiException carrying a pre-classified retryable flag. The
        // friendly message is still produced by JellyfinErrorMapper so existing
        // consumers reading `.message` see the same user-facing text.
        runCatchingRethrowingCancellation { withContext(Dispatchers.IO) { block() } }
            .recoverCatching { throw ApiException.fromJellyfin(it) }

    suspend fun <T> apiResultWithRetry(
        maxRetries: Int = RetryPolicy.DEFAULT_MAX_RETRIES,
        block: suspend () -> T,
    ): Result<T> = RetryPolicy.executeWithRetry(maxRetries = maxRetries) {
        apiResult(block).onFailure { e ->
            // A retryable failure with no HTTP status means we could not talk
            // to the server at all. Before burning a retry against the same
            // dead endpoint, re-run address selection: if the active endpoint
            // died (e.g. the user just left home), the router fails over to
            // an alternate and the retry transparently uses it. Throttled so
            // a burst of parallel failures triggers one probe round, not N.
            if (e is ApiException && e.isRetryable && e.httpCode == null && addressRouter.hasAlternates) {
                // Swallow probe errors but never cancellation — the shared
                // helper rethrows the caller's cancellation; the Result of a
                // best-effort probe is dropped.
                runCatchingRethrowingCancellation {
                    addressRouter.reselectActiveEndpoint(minIntervalMs = RESELECT_THROTTLE_MS)
                }
            }
        }
    }

    /**
     * THE way to call a Jellyfin SDK endpoint from the api clients: the
     * [requireApi] guard plus [apiResultWithRetry] (IO dispatch, typed
     * [ApiException] wrapping, retry with failover re-selection) composed in
     * one place, so a new endpoint never re-derives the guard/retry ritual
     * per method. The guard resolves exactly where the call sites it replaces
     * resolved it — inside the retry block — so a disconnected call fails
     * with the same classified [ApiException] as before, and a non-retryable
     * one still short-circuits without burning retries. The block receives
     * the guarded [ApiClient] and returns its raw value; `.content`
     * unwrapping and DTO-to-model mapping stay in the block so each endpoint
     * keeps its exact shape. Direct [apiResultWithRetry] / [requireApi] calls
     * remain only for deliberately irregular flows: bespoke retry counts,
     * raw-requester paths, fresh-client login/quick-connect legs, and
     * non-Result best-effort fetches.
     */
    suspend fun <T> withApi(
        maxRetries: Int = RetryPolicy.DEFAULT_MAX_RETRIES,
        block: suspend (ApiClient) -> T,
    ): Result<T> = apiResultWithRetry(maxRetries) { block(requireApi()) }

    val currentMaxParentalRating: Int?
        get() = _currentUser.value?.maxParentalAgeRating

    val cachedCapabilities by lazy {
        ClientCapabilitiesDto(
            playableMediaTypes = listOf(SdkMediaType.VIDEO, SdkMediaType.AUDIO),
            supportedCommands = SUPPORTED_REMOTE_COMMANDS,
            supportsMediaControl = true,
            supportsPersistentIdentifier = true,
            deviceProfile = deviceProfileProvider.default,
        )
    }

    companion object {
        val sharedJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        /** Minimum spacing between failure-triggered address re-selections. */
        private const val RESELECT_THROTTLE_MS = 5_000L

        // The command vocabulary this client advertises to the server —
        // server-side remote UIs (jellyfin-web's d-pad, the official apps'
        // remote sheet) render exactly these. MUST stay in lockstep with the
        // serial-name mirror in core/network's AuthWireDto
        // (SUPPORTED_REMOTE_COMMANDS) — a jvmTest pins the two sets equal.
        // Internal (not private) so that mirror test can read it.
        internal val SUPPORTED_REMOTE_COMMANDS = listOf(
            GeneralCommandType.SET_VOLUME,
            GeneralCommandType.VOLUME_UP,
            GeneralCommandType.VOLUME_DOWN,
            GeneralCommandType.MUTE,
            GeneralCommandType.UNMUTE,
            GeneralCommandType.TOGGLE_MUTE,
            GeneralCommandType.SET_AUDIO_STREAM_INDEX,
            GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
            GeneralCommandType.SET_REPEAT_MODE,
            GeneralCommandType.SET_SHUFFLE_QUEUE,
            GeneralCommandType.SET_PLAYBACK_ORDER,
            GeneralCommandType.SET_MAX_STREAMING_BITRATE,
            GeneralCommandType.TOGGLE_FULLSCREEN,
            GeneralCommandType.DISPLAY_MESSAGE,
            GeneralCommandType.PLAY,
            // Navigation ladder + DisplayContent + TakeScreenshot.
            GeneralCommandType.BACK,
            GeneralCommandType.SELECT,
            GeneralCommandType.MOVE_UP,
            GeneralCommandType.MOVE_DOWN,
            GeneralCommandType.MOVE_LEFT,
            GeneralCommandType.MOVE_RIGHT,
            GeneralCommandType.GO_HOME,
            GeneralCommandType.GO_TO_SETTINGS,
            GeneralCommandType.GO_TO_SEARCH,
            GeneralCommandType.TOGGLE_CONTEXT_MENU,
            GeneralCommandType.DISPLAY_CONTENT,
            GeneralCommandType.TAKE_SCREENSHOT,
        )
    }
}

/**
 * The current cache identity from ONE atomic session snapshot — never two
 * separate StateFlow reads, which could observe a synthetic
 * (newServer, oldUser) pair mid-switch. [CacheIdentity.UNKNOWN] before login
 * never leaks into another user's entry: a wrong identity is a guaranteed
 * miss. Internal, module-scoped: the library client's home caches and the
 * user-data write family's favorite-flag cache both key off it.
 */
internal fun JellyfinApiEngine.currentCacheIdentity(): CacheIdentity {
    val session = session.value
    return CacheIdentity.ofOrNull(session?.server?.id, session?.user?.id)
}

/**
 * The [AuthSessionStateStore] port over the engine's internal read +
 * publish pair — how the auth clients' injection site connects the engine
 * to the commonMain spine ([AuthSessionCore]) without this public class
 * carrying an internal supertype (the public class cannot implement the
 * module-private interface; see [AuthSessionStateStore]).
 */
internal fun JellyfinApiEngine.asAuthSessionStateStore(): AuthSessionStateStore =
    AuthSessionStateStoreAdapter(
        read = { currentSession() },
        publish = { server, user -> publishSessionState(server, user) },
    )
