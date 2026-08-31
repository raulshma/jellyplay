package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleLanguageCodes
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.network.NetworkLog
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.api.fromNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SubtitleProvider] for **OpenSubtitles** (`api.opensubtitles.com/api/v1`).
 *
 * Two-phase download: `POST /download {file_id}` → `{link, remaining_downloads}`
 * → `GET link` for the bytes. Search is `GET /subtitles` keyed by TMDB/IMDb id
 * (preferred) or a title `query`, with `languages` as ISO 639-2B.
 *
 * Auth is a shared app `Api-Key` header always (compiled in; never user-visible
 * — same model as the Jellyfin opensubtitles plugin), plus a **mandatory** JWT
 * bearer obtained via `POST /login` with the user's opensubtitles.com username
 * + password. The token is cached in the encrypted credential store (with an
 * expiry) and refreshed lazily on expiry or 401; a [Mutex] serializes re-login
 * so concurrent calls don't each re-authenticate.
 *
 * Rate-limited to ~1 req/s via [SubtitleRateLimiter] (OpenSubtitles enforces
 * 1 req/s and 40 req/10s/IP). Transient 429/5xx retry via [RetryPolicy] in the
 * resilient wrapper; a hard daily-quota exhaustion (`remaining_downloads == 0`
 * from the download response) surfaces as a non-retryable [ApiException] so the
 * wrapper doesn't burn retries against an immovable cap.
 *
 * See https://opensubtitles.stoplight.io/ + the Apidog guide for the spec.
 */
@Singleton
class OpenSubtitlesSubtitleProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val credentialsStore: SubtitleProviderPreferencesStore,
) : SubtitleProvider {

    override val kind: SubtitleProviderKind = SubtitleProviderKind.OPENSUBTITLES

    /**
     * Dedicated [Json] for OpenSubtitles. Deliberately does **not** set
     * `isLenient`: the shared Seerr `lenientJson` parses unquoted barewords as
     * string values, so a non-JSON 2xx body like the plain-text error
     * `Invalid API key` parsed the first token (`Invalid`, 7 chars) as a value
     * and then failed at offset 7 with the misleading "Expected EOF after
     * parsing" message. A strict parser fails at offset 0 instead, which is
     * easier to map to a clean "unexpected response" error below.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val rateLimiter = SubtitleRateLimiter(SubtitleRateLimiter.OPENSUBTITLES_MIN_INTERVAL_MS)
    private val loginMutex = Mutex()

    /**
     * Base URL. The production endpoint (`https://api.opensubtitles.com`) is
     * fixed at compile time; `internal` so unit tests can point it at a
     * MockWebServer via [setBaseUrlForTest].
     */
    internal var baseUrl: String = BASE
        private set

    /** Test-only: redirect the provider at a MockWebServer root. */
    internal fun setBaseUrlForTest(url: String) { baseUrl = url }

    override suspend fun search(
        query: SubtitleQuery,
        credentials: SubtitleProviderCredentials,
    ): Result<List<SubtitleSearchResult>> {
        val os = credentials as? SubtitleProviderCredentials.OpenSubtitles
        if (os == null || !os.isConfigured) {
            return Result.failure(ApiException(false, message = "OpenSubtitles username and password are not configured"))
        }
        return rateLimiter.acquire {
            runCatching {
                withContext(Dispatchers.IO) {
                    val urlBuilder = "$baseUrl/api/v1/subtitles".toHttpUrl().newBuilder()
                    // Collect params, then emit them in canonical (alphabetical) order.
                    // The OpenSubtitles gateway (X-OS-Rule: canonical) 301-redirects any
                    // other order to the sorted form; following that redirect is an extra
                    // round-trip and, on some ISPs, the redirected request is transparently
                    // blocked. Emitting sorted params up front (like the Jellyfin plugin's
                    // AddQueryString) avoids the redirect entirely.
                    val params = buildList {
                        query.tmdbId?.let { add("tmdb_id" to it.toString()) }
                        query.imdbId?.takeIf { it.isNotBlank() }?.let {
                            // OpenSubtitles expects the bare numeric IMDb id (strip the 'tt').
                            add("imdb_id" to it.removePrefix("tt"))
                        }
                        if (query.tmdbId == null && query.imdbId.isNullOrBlank()) {
                            query.query?.takeIf { it.isNotBlank() }?.let { add("query" to it) }
                        }
                        val langs = SubtitleLanguageCodes.join(query.languages) {
                            // OpenSubtitles' `/subtitles?languages=` filter expects ISO 639-1
                            // (2-letter, e.g. `en`) — verified against the live API: `eng`
                            // matches nothing (0 results) while `en` returns matches. This
                            // mirrors the language keys the API itself returns.
                            SubtitleLanguageCodes.toIso1(it)
                        }
                        if (langs.isNotBlank()) add("languages" to langs)
                        query.season?.let { add("season_number" to it.toString()) }
                        query.episode?.let { add("episode_number" to it.toString()) }
                        query.hearingImpaired?.let { add("hearing_impaired" to it.toString()) }
                    }.sortedBy { it.first }
                    params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

                    // Search needs only the app Api-Key (verified against the live API;
                    // the Jellyfin plugin searches with Api-Key alone). Attach a cached
                    // JWT when one is available for authenticated quotas, but never gate
                    // search behind /login — that endpoint is the flakiest (rate-limited,
                    // intermittently ISP-blocked) and login trouble must not blank results.
                    val token = cachedTokenIfAvailable()
                    val request = Request.Builder()
                        .url(urlBuilder.build())
                        .header("Api-Key", APP_API_KEY)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .apply { if (token != null) header("Authorization", "Bearer $token") }
                        .build()
                    val body = execute(request)
                    parseSearchResponse(body, query.season, query.episode)
                }
            }.recoverCatching { e ->
                throw wrapNetwork(e)
            }
        }
    }

    /**
     * Validates the user's username/password with a real `POST /login`. This is
     * the *Test* button path: [search] deliberately authenticates only with the
     * shared app `Api-Key` (never `/login` — that endpoint is the flakiest and
     * gating search on it blanked results), so a wrong password still returns
     * search results. The Test button must catch a bad password **before** it is
     * saved, so it routes here instead.
     *
     * Unlike [ensureValidToken] (used by [download]), this probes the
     * **passed-in** credentials directly: it neither reads nor persists the
     * store, because the Test button runs against unsaved form text. A 401 from
     * `/login` surfaces as a non-retryable "Invalid credentials" [ApiException];
     * network/parse errors flow through the same [wrapNetwork] path as search.
     */
    override suspend fun verifyCredentials(
        credentials: SubtitleProviderCredentials,
    ): Result<Unit> {
        val os = credentials as? SubtitleProviderCredentials.OpenSubtitles
        if (os == null || !os.isConfigured) {
            return Result.failure(
                ApiException(false, message = "OpenSubtitles username and password are not configured"),
            )
        }
        val username = os.username!!.trim()
        val password = os.password!!
        return rateLimiter.acquire {
            runCatching {
                withContext(Dispatchers.IO) {
                    // doLogin throws on a non-2xx (e.g. 401) via execute(); the
                    // resulting token is discarded — we only care that login
                    // succeeded. No store read or write happens here.
                    doLogin(username, password)
                }
            }.recoverCatching { e ->
                throw wrapNetwork(e)
            }.map { }
        }
    }

    override suspend fun download(
        result: SubtitleSearchResult,
        credentials: SubtitleProviderCredentials,
    ): Result<SubtitleFile> {
        val os = credentials as? SubtitleProviderCredentials.OpenSubtitles
        if (os == null || !os.isConfigured) {
            return Result.failure(ApiException(false, message = "OpenSubtitles username and password are not configured"))
        }
        val fileId = result.id.toLongOrNull()
        if (fileId == null) {
            return Result.failure(ApiException(false, message = "OpenSubtitles result has no file_id"))
        }
        return rateLimiter.acquire {
            runCatching {
                withContext(Dispatchers.IO) {
                    val token = ensureValidToken(os)
                    val payload = buildJsonObject { put("file_id", fileId) }
                        .toString()
                        .toRequestBody(JSON_MEDIA)
                    val downloadRequest = Request.Builder()
                        .url("$baseUrl/api/v1/download".toHttpUrl())
                        .header("Api-Key", APP_API_KEY)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer $token")
                        .post(payload)
                        .build()
                    val downloadBody = execute(downloadRequest)
                    val download = parseJsonObject(downloadBody)
                    val fileUrl = download["link"]?.jsonPrimitive?.content
                        ?: throw ApiException(false, message = "OpenSubtitles returned no download link")
                    val remaining = download["remaining"]?.jsonPrimitive?.intOrNull

                    // Hard quota exhaustion — do not let the resilient wrapper retry.
                    if (remaining == 0) {
                        throw ApiException(
                            isRetryable = false,
                            message = "OpenSubtitles daily download limit reached",
                        )
                    }

                    val fileRequest = Request.Builder().url(fileUrl.toHttpUrl()).build()
                    execute(fileRequest).let { body ->
                        val bytes = body.toByteArray()
                        SubtitleFile(
                            bytes = bytes,
                            fileName = result.fileName ?: defaultSubtitleFileName(result),
                            format = result.format?.lowercase(),
                            language = result.language,
                        )
                    }
                }
            }.recoverCatching { e ->
                throw wrapNetwork(e)
            }
        }
    }

    /**
     * Returns a valid cached JWT if one is already persisted, without forcing a
     * login. Used by [search]: search only needs the app `Api-Key` (confirmed
     * against the live API; the Jellyfin OpenSubtitles plugin searches with
     * Api-Key alone), so a missing token must NOT block search — `/login` is the
     * flakiest endpoint (rate-limited, intermittently ISP-blocked) and gating
     * search on it blanked results for users whose login momentarily failed. A
     * cached token (populated by a prior [download]'s login) is attached when
     * available for authenticated rate-limit treatment. Returns null to search
     * anonymously.
     */
    private suspend fun cachedTokenIfAvailable(): String? {
        val cached = credentialsStore.getCredentials(SubtitleProviderKind.OPENSUBTITLES)
            as? SubtitleProviderCredentials.OpenSubtitles ?: return null
        val jwt = cached.jwt ?: return null
        if (jwt.isBlank()) return null
        val now = System.currentTimeMillis()
        return if (cached.jwtExpiresAt > now + TOKEN_REFRESH_LEAD_MS) jwt else null
    }

    /**
     * Returns a non-expired JWT for the configured username/password, logging in
     * (and persisting the token) on demand. Used by [download], which (unlike
     * [search]) genuinely requires an authenticated token. Callers must have
     * already verified [SubtitleProviderCredentials.OpenSubtitles.isConfigured],
     * so this always returns a token (a missing/expired token triggers a fresh
     * login); failures from `/login` propagate as an [ApiException].
     *
     * Serialized by [loginMutex] so a burst of concurrent first-of-the-day calls
     * shares a single login rather than each firing its own.
     */
    private suspend fun ensureValidToken(creds: SubtitleProviderCredentials.OpenSubtitles): String {
        val username = creds.username?.takeIf { it.isNotBlank() }
            ?: throw ApiException(false, message = "OpenSubtitles username is not configured")
        val password = creds.password?.takeIf { it.isNotBlank() }
            ?: throw ApiException(false, message = "OpenSubtitles password is not configured")
        val now = System.currentTimeMillis()
        val cachedJwt = creds.jwt
        if (!cachedJwt.isNullOrBlank() && creds.jwtExpiresAt > now + TOKEN_REFRESH_LEAD_MS) {
            return cachedJwt
        }
        return loginMutex.withLock {
            // Re-check inside the lock: a concurrent caller may have just refreshed.
            val fresh = credentialsStore.getCredentials(SubtitleProviderKind.OPENSUBTITLES)
                as? SubtitleProviderCredentials.OpenSubtitles
            val freshJwt = fresh?.jwt
            if (fresh != null && !freshJwt.isNullOrBlank() && fresh.jwtExpiresAt > now + TOKEN_REFRESH_LEAD_MS) {
                return@withLock freshJwt
            }
            val token = doLogin(username, password)
            // Persist the refreshed token (expiry read from the JWT; fall back to 24h).
            val expiresAt = decodeJwtExpiry(token) ?: (now + DEFAULT_TOKEN_TTL_MS)
            credentialsStore.setCredentials(
                SubtitleProviderKind.OPENSUBTITLES,
                creds.copy(jwt = token, jwtExpiresAt = expiresAt),
            )
            token
        }
    }

    private suspend fun doLogin(username: String, password: String): String {
        val payload = buildJsonObject {
            put("username", username)
            put("password", password)
        }.toString().toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/login".toHttpUrl())
            .header("Api-Key", APP_API_KEY)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(payload)
            .build()
        val body = execute(request)
        val dto = parseJsonObject(body)
        return dto["token"]?.jsonPrimitive?.content
            ?: throw ApiException(false, message = "OpenSubtitles login returned no token")
    }

    /**
     * Parses the `exp` claim from a JWT without validating the signature. The
     * token is self-issued by OpenSubtitles for our own account; we only need
     * the expiry to decide when to refresh, not to trust the token's identity.
     */
    private fun decodeJwtExpiry(jwt: String): Long? = runCatching {
        val payload = jwt.split(".").getOrNull(1) ?: return null
        // JWT base64url (no padding). Pad to a multiple of 4 for the decoder;
        // java.util.Base64's URL decoder accepts the padded form (same bytes
        // android.util.Base64(URL_SAFE) produced pre-KMP).
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val decoded = Base64.getUrlDecoder().decode(padded)
        val element = json.parseToJsonElement(String(decoded)).jsonObject
        element["exp"]?.jsonPrimitive?.long?.times(1000L)
    }.getOrNull()

    /**
     * Parses an OpenSubtitles response body to a JSON object. On failure it
     * logs a trimmed copy of the raw body and throws a clean, **retryable**
     * [ApiException] — a non-JSON body is never an API-key problem, it's one of:
     *  - An ISP/network block page (e.g. an Indian court-order interstitial
     *    served in place of `api.opensubtitles.com`). The body is HTML.
     *  - A captive portal / proxy error page (also HTML).
     *  - A gateway-rendered plain-text error (e.g. "Invalid API key").
     *
     * This only runs on a 2xx response (non-2xx is classified by HTTP status in
     * [execute] via [ApiException.fromHttpResponse], where 401/403 stay
     * non-retryable). A 2xx body that isn't JSON is definitionally a transient
     * intermediary artifact — an injected block page that returns 200 + HTML is
     * intermittent (a retry usually gets the real JSON), so we mark it
     * retryable and let [RetryPolicy] retry up to 3× with backoff. Leaking the
     * raw `JsonDecodingException` ("Unexpected JSON token at offset 7: Expected
     * EOF after parsing...") to the settings Test chip is useless; we classify
     * the body and surface a truthful message instead.
     */
    private fun parseJsonObject(body: String): JsonObject =
        try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: kotlinx.serialization.SerializationException) {
            NetworkLog.w(TAG, "Unparseable OpenSubtitles response: ${body.take(500)}", e)
            throw ApiException(
                isRetryable = true,
                message = friendlyParseError(body),
                cause = e,
            )
        }

    /**
     * Maps an unparseable response body to a user-facing message. Detects HTML
     * (the signature of an injected ISP/captive-portal block page) so the user
     * isn't told to "verify your API key" when the real cause is their network
     * blocking `api.opensubtitles.com`.
     */
    private fun friendlyParseError(body: String): String {
        val head = body.take(200).trimStart()
        val looksLikeHtml = head.startsWith("<", ignoreCase = true) ||
            body.contains("<html", ignoreCase = true) ||
            body.contains("<iframe", ignoreCase = true) ||
            body.contains("<!doctype html", ignoreCase = true)
        return if (looksLikeHtml) {
            "OpenSubtitles is unreachable — your network returned a block page " +
                "instead of the API. The site may be blocked by your ISP or region; " +
                "try a different network or a VPN."
        } else {
            "OpenSubtitles returned an unexpected response. Verify your API key " +
                "and credentials."
        }
    }

    private fun parseSearchResponse(
        body: String,
        requestedSeason: Int? = null,
        requestedEpisode: Int? = null,
    ): List<SubtitleSearchResult> {
        val root = parseJsonObject(body)
        val data = root["data"]?.jsonArray ?: return emptyList()
        // De-duplicate by file_id: OpenSubtitles returns multiple file entries per
        // feature (different releases). The download handle is `files[0].file_id` —
        // it is NOT a top-level attribute (see the Jellyfin plugin's Attributes/SubFile
        // models), so reading it off `attributes` directly silently drops every row.
        val all = data.mapNotNull { element ->
            val obj = element.jsonObject
            val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null
            val firstFile = attrs["files"]?.jsonArray?.firstOrNull()?.jsonObject
            val fileId = firstFile?.get("file_id")?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val language = attrs["language"]?.jsonPrimitive?.content
            val release = attrs["release"]?.jsonPrimitive?.content
            val fileName = firstFile?.get("file_name")?.jsonPrimitive?.content
                ?: attrs["file_name"]?.jsonPrimitive?.content
            val downloads = attrs["download_count"]?.jsonPrimitive?.intOrNull
            val rating = attrs["ratings"]?.jsonPrimitive?.doubleOrNull
            val hearingImpaired = attrs["hearing_impaired"]?.let { isTruthy(it) }
            val aiTranslated = attrs["ai_translated"]?.let { isTruthy(it) }
            val machineTranslated = attrs["machine_translated"]?.let { isTruthy(it) }
            // OpenSubtitles echoes the feature metadata (including season/episode)
            // on each row via attributes.feature_details. We capture it here so the
            // TV-episode filter below can drop cross-episode rows that slip through
            // the (loose) imdb_id + season_number/episode_number server match.
            val featureDetails = attrs["feature_details"]?.jsonObject
            val resultSeason = featureDetails?.get("season_number")?.jsonPrimitive?.intOrNull
            val resultEpisode = featureDetails?.get("episode_number")?.jsonPrimitive?.intOrNull
            SubtitleSearchResult(
                provider = SubtitleProviderKind.OPENSUBTITLES,
                id = fileId.toString(),
                language = SubtitleLanguageCodes.toIso3(language),
                displayName = SubtitleLanguageCodes.displayName(language) ?: language ?: "Unknown",
                releaseName = release,
                format = inferFormat(fileName),
                isHearingImpaired = hearingImpaired ?: false,
                downloadCount = downloads,
                rating = rating,
                fileName = fileName,
                // ai/machine-translated both indicate non-human translation.
                isAiTranslated = (aiTranslated == true || machineTranslated == true).takeIf { it },
                season = resultSeason,
                episode = resultEpisode,
            )
        }.distinctBy { it.id }

        return filterByEpisode(all, requestedSeason, requestedEpisode)
    }

    /**
     * For TV episodes, OpenSubtitles' `imdb_id` + `season_number`/`episode_number`
     * server-side match is loose: it can return subtitles for sibling episodes of
     * the same season (especially when `imdb_id` resolves to the series rather
     * than the episode). Since each row echoes its true feature via
     * `feature_details.season_number`/`episode_number`, we re-check client-side
     * and drop mismatches — but only when at least one row matches, so a sparse
     * response (no per-row episode metadata) falls back to the full list rather
     * than showing an empty sheet. See issue #121.
     */
    private fun filterByEpisode(
        results: List<SubtitleSearchResult>,
        requestedSeason: Int?,
        requestedEpisode: Int?,
    ): List<SubtitleSearchResult> {
        if (requestedSeason == null || requestedEpisode == null) return results
        val matching = results.filter {
            it.season == requestedSeason && it.episode == requestedEpisode
        }
        if (matching.isEmpty()) {
            if (results.any { it.season != null || it.episode != null }) {
                // Server returned episode metadata for other rows but none for the
                // requested episode — likely a wrong-episode set. Still fall back
                // to keep the sheet usable, but surface it for diagnosis.
                NetworkLog.w(
                    TAG,
                    "No rows matched S${requestedSeason}E${requestedEpisode}; " +
                        "falling back to ${results.size} unfiltered results.",
                )
            }
            return results
        }
        return matching
    }

    private fun execute(request: Request): String =
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val rawBody = runCatching { response.body?.string() }.getOrNull()
                NetworkLog.w(
                    TAG,
                    "HTTP ${response.code} for ${request.url}" +
                        (rawBody?.take(500)?.let { " body=$it" } ?: ""),
                )
                throw ApiException.fromHttpResponse(
                    response.code,
                    "OpenSubtitles HTTP ${response.code}",
                    response.header("Retry-After"),
                )
            }
            response.body?.string() ?: throw IOException("Empty OpenSubtitles response")
        }

    private fun wrapNetwork(e: Throwable): ApiException {
        if (e is ApiException) return e
        if (e is kotlinx.coroutines.CancellationException) throw e
        val friendly = when (e) {
            is java.net.UnknownHostException -> "Unable to reach OpenSubtitles. Check your connection."
            is java.net.SocketTimeoutException -> "OpenSubtitles request timed out."
            // Raw serialization failures (e.g. a non-JSON 2xx body) should never
            // leak "Unexpected JSON token at offset N" to the user — reword them.
            is kotlinx.serialization.SerializationException ->
                "OpenSubtitles returned an unexpected response. Verify your API key and credentials."
            else -> e.message ?: "OpenSubtitles request failed"
        }
        return ApiException.fromNetwork(e, friendly)
    }

    private fun inferFormat(fileName: String?): String? {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        return when (ext) {
            "srt", "subrip" -> "srt"
            "ass", "ssa" -> "ass"
            "vtt" -> "vtt"
            "ttml", "dfxp" -> "ttml"
            "txt" -> "srt"
            else -> null
        }
    }

    private fun isTruthy(element: JsonElement): Boolean =
        (element as? JsonPrimitive)?.content?.let { it == "1" || it.equals("true", ignoreCase = true) } ?: false

    companion object {
        private const val BASE = "https://api.opensubtitles.com"
        private const val USER_AGENT = "JellyPlay"

        /**
         * Shared application `Api-Key`, sent on every OpenSubtitles request. This
         * is the same registered consumer key the Jellyfin opensubtitles plugin
         * ships (see `scratch/jellyfin-plugin-opensubtitles`), used so that users
         * authenticate with their opensubtitles.com username/password only — they
         * never see or manage an API key, matching the plugin UX. A dedicated
         * JellyPlay consumer key would be the cleaner long-term option.
         *
         * `internal` so unit tests can assert the header value without exposing
         * the key outside the network module.
         */
        internal const val APP_API_KEY = "gUCLWGoAg2PmyseoTM0INFFVPcDCeDlT"

        private const val DEFAULT_TOKEN_TTL_MS = 24L * 60 * 60 * 1000
        private const val TOKEN_REFRESH_LEAD_MS = 5 * 60 * 1000L
        private const val TAG = "OpenSubs"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
