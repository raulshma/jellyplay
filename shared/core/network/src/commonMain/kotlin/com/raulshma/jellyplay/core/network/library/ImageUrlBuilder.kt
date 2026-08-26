package com.raulshma.jellyplay.core.network.library

/**
 * Pure item-image URL builder for the wasm library client — a verbatim port
 * of the Jellyfin SDK 1.8.12 `ImageApi.getItemImageUrl` +
 * `UrlBuilder.buildUrl` behavior for exactly the parameters the app uses
 * (`maxWidth` / `tag` / `imageIndex`; the other query keys keep their SDK
 * order so adding one later is a one-line change).
 *
 * Byte-parity notes (verified against the SDK sources):
 *  - Path is `/Items/{itemId}/Images/{imageType}`; path segments are
 *    URL-encoded by the SDK, but ids/types/tags are hex/alphanumeric so the
 *    no-op encoding here is exact for valid inputs.
 *  - Query keys are the SDK's camelCase names, appended in the SDK's buildMap
 *    order, null values omitted.
 *  - NO `api_key` query parameter and NO token: the SDK's `createUrl` never
 *    appends the access token (auth travels on the request layer), so image
 *    URLs are bearer-less exactly like the JVM/desktop ones.
 *
 * wasm v1 note: the Coil wasm engine landed (wave 10B) but no browser has
 * fetched a real image through it yet — whether images need a credential-
 * bearing variant stays open until that pass; until then this stays
 * byte-identical to the JVM builder output.
 */

/** The SDK `ImageType` serial names (`ImageType.fromNameOrNull` table). */
val KNOWN_IMAGE_TYPES: Set<String> = setOf(
    "Primary", "Art", "Backdrop", "Banner", "Logo", "Thumb", "Disc", "Box",
    "Screenshot", "Menu", "Chapter", "BoxRear", "Profile",
)

private val GUID_REGEX = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
)

/**
 * True when [value] parses as the dashed UUID form the SDK's
 * `String.toUUID()` accepts (`java.util.UUID.fromString` semantics: exactly
 * 8-4-4-4-12 hex digits). Callers mirror the JVM behavior of refusing to
 * build an image URL for a non-UUID id.
 */
fun isGuid(value: String): Boolean = GUID_REGEX.matches(value)

/**
 * Builds `/Items/{itemId}/Images/{imageType}` under [baseUrl]. Returns ""
 * exactly where the JVM path returns "": no base URL, unknown image type, or
 * a non-UUID item id.
 */
fun buildItemImageUrl(
    baseUrl: String?,
    itemId: String,
    imageType: String,
    maxWidth: Int? = null,
    maxHeight: Int? = null,
    tag: String? = null,
    imageIndex: Int? = null,
): String {
    if (baseUrl.isNullOrBlank()) return ""
    if (imageType !in KNOWN_IMAGE_TYPES) return ""
    if (!isGuid(itemId)) return ""
    return buildString {
        append(baseUrl.trimEnd('/'))
        append("/Items/").append(itemId).append("/Images/").append(imageType)
        var separator = '?'
        fun param(key: String, value: Any?) {
            append(separator).append(key).append('=').append(value.toString())
            separator = '&'
        }
        // SDK buildMap order, null-filtered.
        maxWidth?.let { param("maxWidth", it) }
        maxHeight?.let { param("maxHeight", it) }
        // width/height/quality/fillWidth/fillHeight sit between — omitted
        // (null) for every current call site.
        tag?.let { param("tag", it) }
        // format/percentPlayed/unplayedCount/blur/backgroundColor/
        // foregroundLayer also null for every current call site.
        imageIndex?.let { param("imageIndex", it) }
    }
}
