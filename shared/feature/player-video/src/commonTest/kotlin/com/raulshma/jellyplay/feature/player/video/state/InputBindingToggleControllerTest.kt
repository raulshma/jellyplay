package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerBinding
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.core.model.SwipeSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [InputBindingToggleController] — the input-binding
 * quick toggle (the `TvSeekControllerTest` pattern: recording probe lambdas,
 * no coroutines). Pins the behaviors the former inline lambdas carried:
 *
 *  - a known-binding flip pushes the mirrored map AND requests the persist
 *    by (bindingId, enabled);
 *  - unknown ids and no-op flips write and persist nothing (the identity
 *    gate — a stale sheet row racing a mapping reset);
 *  - the persist request carries the id and flag, never a whole map (the
 *    store's read-modify-write verb re-derives the blob inside the edit).
 */
class InputBindingToggleControllerTest {

    private val initialMap = PlayerInputMap(
        bindings = listOf(
            PlayerBinding(PlayerInputDefaults.ID_TAP, InputPattern.Tap, PlayerAction.TOGGLE_CONTROLS),
            PlayerBinding(
                PlayerInputDefaults.ID_SWIPE_VOLUME,
                InputPattern.VerticalSwipe(SwipeSide.RIGHT),
                PlayerAction.SWIPE_VOLUME,
            ),
        ),
    )

    private val updatedMaps = mutableListOf<PlayerInputMap>()

    /** Records the (bindingId, enabled) pairs the persist request carries. */
    private val persistRequests = mutableListOf<Pair<String, Boolean>>()

    private fun controller(map: PlayerInputMap = initialMap) = InputBindingToggleController(
        getMap = { map },
        updateMap = { updatedMaps += it },
        // The explicit parameter types are the pin: the controller must hand
        // over id + flag, not a whole PlayerInputMap blob.
        requestPersist = { bindingId: String, enabled: Boolean -> persistRequests += bindingId to enabled },
    )

    // ---- known-binding flip ----

    @Test
    fun `flipping a known binding updates the map and requests the persist by id`() {
        controller().setEnabled(PlayerInputDefaults.ID_TAP, enabled = false)

        assertEquals(1, updatedMaps.size)
        val flipped = updatedMaps.single().bindings.first { it.id == PlayerInputDefaults.ID_TAP }
        assertEquals(false, flipped.enabled)
        // The untouched row rides along unchanged.
        assertEquals(initialMap.bindings.last(), updatedMaps.single().bindings.last())
        assertEquals(listOf(PlayerInputDefaults.ID_TAP to false), persistRequests)
    }

    @Test
    fun `the persist request carries the id and flag - not a whole map`() {
        controller().setEnabled(PlayerInputDefaults.ID_SWIPE_VOLUME, enabled = false)

        assertEquals(1, updatedMaps.size)
        assertEquals(listOf(PlayerInputDefaults.ID_SWIPE_VOLUME to false), persistRequests)
    }

    // ---- the identity gate ----

    @Test
    fun `an unknown binding id writes and persists nothing`() {
        controller().setEnabled("key.no_such_row", enabled = false)

        assertTrue(updatedMaps.isEmpty())
        assertTrue(persistRequests.isEmpty())
    }

    @Test
    fun `a flip to the current value writes and persists nothing`() {
        controller().setEnabled(PlayerInputDefaults.ID_TAP, enabled = true)

        assertTrue(updatedMaps.isEmpty())
        assertTrue(persistRequests.isEmpty())
    }
}
