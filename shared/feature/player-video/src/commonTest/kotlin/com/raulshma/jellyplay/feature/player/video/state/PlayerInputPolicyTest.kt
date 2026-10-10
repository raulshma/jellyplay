package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerBinding
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputKey
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.core.model.SwipeSide
import com.raulshma.jellyplay.core.model.TouchZone
import com.raulshma.jellyplay.core.model.WheelAxis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the input-mapping resolver (`PlayerInputPolicy`) and the preset
 * engine (`PlayerInputDefaults.applyGestureModePreset`): candidate ladders,
 * enabled/NONE semantics, duplicate detection, and the exact rows the
 * demoted [GestureMode] presets flip.
 */
class PlayerInputPolicyTest {

    // ── Candidate ladders ────────────────────────────────────────────────

    @Test
    fun keyCandidates_exactModifiersFirst_thenPlainRow() {
        val candidates = PlayerInputPolicy.keyCandidates(
            key = PlayerInputKey.M,
            isCtrlPressed = true,
            isShiftPressed = false,
            isAltPressed = false,
        )
        assertEquals(
            listOf(
                InputPattern.Key(PlayerInputKey.M, ctrl = true),
                InputPattern.Key(PlayerInputKey.M),
            ),
            candidates,
        )
    }

    @Test
    fun keyCandidates_plainPress_isSingleCandidate() {
        assertEquals(
            listOf(InputPattern.Key(PlayerInputKey.M)),
            PlayerInputPolicy.keyCandidates(PlayerInputKey.M, isCtrlPressed = false, isShiftPressed = false, isAltPressed = false),
        )
    }

    @Test
    fun keyCandidates_seekFamily_foldsModifiersIntoOneCanonicalPattern() {
        // The modifier steps are event-time semantics; the seek keys resolve
        // against ONE modifier-less row no matter what mods are held.
        for (mods in listOf(
            Triple(false, false, false),
            Triple(true, false, false),
            Triple(false, true, false),
            Triple(true, true, true),
        )) {
            assertEquals(
                listOf(InputPattern.Key(PlayerInputKey.L)),
                PlayerInputPolicy.keyCandidates(PlayerInputKey.L, mods.first, mods.second, mods.third),
                "mods=$mods",
            )
        }
    }

    @Test
    fun wheelCandidates_shiftFallsBackToThePlainRow() {
        assertEquals(
            listOf(
                InputPattern.Wheel(WheelAxis.VERTICAL, shift = true),
                InputPattern.Wheel(WheelAxis.VERTICAL, shift = false),
            ),
            PlayerInputPolicy.wheelCandidates(WheelAxis.VERTICAL, isShiftPressed = true),
        )
        assertEquals(
            listOf(InputPattern.Wheel(WheelAxis.VERTICAL, shift = false)),
            PlayerInputPolicy.wheelCandidates(WheelAxis.VERTICAL, isShiftPressed = false),
        )
    }

    // ── Resolution semantics ─────────────────────────────────────────────

    private val swipeLeft = InputPattern.VerticalSwipe(SwipeSide.LEFT)

    @Test
    fun resolve_enabledBindingWins_disabledFallsThrough() {
        val map = PlayerInputMap(
            bindings = listOf(
                PlayerBinding("a", swipeLeft, PlayerAction.SWIPE_BRIGHTNESS, enabled = false),
            ),
        )
        assertNull(PlayerInputPolicy.resolve(map, listOf(swipeLeft)))
    }

    @Test
    fun resolve_noneActionAbsorbs_theLadderStops() {
        val map = PlayerInputMap(
            bindings = listOf(
                PlayerBinding("exact", InputPattern.Key(PlayerInputKey.M, ctrl = true), PlayerAction.NONE),
                PlayerBinding("plain", InputPattern.Key(PlayerInputKey.M), PlayerAction.TOGGLE_MUTE),
            ),
        )
        // The exact row is bound to NONE — an explicit unbind shadows the
        // plain fallback behind it.
        val binding = PlayerInputPolicy.resolve(map, PlayerInputPolicy.keyCandidates(PlayerInputKey.M, isCtrlPressed = true, isShiftPressed = false, isAltPressed = false))
        assertEquals("exact", binding?.id)
        assertNull(PlayerInputPolicy.resolve(map, swipeLeft))
    }

    @Test
    fun gate_disabledOrNoneArmIsOff() {
        val map = PlayerInputMap(
            bindings = listOf(
                PlayerBinding("off", swipeLeft, PlayerAction.SWIPE_BRIGHTNESS, enabled = false),
                PlayerBinding("none", InputPattern.VerticalSwipe(SwipeSide.RIGHT), PlayerAction.NONE, enabled = true),
            ),
        )
        assertFalse(PlayerInputPolicy.isActionEnabled(map, swipeLeft))
        assertFalse(PlayerInputPolicy.isActionEnabled(map, InputPattern.VerticalSwipe(SwipeSide.RIGHT)))
        // Issue #171's exact scenario: brightness off, volume stays on when
        // only the brightness row is disabled.
        val issue171 = PlayerInputMap(
            bindings = listOf(
                PlayerBinding("bright", InputPattern.VerticalSwipe(SwipeSide.LEFT), PlayerAction.SWIPE_BRIGHTNESS, enabled = false),
                PlayerBinding("vol", InputPattern.VerticalSwipe(SwipeSide.RIGHT), PlayerAction.SWIPE_VOLUME),
            ),
        )
        assertFalse(PlayerInputPolicy.isActionEnabled(issue171, InputPattern.VerticalSwipe(SwipeSide.LEFT)))
        assertTrue(PlayerInputPolicy.isActionEnabled(issue171, InputPattern.VerticalSwipe(SwipeSide.RIGHT)))
    }

    // ── Write guard (the editor's duplicate block) ───────────────────────

    // The per-pattern save rule the binding picker enforces (one row per
    // pattern); the whole-map guard is [PlayerInputMap.hasNoDuplicates],
    // which InputBindingsViewModel checks before persisting.
    private fun canSave(map: PlayerInputMap, pattern: InputPattern, ignoreBindingId: String? = null): Boolean =
        map.bindings.none { it.pattern == pattern && it.id != ignoreBindingId }

    @Test
    fun writeGuard_blocksExactDuplicates() {
        val map = PlayerInputMap(
            bindings = listOf(
                PlayerBinding("a", swipeLeft, PlayerAction.SWIPE_BRIGHTNESS),
                PlayerBinding("b", swipeLeft, PlayerAction.SWIPE_VOLUME),
            ),
        )
        assertFalse(map.hasNoDuplicates())
        assertFalse(canSave(map, swipeLeft))
        // Ignoring one duplicate row still leaves the other in the way —
        // canSave only clears when the pattern is free.
        assertFalse(canSave(map, swipeLeft, ignoreBindingId = "b"))
        val freeMap = map.copy(bindings = map.bindings.drop(1))
        assertTrue(freeMap.hasNoDuplicates())
        assertTrue(canSave(freeMap, swipeLeft, ignoreBindingId = "b"))
    }

    // ── Defaults + the demoted preset ────────────────────────────────────

    @Test
    fun defaults_allMode_everyTouchRowOn_issue171RowsSeparable() {
        val gates = PlayerInputGates.of(PlayerInputDefaults.defaultMap())
        assertTrue(gates.tap && gates.pinch && gates.longPress)
        assertTrue(gates.doubleTapBack && gates.doubleTapCenter && gates.doubleTapForward)
        assertTrue(gates.swipeBrightness)
        assertTrue(gates.swipeVolume)
        assertTrue(gates.swipeSeek)
        assertTrue(gates.edgeLeft && gates.edgeRight)
        assertTrue(gates.wheelVolume && gates.wheelSeek)
    }

    @Test
    fun preset_tapOnly_killsExactlyTheSwipeTier() {
        val preset = PlayerInputDefaults.applyGestureModePreset(PlayerInputDefaults.defaultMap(), GestureMode.TAP_ONLY)
        val gates = PlayerInputGates.of(preset)
        assertFalse(gates.swipeBrightness)
        assertFalse(gates.swipeVolume)
        assertFalse(gates.swipeSeek)
        assertFalse(gates.edgeLeft)
        assertFalse(gates.edgeRight)
        // Tap tier + every non-touch surface untouched.
        assertTrue(gates.tap && gates.pinch && gates.doubleTapForward)
        assertTrue(gates.wheelVolume && gates.wheelSeek)
    }

    @Test
    fun preset_none_killsTheWholeTouchSurface_only() {
        val preset = PlayerInputDefaults.applyGestureModePreset(PlayerInputDefaults.defaultMap(), GestureMode.NONE)
        val gates = PlayerInputGates.of(preset)
        assertFalse(gates.tap)
        assertFalse(gates.pinch)
        assertFalse(gates.doubleTapBack)
        assertFalse(gates.doubleTapCenter)
        assertFalse(gates.doubleTapForward)
        assertFalse(gates.longPress)
        assertFalse(gates.holdBack)
        assertFalse(gates.holdForward)
        assertFalse(gates.swipeBrightness)
        assertFalse(gates.swipeVolume)
        assertFalse(gates.swipeSeek)
        assertFalse(gates.edgeLeft)
        assertFalse(gates.edgeRight)
        // Non-touch surfaces untouched.
        assertTrue(gates.wheelVolume && gates.wheelSeek)
        // A fresh NONE read (the absent-blob legacy fallback) agrees with the
        // preset — the two paths to "mode = NONE" must produce the same map.
        assertEquals(
            PlayerInputDefaults.applyGestureModePreset(PlayerInputDefaults.defaultMap(), GestureMode.NONE),
            PlayerInputDefaults.defaultMap(gestureMode = GestureMode.NONE),
        )
    }

    @Test
    fun preset_preservesRowIdentity_andIndividualUnbinds() {
        val customized = PlayerInputDefaults.defaultMap().let { map ->
            map.copy(
                bindings = map.bindings.map {
                    if (it.id == PlayerInputDefaults.ID_SWIPE_SEEK) {
                        it.copy(action = PlayerAction.NONE, enabled = true)
                    } else {
                        it
                    }
                },
            )
        }
        val preset = PlayerInputDefaults.applyGestureModePreset(customized, GestureMode.ALL)
        // Same ids, same row count — the preset flips flags, never mutates
        // bindings or drops rows.
        assertEquals(customized.bindings.map { it.id }, preset.bindings.map { it.id })
        val seekRow = preset.bindings.first { it.id == PlayerInputDefaults.ID_SWIPE_SEEK }
        assertEquals(PlayerAction.NONE, seekRow.action)
        assertTrue(seekRow.enabled)
    }

    @Test
    fun legacyFallback_tapOnlyConfig_seedsTheMatchingMap() {
        val map = PlayerInputDefaults.defaultMap(
            gestureMode = GestureMode.TAP_ONLY,
            holdSpeedEnabled = true,
            doubleTapHoldSeekEnabled = false,
        )
        val gates = PlayerInputGates.of(map)
        assertTrue(gates.tap)
        assertFalse(gates.swipeBrightness)
        assertFalse(gates.swipeVolume)
        assertTrue(gates.longPress, "hold-speed pref stays on")
        assertFalse(gates.holdBack, "double-tap-hold pref off")
        assertFalse(gates.holdForward, "double-tap-hold pref off")
    }

    @Test
    fun touchZonePatterns_areStructurallyDistinct() {
        // Duplicate detection leans on pattern equality — the zone split must
        // produce distinct keys.
        val map = PlayerInputMap(
            bindings = listOf(
                PlayerBinding("l", InputPattern.DoubleTap(TouchZone.LEFT), PlayerAction.SEEK_BACK),
                PlayerBinding("r", InputPattern.DoubleTap(TouchZone.RIGHT), PlayerAction.SEEK_FORWARD),
            ),
        )
        assertTrue(canSave(map, InputPattern.DoubleTap(TouchZone.CENTER)))
        assertFalse(canSave(map, InputPattern.DoubleTap(TouchZone.LEFT)))
    }
}
