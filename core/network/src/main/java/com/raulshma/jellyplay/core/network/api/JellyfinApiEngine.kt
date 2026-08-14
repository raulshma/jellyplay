package com.raulshma.jellyplay.core.network.api

import android.content.Context
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.RetryPolicy
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import dagger.hilt.android.qualifiers.ApplicationContext
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
import org.jellyfin.sdk.model.api.ClientCapabilitiesDto
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.MediaType as SdkMediaType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinApiEngine @Inject constructor(
    @ApplicationContext val context: Context,
    val jellyfin: Jellyfin,
    val okHttpClient: OkHttpClient,
    private val deviceProfileProvider: DeviceProfileProvider,
    private val addressRouter: ServerAddressRouter,
) {
    private val _currentServer = MutableStateFlow<ServerInfo?>(null)
    val currentServer: StateFlow<ServerInfo?> = _currentServer.asStateFlow()

    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    val currentUser: StateFlow<UserInfo?> = _currentUser.asStateFlow()

    val authMutex = Mutex()

    @Volatile
    private var _api: ApiClient? = null
    val api: ApiClient? get() = _api

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
                if (address != null) rebuildApiFor(address)
            }
        }
    }

    fun requireApi(): ApiClient =
        _api ?: throw IllegalStateException("Not connected to server")

    fun updateServer(server: ServerInfo?) {
        _currentServer.value = server
        if (server == null) addressRouter.clear() else addressRouter.configure(server)
    }

    fun updateUser(user: UserInfo?) {
        _currentUser.value = user
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
     * authoritative construction.
     */
    private fun rebuildApiFor(address: String) {
        if (_api == null) return
        val user = _currentUser.value
        _api = user?.let { jellyfin.createApi(baseUrl = address, accessToken = it.accessToken) }
        if (user != null) {
            _currentUser.value = user.copy(serverAddress = address)
        }
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

    fun ratingToAge(rating: String): Int? = when (rating.uppercase()) {
        "G", "TV-Y", "TV-G" -> 0
        "PG", "TV-Y7", "TV-PG" -> 7
        "PG-13", "TV-14" -> 13
        "R", "TV-MA" -> 17
        "NC-17" -> 18
        else -> null
    }

    fun <T : com.raulshma.jellyplay.core.model.MediaItem> List<T>.filterByParentalRating(): List<T> {
        val max = currentMaxParentalRating ?: return this
        return mapNotNull { item ->
            if (item.officialRating?.let { rating ->
                    ratingToAge(rating)?.let { age -> age <= max }
                } != false) item else null
        }
    }

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
