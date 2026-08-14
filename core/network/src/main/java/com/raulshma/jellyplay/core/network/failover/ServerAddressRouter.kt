package com.raulshma.jellyplay.core.network.failover

import com.raulshma.jellyplay.core.model.ServerInfo
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Outcome of a reachability probe against one server address.
 *
 * A probe is an unauthenticated `GET {address}/System/Info/Public` on a
 * dedicated [OkHttpClient] with short timeouts. Any HTTP response — including
 * a non-2xx status — means the endpoint is reachable (something answering HTTP
 * is there); only transport failures (DNS, connect, timeout) mean unreachable.
 */
data class AddressProbeResult(
    val reachable: Boolean,
    val serverId: String? = null,
    val serverName: String? = null,
    val latencyMs: Long = 0L,
    val error: Exception? = null,
)

/**
 * Single source of truth for which address of the active Jellyfin server the
 * app should talk to.
 *
 * A server has a primary address and optional alternates (Server Management →
 * "Add address"). The router keeps them in preference order — primary first —
 * and tracks the *active* address: the first endpoint known to be reachable.
 * When the primary is unreachable (e.g. the user left home and the LAN address
 * no longer routes), the active address moves to an alternate; when the
 * primary answers again, selection moves back, so the primary is always
 * preferred while available.
 *
 * Probing uses a standalone [OkHttpClient] (NOT the shared app client) for two
 * reasons:
 *  1. it never contains [ServerFailoverInterceptor], so a probe against the
 *     primary can never be silently rewritten to the active alternate — which
 *     would make every probe report the primary as healthy;
 *  2. its short timeouts (2s connect / 3s read) keep selection fast even when
 *     an address black-holes packets.
 */
@Singleton
class ServerAddressRouter @Inject constructor() {

    data class Endpoint(
        val url: HttpUrl,
        val isPrimary: Boolean,
    )

    private val reselectMutex = Mutex()

    @Volatile
    private var endpoints: List<Endpoint> = emptyList()

    @Volatile
    private var primaryUrl: HttpUrl? = null

    private val _activeAddress = MutableStateFlow<String?>(null)

    /** The address all server traffic should use right now (no trailing slash). */
    val activeAddress: StateFlow<String?> = _activeAddress.asStateFlow()

    /** Last time [reselectActiveEndpoint] actually probed, to throttle stampedes of concurrent failures. */
    @Volatile
    private var lastReselectAt: Long = 0L

    /**
     * Probe function, swappable in unit tests to avoid real network IO.
     * Production default is [probeHttp].
     */
    internal var prober: suspend (String) -> AddressProbeResult = ::probeHttp

    /**
     * Publishes the server's endpoints and resets selection. The active
     * address resets to the (new) primary whenever the primary changes — e.g.
     * the manual primary/alternate swap in Server Management — and is otherwise
     * preserved across engine state updates that keep the same endpoints (e.g.
     * auth flows re-publishing the same server with a token attached).
     */
    fun configure(server: ServerInfo) {
        val primary = server.address.toHttpUrlOrNull()
        if (primary == null) {
            clear()
            return
        }
        val alternates = server.alternateAddresses.mapNotNull { it.toHttpUrlOrNull() }
        val primaryChanged = primaryUrl != primary
        primaryUrl = primary
        endpoints = listOf(Endpoint(primary, isPrimary = true)) +
            alternates.map { Endpoint(it, isPrimary = false) }
        val activeUrl = _activeAddress.value?.toHttpUrlOrNull()
        if (primaryChanged || activeUrl == null || endpoints.none { it.url == activeUrl }) {
            _activeAddress.value = addressString(primary)
        }
    }

    /** Clears all endpoints (disconnect). */
    fun clear() {
        endpoints = emptyList()
        primaryUrl = null
        _activeAddress.value = null
    }

    /** Whether the active server has more than one address to route between. */
    val hasAlternates: Boolean
        get() = endpoints.size > 1

    /** Ordered addresses in preference order (primary first). Empty when not configured. */
    val preferenceOrder: List<String>
        get() = endpoints.map { addressString(it.url) }

    /**
     * The endpoint for [url] when it targets a known address of the active
     * server (matched on scheme + host + port), else null. Requests that
     * return null (GitHub, TMDB, Seerr, …) must never be rewritten.
     */
    fun endpointFor(url: HttpUrl): Endpoint? =
        endpoints.firstOrNull { it.url.scheme == url.scheme && it.url.host == url.host && it.url.port == url.port }

    /** Canonical no-trailing-slash address string for an endpoint. */
    fun addressString(url: HttpUrl): String {
        val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
        return "${url.scheme}://${url.host}$port"
    }

    /**
     * Rewrites [url] onto the active endpoint when it targets a *different*
     * known endpoint of the same server. Returns null when no rewrite applies
     * (url not a known endpoint, or already targeting the active one).
     */
    fun rewriteToActive(url: HttpUrl): HttpUrl? {
        val activeUrl = _activeAddress.value?.toHttpUrlOrNull() ?: return null
        val known = endpointFor(url) ?: return null
        if (known.url == activeUrl) return null
        return retarget(url, activeUrl)
    }

    /**
     * Candidate URLs (in preference order) that [ServerFailoverInterceptor]
     * should try for a request whose original URL targets [url]: the active
     * endpoint first (it is the one known to work), then every other endpoint
     * in primary-first order. Empty when the URL is not a known endpoint.
     */
    fun failoverCandidates(url: HttpUrl): List<HttpUrl> {
        if (endpoints.isEmpty()) return emptyList()
        if (endpointFor(url) == null) return emptyList()
        val activeUrl = _activeAddress.value?.toHttpUrlOrNull()
        val ordered = buildList {
            activeUrl?.let { add(it) }
            endpoints.forEach { endpoint -> if (endpoint.url != activeUrl) add(endpoint.url) }
        }
        return ordered.map { candidate -> if (candidate == url) url else retarget(url, candidate) }
    }

    /**
     * Marks [address] as the active endpoint. Called by the failover
     * interceptor after a candidate answered, making the switch sticky for
     * subsequent requests. No-op for unknown addresses.
     */
    fun markActive(address: String) {
        val url = address.toHttpUrlOrNull() ?: return
        if (endpointFor(url) == null) return
        _activeAddress.value = addressString(url)
    }

    /**
     * Re-selects the active address by probing endpoints in preference order
     * (primary first); the first reachable one becomes active. When every
     * endpoint is unreachable the current active address is kept — the app is
     * simply offline and cached content still works as before.
     *
     * The primary is probed alone first (the healthy common case costs one
     * cheap probe); only when it is down are the alternates probed
     * concurrently, so a black-holed network costs one probe window rather
     * than the sum of every endpoint's timeout.
     *
     * Returns true when the active address changed.
     *
     * @param minIntervalMs skip probing when a reselect ran within this window;
     *   a burst of failing requests would otherwise each pay a full probe
     *   round (up to ~5s per dead endpoint).
     */
    suspend fun reselectActiveEndpoint(minIntervalMs: Long = 0L): Boolean = reselectMutex.withLock {
        val list = endpoints
        if (list.isEmpty()) return false
        val now = System.currentTimeMillis()
        if (minIntervalMs > 0 && now - lastReselectAt < minIntervalMs) return false
        lastReselectAt = now

        val primary = list.first()
        val primaryAddress = addressString(primary.url)
        if (prober(primaryAddress).reachable) {
            val changed = _activeAddress.value != primaryAddress
            _activeAddress.value = primaryAddress
            return changed
        }

        val alternates = list.drop(1)
        if (alternates.isEmpty()) return false
        val results = coroutineScope {
            alternates.map { endpoint ->
                val address = addressString(endpoint.url)
                async { address to prober(address) }
            }.awaitAll()
        }
        val firstReachable = results.firstOrNull { it.second.reachable } ?: return false
        val address = firstReachable.first
        val changed = _activeAddress.value != address
        _activeAddress.value = address
        changed
    }

    /** Probes one specific address. Exposed for health checks / validation. */
    suspend fun probe(address: String): AddressProbeResult {
        val normalized = address.trim().trimEnd('/')
        return if (normalized.isEmpty()) {
            AddressProbeResult(reachable = false, error = IllegalArgumentException("Blank address"))
        } else {
            prober(normalized)
        }
    }

    private suspend fun probeHttp(address: String): AddressProbeResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        try {
            val url = ("$address/System/Info/Public").toHttpUrl()
            val request = Request.Builder().url(url).build()
            probeClient.newCall(request).execute().use { response ->
                val bodyText = if (response.isSuccessful) response.body.string() else null
                val (id, name) = bodyText?.let { parsePublicSystemInfo(it) } ?: (null to null)
                AddressProbeResult(
                    reachable = true,
                    serverId = id,
                    serverName = name,
                    latencyMs = System.currentTimeMillis() - startedAt,
                )
            }
        } catch (e: Exception) {
            AddressProbeResult(
                reachable = false,
                latencyMs = System.currentTimeMillis() - startedAt,
                error = e as? Exception ?: RuntimeException(e),
            )
        }
    }

    private fun parsePublicSystemInfo(body: String): Pair<String?, String?> = try {
        val root = json.parseToJsonElement(body).jsonObject
        val id = root["Id"]?.jsonPrimitive?.content
        val name = root["ServerName"]?.jsonPrimitive?.content
        id to name
    } catch (_: Exception) {
        null to null
    }

    private fun retarget(url: HttpUrl, endpoint: HttpUrl): HttpUrl =
        url.newBuilder()
            .scheme(endpoint.scheme)
            .host(endpoint.host)
            .port(endpoint.port)
            .build()

    private fun String.toHttpUrlOrNull(): HttpUrl? = try {
        val withScheme = if (startsWith("http://") || startsWith("https://")) this else "https://$this"
        val normalized = withScheme.trim().trimEnd('/')
        if (normalized.isBlank()) null else normalized.toHttpUrl()
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Probe client: deliberately NOT derived from the shared app client —
         * see the class KDoc. Fresh construction avoids the failover
         * interceptor (and its DI cycle) and pins short timeouts.
         */
        private val probeClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }
}
