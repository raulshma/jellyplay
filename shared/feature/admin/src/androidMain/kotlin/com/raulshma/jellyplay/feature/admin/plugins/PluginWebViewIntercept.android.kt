package com.raulshma.jellyplay.feature.admin.plugins

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Android-only half of the legacy `PluginWebViewAuth.kt` (renamed at the
 * admin-conveyor split so the facade name is not shadowed across source sets):
 * the WebView request interceptor. The pure `buildBridgeScript`/`isSameOrigin`
 * halves moved to commonMain's `PluginBridgeScript.kt`.
 */

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
