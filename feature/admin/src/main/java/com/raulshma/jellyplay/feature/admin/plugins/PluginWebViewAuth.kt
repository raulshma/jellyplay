package com.raulshma.jellyplay.feature.admin.plugins

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.URI

/**
 * Substitutes the placeholder tokens in [pluginBridgeJs] (loaded from
 * `assets/pluginBridge.js`) with runtime values. The token list here MUST match
 * the `__TOKEN__` strings embedded in `pluginBridge.js`.
 *
 * The access token IS injected into JS so write requests (config save = POST)
 * carry their own auth header — the host cannot re-issue a POST via
 * [interceptAuthedRequest] while preserving its body (Android's
 * `WebResourceRequest` exposes none). See [interceptAuthedRequest] for the
 * narrower GET-only role the host still plays.
 */
internal fun buildBridgeScript(
    pluginBridgeJs: String,
    serverAddress: String,
    userId: String,
    accessToken: String,
): String = pluginBridgeJs
    .replace("__SERVER_ADDRESS__", serverAddress.jsEscape())
    .replace("__USER_ID__", userId.jsEscape())
    .replace("__ACCESS_TOKEN__", accessToken.jsEscape())

private fun String.jsEscape(): String {
    val sb = StringBuilder(this.length + 8)
    for (c in this) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c.code < 0x20) sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
    }
    return sb.toString()
}

/**
 * True iff [requestUrl] is the same Jellyfin server origin as [serverAddress],
 * compared by scheme + host + port (not a string prefix). A plain `startsWith`
 * check is unsafe: it matches attacker-controlled hosts such as
 * `https://server.evil.com` (suffix append) or `http://server:8096@evil.com`
 * (userinfo trick), which would let a malicious plugin page exfiltrate the
 * bearer token. Returns false for any non-strict-origin match so the caller
 * lets the WebView handle the request unauthenticated.
 */
internal fun isSameOrigin(requestUrl: String, serverAddress: String): Boolean =
    runCatching {
        val a = URI(requestUrl)
        val b = URI(serverAddress)
        val aPort = if (a.port != -1) a.port else defaultPortFor(a.scheme)
        val bPort = if (b.port != -1) b.port else defaultPortFor(b.scheme)
        !a.host.isNullOrBlank() &&
            a.host.equals(b.host, ignoreCase = true) &&
            aPort == bPort &&
            a.scheme.equals(b.scheme, ignoreCase = true)
    }.getOrDefault(false)

/** IANA default port for [scheme] (http=80, https=443), or -1 if unknown. */
private fun defaultPortFor(scheme: String?): Int = when (scheme?.lowercase()) {
    "http" -> 80
    "https" -> 443
    else -> -1
}

/**
 * Intercepts same-origin **GET** requests (plugin images, CSS, Pattern-B
 * controller scripts) and re-issues them through OkHttp with the
 * `X-Emby-Token` header, so resource loads the page itself initiates
 * authenticate against the Jellyfin server.
 *
 * Only GET is handled: `WebResourceRequest` exposes no request body, so
 * re-issuing a POST/PUT/PATCH would either throw (OkHttp requires a body) or
 * send an empty body, discarding the real payload and silently breaking config
 * saves. Writes carry their own token from `pluginBridge.js`. Non-GET
 * same-origin requests and any non-same-origin URL return null and fall through
 * to the WebView's own networking.
 */
internal fun interceptAuthedRequest(
    request: WebResourceRequest,
    okHttpClient: OkHttpClient,
    accessToken: String,
    serverAddress: String,
): WebResourceResponse? {
    val url = request.url ?: return null
    val urlStr = url.toString()
    val method = (request.method ?: "GET").uppercase()
    // Only authenticate read-only resource loads. Writes are authed in JS.
    if (method != "GET") return null
    if (!isSameOrigin(urlStr, serverAddress)) return null

    val requestBuilder = Request.Builder()
        .url(urlStr)
        .get()
    if (accessToken.isNotBlank()) requestBuilder.header("X-Emby-Token", accessToken)
    // Carry over request headers (Accept, etc.), skipping ones OkHttp manages
    // and the one we set explicitly.
    request.requestHeaders?.forEach { (key, value) ->
        val lower = key.lowercase()
        if (lower == "x-emby-token" || lower == "host") return@forEach
        requestBuilder.header(key, value)
    }

    val response = runCatching {
        okHttpClient.newCall(requestBuilder.build()).execute()
    }.getOrNull() ?: return null

    return runCatching {
        val contentType = response.header("Content-Type") ?: "text/html"
        val mime = contentType.substringBefore(';').trim().ifBlank { "text/html" }
        val charset = contentType.substringAfter("charset=", missingDelimiterValue = "")
            .trim().ifBlank { "UTF-8" }

        val bytes = response.body.bytes()
        WebResourceResponse(
            mime,
            charset,
            response.code,
            response.message,
            response.headers.toMultimap()
                .mapValues { (_, v) -> v.joinToString(", ") }
                .toMap(),
            ByteArrayInputStream(bytes),
        )
    }.also { response.close() }.getOrNull()
}
