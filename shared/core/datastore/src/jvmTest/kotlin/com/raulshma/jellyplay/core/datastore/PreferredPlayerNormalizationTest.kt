package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.datastore.playback.normalizePreferredPlayer
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.platformEngineSupport
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the read-time preferred-engine clamp ([normalizePreferredPlayer], the
 * single choke point behind [PlaybackStore.read]). Written against
 * [platformEngineSupport] rather than hardcoded expectations, so it asserts
 * the clamp's contract on whichever platform runs the suite (desktop JVM in
 * this lane: MPV-only, and the Android-restored values below are exactly the
 * ones that must degrade).
 */
class PreferredPlayerNormalizationTest {

    @Test
    fun `a stored engine the platform ships passes through`() {
        platformEngineSupport.engines.forEach { engine ->
            assertEquals(engine, normalizePreferredPlayer(engine.name))
        }
    }

    @Test
    fun `engines this platform does not ship fall back to the platform default`() {
        PlayerType.entries
            .filter { it !in platformEngineSupport.engines }
            .forEach { engine ->
                assertEquals(
                    platformEngineSupport.default,
                    normalizePreferredPlayer(engine.name),
                    "stored '$engine' is not shipped here — must clamp to the default",
                )
            }
    }

    @Test
    fun `null and unknown values fall back to the platform default`() {
        assertEquals(platformEngineSupport.default, normalizePreferredPlayer(null))
        assertEquals(platformEngineSupport.default, normalizePreferredPlayer("NOT_AN_ENGINE"))
    }
}
