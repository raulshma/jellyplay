package com.raulshma.jellyplay.feature.details

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * The trailer-preview focus-gating state machine — the pure mount
 * decision (`trailerPreviewShouldMount`) and the port holder behind it:
 *
 *  - the interaction matrix's hard row: `trailerAutoplay` off mounts
 *    NOTHING, for every other input combination (the preview family is dead
 *    exactly as before the ambient preview);
 *  - non-TV is unchanged: immediate mount regardless of focus or window
 *    visibility;
 *  - TV with the `trailer_preview_on_focus` semantic off: immediate mount;
 *  - TV with the semantic on: focus gained mounts, focus lost unmounts, and
 *    a hidden window counts as unfocused (focus gained/lost/hidden — the
 *    plan's three events).
 */
class TrailerFocusPreviewTest {

    // --- interaction matrix: trailerAutoplay off → nothing mounts, ever ---

    @Test
    fun autoplayOff_mountsNothing_forEveryCombination() {
        for (focusPreview in listOf(false, true)) {
            for (isTv in listOf(false, true)) {
                for (focused in listOf(false, true)) {
                    for (windowVisible in listOf(false, true)) {
                        assertFalse(
                            trailerPreviewShouldMount(
                                trailerAutoplayEnabled = false,
                                focusPreviewEnabled = focusPreview,
                                isTv = isTv,
                                playTargetFocused = focused,
                                windowVisible = windowVisible,
                            ),
                            "autoplay off must never mount " +
                                "(focusPreview=$focusPreview, isTv=$isTv, focused=$focused, windowVisible=$windowVisible)",
                        )
                    }
                }
            }
        }
    }

    // --- non-TV: unchanged (immediate mount as today) ---

    @Test
    fun nonTv_mountsImmediately_regardlessOfFocusOrWindow() {
        for (focused in listOf(false, true)) {
            for (windowVisible in listOf(false, true)) {
                assertTrue(
                    trailerPreviewShouldMount(
                        trailerAutoplayEnabled = true,
                        focusPreviewEnabled = true,
                        isTv = false,
                        playTargetFocused = focused,
                        windowVisible = windowVisible,
                    ),
                )
            }
        }
    }

    // --- TV with the semantic off: immediate mount as today ---

    @Test
    fun tvWithSemanticOff_mountsImmediately() {
        for (focused in listOf(false, true)) {
            for (windowVisible in listOf(false, true)) {
                assertTrue(
                    trailerPreviewShouldMount(
                        trailerAutoplayEnabled = true,
                        focusPreviewEnabled = false,
                        isTv = true,
                        playTargetFocused = focused,
                        windowVisible = windowVisible,
                    ),
                )
            }
        }
    }

    // --- TV with the semantic on: the focus-gated machine ---

    @Test
    fun tvFocusGated_noFocus_noMount() {
        assertFalse(
            trailerPreviewShouldMount(
                trailerAutoplayEnabled = true,
                focusPreviewEnabled = true,
                isTv = true,
                playTargetFocused = false,
                windowVisible = true,
            ),
        )
    }

    @Test
    fun tvFocusGated_focusGained_mounts() {
        assertTrue(
            trailerPreviewShouldMount(
                trailerAutoplayEnabled = true,
                focusPreviewEnabled = true,
                isTv = true,
                playTargetFocused = true,
                windowVisible = true,
            ),
        )
    }

    @Test
    fun tvFocusGated_focusLost_unmounts() {
        val port = TrailerFocusPreviewState()
        port.playTargetFocused = true
        assertTrue(port.playTargetFocused)
        // The button's focus-exit write (and its onDispose reset) drops the
        // flag; the machine unmounts on the next read.
        port.playTargetFocused = false
        assertFalse(
            trailerPreviewShouldMount(
                trailerAutoplayEnabled = true,
                focusPreviewEnabled = true,
                isTv = true,
                playTargetFocused = port.playTargetFocused,
                windowVisible = true,
            ),
        )
    }

    @Test
    fun tvFocusGated_windowHidden_countsAsUnfocused() {
        // Focus cannot survive a hidden window — the mount drops even with
        // a stale focused flag.
        assertFalse(
            trailerPreviewShouldMount(
                trailerAutoplayEnabled = true,
                focusPreviewEnabled = true,
                isTv = true,
                playTargetFocused = true,
                windowVisible = false,
            ),
        )
    }

    @Test
    fun port_defaultsToUnfocused() {
        // An unwired (default) port reads unfocused — DetailBackdrop treats
        // an absent port as NOT focus-gated, but a provided-never-written
        // one keeps the preview held back until the button reports.
        assertFalse(TrailerFocusPreviewState().playTargetFocused)
    }
}
