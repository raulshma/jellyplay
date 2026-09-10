package com.raulshma.jellyplay.core.network.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

/**
 * Base address + access token for one raw request. A pair-wanting-to-be-a-
 * type: both halves always travel together through [JellyfinRawRequester]'s
 * guard members and `getBodyText`'s session parameter. Module-internal like
 * the requester itself — not a stable API surface.
 */
internal data class RawSession(val base: String, val token: String)

/**
 * One seam for the jvmShared clients' hand-built raw-OkHttp requests (the
 * plugin catalogue, the newsletter / playback-reporting plugin endpoints, the
 * intro/credit timestamp probes): session guard → failover-correct base URL →
 * `X-Emby-Token` header → `newCall().execute().use` → status check, with the
 * per-endpoint failure text. The JVM twin of the wasm stack's WasmApiSupport
 * helpers (`getJson` / `postStatusOnly` / `deleteStatusOnly`) — one fold of the
 * choreography PluginApiClientImpl, MediaInfoApiClientImpl and
 * PlaybackApiClientImpl used to copy per endpoint.
 *
 * Base-address rule (the failover fix this seam exists to enforce): every
 * member derives the base from [JellyfinApiEngine.activeServerAddress] — the
 * router's active endpoint, which follows failover to an alternate — never
 * from the current server's primary address, which goes stale the moment the
 * router fails over (the pre-fold Plugin/MediaInfo endpoints built their URLs
 * from `currentServer.value?.address` and relied on the failover interceptor
 * to rescue them).
 *
 * Members block on the OkHttp call exactly as the folded call sites did; they
 * run inside the engine's IO-dispatched `apiResultWithRetry` blocks. Query
 * strings stay hand-built at the call sites (URLEncoder encodings and
 * interpolations whose exact bytes are pinned) and ride in [path] verbatim
 * after the base.
 */
internal class JellyfinRawRequester(
    private val engine: JellyfinApiEngine,
) {

    /**
     * Failover-correct base address + access token — the guard every folded
     * Plugin/MediaInfo endpoint used to run inline, with the same texts:
     * not-connected / not-authenticated as [IllegalStateException].
     *
     * Existence is decided by the engine's ATOMIC [JellyfinApiEngine.session]
     * value (never re-combined from the separate currentServer/currentUser
     * flows — the session-identity rule); a null session means no fully
     * established identity, and the server side is consulted only to pick
     * the error text — message cosmetics after the existence decision, not
     * a session derivation.
     */
    fun requireSession(): RawSession {
        val session = engine.session.value
            ?: throw IllegalStateException(
                if (engine.currentServer.value == null) "Not connected" else "Not authenticated",
            )
        return RawSession(
            base = engine.activeServerAddress ?: session.server.address,
            token = session.user.accessToken,
        )
    }

    /**
     * The PlaybackApiClientImpl flavour of the same guard ("No server" /
     * "No user" texts, base resolved failover-first) over the same atomic
     * session read.
     */
    fun requirePlaybackSession(): RawSession {
        val session = engine.session.value
            ?: throw IllegalStateException(
                if (engine.currentServer.value == null) "No server" else "No user",
            )
        return RawSession(
            base = engine.activeServerAddress ?: session.server.address,
            token = session.user.accessToken,
        )
    }

    /**
     * GET + `X-Emby-Token`; [decode] receives the success body (stream- or
     * text-decoding stays at the call site — the plugin catalogue streams, the
     * playback-reporting plugin decodes strings, a few endpoints return the
     * raw text). Non-2xx throws `Exception("<failureMessage>: <code>")` — the
     * per-endpoint texts the folded call sites pinned.
     */
    fun <T> getJson(
        path: String,
        failureMessage: String,
        decode: (ResponseBody?) -> T,
    ): T {
        val (base, token) = requireSession()
        val request = Request.Builder()
            .url(base + path)
            .header("X-Emby-Token", token)
            .get()
            .build()
        return engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("$failureMessage: ${response.code}")
            decode(response.body)
        }
    }

    /**
     * POST (JSON [bodyText]; the empty-body mutations post `""` like they
     * always did) whose success depends only on the status code; non-2xx
     * throws like [getJson].
     */
    fun postStatusOnly(
        path: String,
        failureMessage: String,
        bodyText: String = "",
    ) {
        val (base, token) = requireSession()
        val request = Request.Builder()
            .url(base + path)
            .header("X-Emby-Token", token)
            .post(bodyText.toRequestBody("application/json".toMediaType()))
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("$failureMessage: ${response.code}")
        }
    }

    /** DELETE whose success depends only on the status code; non-2xx throws like [getJson]. */
    fun deleteStatusOnly(path: String, failureMessage: String) {
        val (base, token) = requireSession()
        val request = Request.Builder()
            .url(base + path)
            .header("X-Emby-Token", token)
            .delete()
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("$failureMessage: ${response.code}")
        }
    }

    /**
     * GET + `X-Emby-Token` whose BODY TEXT the caller needs, null on non-2xx —
     * failure is "no data", never an error (intro/credit timestamps,
     * remote-subtitle search, the playback-reporting availability probe). The
     * JVM twin of the wasm support's `getBodyTextWithEmbyToken`; [session]
     * defaults to [requireSession], and the playback client passes its
     * [requirePlaybackSession] flavour so its historic guard texts survive.
     */
    fun getBodyText(
        path: String,
        session: RawSession = requireSession(),
    ): String? {
        val request = Request.Builder()
            .url(session.base + path)
            .header("X-Emby-Token", session.token)
            .get()
            .build()
        return engine.okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }
}
