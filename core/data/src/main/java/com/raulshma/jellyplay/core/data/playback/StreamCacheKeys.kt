package com.raulshma.jellyplay.core.data.playback

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Query params that key a URL to one playback session or live edge
 * (`PlaySessionId`, `TranscodingJobId`, `LiveStreamId`). URLs carrying them
 * are one-shot or growing content: byte caches must reject them outright
 * ([isSessionKeyedUrl]) instead of key-normalizing them — unlike the
 * strip-safe params in [stripVolatileQueryParams], the bytes behind these
 * differ per session, so stripping would collide distinct content onto one
 * cache key.
 */
val SESSION_SCOPED_QUERY_PARAMS: Set<String> =
    setOf("PlaySessionId", "TranscodingJobId", "LiveStreamId")

/**
 * Query parameters that are safe to strip from byte-cache keys: the bytes
 * behind the URL are identical with or without them. Currently just the
 * auth token — token rotation must not invalidate cached bytes. Anything
 * else is either content-bearing (resume offsets) or session-scoped
 * ([SESSION_SCOPED_QUERY_PARAMS], which must be rejected via
 * [isSessionKeyedUrl], never stripped).
 */
val STRIP_SAFE_QUERY_PARAMS: Set<String> = setOf("api_key")

/** True if [url] carries any [SESSION_SCOPED_QUERY_PARAMS] entry. */
fun isSessionKeyedUrl(url: String): Boolean =
    SESSION_SCOPED_QUERY_PARAMS.any { url.contains("$it=", ignoreCase = true) }

/**
 * Strips session-volatile query parameters ([params]) from [url] so byte-cache
 * keys stay stable across whatever [params] declares volatile (today: token
 * rotation, via [STRIP_SAFE_QUERY_PARAMS]). Shared by the audio and video
 * stream caches.
 *
 * Primary path rewrites the query via [URI]; the rare URI-parse failure falls
 * back to a regex strip built from [params]. The fallback regex is memoized
 * per param set — only the malformed-URL path ever compiles one, and each
 * call site passes a fixed set.
 */
fun stripVolatileQueryParams(url: String, params: Set<String>): String {
    return try {
        val uri = URI(url)
        val query = uri.rawQuery ?: return url
        val filtered = query.split("&")
            .filterNot { it.substringBefore('=') in params }
            .joinToString("&")
        val newQuery = if (filtered.isEmpty()) null else filtered
        URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, newQuery, uri.fragment).toString()
    } catch (e: Exception) {
        url.replace(fallbackStripRegex(params), "").replace(QUESTION_AMP_REGEX, "?").replace(DOUBLE_AMP_REGEX, "&")
    }
}

private val fallbackStripRegexCache = ConcurrentHashMap<Set<String>, Regex>()

private fun fallbackStripRegex(params: Set<String>): Regex =
    fallbackStripRegexCache.getOrPut(params) {
        Regex("[?&](?:${params.joinToString("|") { Regex.escape(it) }})=[^&]*")
    }

private val QUESTION_AMP_REGEX = Regex("\\?&")
private val DOUBLE_AMP_REGEX = Regex("&&")
