package com.raulshma.jellyplay.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the reader chrome's auto-hide timeout selection
 * ([ReaderPrefsSnapshot.controlsAutoHideTimeoutMs]): the pref knob's default
 * is the long-standing 4 s, and the knob folds through the shared player
 * TV doubling (core:player-contract's controlsAutoHideTimeoutMs — the same
 * fold the VOD and live players pass their preference through) so a TV user
 * keeps controls twice as long as a touch user. The LaunchedEffect shell
 * around the delay stays presentation ([AutoHideControlsEffect]); the
 * SELECTION is what is pinned here.
 */
class ReaderAutoHidePolicyTest {

    @Test
    fun `the default snapshot hides after the long-standing 4 seconds`() {
        assertEquals(DEFAULT_READER_CONTROLS_TIMEOUT_MS, ReaderPrefsSnapshot().controlsTimeoutMs)
        assertEquals(4_000L, ReaderPrefsSnapshot().controlsAutoHideTimeoutMs(isTv = false))
    }

    @Test
    fun `tv doubles the timeout`() {
        assertEquals(8_000L, ReaderPrefsSnapshot().controlsAutoHideTimeoutMs(isTv = true))
    }

    @Test
    fun `a custom knob folds through the tv doubling`() {
        val snapshot = ReaderPrefsSnapshot(controlsTimeoutMs = 7_000L)
        assertEquals(7_000L, snapshot.controlsAutoHideTimeoutMs(isTv = false))
        assertEquals(14_000L, snapshot.controlsAutoHideTimeoutMs(isTv = true))
    }
}
