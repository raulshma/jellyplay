package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleLanguageCodes
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import android.util.Log
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SubtitleProvider] for **Wyzie Subs** (`sub.wyzie.io`).
 *
 * Wyzie exposes a single search endpoint; the response object's `url` field is
 * the direct subtitle-file URL, so download is a plain GET of that URL — no
 * separate download handshake. Auth is the `key` query param on every request.
 *
 * See https://docs.wyzie.io/subs/usage/direct for the parameter/response spec.
 * Rate-limited via [SubtitleRateLimiter] (light burst spacing); transient
 * failures retry via [com.raulshma.jellyplay.core.network.RetryPolicy] in the
 * resilient wrapper.
 */
@Singleton
class WyzieSubtitleProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : SubtitleProvider {

    override val kind: SubtitleProviderKind = SubtitleProviderKind.WYZIE

    private val json = SeerrApiClientImpl.lenientJson
    private val rateLimiter = SubtitleRateLimiter(SubtitleRateLimiter.WYZIE_MIN_INTERVAL_MS)

    /**
     * Search base URL. The production endpoint (`https://sub.wyzie.io`) is fixed
     * at compile time; `internal` so unit tests can point it at a MockWebServer
     * via [setBaseUrlForTest].
     */
    internal var baseUrl: String = BASE
        private set

    /** Test-only: redirect the provider at a MockWebServer root. */
    internal fun setBaseUrlForTest(url: String) { baseUrl = url }

    override suspend fun search(
        query: SubtitleQuery,
        credentials: SubtitleProviderCredentials,
    ): Result<List<SubtitleSearchResult>> {
        val key = (credentials as? SubtitleProviderCredentials.Wyzie)?.apiKey
        if (key.isNullOrBlank()) {
            return Result.failure(ApiException(false, message = "Wyzie API key is not configured"))
        }
        // Wyzie requires a TMDB or IMDb id; fall back to no search if neither is present
        // (a title query is not supported by the endpoint). IMDb is preferred — it is
        // Wyzie's native key, while a TMDB id forces an internal TMDB→IMDb lookup that
        // can 400 when the id is stale or TMDB is unavailable. A non-positive TMDB id
        // (Jellyfin emits "0" for unmatched items) is rejected to avoid id=0 → 400.
        val id = query.imdbId?.takeIf { it.isNotBlank() }
            ?: query.tmdbId?.takeIf { it > 0 }?.toString()
        if (id.isNullOrBlank()) {
            Log.d(
                TAG,
                "search skipped: no usable id " +
                    "(tmdbId=${query.tmdbId}, imdbId=${query.imdbId}, " +
                    "season=${query.season}, episode=${query.episode})",
            )
            return Result.success(emptyList())
        }
        val idSource = if (query.imdbId?.takeIf { it.isNotBlank() } != null) "imdb" else "tmdb"
        Log.d(TAG, "search id=$id ($idSource) langs=${query.languages} s=${query.season} e=${query.episode}")
        return rateLimiter.acquire {
            runCatching {
                withContext(Dispatchers.IO) {
                    val urlBuilder = "$baseUrl/search".toHttpUrl().newBuilder()
                        .addQueryParameter("id", id)
                        .addQueryParameter("key", key)
                    // Wyzie uses ISO 639-1 (2-letter), comma-separated.
                    val langParam = SubtitleLanguageCodes.join(query.languages) { SubtitleLanguageCodes.toIso1(it) }
                    if (langParam.isNotBlank()) urlBuilder.addQueryParameter("language", langParam)
                    query.season?.let { urlBuilder.addQueryParameter("season", it.toString()) }
                    query.episode?.let { urlBuilder.addQueryParameter("episode", it.toString()) }
                    query.hearingImpaired?.let { urlBuilder.addQueryParameter("hi", it.toString()) }

                    val request = Request.Builder().url(urlBuilder.build()).build()
                    val body = execute(request) { response ->
                        response.body?.string() ?: throw IOException("Empty Wyzie response")
                    }
                    val parsed = json.decodeFromString<List<WyzieSubtitleDto>>(body).map { it.toResult() }
                    preferEpisodeMarker(parsed, query.season, query.episode)
                }
            }.recoverCatching { e ->
                // Wyzie signals "zero matches" as HTTP 400 with a
                // {"message":"No subtitles found"} body — not an error. Map that
                // single case to an empty success so the UI shows no results
                // instead of a misleading "Wyzie HTTP 400" error chip. Everything
                // else (network failure, genuine 4xx/5xx) flows through [wrapNetwork].
                if (isEmptyMatchesResponse(e)) {
                    Log.d(TAG, "search returned no matches (Wyzie 400 'No subtitles found') → empty success")
                    return@recoverCatching emptyList<SubtitleSearchResult>()
                }
                throw wrapNetwork(e)
            }
        }
    }

    override suspend fun download(
        result: SubtitleSearchResult,
        credentials: SubtitleProviderCredentials,
    ): Result<SubtitleFile> {
        val url = result.downloadUrl
        if (url.isNullOrBlank()) {
            return Result.failure(ApiException(false, message = "Wyzie subtitle has no download URL"))
        }
        return rateLimiter.acquire {
            runCatching {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url.toHttpUrl()).build()
                    execute(request) { response ->
                        val bytes = response.body?.bytes()
                            ?: throw IOException("Empty subtitle response from Wyzie")
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
     * Shared OkHttp executor. [onResponse] receives the successful response and
     * produces the typed value; non-2xx responses are mapped to a retryable
     * [ApiException]. Network throwables are surfaced as-is for [wrapNetwork].
     */
    private inline fun <T> execute(request: Request, onResponse: (okhttp3.Response) -> T): T {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // The error body explains *why* (e.g. an unmappable TMDB id), so
                // log it before discarding. Keep it bounded to avoid spamming
                // logcat with a huge error page, and redact the key out of the URL.
                val rawBody = runCatching { response.body?.string() }.getOrNull()
                val bodyPreview = rawBody?.take(500)
                Log.w(
                    TAG,
                    "HTTP ${response.code} for ${redactSecrets(request.url.toString())}" +
                        (bodyPreview?.let { " body=$it" } ?: ""),
                )
                throw ApiException.fromHttpResponse(
                    response.code,
                    "Wyzie HTTP ${response.code}",
                    response.header("Retry-After"),
                    responseBody = rawBody,
                )
            }
            return onResponse(response)
        }
    }

    /**
     * True when [e] is Wyzie's "no subtitles matched your query" signal. Wyzie
     * returns this as HTTP 400 with a JSON body containing
     * `"message":"No subtitles found"` — a 400 status misused to mean "empty
     * result" rather than "bad request". Matched on the response body (captured
     * on the [ApiException]) so a genuinely malformed request still surfaces as
     * an error.
     */
    private fun isEmptyMatchesResponse(e: Throwable): Boolean {
        if (e !is ApiException || e.httpCode != 400) return false
        val body = e.responseBody ?: return false
        return body.contains("No subtitles found", ignoreCase = true)
    }

    /**
     * Defensive cross-episode guard for TV queries. Wyzie's response carries no
     * echoed season/episode metadata, and its server-side match on `season`/
     * `episode` can occasionally return sibling-episode releases from the same
     * season. When [season]+[episode] are requested, prefer rows whose release
     * name or file name contains the `S{ss}E{ee}` marker; fall back to the full
     * list when no row carries the marker so the sheet is never emptier than the
     * raw server response. Companion to the OpenSubtitles client-side filter for
     * issue #121.
     */
    private fun preferEpisodeMarker(
        results: List<SubtitleSearchResult>,
        season: Int?,
        episode: Int?,
    ): List<SubtitleSearchResult> {
        if (season == null || episode == null || results.isEmpty()) return results
        val marker = "S%02dE%02d".format(season, episode)
        val matching = results.filter {
            it.releaseName?.contains(marker, ignoreCase = true) == true ||
                it.fileName?.contains(marker, ignoreCase = true) == true
        }
        if (matching.isEmpty()) {
            Log.d(
                TAG,
                "No Wyzie rows carried marker $marker; " +
                    "returning ${results.size} results unfiltered.",
            )
            return results
        }
        return matching
    }

    private fun wrapNetwork(e: Throwable): ApiException {
        if (e is ApiException) return e
        if (e is kotlinx.coroutines.CancellationException) throw e
        val friendly = when (e) {
            is java.net.UnknownHostException -> "Unable to reach Wyzie Subs. Check your connection."
            is java.net.SocketTimeoutException -> "Wyzie Subs request timed out."
            else -> redactSecrets(e.message) ?: "Wyzie Subs request failed"
        }
        return ApiException.fromNetwork(e, friendly)
    }

    /**
     * Strips secrets from a network exception message before it surfaces in the
     * UI. Wyzie carries the API key as a `key` query param on every request, and
     * OkHttp `IOException` messages frequently embed the full request URL — so an
     * unredacted message would leak the key straight into an error chip. Anything
     * that looks like a `key=`/`api_key=`/`token=` param is replaced with a
     * placeholder; if the whole message is just a URL, drop it for a generic one.
     */
    private fun redactSecrets(message: String?): String? {
        if (message.isNullOrBlank()) return null
        val redacted = message
            .replace(Regex("(?i)(\\b(?:key|api[_-]?key|token)\\s*=\\s*)[^&\\s]+"), "$1<redacted>")
            .replace(Regex("https?://[^\\s]*[?&](?:key|api[_-]?key|token)=[^&\\s]+"), "<request url with key redacted>")
        return redacted.ifBlank { null }
    }

    private fun defaultFileName(result: SubtitleSearchResult): String {
        val ext = result.format?.lowercase()?.let { if (it.isBlank()) "srt" else it } ?: "srt"
        val base = result.releaseName?.takeIf { it.isNotBlank() }?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?: "subtitle"
        return "$base.$ext"
    }

    @Serializable
    private data class WyzieSubtitleDto(
        @SerialName("id") val id: String? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("format") val format: String? = null,
        @SerialName("display") val display: String? = null,
        @SerialName("language") val language: String? = null,
        @SerialName("isHearingImpaired") val isHearingImpaired: Boolean = false,
        @SerialName("source") val source: String? = null,
        @SerialName("release") val release: String? = null,
        @SerialName("fileName") val fileName: String? = null,
        @SerialName("downloadCount") val downloadCount: Int? = null,
        @SerialName("ai") val ai: Boolean? = null,
    ) {
        fun toResult(): SubtitleSearchResult = SubtitleSearchResult(
            provider = SubtitleProviderKind.WYZIE,
            id = id ?: "",
            language = SubtitleLanguageCodes.toIso3(language),
            displayName = display ?: SubtitleLanguageCodes.displayName(language) ?: language ?: "Unknown",
            releaseName = release,
            format = format?.lowercase(),
            isHearingImpaired = isHearingImpaired,
            downloadCount = downloadCount,
            isAiTranslated = ai,
            fileName = fileName,
            downloadUrl = url,
        )
    }

    companion object {
        internal const val BASE = "https://sub.wyzie.io"
        private const val TAG = "WyzieSubs"
    }
}
