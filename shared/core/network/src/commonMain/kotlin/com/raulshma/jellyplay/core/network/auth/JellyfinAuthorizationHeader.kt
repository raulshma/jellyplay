package com.raulshma.jellyplay.core.network.auth

/**
 * Builds the Jellyfin authorization header value, byte-compatible with the
 * Jellyfin SDK's `AuthorizationHeaderBuilder` (org.jellyfin.sdk 1.8.12) so a
 * wasm client presents the same identity format the android/desktop SDK
 * clients do.
 *
 * The SDK sends it as an `Authorization` header with the `MediaBrowser`
 * scheme — `X-Emby-Authorization` is the legacy header alias servers still
 * accept, but this builder mirrors the SDK exactly (header name + scheme are
 * the caller's choice; the value is what this object produces):
 *
 * ```
 * MediaBrowser Client="JellyPlay", Version="1.0", DeviceId="…", Device="…", Token="…"
 * ```
 *
 * The `Token` parameter is omitted entirely when null (unauthenticated
 * requests), parameters are joined with `", "`, and every value is trimmed,
 * stripped of line feeds and percent-encoded (unreserved chars kept, space →
 * `+`, everything else `%XX` uppercase) — all verbatim SDK semantics.
 */
object JellyfinAuthorizationHeader {

    const val HEADER_NAME = "Authorization"
    const val SCHEME = "MediaBrowser"

    fun build(
        clientName: String,
        clientVersion: String,
        deviceId: String,
        deviceName: String,
        accessToken: String? = null,
    ): String = listOf(
        "Client" to clientName,
        "Version" to clientVersion,
        "DeviceId" to deviceId,
        "Device" to deviceName,
        "Token" to accessToken,
    )
        .filter { it.second != null }
        .joinToString(", ", "$SCHEME ") { (key, value) -> buildParameter(key, value!!) }

    /**
     * `key="percent-encoded value"` with the SDK's key validation: a key may
     * not contain `=` or `,` and may not start or end with `"` (the header is
     * comma-separated `k="v"` pairs, so those would corrupt the parse).
     */
    internal fun buildParameter(key: String, value: String): String {
        require(!key.contains('=')) { "Key $key can not contain the = character in the authorization header" }
        require(!key.contains(',')) { "Key $key can not contain the , character in the authorization header" }
        require(!(key.startsWith("\"") || key.endsWith("\""))) {
            "Key $key can not start or end with the \" character in the authorization header"
        }
        return "$key=\"${encodeParameterValue(value)}\""
    }

    /** Trim, remove line feeds, then percent-encode — SDK order preserved. */
    internal fun encodeParameterValue(raw: String): String =
        encodeUrlPart(raw.trim().replace(LINE_FEEDS, ""))

    /**
     * Percent-encoding mirroring the SDK's `encodeURLPart`: unreserved
     * characters (RFC 3986 unreserved set) pass through, space becomes `+`,
     * anything else is UTF-8 percent-encoded with uppercase hex digits.
     */
    private fun encodeUrlPart(raw: String): String = buildString {
        for (char in raw) {
            when {
                char.isUnreserved() -> append(char)
                char == ' ' -> append('+')
                else -> {
                    for (byte in char.toString().encodeToByteArray()) {
                        append('%')
                        append(HEX[(byte.toInt() shr 4) and 0xf])
                        append(HEX[byte.toInt() and 0xf])
                    }
                }
            }
        }
    }

    private fun Char.isUnreserved(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' ||
            this == '-' || this == '.' || this == '_' || this == '~'

    private val LINE_FEEDS = Regex("\\n")

    private const val HEX = "0123456789ABCDEF"
}
