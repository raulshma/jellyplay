package com.raulshma.jellyplay.core.network.playback

import com.raulshma.jellyplay.core.model.isImageSubtitleCodec

/**
 * The ONE stream/subtitle URL policy for the playback clients: pure builders
 * the playback client delegates to — the jvmShared
 * `PlaybackApiClientImpl`, whose hand copies were replaced with delegation
 * (output unchanged apart from the trailing-'/'
 * base trim noted at the delegation sites). Pure so commonTest can pin the
 * exact query parameters.
 *
 * Session inputs ([baseUrl]/[apiKey]/[userId]/[userServerId]) are supplied
 * per call by the client from the atomic session state.
 *
 * The token rides as the capital `ApiKey` query param: players (ExoPlayer,
 * mpv, browsers) cannot attach headers to media fetches, so the query param
 * is unavoidable — and the lowercase `api_key` alias is Jellyfin-12 legacy,
 * gated behind the server's `EnableLegacyAuthorization` flag (off by
 * default), where `/Items/{id}/Download` and the socket 401/403 on it.
 */

/**
 * Builds the stream URL. The jvmShared impl's overload with `maxBitrate` /
 * `useAudioEndpoint` delegates here; [userServerId] is the `UserInfo.serverId`
 * the JVM impl interpolates into `deviceId=` (never populated by either login
 * path — both platforms emit `deviceId=null` today; kept for parity).
 */
fun buildStreamUrl(
    baseUrl: String?,
    apiKey: String?,
    userId: String?,
    userServerId: String?,
    itemId: String,
    mediaSourceId: String,
    startTimeTicks: Long = 0,
    maxBitrate: Int? = null,
    useAudioEndpoint: Boolean = false,
    liveStreamId: String? = null,
): String {
    if (baseUrl == null) return ""
    if (apiKey == null) return ""
    val isLive = !liveStreamId.isNullOrBlank()
    val path = if (useAudioEndpoint) {
        "/Audio/$itemId/universal"
    } else {
        "/Videos/$itemId/stream"
    }
    val baseParams = buildString {
        append("mediaSourceId=$mediaSourceId")
        append("&startTimeTicks=$startTimeTicks")
        if (maxBitrate != null && maxBitrate > 0) {
            append("&maxBitrate=$maxBitrate")
        }
        if (useAudioEndpoint) {
            append("&deviceId=$userServerId")
            append("&userId=$userId")
        }
        // Echo the live-stream id so the server opens/attaches the tuner
        // session. Required for Live TV channels; omitted for VOD.
        if (isLive) append("&LiveStreamId=$liveStreamId")
    }
    // Static direct-play only applies to VOD files. Live sources are
    // opened as a (growing) direct stream — `static=true` makes the server
    // try a byte-range seek on a non-seekable stream and fail.
    val paramPrefix = if (useAudioEndpoint || isLive) "?" else "?static=true&"
    return "${baseUrl.trimEnd('/')}$path$paramPrefix$baseParams&ApiKey=$apiKey"
}

/**
 * Absolute-izes a server-provided [deliveryUrl] against [baseUrl] — the
 * token-less half of the [resolveDeliveryUrlWithApiKey] fold, for callers
 * with no session token (core/data's transcode resolver). The base's
 * trailing slash is trimmed so the joined path can never start `//`; an
 * already-absolute delivery URL passes through untouched.
 */
fun resolveDeliveryUrl(baseUrl: String, deliveryUrl: String): String =
    if (deliveryUrl.startsWith("http")) deliveryUrl else "${baseUrl.trimEnd('/')}$deliveryUrl"

/**
 * Absolute-izes a server-provided [deliveryUrl] against [baseUrl] and
 * appends the access token as the capital `ApiKey` query param — unless the
 * URL already carries a token param in either spelling (capital `ApiKey`, or
 * the lowercase `api_key` alias pre-12 servers bake into delivery URLs).
 *
 * The ONE fold for server-provided delivery URLs: the subtitle resolver
 * below and core/data's transcode resolver previously hand-copied it and had
 * drifted — only the transcode copy guarded the pre-baked param. The guard
 * now protects both (a URL that already carries a token is returned
 * untouched, never double-appended). Callers keep their own session-null
 * sentinels ("" for missing session, or the token-less absolute base)
 * before calling. The base's trailing slash is trimmed so the joined path
 * can never start `//` (the [buildBookDownloadUrl] policy).
 */
fun resolveDeliveryUrlWithApiKey(
    baseUrl: String,
    deliveryUrl: String,
    apiKey: String,
): String {
    val base = resolveDeliveryUrl(baseUrl, deliveryUrl)
    if ("api_key=" in base || "ApiKey=" in base) return base
    val separator = if ("?" in base) "&" else "?"
    return "$base${separator}ApiKey=$apiKey"
}

/**
 * Absolute-izes a server-provided subtitle [deliveryUrl] and appends the
 * access token (`getSubtitleDeliveryUrl` in the JVM impl).
 */
fun resolveSubtitleDeliveryUrl(
    baseUrl: String?,
    apiKey: String?,
    deliveryUrl: String,
): String {
    if (baseUrl == null) return ""
    if (apiKey == null) return ""
    return resolveDeliveryUrlWithApiKey(baseUrl, deliveryUrl, apiKey)
}

/**
 * Builds the text-subtitle extraction URL
 * (`/Videos/{itemId}/{mediaSourceId}/Subtitles/{index}/Stream.{format}`).
 * Returns "" for image codecs (PGS/VOBSUB/DVB) — the Jellyfin endpoint only
 * serves text formats, and refusing here (instead of emitting a URL the
 * endpoint will reject) lets the caller fall back to burn-in / container
 * demux, exactly like the JVM impl.
 */
fun buildSubtitleDeliveryUrl(
    baseUrl: String?,
    apiKey: String?,
    itemId: String,
    mediaSourceId: String,
    index: Int,
    codec: String?,
): String {
    if (baseUrl == null) return ""
    if (apiKey == null) return ""
    if (isImageSubtitleCodec(codec)) return ""
    val format = when ((codec ?: "srt").lowercase()) {
        "subrip" -> "srt"
        "ass", "ssa" -> codec!!.lowercase()
        else -> (codec ?: "srt").lowercase()
    }
    return "${baseUrl.trimEnd('/')}/Videos/$itemId/$mediaSourceId/Subtitles/$index/Stream.$format?ApiKey=$apiKey"
}

/**
 * The book download URL (`/Items/{itemId}/Download?ApiKey=…`). Books have
 * no stream URL — the reader fetches the file verbatim from the Download
 * endpoint and renders it locally. Callers hold a resolved server address +
 * token (no session-null sentinels, unlike the stream builders above); only
 * the base's trailing slash is trimmed so the path can never start `//`.
 */
fun buildBookDownloadUrl(baseUrl: String, apiKey: String, itemId: String): String =
    "${baseUrl.trimEnd('/')}/Items/$itemId/Download?ApiKey=$apiKey"
