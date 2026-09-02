package com.raulshma.jellyplay.core.network.websocket

import java.net.URLEncoder

/**
 * Assembles the Jellyfin `/socket` WebSocket URL shared by the app-lifetime
 * socket ([JellyfinWebSocketClient]) and the screen-lifetime activity-log
 * channel: scheme swap (http(s) → ws(s)) plus the device-identification
 * query params.
 *
 * The access token is the caller's choice — an `api_key` query param (shared
 * socket) or the `X-Emby-Token` header (activity-log channel), which keeps
 * the token out of URLs and logs.
 *
 * Public since the C3 split: the activity-log realtime channel lives in the
 * legacy Android shim while this helper moved into the shared module.
 */
fun buildSocketUrl(
    serverAddress: String,
    deviceId: String,
    deviceName: String? = null,
    client: String? = null,
    apiKey: String? = null,
): String {
    val base = serverAddress.trim().trimEnd('/')
        .replace("https://", "wss://")
        .replace("http://", "ws://")
    val params = listOfNotNull(
        apiKey?.let { "api_key" to it },
        "deviceId" to deviceId,
        deviceName?.let { "deviceName" to it },
        client?.let { "client" to it },
    ).joinToString("&") { (key, value) ->
        "$key=${URLEncoder.encode(value, "UTF-8")}"
    }
    return "$base/socket?$params"
}
