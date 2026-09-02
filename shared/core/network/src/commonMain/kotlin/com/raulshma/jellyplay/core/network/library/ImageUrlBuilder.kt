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
 * wasm v1 note: the Coil wasm engine landed (wave 10B) and the wave 13C
 * browser pass VERIFIED these bearer-less URLs decode end-to-end — no
 * credential-bearing variant is needed (the wave-12 open question is
 * closed; see the harness notes in apps/web and tools/e2e/web-verify).
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
 * build an image URL for a non-UUID id; the compact 32-hex form is handled
 * by [normalizeItemIdGuid], not here.
 */
fun isGuid(value: String): Boolean = GUID_REGEX.matches(value)

private val COMPACT_GUID_REGEX = Regex("^[0-9a-fA-F]{32}$")

/**
 * Normalizes an item id into the dashed GUID form, accepting BOTH
 * serializations Jellyfin emits: the dashed 8-4-4-4-12 form (what the JVM
 * SDK's UUID-typed DTOs produce) and the bare 32-hex compact form (what
 * Jellyfin 10.11's `/Items` responses carry — observed against the wave 13C
 * harness server, whose ids arrive as e.g. `d27f3684c285863ac935d847f16548d1`;
 * the server accepts either form on the image path, and the dashed URL this
 * function emits for a compact id was decoded end-to-end by the wave 13C
 * headless-browser lane — Coil fetched and rendered the artwork). Returns
 * null for anything else, in which case callers refuse to build a URL.
 */
fun normalizeItemIdGuid(value: String): String? = when {
    GUID_REGEX.matches(value) -> value
    COMPACT_GUID_REGEX.matches(value) -> buildString {
        append(value.substring(0, 8)); append('-')
        append(value.substring(8, 12)); append('-')
        append(value.substring(12, 16)); append('-')
        append(value.substring(16, 20)); append('-')
        append(value.substring(20, 32))
    }
    else -> null
}

/**
 * Builds `/Users/{userId}/Images/{imageType}` under [baseUrl] — the user
 * avatar variant (SDK `ImageApi.getUserImageUrl`), sibling of
 * [buildItemImageUrl]. Same refusal contract: returns "" for no base URL,
 * unknown image type, or a user id that is a GUID in neither serialization
 * (compact 32-hex ids normalize to dashed via [normalizeItemIdGuid]).
 *
 * Two deltas from the item variant, both mirroring the SDK's user endpoint:
 * no `imageIndex` (user images are singletons), and the server serves these
 * URLs anonymously — the pre-login user-picker gallery depends on that — so
 * the bearer-less note above holds here too. [tag] (the `UserDto`/
 * `UserInfo.primaryImageTag`) is optional cache-busting only: a tag-less URL
 * is fully valid, it just stops the server 304/coil cache from noticing an
 * avatar change.
 */
fun buildUserImageUrl(
    baseUrl: String?,
    userId: String,
    imageType: String,
    maxWidth: Int? = null,
    maxHeight: Int? = null,
    tag: String? = null,
): String {
    if (baseUrl.isNullOrBlank()) return ""
    if (imageType !in KNOWN_IMAGE_TYPES) return ""
    val normalizedUserId = normalizeItemIdGuid(userId) ?: return ""
    return buildString {
        append(baseUrl.trimEnd('/'))
        append("/Users/").append(normalizedUserId).append("/Images/").append(imageType)
        var separator = '?'
        fun param(key: String, value: Any?) {
            append(separator).append(key).append('=').append(value.toString())
            separator = '&'
        }
        // Same SDK buildMap order / null-filter as the item variant.
        maxWidth?.let { param("maxWidth", it) }
        maxHeight?.let { param("maxHeight", it) }
        tag?.let { param("tag", it) }
    }
}

/**
 * Builds `/Items/{itemId}/Images/{imageType}` under [baseUrl]. Returns ""
 * exactly where the JVM path returns "": no base URL, unknown image type, or
 * an item id that is a GUID in neither serialization. Compact-form ids are
 * normalized to dashed (see [normalizeItemIdGuid]) so the emitted URL is
 * byte-identical to the JVM builder output for the same item.
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
    val normalizedItemId = normalizeItemIdGuid(itemId) ?: return ""
    return buildString {
        append(baseUrl.trimEnd('/'))
        append("/Items/").append(normalizedItemId).append("/Images/").append(imageType)
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
