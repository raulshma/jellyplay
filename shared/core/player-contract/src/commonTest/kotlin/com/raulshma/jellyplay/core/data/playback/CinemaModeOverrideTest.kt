package com.raulshma.jellyplay.core.data.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the D14 one-shot semantics: exactly one consume per arm, and a
 * never-armed holder reads false (the "tests that don't wire it" default).
 */
class CinemaModeOverrideTest {

    @Test
    fun consume_isTrueExactlyOncePerArm() {
        val override = CinemaModeOverride()
        assertFalse(override.consume(), "an unarmed holder never forces")

        override.arm()
        assertTrue(override.consume(), "the load after arm() forces")
        assertFalse(override.consume(), "the flag is spent — later loads follow the global pref")
    }

    @Test
    fun rearm_rearmsAfterConsume() {
        val override = CinemaModeOverride()
        override.arm()
        assertTrue(override.consume())

        override.arm()
        assertTrue(override.consume())
        assertFalse(override.consume())
    }
}
