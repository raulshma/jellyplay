package com.raulshma.jellyplay.core.ui.settingssearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the fuzzy matcher against synthetic items that mirror the shape of the
 * real catalog (jargon-heavy titles, thin keyword lists, category text).
 *
 * The item lists themselves live in :feature:settings next to their screens
 * (aggregated by `SettingsSearchCatalog`, pinned by that module's own
 * `SettingsSearchCatalogTest`); core/ui only owns matching, so this suite
 * stays JVM-pure and dependency-free — no registry, resources or Robolectric.
 */
class SettingsSearchMatcherTest {

    private fun item(
        id: String,
        title: String,
        subtitle: String = "",
        keywords: List<String> = emptyList(),
        category: String = "Playback",
    ): ResolvedSettingsItem = ResolvedSettingsItem(
        item = SettingsSearchItem(
            id = id,
            titleRes = 0,
            subtitleRes = 0,
            categoryRes = 0,
            keywords = keywords,
            route = com.raulshma.jellyplay.core.ui.navigation.Route.Settings,
            icon = mockIcon,
        ),
        title = title,
        subtitle = subtitle,
        category = category,
    )

    // One shared dummy icon instance — the matcher never reads it.
    private val mockIcon get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "test",
        defaultWidth = androidx.compose.ui.unit.Dp.Unspecified,
        defaultHeight = androidx.compose.ui.unit.Dp.Unspecified,
        viewportWidth = 1f,
        viewportHeight = 1f,
    ).build()

    private val items = listOf(
        item(
            id = "audio_passthrough",
            title = "Audio Passthrough",
            subtitle = "Bitstream audio to your receiver",
            keywords = listOf("passthrough", "bitstream", "spdif", "eac3"),
        ),
        item(
            id = "frame_rate_matching",
            title = "Frame Rate Matching",
            subtitle = "Match display refresh to content",
            keywords = listOf("frame rate", "refresh rate", "match", "fps"),
        ),
        item(id = "decoder", title = "Decoder", subtitle = "Preferred video decoder"),
        item(
            id = "audio_delay",
            title = "Audio Delay",
            subtitle = "Offset audio relative to video",
            keywords = listOf("delay", "offset", "sync"),
        ),
        item(id = "dialogue_boost", title = "Dialogue Boost", subtitle = "Lift centre channel"),
        item(
            id = "streaming_quality",
            title = "Streaming Quality",
            subtitle = "Max transcode bitrate",
            keywords = listOf("bitrate", "transcode", "quality"),
        ),
    )

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
        // Keyword is "frame rate" (with space); query "frame rate" must hit it.
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
