package com.raulshma.jellyplay.feature.player.video.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the overflow menu's ONE exclusive submenu slot ([OverflowSubmenu] +
 * [overflowSubmenuFocusTarget]) — the fold that replaced the former five
 * parallel composition booleans (`showDialogueBoostSubmenu` /
 * `showNightModeSubmenu` / `showAudioNormalizationSubmenu` /
 * `showChannelMixSubmenu` / `showGesturesSubmenu`) and their five hand-copied
 * `isFirst*Render` first-render focus latches. The composition wiring itself
 * is comment-pinned (at [PlayerOverflowMenu]'s KDoc and the row click sites):
 * the module has no Compose harness for the in-window menu, matching the
 * language screen's `LanguageSettingsDialogSlotTest` precedent. Submenu
 * contents are static entry lists (no async load), so there is no
 * `focusEffectKeys`-style re-arm to pin — the focus choreography's only
 * extractable pure half is the slot-transition target fold.
 */
class PlayerOverflowMenuSlotTest {

    @Test
    fun `slot inventory - the five folded submenu booleans`() {
        assertEquals(
            setOf("Gestures", "DialogueBoost", "NightMode", "AudioNormalization", "ChannelMix"),
            OverflowSubmenu.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `one slot - opening a submenu structurally replaces the open one`() {
        var slot: OverflowSubmenu? = null
        slot = OverflowSubmenu.DialogueBoost
        slot = OverflowSubmenu.NightMode
        assertEquals(OverflowSubmenu.NightMode, slot)
        // The back-arrow row clears the slot; all slot state resets with the
        // panel (the early return sits above the remembers).
        slot = null
        assertEquals(null, slot)
    }

    @Test
    fun `focus target - first composition resolves to no grab`() {
        // Every fresh menu open (and the first composition run that the former
        // five latch booleans skipped by hand): no target, no grab.
        assertEquals(null, overflowSubmenuFocusTarget(previous = null, current = null))
    }

    @Test
    fun `focus target - open lands on the newly-opened submenu`() {
        assertEquals(
            OverflowSubmenu.Gestures,
            overflowSubmenuFocusTarget(previous = null, current = OverflowSubmenu.Gestures),
        )
    }

    @Test
    fun `focus target - close re-lands on the just-collapsed submenu's header row`() {
        assertEquals(
            OverflowSubmenu.ChannelMix,
            overflowSubmenuFocusTarget(previous = OverflowSubmenu.ChannelMix, current = null),
        )
    }

    @Test
    fun `focus target - direct switch goes to the newest submenu`() {
        // Where the five parallel latches each fired and fought over the
        // requester chain, the one slot effect resolves deterministically:
        // newest show wins.
        assertEquals(
            OverflowSubmenu.NightMode,
            overflowSubmenuFocusTarget(
                previous = OverflowSubmenu.AudioNormalization,
                current = OverflowSubmenu.NightMode,
            ),
        )
    }
}
