package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the invariants of the intro/credit timestamp models — the server-side
 * "skip" feature payloads (Jellyfin `Intros`/`Credits` endpoints):
 *
 *  - [IntroTimestamps.hasIntro] and [CreditTimestamps.hasCredits] are true
 *    only when the end tick is STRICTLY greater than the start tick; a
 *    zero/zero payload (the server's "no segment" shape) reads as absent, and
 *    an inverted range reads as absent rather than crashing the skip UI.
 *  - Wire names carry the Jellyfin PascalCase `@SerialName`s (ItemId,
 *    IntroStartTicks, CreditStartTicks, …) — decode from raw JSON to pin them.
 */
class PlayerFeatureModelsTest {

    @Test
    fun `hasIntro is true only for a strictly positive range`() {
        assertTrue(
            IntroTimestamps(itemId = "i", introStartTicks = 100, introEndTicks = 200).hasIntro,
        )
        assertFalse(
            IntroTimestamps(itemId = "i", introStartTicks = 0, introEndTicks = 0).hasIntro,
        )
        assertFalse(
            IntroTimestamps(itemId = "i", introStartTicks = 200, introEndTicks = 200).hasIntro,
        )
        assertFalse(
            IntroTimestamps(itemId = "i", introStartTicks = 300, introEndTicks = 200).hasIntro,
        )
    }

    @Test
    fun `hasCredits is true only for a strictly positive range`() {
        assertTrue(
            CreditTimestamps(itemId = "i", creditStartTicks = 100, creditEndTicks = 200).hasCredits,
        )
        assertFalse(
            CreditTimestamps(itemId = "i", creditStartTicks = 0, creditEndTicks = 0).hasCredits,
        )
        assertFalse(
            CreditTimestamps(itemId = "i", creditStartTicks = 200, creditEndTicks = 200).hasCredits,
        )
    }

    @Test
    fun `intro timestamps decode the PascalCase wire shape`() {
        val json = kotlinx.serialization.json.Json
        val decoded = json.decodeFromString(
            IntroTimestamps.serializer(),
            """{"ItemId":"item-1","IntroStartTicks":1000000,"IntroEndTicks":2000000,""" +
                """"ShowSkipPromptAtTicks":1100000,"HideSkipPromptAtTicks":1900000}""",
        )
        assertEquals("item-1", decoded.itemId)
        assertEquals(1_000_000L, decoded.introStartTicks)
        assertEquals(2_000_000L, decoded.introEndTicks)
        assertTrue(decoded.hasIntro)
    }

    @Test
    fun `credit timestamps decode the PascalCase wire shape`() {
        val json = kotlinx.serialization.json.Json
        val decoded = json.decodeFromString(
            CreditTimestamps.serializer(),
            """{"ItemId":"item-2","CreditStartTicks":5000000,"CreditEndTicks":9000000}""",
        )
        assertEquals("item-2", decoded.itemId)
        assertEquals(5_000_000L, decoded.creditStartTicks)
        assertEquals(9_000_000L, decoded.creditEndTicks)
    }
}
