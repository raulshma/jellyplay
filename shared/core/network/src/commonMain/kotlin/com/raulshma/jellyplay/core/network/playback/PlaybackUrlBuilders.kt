package com.raulshma.jellyplay.core.network.playback

import com.raulshma.jellyplay.core.model.isImageSubtitleCodec

/**
 * Pure stream/subtitle URL builders for the wasm playback client — verbatim
 * ports of the jvmShared `PlaybackApiClientImpl.getStreamUrl` /
 * `buildSubtitleDeliveryUrl` / `getSubtitleDeliveryUrl` string construction
 * (byte-identical output for the same inputs, including the `static=true`
 * prefix, the `LiveStreamId` echo, the audio-universal `deviceId`/`userId`
 * pair, and the trailing `api_key`), extracted pure so commonTest can pin
 * the exact query parameters.
 *
 * Session inputs ([baseUrl]/[apiKey]/[userId]/[userServerId]) are supplied
 * per call by the client from the atomic session state.
 */

/**
 * Builds the stream URL. Mirrors the JVM overload with `maxBitrate` /
 * `useAudioEndpoint`; [userServerId] is the `UserInfo.serverId` the JVM impl
 * interpolates into `deviceId=` (never populated by either login path — both
 * platforms emit `deviceId=null` today; kept for parity, KDoc'd delta).
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
