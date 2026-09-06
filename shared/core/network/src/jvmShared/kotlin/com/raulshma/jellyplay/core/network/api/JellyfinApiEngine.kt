package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.RetryPolicy
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import com.raulshma.jellyplay.core.network.library.parentalRatingAge
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
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ClientCapabilitiesDto
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.MediaType as SdkMediaType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinApiEngine @Inject constructor(
    // LazyProvider ctor params (the local seam in LazyProvider.kt, audit BIN-8 — this used
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
     * `currentServer` and `currentUser` are separate StateFlows, so any
     * caller that updates them as two assignments (login / switchUser /
     * disconnect) lets a `combine(currentServer, currentUser)` downstream
     * observe a synthetic mixed `(newServer, oldUser)` intermediate — an
     * identity that never existed. This flow is updated inside the same
     * critical sections (see [updateSession]) so a session transition is
     * always observed as a single step from one stable session to the next,
     * or to/from `null`. `null` means "no fully-established identity"
     * (either side missing), matching the `if (server != null && user != null)`
     * projection consumers used to derive from the separate flows.
     */
    private val _session = MutableStateFlow<ActiveSession?>(null)
    val session: StateFlow<ActiveSession?> = _session.asStateFlow()

    val authMutex = Mutex()

    @Volatile
    private var _api: ApiClient? = null

    // C3 note: internal since the Phase C3 audit — referenced only inside this
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
                // Under authMutex: rebuildApiFor republishes the session via
                // updateUser, and without the lock an address failover could
                // interleave with an authMutex-guarded updateSession — publishing
                // the mixed (currentServer, refreshedUser) pair the atomic
                // session exists to prevent.
                if (address != null) authMutex.withLock { rebuildApiFor(address) }
            }
        }
    }

    // C3 note: internal — see the note on [api].
    internal fun requireApi(): ApiClient =
        _api ?: throw IllegalStateException("Not connected to server")

    fun updateServer(server: ServerInfo?) {
        _currentServer.value = server
        publishSession(server, _currentUser.value)
        if (server == null) addressRouter.clear() else addressRouter.configure(server)
    }

    fun updateUser(user: UserInfo?) {
        _currentUser.value = user
        publishSession(_currentServer.value, user)
    }

    /**
     * Atomically adopts BOTH sides of the session in one critical-section
     * step, so [session] observers never see the mixed intermediate that two
     * separate [updateServer]+[updateUser] calls would produce. Callers that
     * know both sides at once (the login / switchUser / disconnect paths in
     * [AuthApiClientImpl]) must use this; [updateServer]/[updateUser] remain
     * for legitimate single-side updates and pair with the current other side
     * (e.g. `updateUser(token-refreshed-user)` re-publishes the same
     * identity).
     */
    fun updateSession(server: ServerInfo?, user: UserInfo?) {
        _currentServer.value = server
        _currentUser.value = user
        publishSession(server, user)
        if (server == null) addressRouter.clear() else addressRouter.configure(server)
    }

    /** Publishes the combined session; a missing side collapses it to null. */
    private fun publishSession(server: ServerInfo?, user: UserInfo?) {
        _session.value = if (server != null && user != null) ActiveSession(server, user) else null
    }

    fun updateApi(api: ApiClient?) {
        _api = api
    }

    /**
     * Swaps the ApiClient's base URL to [address], keeping the current user's
     * access token (both addresses belong to the same server, so the token
     * stays valid) and mirroring the address into the published user so
     * URL-building consumers see the active endpoint. Only retargets an
     * existing client — creating one from here would race setUser's
     * authoritative construction. MUST be called while holding [authMutex]:
     * it republishes the combined session, and an unguarded publish could
     * interleave with an authMutex-guarded [updateSession].
     */
    private fun rebuildApiFor(address: String) {
        if (_api == null) return
        // No user yet → no token to preserve; keep the existing client rather
        // than nulling it. A live client must not die merely because an
        // address flap raced the session seeding (updateServer → updateApi →
        // updateUser is not atomic) — the next address change after the user
        // lands performs the retarget.
        val user = _currentUser.value ?: return
        _api = jellyfin.createApi(baseUrl = address, accessToken = user.accessToken)
        // updateUser (not a raw assignment) so the combined session flow
        // republishes the pair with the mirrored address.
        updateUser(user.copy(serverAddress = address))
    }

    suspend fun <T> apiResult(block: suspend () -> T): Result<T> =
        runCatching { withContext(Dispatchers.IO) { block() } }
            .recoverCatching {
                // CancellationException must propagate so structured concurrency
                // (parent coroutine cancellation) is not masked as a Result.failure.
                // runCatching captures it (Kotlin stdlib behaviour); rethrow here before
                // wrapping into ApiException, mirroring SeerrApiClientImpl.
                if (it is kotlinx.coroutines.CancellationException) throw it
                // Wrap into a typed ApiException carrying a pre-classified retryable flag.
                // The friendly message is still produced by JellyfinErrorMapper so existing
                // consumers reading `.message` see the same user-facing text.
                throw ApiException.fromJellyfin(it)
            }

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
                // Swallow probe errors but never cancellation — the caller's
                // cancellation must keep propagating through the retry path.
                runCatching { addressRouter.reselectActiveEndpoint(minIntervalMs = RESELECT_THROTTLE_MS) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
        }
    }

    val currentMaxParentalRating: Int?
        get() = _currentUser.value?.maxParentalAgeRating

    /** Delegates to the canonical commonMain table ([parentalRatingAge]). */
    fun ratingToAge(rating: String): Int? = parentalRatingAge(rating)

    /**
     * Selector-based parental-rating filter applied on raw values (e.g. DTOs)
     * before they are mapped to [MediaItem].
     */
    fun <T> List<T>.filterByParentalRating(officialRatingOf: (T) -> String?): List<T> {
        val max = currentMaxParentalRating ?: return this
        return filter { item ->
            officialRatingOf(item)?.let { rating ->
                ratingToAge(rating)?.let { age -> age <= max }
            } != false
        }
    }

    /**
     * The standard tail of every library listing call: parental-rate the raw
     * DTOs, then map to [MediaItem] — one shared shape instead of a
     * filter+map pair repeated per call site.
     */
    fun List<BaseItemDto>.toFilteredMediaItems(): List<MediaItem> =
        filterByParentalRating { it.officialRating }.map { it.toMediaItem() }

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

        private val SUPPORTED_REMOTE_COMMANDS = listOf(
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
        )
    }
}
