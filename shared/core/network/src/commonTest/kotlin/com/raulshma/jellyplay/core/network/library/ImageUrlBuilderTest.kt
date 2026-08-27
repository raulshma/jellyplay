package com.raulshma.jellyplay.core.network.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the pure image URL builder against the exact output of the Jellyfin
 * SDK 1.8.12 `ImageApi.getItemImageUrl` + `UrlBuilder` (path shape, camelCase
 * query keys, SDK parameter order, NO api_key — the SDK never appends the
 * token to image URLs).
 */
class ImageUrlBuilderTest {

    private val base = "https://jf.example"

    @Test
    fun `builds the SDK-shaped path with camelCase params in SDK order`() {
        assertEquals(
            "$base/Items/0b0f2a75-5677-4c76-a416-a1c0d9d11111/Images/Primary?maxWidth=400&tag=pt&imageIndex=2",
            buildItemImageUrl(
                baseUrl = base,
                itemId = "0b0f2a75-5677-4c76-a416-a1c0d9d11111",
                imageType = "Primary",
                maxWidth = 400,
                tag = "pt",
                imageIndex = 2,
            ),
        )
    }

    @Test
    fun `null params are omitted like the SDK null-filter`() {
        assertEquals(
            "$base/Items/0b0f2a75-5677-4c76-a416-a1c0d9d11111/Images/Primary?maxWidth=200",
            buildItemImageUrl(base, "0b0f2a75-5677-4c76-a416-a1c0d9d11111", "Primary", maxWidth = 200),
        )
        assertEquals(
            "$base/Items/0b0f2a75-5677-4c76-a416-a1c0d9d11111/Images/Backdrop",
            buildItemImageUrl(base, "0b0f2a75-5677-4c76-a416-a1c0d9d11111", "Backdrop"),
        )
    }

    @Test
    fun `invalid inputs return empty like the jvmShared builder`() {
        assertEquals("", buildItemImageUrl(null, "0b0f2a75-5677-4c76-a416-a1c0d9d11111", "Primary", 400))
        assertEquals("", buildItemImageUrl(base, "not-a-uuid", "Primary", 400), "non-UUID item id refused")
        assertEquals("", buildItemImageUrl(base, "0b0f2a75-5677-4c76-a416-a1c0d9d11111", "Frobnicate", 400),
            "unknown image type refused")
    }

    @Test
    fun `known image types cover the sdk enum`() {
        assertEquals(
            setOf("Primary", "Art", "Backdrop", "Banner", "Logo", "Thumb", "Disc", "Box",
                "Screenshot", "Menu", "Chapter", "BoxRear", "Profile"),
            KNOWN_IMAGE_TYPES,
        )
    }

    @Test
    fun `guid check accepts dashed uuids only`() {
        assertTrue(isGuid("0B0F2A75-5677-4C76-A416-A1C0D9D11111"), "uppercase hex accepted")
        assertFalse(isGuid("0b0f2a7556774c76a416a1c0d9d11111"), "undashed form refused (UUID.fromString parity)")
        assertFalse(isGuid(""))
        assertFalse(isGuid("0b0f2a75-5677-4c76-a416-a1c0d9d1111g"))
    }

    @Test
    fun `compact 32-hex ids normalize to the dashed sdk form`() {
        // Wave 13C harness finding: Jellyfin 10.11 /Items responses carry
        // compact 32-hex ids; the builder must still emit the dashed URL the
        // JVM SDK produces (the server accepts both path forms).
        assertEquals(
            "$base/Items/0b0f2a75-5677-4c76-a416-a1c0d9d11111/Images/Primary?maxWidth=300",
            buildItemImageUrl(base, "0b0f2a7556774c76a416a1c0d9d11111", "Primary", maxWidth = 300),
        )
        assertEquals(
            "0b0f2a75-5677-4c76-a416-a1c0d9d11111",
            normalizeItemIdGuid("0b0f2a7556774c76a416a1c0d9d11111"),
        )
        assertEquals(
            "0b0f2a75-5677-4c76-a416-a1c0d9d11111",
            normalizeItemIdGuid("0b0f2a75-5677-4c76-a416-a1c0d9d11111"),
            "dashed input passes through unchanged",
        )
        assertEquals(null, normalizeItemIdGuid("0b0f2a75-5677-4c76-a416-a1c0d9d1111g"), "non-hex refused")
        assertEquals(null, normalizeItemIdGuid("0b0f2a75"), "truncated refused")
    }
}
