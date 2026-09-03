package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the invariants of the [PlayMethod] taxonomy — the three Jellyfin play
 * methods whose display names surface in the player UI and whose identities
 * gate stream caching ([com.raulshma.jellyplay.feature.player.video.engine]
 * scopes byte-level caching to Direct Play / Direct Stream only):
 *
 *  - Every entry maps to its exact human-readable display name.
 *  - The enum round-trips through its kotlinx-generated name serializer.
 */
class PlaybackInfoTest {

    @Test
    fun `every play method has its display name`() {
        assertEquals("Direct Play", PlayMethod.DIRECT_PLAY.displayName())
        assertEquals("Direct Stream", PlayMethod.DIRECT_STREAM.displayName())
        assertEquals("Transcode", PlayMethod.TRANSCODE.displayName())
    }

    @Test
    fun `play method serializes by name and round-trips`() {
        val json = kotlinx.serialization.json.Json
        for (method in PlayMethod.entries) {
            val encoded = json.encodeToString(PlayMethod.serializer(), method)
            assertEquals("\"${method.name}\"", encoded)
            assertEquals(method, json.decodeFromString(PlayMethod.serializer(), encoded))
        }
    }
}
