package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [resolveHighlightScrollIndex], the pure deep-link resolver behind
 * `rememberHighlightScrollIndex` (the composable only caches it): first-group
 * wins on duplicate ids, absent/null ids resolve to -1 (no scroll), and the
 * advanced-group offset applies only to a found target.
 */
class HighlightScrollTest {

    private val groups = listOf(
        setOf("playback.autoplay", "playup.next"),
        setOf("appearance.theme", "appearance.pets"),
        setOf("privacy.cache"),
    )

    // ── Target present ──────────────────────────────────────────────────

    @Test
    fun `target in the first group resolves to index 0`() {
        assertEquals(0, resolveHighlightScrollIndex("playback.autoplay", groups))
    }

    @Test
    fun `target in a middle group resolves to its group index`() {
        assertEquals(1, resolveHighlightScrollIndex("appearance.pets", groups))
    }

    @Test
    fun `target in the last group resolves to the last index`() {
        assertEquals(2, resolveHighlightScrollIndex("privacy.cache", groups))
    }

    @Test
    fun `duplicate id resolves to the first group containing it`() {
        val duplicated = listOf(setOf("a"), setOf("b", "a"), setOf("a"))

        assertEquals(0, resolveHighlightScrollIndex("a", duplicated))
    }

    // ── Target absent / degenerate input ────────────────────────────────

    @Test
    fun `absent id resolves to -1`() {
        assertEquals(-1, resolveHighlightScrollIndex("nope", groups))
    }

    @Test
    fun `null id resolves to -1 without consulting the groups`() {
        assertEquals(-1, resolveHighlightScrollIndex(null, groups))
    }

    @Test
    fun `empty group list resolves to -1`() {
        assertEquals(-1, resolveHighlightScrollIndex("privacy.cache", emptyList()))
    }

    // ── Advanced-group offset ────────────────────────────────────────────

    @Test
    fun `adjustForAdvanced shifts a found target`() {
        // The conditional-hidden-advanced-group case: a hidden group ahead of
        // the target shifts the renderable index by one.
        assertEquals(2, resolveHighlightScrollIndex("appearance.theme", groups) { it + 1 })
    }

    @Test
    fun `adjustForAdvanced is not applied to a miss`() {
        assertEquals(-1, resolveHighlightScrollIndex("nope", groups) { it + 1 })
        assertEquals(-1, resolveHighlightScrollIndex(null, groups) { it + 1 })
    }
}
