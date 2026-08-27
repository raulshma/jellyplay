package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrCredentials
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Pure, commonMain wire helpers for the Phase W wasm Seerr/TMDB client —
 * every byte-level convention of the jvmShared `SeerrApiClientImpl`
 * (OkHttp) extracted so the wasm client consumes them unchanged and
 * commonTest can pin them. The jvmShared impl keeps its own private copies;
 * the two MUST stay in sync (same paths, same error strings, same encodings).
 *
 * The Radarr/Sonarr clients reuse [arrSeerrWireJson] and the arr siblings in
 * `com.raulshma.jellyplay.core.network.arr` (they share this seam's shape;
 * see `ArrWireSupport.kt`).
 */

/**
 * The exact commonMain twin of `SeerrApiClientImpl.lenientJson` (jvmShared):
 * `ignoreUnknownKeys + coerceInputValues + isLenient`, and kotlinx's default
 * `encodeDefaults = false` — which is load-bearing on the encode side: the
 * JVM builds `SeerrRequestPayload`/`SeerrEditRequestPayload` and encodes them
 * with THIS configuration, so `is4k = false` and every null field are
 * omitted from the POST body. Any wasm Json that flips encodeDefaults would
 * put `\"is4k\":false` on the wire where the JVM sends nothing.
 */
internal val arrSeerrWireJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

/** `SeerrApiClientImpl.buildUrl`: `$base/api/v1$path` with the base trailing slash trimmed. */
internal fun seerrApiUrl(baseUrl: String, path: String): String {
    val base = baseUrl.trimEnd('/')
    return "$base/api/v1$path"
}

/**
 * `SeerrApiClientImpl.parseErrorMessage`, verbatim: JSON-object bodies give up
 * their `message` field (toString + quote-trim, so non-string messages pass
 * through raw), blank messages echo the whole body, and anything that does
 * not parse as a JSON object falls back to the first 200 chars of the body.
 */
internal fun seerrHttpErrorMessage(code: Int, body: String): String {
    return try {
        val errorJson = arrSeerrWireJson.parseToJsonElement(body).jsonObject
        val message = errorJson["message"]?.toString()?.trim('"') ?: ""
        if (message.isNotBlank()) "HTTP $code: $message" else "HTTP $code: $body"
    } catch (_: Exception) {
        "HTTP $code: ${body.take(200)}"
    }
}

/**
 * `SeerrApiClientImpl.executeRequestWithCookie`'s Set-Cookie join, verbatim:
 * every `Set-Cookie` header is cut at the first `;` (attributes dropped) and
 * the name=value pairs are re-joined with `"; "`; a blank result is null.
 */
internal fun joinSetCookieHeader(setCookieValues: List<String>): String? =
    setCookieValues.joinToString("; ") { it.substringBefore(";") }.ifBlank { null }

/**
 * commonMain stand-in for the `java.net.URLEncoder.encode(value, "UTF-8")`
 * calls the jvmShared Seerr client uses when appending `search` /
 * `primaryReleaseDateGte` / `firstAirDateGte` into hand-built query strings.
 * Same rules as URLEncoder: alphanumerics plus `.`, `-`, `*`, `_` pass
 * through, space becomes `+`, everything else is emitted as uppercase `%XX`
 * per UTF-8 byte (multi-byte chars encoded whole — surrogate halves are never
 * split because the whole string is encoded to bytes up front).
 */
internal fun urlFormEncode(value: String): String {
    val bytes = value.encodeToByteArray()
    val sb = StringBuilder(bytes.size)
    for (b in bytes) {
        val c = b.toInt() and 0xFF
        when {
            c in 0x41..0x5A || c in 0x61..0x7A || c in 0x30..0x39 -> sb.append(c.toChar())
            c == '.'.code || c == '-'.code || c == '*'.code || c == '_'.code -> sb.append(c.toChar())
            c == ' '.code -> sb.append('+')
            else -> {
                sb.append('%')
                sb.append("0123456789ABCDEF"[(c shr 4) and 0xF])
                sb.append("0123456789ABCDEF"[c and 0xF])
            }
        }
    }
    return sb.toString()
}

/**
 * `SeerrApiClientImpl.withAuth` as data: `X-Api-Key` for
 * [SeerrCredentials.ApiKey], `Cookie` for [SeerrCredentials.SessionCookie].
 * WASM BROWSER CAVEAT (documented delta, code kept faithful): `Cookie` is a
 * forbidden request-header name for browser `fetch` (the Ktor Js/wasmJs
 * engine), so the browser silently strips it — session-cookie credentials
 * cannot authenticate from a browser tab the way OkHttp does on JVM. The
 * header set here is correct for non-restricted contexts (Node/wasm test
 * runtimes) and for the header-selection contract itself.
 */
internal fun seerrAuthHeaders(credentials: SeerrCredentials): List<Pair<String, String>> =
    when (credentials) {
        is SeerrCredentials.ApiKey -> listOf("X-Api-Key" to credentials.apiKey)
        is SeerrCredentials.SessionCookie -> listOf("Cookie" to credentials.cookie)
    }

/**
 * `getRequests`' hand-assembled path, verbatim (string concatenation, not a
 * query builder — the JVM impl embeds take/skip/filter/sort/sortDirection
 * unencoded, appends the optional params in exactly this order, and
 * URLEncoder-encodes only `search`, which is skipped entirely when blank).
 */
internal fun seerrRequestsPath(
    take: Int,
    skip: Int,
    filter: String,
    sort: String,
    sortDirection: String,
    requestedBy: Int?,
    mediaType: String?,
    search: String?,
): String = buildString {
    append("/request?take=$take&skip=$skip&filter=$filter&sort=$sort&sortDirection=$sortDirection")
    requestedBy?.let { append("&requestedBy=$it") }
    mediaType?.let { append("&mediaType=$it") }
    search?.takeIf { it.isNotBlank() }?.let {
        append("&search=")
        append(urlFormEncode(it))
    }
}

/** `getDiscoverMovies`' path: `page` always, `primaryReleaseDateGte` URLEncoder-encoded when present. */
internal fun seerrDiscoverMoviesPath(page: Int, primaryReleaseDateGte: String?): String = buildString {
    append("/discover/movies?page=$page")
    if (primaryReleaseDateGte != null) {
        append("&primaryReleaseDateGte=")
        append(urlFormEncode(primaryReleaseDateGte))
    }
}

/** `getDiscoverTv`' path: `page` always, `firstAirDateGte` URLEncoder-encoded when present. */
internal fun seerrDiscoverTvPath(page: Int, firstAirDateGte: String?): String = buildString {
    append("/discover/tv?page=$page")
    if (firstAirDateGte != null) {
        append("&firstAirDateGte=")
        append(urlFormEncode(firstAirDateGte))
    }
}
