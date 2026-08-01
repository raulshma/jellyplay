package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleLanguageCodes
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
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

    override suspend fun search(
        query: SubtitleQuery,
        credentials: SubtitleProviderCredentials,
    ): Result<List<SubtitleSearchResult>> {
        val key = (credentials as? SubtitleProviderCredentials.Wyzie)?.apiKey
        if (key.isNullOrBlank()) {
            return Result.failure(ApiException(false, message = "Wyzie API key is not configured"))
        }
        // Wyzie requires a TMDB or IMDb id; fall back to no search if neither is present
        // (a title query is not supported by the endpoint).
        val id = query.tmdbId?.toString() ?: query.imdbId?.takeIf { it.isNotBlank() }
        if (id.isNullOrBlank()) {
            return Result.success(emptyList())
        }
        return rateLimiter.acquire {
            runCatching {
                withContext(Dispatchers.IO) {
                    val urlBuilder = "https://sub.wyzie.io/search".toHttpUrl().newBuilder()
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
                    json.decodeFromString<List<WyzieSubtitleDto>>(body).map { it.toResult() }
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
                throw ApiException.fromHttpResponse(
                    response.code,
                    "Wyzie HTTP ${response.code}",
                    response.header("Retry-After"),
                )
            }
            return onResponse(response)
        }
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
}
