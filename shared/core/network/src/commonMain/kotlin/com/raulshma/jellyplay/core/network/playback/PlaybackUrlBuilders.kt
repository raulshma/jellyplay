package com.raulshma.jellyplay.core.network.playback

import com.raulshma.jellyplay.core.model.isImageSubtitleCodec

/**
 * The ONE stream/subtitle URL policy for the playback clients: pure builders
 * that BOTH platform clients delegate to — the wasm `KtorWasmPlaybackApiClient`
 * (ported from the jvmShared impl's original inline string building) and, since
 * that impl's hand copies were replaced with delegation, the jvmShared
 * `PlaybackApiClientImpl` itself (output unchanged apart from the trailing-'/'
 * base trim noted at the delegation sites). Pure so commonTest can pin the
 * exact query parameters.
 *
 * Session inputs ([baseUrl]/[apiKey]/[userId]/[userServerId]) are supplied
 * per call by the client from the atomic session state.
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
    return "${baseUrl.trimEnd('/')}$path$paramPrefix$baseParams&api_key=$apiKey"
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
    val base = if (deliveryUrl.startsWith("http")) deliveryUrl else "${baseUrl.trimEnd('/')}$deliveryUrl"
    val separator = if ("?" in base) "&" else "?"
    return "$base${separator}api_key=$apiKey"
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
    return "${baseUrl.trimEnd('/')}/Videos/$itemId/$mediaSourceId/Subtitles/$index/Stream.$format?api_key=$apiKey"
}

/**
 * The book download URL (`/Items/{itemId}/Download?api_key=…`). Books have
 * no stream URL — the reader fetches the file verbatim from the Download
 * endpoint and renders it locally. Callers hold a resolved server address +
 * token (no session-null sentinels, unlike the stream builders above); only
 * the base's trailing slash is trimmed so the path can never start `//`.
 */
fun buildBookDownloadUrl(baseUrl: String, apiKey: String, itemId: String): String =
    "${baseUrl.trimEnd('/')}/Items/$itemId/Download?api_key=$apiKey"
