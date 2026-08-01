package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleLanguageCodes
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SubtitleProvider] for **OpenSubtitles** (`api.opensubtitles.com/api/v1`).
 *
 * Two-phase download: `POST /download {file_id}` → `{link, remaining_downloads}`
 * → `GET link` for the bytes. Search is `GET /subtitles` keyed by TMDB/IMDb id
 * (preferred) or a title `query`, with `languages` as ISO 639-2B.
 *
 * Auth is an `Api-Key` header always, plus an **optional** JWT bearer obtained
 * via `POST /login` when the user supplies username + password — this raises the
 * per-day download quota. The token is cached in the encrypted credential store
 * (with an expiry) and refreshed lazily on expiry or 401; a [Mutex] serializes
 * re-login so concurrent calls don't each re-authenticate.
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

    private val json = SeerrApiClientImpl.lenientJson
    private val rateLimiter = SubtitleRateLimiter(SubtitleRateLimiter.OPENSUBTITLES_MIN_INTERVAL_MS)
    private val loginMutex = Mutex()

    override suspend fun search(
        query: SubtitleQuery,
        credentials: SubtitleProviderCredentials,
    ): Result<List<SubtitleSearchResult>> {
        val os = credentials as? SubtitleProviderCredentials.OpenSubtitles
        if (os == null || !os.isConfigured) {
            return Result.failure(ApiException(false, message = "OpenSubtitles API key is not configured"))
        }
        return rateLimiter.acquire {
            runCatching {
                withContext(Dispatchers.IO) {
                    val urlBuilder = "$BASE/api/v1/subtitles".toHttpUrl().newBuilder()
                    query.tmdbId?.let { urlBuilder.addQueryParameter("tmdb_id", it.toString()) }
                    query.imdbId?.takeIf { it.isNotBlank() }?.let {
                        // OpenSubtitles expects the bare numeric IMDb id (strip the 'tt').
                        urlBuilder.addQueryParameter("imdb_id", it.removePrefix("tt"))
                    }
                    if (query.tmdbId == null && query.imdbId.isNullOrBlank()) {
                        query.query?.takeIf { it.isNotBlank() }?.let {
                            urlBuilder.addQueryParameter("query", it)
                        }
                    }
                    val langs = SubtitleLanguageCodes.join(query.languages) { SubtitleLanguageCodes.toIso2B(it) }
                    if (langs.isNotBlank()) urlBuilder.addQueryParameter("languages", langs)
                    query.season?.let { urlBuilder.addQueryParameter("season_number", it.toString()) }
                    query.episode?.let { urlBuilder.addQueryParameter("episode_number", it.toString()) }
                    query.hearingImpaired?.let { urlBuilder.addQueryParameter("hearing_impaired", it.toString()) }

                    val token = ensureValidToken(os)
                    val request = Request.Builder()
                        .url(urlBuilder.build())
                        .header("Api-Key", os.apiKey)
                        .header("User-Agent", USER_AGENT)
                        .apply { token?.let { header("Authorization", "Bearer $it") } }
                        .build()
                    val body = execute(request)
                    parseSearchResponse(body)
                }
            }.recoverCatching { e ->
                throw wrapNetwork(e)
            }
        }
    }

    override suspend fun download(
        result: SubtitleSearchResult,
        credentials: SubtitleProviderCredentials,
    ): Result<SubtitleFile> {
        val os = credentials as? SubtitleProviderCredentials.OpenSubtitles
        if (os == null || !os.isConfigured) {
            return Result.failure(ApiException(false, message = "OpenSubtitles API key is not configured"))
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
                        .url("$BASE/api/v1/download".toHttpUrl())
                        .header("Api-Key", os.apiKey)
                        .header("User-Agent", USER_AGENT)
                        .header("Content-Type", "application/json")
                        .apply { token?.let { header("Authorization", "Bearer $it") } }
                        .post(payload)
                        .build()
                    val downloadBody = execute(downloadRequest)
                    val download = json.decodeFromString<OpenSubtitlesDownloadDto>(downloadBody)
                    val fileUrl = download.link
                        ?: throw ApiException(false, message = "OpenSubtitles returned no download link")

                    // Hard quota exhaustion — do not let the resilient wrapper retry.
                    if (download.remaining == 0) {
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
                            fileName = result.fileName ?: defaultFileName(result),
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
     * Returns a non-expired JWT if the user has configured username/password,
     * logging in (and persisting the token) on demand. Returns null when the
     * user is on the anonymous (API-key-only) tier — callers simply omit the
     * `Authorization` header.
     *
     * Serialized by [loginMutex] so a burst of concurrent first-of-the-day calls
     * shares a single login rather than each firing its own.
     */
    private suspend fun ensureValidToken(creds: SubtitleProviderCredentials.OpenSubtitles): String? {
        val username = creds.username?.takeIf { it.isNotBlank() } ?: return null
        val password = creds.password?.takeIf { it.isNotBlank() } ?: return null
        val now = System.currentTimeMillis()
        if (!creds.jwt.isNullOrBlank() && creds.jwtExpiresAt > now + TOKEN_REFRESH_LEAD_MS) {
            return creds.jwt
        }
        return loginMutex.withLock {
            // Re-check inside the lock: a concurrent caller may have just refreshed.
            val fresh = credentialsStore.getCredentials(SubtitleProviderKind.OPENSUBTITLES)
                as? SubtitleProviderCredentials.OpenSubtitles
            if (fresh != null && !fresh.jwt.isNullOrBlank() && fresh.jwtExpiresAt > now + TOKEN_REFRESH_LEAD_MS) {
                return@withLock fresh.jwt
            }
            val token = doLogin(creds.apiKey, username, password)
            // Persist the refreshed token (expiry read from the JWT; fall back to 24h).
            val expiresAt = decodeJwtExpiry(token) ?: (now + DEFAULT_TOKEN_TTL_MS)
            credentialsStore.setCredentials(
                SubtitleProviderKind.OPENSUBTITLES,
                creds.copy(jwt = token, jwtExpiresAt = expiresAt),
            )
            token
        }
    }

    private suspend fun doLogin(apiKey: String, username: String, password: String): String {
        val payload = buildJsonObject {
            put("username", username)
            put("password", password)
        }.toString().toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$BASE/api/v1/login".toHttpUrl())
            .header("Api-Key", apiKey)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .post(payload)
            .build()
        val body = execute(request)
        val dto = json.decodeFromString<OpenSubtitlesLoginDto>(body)
        return dto.token ?: throw ApiException(false, message = "OpenSubtitles login returned no token")
    }

    /**
     * Parses the `exp` claim from a JWT without validating the signature. The
     * token is self-issued by OpenSubtitles for our own account; we only need
     * the expiry to decide when to refresh, not to trust the token's identity.
     */
    private fun decodeJwtExpiry(jwt: String): Long? = runCatching {
        val payload = jwt.split(".").getOrNull(1) ?: return null
        // JWT base64url (no padding). Pad to a multiple of 4 for android.util.Base64.
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val decoded = android.util.Base64.decode(padded, android.util.Base64.URL_SAFE)
        val element = SeerrApiClientImpl.lenientJson.parseToJsonElement(String(decoded)).jsonObject
        element["exp"]?.jsonPrimitive?.long?.times(1000L)
    }.getOrNull()

    private fun parseSearchResponse(body: String): List<SubtitleSearchResult> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyList()
        // De-duplicate by file_id: OpenSubtitles returns multiple file entries per
        // feature (different releases); the attributes.file_id is the download handle.
        return data.mapNotNull { element ->
            val obj = element.jsonObject
            val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null
            val fileId = attrs["file_id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val language = attrs["language"]?.jsonPrimitive?.content
            val release = attrs["release"]?.jsonPrimitive?.content
            val fileName = attrs["file_name"]?.jsonPrimitive?.content
            val downloads = attrs["download_count"]?.jsonPrimitive?.intOrNull
            val rating = attrs["ratings"]?.jsonPrimitive?.doubleOrNull
            val hearingImpaired = attrs["hearing_impaired"]?.jsonPrimitive?.content?.let { it == "1" }
            val aiTranslated = attrs["ai_translated"]?.let { isTruthy(it) }
            val machineTranslated = attrs["machine_translated"]?.let { isTruthy(it) }
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
            )
        }.distinctBy { it.id }
    }

    private fun execute(request: Request): String =
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
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
            else -> e.message ?: "OpenSubtitles request failed"
        }
        return ApiException.fromNetwork(e, friendly)
    }

    private fun defaultFileName(result: SubtitleSearchResult): String {
        val ext = result.format?.lowercase()?.let { if (it.isBlank()) "srt" else it } ?: "srt"
        val base = result.releaseName?.takeIf { it.isNotBlank() }?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?: "subtitle"
        return "$base.$ext"
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

    @Serializable
    private data class OpenSubtitlesLoginDto(
        @SerialName("token") val token: String? = null,
    )

    @Serializable
    private data class OpenSubtitlesDownloadDto(
        @SerialName("link") val link: String? = null,
        @SerialName("file_name") val fileName: String? = null,
        @SerialName("remaining") val remaining: Int? = null,
    )

    companion object {
        private const val BASE = "https://api.opensubtitles.com"
        private const val USER_AGENT = "JellyPlay"
        private const val DEFAULT_TOKEN_TTL_MS = 24L * 60 * 60 * 1000
        private const val TOKEN_REFRESH_LEAD_MS = 5 * 60 * 1000L
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
