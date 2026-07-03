package com.raulshma.jellyplay.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the fuzzy matcher against the real [SettingsSearchRegistry] — so coverage reflects actual
 * production data (titles, keywords, advanced flags) rather than hand-rolled fixtures.
 */
class SettingsSearchMatcherTest {

    private val items = SettingsSearchRegistry.items

    private fun idsFor(query: String): List<String> =
        SettingsSearchMatcher.search(query, items).map { it.id }

    private fun contains(query: String, id: String) = idsFor(query).contains(id)

    @Test fun `blank query returns nothing`() {
        assertTrue(SettingsSearchMatcher.search("", items).isEmpty())
        assertTrue(SettingsSearchMatcher.search("   ", items).isEmpty())
    }

    @Test fun `typo passthru matches audio passthrough`() {
        // Previously failed under strict `contains` — "passthru" is not a substring of any field.
        assertTrue("expected audio_passthrough: ${idsFor("passthru")}", contains("passthru", "audio_passthrough"))
    }

    @Test fun `typo framrate matches frame rate match`() {
        assertTrue("expected frame_rate_matching: ${idsFor("framrate")}", contains("framrate", "frame_rate_matching"))
    }

    @Test fun `split term frame rate matches merged keyword`() {
        // Registry keyword is "frame rate" (with space); query "frame rate" must hit it.
        assertTrue("expected frame_rate_matching: ${idsFor("frame rate")}", contains("frame rate", "frame_rate_matching"))
    }

    @Test fun `exact title term ranks the titled item first`() {
        assertEquals("decoder", idsFor("decoder").first())
    }

    @Test fun `multiword query is AND across tokens`() {
        // "audio delay" must match BOTH tokens; items matching only "audio" are excluded.
        val results = idsFor("audio delay")
        assertTrue("audio_delay expected: $results", results.contains("audio_delay"))
        // dialogue_boost has neither "audio" nor "delay" — must be absent.
        assertFalse("dialogue_boost should not match 'audio delay': $results", results.contains("dialogue_boost"))
    }

    @Test fun `gibberish query returns nothing`() {
        assertTrue(idsFor("zzzzz").isEmpty())
    }

    @Test fun `results are sorted best-first`() {
        // "streaming quality" should surface streaming_quality ahead of items matching only one token.
        val results = idsFor("streaming quality")
        assertTrue("streaming_quality expected in results: $results", results.contains("streaming_quality"))
        assertEquals("streaming_quality", results.first())
    }

    @Test fun `score is zero when no token matches`() {
        val someItem = items.first { it.id == "decoder" }
        assertEquals(0.0, SettingsSearchMatcher.scoreItem("network", someItem), 0.0)
    }
}
