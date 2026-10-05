package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.PlayerInputKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the platform key-code seam's contract: the reverse bridge is the
 * exact inverse of the forward one over the whole catalog, codes are
 * distinct per platform (a collision would silently merge two rows in the
 * capture dialog), and out-of-catalog codes degrade to `null` instead of a
 * wrong key.
 */
class PlayerKeyCodesTest {

    @Test
    fun reverse_is_exact_inverse_of_forward_for_every_catalog_key() {
        for (key in PlayerInputKey.entries) {
            assertEquals(key, playerInputKeyOf(playerKeyCodeOf(key)), "round-trip failed for $key")
        }
    }

    @Test
    fun forward_codes_are_distinct() {
        val codes = PlayerInputKey.entries.map { playerKeyCodeOf(it) }
        assertEquals(codes.size, codes.distinct().size, "two catalog keys share one platform code")
    }

    @Test
    fun out_of_catalog_codes_map_to_null() {
        assertNull(playerInputKeyOf(0), "KEYCODE_UNKNOWN must not resolve to a catalog key")
        assertNull(playerInputKeyOf(-1))
        assertNull(playerInputKeyOf(999_999))
    }
}
