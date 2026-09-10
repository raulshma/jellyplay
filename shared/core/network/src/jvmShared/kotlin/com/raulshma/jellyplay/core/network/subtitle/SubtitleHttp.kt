package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.network.NetworkLog
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.api.fromNetwork
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The shared OkHttp chassis behind [OpenSubtitlesSubtitleProvider] and
 * [WyzieSubtitleProvider] — the `execute`/`wrapNetwork` pair both providers
 * used to hand-copy:
 *
 *  - **[execute]** — OkHttp `execute().use`, non-2xx → bounded `take(500)`
 *    body log → [ApiException.fromHttpResponse] carrying the
 *    "`<service> HTTP <code>`" message and the `Retry-After` header;
 *  - **[wrapNetwork]** — the friendly ladder (`UnknownHostException` →
 *    "Unable to reach X. Check your connection." / `SocketTimeoutException`
 *    → "X request timed out." / else message passthrough), over the
 *    `ApiException` passthrough and `CancellationException` rethrow guards.
 *
 * The genuine per-provider divergences ride [Options] as declared flags (not
 * copy variance): only OpenSubtitles rewords raw [SerializationException]s
 * (the "Unexpected JSON token at offset N" leak guard); only Wyzie redacts
 * secrets (its API key is a URL query param, so logged URLs and exception
 * messages must be scrubbed) and captures the error `responseBody` (its
 * 400-empty-matches detection reads the captured body off the
 * [ApiException]). Wyzie also spells itself differently per surface —
 * "Wyzie" in the wire-level HTTP/empty-body messages, "Wyzie Subs" in the
 * user-facing friendly ladder — so [serviceName] is a per-call argument
 * rather than construction state.
 *
 * Internal like the providers' other shared machinery ([SubtitleRateLimiter]
 * precedent): nothing outside the network module's subtitle package should
 * grow a third copy.
 */
internal class SubtitleHttp(private val client: OkHttpClient) {

    /**
     * Declared per-provider divergences of the shared chassis. Defaults are
     * the plain-vanilla arms; each provider sets only what it genuinely does.
     */
    class Options(
        /** Tag for the non-2xx warning log. */
        val logTag: String,
        /**
         * Scrub `key=`/`api_key=`/`token=` params (and whole URLs carrying
         * them) from logged URLs and wrapped exception messages.
         */
        val redactSecrets: Boolean = false,
        /** Attach the raw non-2xx body to the [ApiException.responseBody]. */
        val captureResponseBody: Boolean = false,
        /** Add the serialization-failure reword arm to [wrapNetwork]'s ladder. */
        val rewordSerializationErrors: Boolean = false,
    )

    /**
     * Runs [request] through the shared executor. [onResponse] receives the
     * successful (2xx) response and produces the typed value — the caller
     * owns the body shape (string vs bytes vs whatever); non-2xx responses
     * are logged (bounded body preview) and mapped to a retryable-flagged
     * [ApiException] via [ApiException.fromHttpResponse].
     */
    fun <T> execute(
        request: Request,
        serviceName: String,
        opts: Options,
        onResponse: (Response) -> T,
    ): T =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // The error body explains *why* (e.g. an unmappable TMDB id), so
                // log it before discarding. Keep it bounded to avoid spamming
                // the log with a huge error page.
                val rawBody = runCatching { response.body?.string() }.getOrNull()
                val bodyPreview = rawBody?.take(500)
                NetworkLog.w(
                    opts.logTag,
                    "HTTP ${response.code} for ${loggedUrl(request, opts)}" +
                        (bodyPreview?.let { " body=$it" } ?: ""),
                )
                throw ApiException.fromHttpResponse(
                    response.code,
                    "$serviceName HTTP ${response.code}",
                    response.header("Retry-After"),
                    responseBody = if (opts.captureResponseBody) rawBody else null,
                )
            }
            onResponse(response)
        }

    /**
     * [execute] for the common string-body shape: 2xx → the body text, an
     * absent body → `IOException("Empty <serviceName> response")`.
     */
    fun executeForString(request: Request, serviceName: String, opts: Options): String =
        execute(request, serviceName, opts) { response ->
            response.body?.string() ?: throw IOException("Empty $serviceName response")
        }

    /**
     * Maps a transport-layer throwable to a user-facing [ApiException]:
     * `ApiException` passes through unchanged, `CancellationException` is
     * rethrown (the [com.raulshma.jellyplay.core.concurrency] contract), and
     * everything else rides the friendly ladder above into
     * [ApiException.fromNetwork].
     */
    fun wrapNetwork(e: Throwable, serviceName: String, opts: Options): ApiException {
        if (e is ApiException) return e
        if (e is CancellationException) throw e
        val friendly = when {
            e is UnknownHostException -> "Unable to reach $serviceName. Check your connection."
            e is SocketTimeoutException -> "$serviceName request timed out."
            // Raw serialization failures (e.g. a non-JSON 2xx body) should never
            // leak "Unexpected JSON token at offset N" to the user — reword them
            // (declared OpenSubtitles-only arm).
            opts.rewordSerializationErrors && e is SerializationException ->
                "$serviceName returned an unexpected response. Verify your API key and credentials."
            else -> {
                val message = if (opts.redactSecrets) redactSecrets(e.message) else e.message
                message ?: "$serviceName request failed"
            }
        }
        return ApiException.fromNetwork(e, friendly)
    }

    private fun loggedUrl(request: Request, opts: Options): String {
        val url = request.url.toString()
        return if (opts.redactSecrets) redactSecretsOrSelf(url) else url
    }
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
        .replace(SECRET_PARAM_REGEX, "$1<redacted>")
        .replace(SECRET_URL_REGEX, "<request url with key redacted>")
    return redacted.ifBlank { null }
}

/** [redactSecrets] for messages that are never blank (URLs): null falls back to the input. */
private fun redactSecretsOrSelf(message: String): String = redactSecrets(message) ?: message

private val SECRET_PARAM_REGEX = Regex("(?i)(\\b(?:key|api[_-]?key|token)\\s*=\\s*)[^&\\s]+")
private val SECRET_URL_REGEX = Regex("https?://[^\\s]*[?&](?:key|api[_-]?key|token)=[^&\\s]+")
