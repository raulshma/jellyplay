package com.raulshma.jellyplay.core.network.auth

import okhttp3.Request

/**
 * Sets the token-only [JellyfinAuthorizationHeader] on an OkHttp request —
 * the one fold every hand-built OkHttp call site (downloads, transfer
 * clients, book fetches, the realtime socket, the plugin WebView intercept)
 * goes through, so no site can send the wrong header name or an unencoded
 * token.
 */
fun Request.Builder.tokenAuthHeader(accessToken: String): Request.Builder =
    header(JellyfinAuthorizationHeader.HEADER_NAME, JellyfinAuthorizationHeader.tokenOnly(accessToken))
