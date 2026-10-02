package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.network.auth.tokenAuthHeader
import okhttp3.Request

/**
 * Feature-facing re-export of the OkHttp authorization-header fold: the ONE
 * header-building path (`core:network`'s `Request.Builder.tokenAuthHeader`
 * over `JellyfinAuthorizationHeader`, byte-compatible with the Jellyfin SDK
 * encoding) behind a `core:data` declaration, so feature modules that
 * hand-build OkHttp requests (the admin plugin WebView intercept) keep the
 * feature-layer core:network embargo while the header shape still cannot
 * drift from the SDK.
 *
 * `core:data` itself already folds through the same extension (the download
 * transfer clients) — this adds no second builder, only an import boundary.
 * (Named `jellyfinTokenAuthHeader` rather than shadowing the core:network
 * name so a file cannot resolve the wrong fold silently.)
 */
fun Request.Builder.jellyfinTokenAuthHeader(accessToken: String): Request.Builder =
    tokenAuthHeader(accessToken)
