package com.raulshma.jellyplay.feature.admin.plugins

/**
 * Pure halves of the legacy `PluginWebViewAuth.kt` (admin conveyor split):
 * the placeholder-token substitution for `pluginBridge.js` and the strict
 * same-origin check those tokens are safe to inject under. Both are plain
 * string/URI logic with no Android API, so they live in commonMain; the
 * Android-only WebView request interception stayed behind in androidMain
 * (`PluginWebViewIntercept.android.kt`).
 */

/**
 * Substitutes the placeholder tokens in [pluginBridgeJs] (loaded from the
 * `pluginBridge.js` asset) with runtime values. The token list here MUST match
 * the `__TOKEN__` strings embedded in `pluginBridge.js`.
 *
 * The access token IS injected into JS so write requests (config save = POST)
 * carry their own auth header — the host cannot re-issue a POST via the
 * Android-only `interceptAuthedRequest` while preserving its body (Android's
 * `WebResourceRequest` exposes none). See that function for the narrower
 * GET-only role the host still plays.
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
        val a = java.net.URI(requestUrl)
        val b = java.net.URI(serverAddress)
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
