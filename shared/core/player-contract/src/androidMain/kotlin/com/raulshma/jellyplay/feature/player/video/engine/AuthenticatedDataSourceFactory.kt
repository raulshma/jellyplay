package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.raulshma.jellyplay.core.network.auth.JellyfinAuthorizationHeader
import okhttp3.OkHttpClient

/**
 * The one authenticated media data-source factory for the media3 engines —
 * the merged body of player-live's ExoLiveEngine and player-video's
 * ExoPlayerEngine `createAuthenticatedDataSourceFactory` prologue, which each
 * used to hand-copy the same three lines (UA + token header + OkHttp factory)
 * plus the [DefaultDataSource] scheme-routing wrap.
 *
 * The deep interface hides:
 *
 *  - the client user agent (`"JellyPlay"`, the one string both engines sent);
 *  - the auth header construction — [authToken] is folded through
 *    [JellyfinAuthorizationHeader.tokenOnlyHeader] (the `Authorization` /
 *    `MediaBrowser Token="…"` pair, the one builder so the value cannot drift
 *    from the SDK encoding) into the default request properties, with
 *    [extraRequestHeaders] merged after it (callers that already carry the
 *    identical auth pair in their header map — the VOD session manager does —
 *    merge idempotently: same key, same value);
 *  - the [DefaultDataSource] wrap that routes local/content/asset URIs
 *    around the HTTP source — and, through [composeBase], whatever byte-cache
 *    layer a host composes UNDER that routing (so side-loaded local
 *    subtitles bypass the cache, the exact property the VOD engine's KDoc
 *    pins). Identity by default (the live-tuned engine has no cache layer);
 *    the VOD engine passes its [VideoStreamCache] composition.
 *
 * What stays at the CALL SITE on purpose:
 *  - the VOD engine's per-authority [androidx.media3.datasource.ResolvingDataSource]
 *    re-headering — it needs the request's [serverUrl][PlaybackRequest.serverUrl]
 *    authority and composes ON TOP of this factory's result;
 *  - the live engine's [androidx.media3.exoplayer.DefaultLoadControl] tuning
 *    (fast live join) — orthogonal to data sources.
 *
 * Neither host drags the other's weight in: this lives in player-contract
 * androidMain (media3-datasource + okhttp only — no core:data/Room), which
 * both player feature modules already depend on.
 */
fun authenticatedDataSourceFactory(
    context: Context,
    okHttpClient: OkHttpClient,
    authToken: String?,
    extraRequestHeaders: Map<String, String> = emptyMap(),
    composeBase: (DataSource.Factory) -> DataSource.Factory = { it },
): DataSource.Factory {
    val defaultRequestProperties = if (authToken != null) {
        mapOf(JellyfinAuthorizationHeader.tokenOnlyHeader(authToken)) + extraRequestHeaders
    } else {
        extraRequestHeaders
    }
    val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent(CLIENT_USER_AGENT)
        .setDefaultRequestProperties(defaultRequestProperties)
    return DefaultDataSource.Factory(context, composeBase(httpDataSourceFactory))
}

/** The client user agent both former engine copies hardcoded. */
private const val CLIENT_USER_AGENT = "JellyPlay"
