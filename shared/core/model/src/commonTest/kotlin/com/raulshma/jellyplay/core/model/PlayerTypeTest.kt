package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Pins the invariants of [PlayerType.fromStoredName] — the persisted-name
 * decoder every preference restore path funnels through:
 *
 *  - The legacy `"INTERNAL"` alias maps to [PlayerType.EXO_PLAYER] (pre-KMP
 *    backups stored the ExoPlayer engine under that name).
 *  - Exact enum names resolve to their entry.
 *  - Anything unknown (renamed/removed entry, corrupted store) falls back to
 *    [PlayerType.EXO_PLAYER] rather than throwing.
 *  - The lookup is case-sensitive on the canonical name (the store always
 *    writes `name`).
 */
class PlayerTypeTest {

    @Test
    fun `legacy INTERNAL alias maps to ExoPlayer`() {
        assertEquals(PlayerType.EXO_PLAYER, PlayerType.fromStoredName("INTERNAL"))
    }

    @Test
    fun `canonical names resolve to their entries`() {
        assertEquals(PlayerType.EXO_PLAYER, PlayerType.fromStoredName("EXO_PLAYER"))
        assertEquals(PlayerType.MPV, PlayerType.fromStoredName("MPV"))
        assertEquals(PlayerType.LIBVLC, PlayerType.fromStoredName("LIBVLC"))
        assertEquals(PlayerType.EXTERNAL, PlayerType.fromStoredName("EXTERNAL"))
    }

    @Test
    fun `null and unknown names fall back to ExoPlayer`() {
        assertEquals(PlayerType.EXO_PLAYER, PlayerType.fromStoredName(""))
        assertEquals(PlayerType.EXO_PLAYER, PlayerType.fromStoredName("VOBSUB_ENGINE"))
        assertEquals(PlayerType.EXO_PLAYER, PlayerType.fromStoredName("exo_player"))
    }

    @Test
    fun `every entry resolves to itself`() {
        for (type in PlayerType.entries) {
            assertEquals(type, PlayerType.fromStoredName(type.name))
        }
    }

    @Test
    fun `every entry carries presentation metadata`() {
        for (type in PlayerType.entries) {
            assertNotNull(type.displayName, type.name)
            assertNotNull(type.description, type.name)
        }
    }
}
