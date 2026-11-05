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
 * auth token — token rotation must not invalidate cached bytes. Both token
 * spellings: `ApiKey` (what the app's URL builders emit) and the legacy
 * lowercase `api_key` alias pre-12 servers bake into URLs they hand back.
 * Anything else is either content-bearing (resume offsets) or session-scoped
 * ([SESSION_SCOPED_QUERY_PARAMS], which must be rejected via
 * [isSessionKeyedUrl], never stripped).
 */
val STRIP_SAFE_QUERY_PARAMS: Set<String> = setOf("ApiKey", "api_key")

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
 *
 * Media URLs almost never carry a [params] entry (auth rides in headers), yet
 * this runs per media chunk for cache-key resolution — so a zero-allocation
 * fast path returns [url] untouched when the query cannot contain a [params]
 * entry. The guards are deliberately conservative: they short-circuit only
 * shapes where the URI round trip provably reproduces the input verbatim, so
 * the result is byte-identical to the slow path for every input — including
 * the quirky ones that get re-encoded ([URI] re-escapes `%`, re-anchors
 * registry-based authorities like `http://foo_bar/…` to `http:/…`, and drops
 * the bare `?` of an empty query). Those still take the slow path; see
 * [hasRoundTripStableAuthority].
 */
fun stripVolatileQueryParams(url: String, params: Set<String>): String {
    // Fast path. No '?' at all means the URI below parses with a null rawQuery
    // and returns `url` verbatim. A query carrying no case-insensitive
    // [params] substring cannot match a param name (the slow path compares
    // case-sensitively — a strict subset of this test), and the remaining
    // guards exclude exactly the shapes the round trip does not reproduce
    // verbatim: percent escapes (re-encoded), empty queries (the bare '?' is
    // dropped), registry-based authorities (re-anchored), and "?&"/"&&"
    // (collapsed by the fallback path). Any miss here falls through to the
    // slow path unchanged.
    if (
        params.isNotEmpty() &&
        url.contains('?') &&
        params.none { url.contains(it, ignoreCase = true) } &&
        !url.contains('%') &&
        !url.endsWith("?") &&
        !url.contains("?#") &&
        !url.contains("?&") &&
        !url.contains("&&") &&
        hasRoundTripStableAuthority(url)
    ) {
        return url
    }
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

/**
 * True when [url]'s authority — the component after `://`, up to the first
 * `/`, `?` or `#` — is a plain ASCII `host[:port]` that [URI] parses as a
 * server-based authority and prints back verbatim on a component-wise round
 * trip. That is the only authority shape guaranteed to survive the primary
 * path's `URI(scheme, userInfo, host, port, path, query, fragment)`
 * reconstruction unchanged: registry-based authorities (underscore hosts like
 * `foo_bar`, empty domain labels, non-numeric ports), userinfo, IPv6 literals,
 * and empty authorities (`file:///…`) all get re-anchored — `http://foo_bar/x?q`
 * reconstructs as `http:/x?q` — so those URLs must take the slow path.
 *
 * Deliberately narrower than what [URI] accepts (labels must start and end
 * alphanumeric; ports are digit-only and non-zero-padded) — anything not
 * proven to round trip verbatim is sent to the slow path instead.
 */
private fun hasRoundTripStableAuthority(url: String): Boolean {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return false
    var i = schemeEnd + 3
    val n = url.length
    if (i >= n) return false
    var prev = ' '
    while (i < n) {
        val c = url[i]
        when (c) {
            '/', '?', '#' -> break
            ':' -> {
                if (!prev.isAsciiAlnum()) return false
                return hasRoundTripStablePort(url, i + 1, n)
            }
            // A label must begin and end alphanumeric: reject ".a", "a..b",
            // "a.-" and friends, which degrade the authority to registry-based.
            '.' -> if (!prev.isAsciiAlnum()) return false
            // Inside a label '-' may chain ("a--b"), but a label may not start
            // with '-' and must not END with one ("a-" is registry-based).
            '-' -> if (!prev.isAsciiAlnum() && prev != '-') return false
            // Userinfo ('@'), IPv6 brackets, underscores, escapes: slow path.
            else -> if (!c.isAsciiAlnum()) return false
        }
        prev = c
        i++
    }
    return prev.isAsciiAlnum() || prev == '.'  // "h" and "h." are stable; "a-" is not
}

/**
 * True for the digit-only, non-zero-padded ports (at most 5 digits) that [URI]
 * prints back verbatim — `:8096` and even `:99999`, but not `:080` (parsed as
 * `80`, then re-printed without the padding) and not `:abc` or `:80x` (not
 * ports, so the whole authority degrades to registry-based).
 */
private fun hasRoundTripStablePort(url: String, start: Int, end: Int): Boolean {
    var i = start
    while (i < end && url[i] in '0'..'9') i++
    if (i < end && url[i] !in "/?#") return false
    val digits = i - start
    return digits in 1..5 && (digits == 1 || url[start] != '0')
}

private fun Char.isAsciiAlnum(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
