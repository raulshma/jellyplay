package com.raulshma.jellyplay.core.data.repository

import okhttp3.OkHttpClient

/**
 * Everything a plugin configuration WebView needs from the app session:
 * bridge-script parameters (server address, user id, access token) and the
 * shared OkHttpClient that authenticates same-origin requests.
 */
data class PluginWebViewSession(
    val serverAddress: String = "",
    val userId: String = "",
    val accessToken: String = "",
    val okHttpClient: OkHttpClient,
)

/** A plugin's resolved configuration page: display name plus HTML body. */
data class PluginConfigPageContent(
    val name: String,
    val html: String,
)
