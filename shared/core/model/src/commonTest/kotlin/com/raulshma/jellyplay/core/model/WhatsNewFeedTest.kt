package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the What's-New feed contract: lenient decoding (hand-edited remote
 * document), per-item degradation (drop bad releases/entries, fall back
 * categories), version normalization, newest-first ordering, and the
 * bundled-vs-fetched merge precedence.
 */
class WhatsNewFeedTest {

    private val fullJson = """
        {
          "releases": [
            {
              "version": "v0.11.2",
              "date": "2026-09-26",
              "title": "Fresh coat of paint",
              "entries": [
                {
                  "id": "discover-rows",
                  "category": "new",
                  "title": "Discover Rows",
                  "summary": "Pin custom rows to Home.",
                  "howTo": "Settings → Home Screen → Discover Rows",
                  "icon": "sparkles",
                  "target": "settings.discoverRows"
                },
                { "id": "fix-1", "category": "fix", "title": "Fixed a crash", "summary": "" },
                { "id": "impr-1", "category": "improvement", "title": "Faster search" },
                { "id": "mystery", "category": "refactor", "title": "Internal rewrite" },
                { "id": "no-title", "category": "new", "summary": "entry without a title" },
                { "title": "Untitled sibling", "summary": "id defaults to the title" }
              ],
              "futureField": true
            },
            { "version": "", "entries": [{ "title": "orphan" }] },
            { "version": "0.11.1", "entries": [] },
            { "entries": [{ "title": "no version" }] }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses a full document with normalization and per-entry degradation`() {
        val feed = parseWhatsNewFeed(fullJson)!!

        assertEquals(1, feed.releases.size)
        val release = feed.releases[0]
        assertEquals("0.11.2", release.version) // leading v stripped
        assertEquals("2026-09-26", release.date)
        assertEquals("Fresh coat of paint", release.title)

        // Entries: 6 authored, 1 dropped (no title) → 5 survive.
        assertEquals(5, release.entries.size)
        val discover = release.entries[0]
        assertEquals(WhatsNewCategory.NEW, discover.category)
        assertEquals("settings.discoverRows", discover.target)
        assertEquals(WhatsNewCategory.FIX, release.entries[1].category)
        assertEquals(WhatsNewCategory.IMPROVEMENT, release.entries[2].category)
        // Unknown category degrades to IMPROVEMENT, not dropped.
        assertEquals(WhatsNewCategory.IMPROVEMENT, release.entries[3].category)
        // Missing id falls back to the title so list keys stay stable.
        assertEquals("Untitled sibling", release.entries[4].id)
    }

    @Test
    fun `blank and null documents yield the empty feed, not null`() {
        assertEquals(WhatsNewFeed(), parseWhatsNewFeed(null))
        assertEquals(WhatsNewFeed(), parseWhatsNewFeed(""))
        assertEquals(WhatsNewFeed(), parseWhatsNewFeed("   "))
    }

    @Test
    fun `structurally invalid documents return null`() {
        assertNull(parseWhatsNewFeed("not json at all"))
        assertNull(parseWhatsNewFeed("[1,2,3]"))
    }

    @Test
    fun `releaseFor matches after tag normalization`() {
        val feed = parseWhatsNewFeed(fullJson)!!
        assertEquals("0.11.2", feed.releaseFor("0.11.2")?.version)
        assertEquals("0.11.2", feed.releaseFor("v0.11.2")?.version)
        assertNull(feed.releaseFor("0.11.1"))
    }

    @Test
    fun `sortedByNewest orders by dotted version not document order`() {
        val feed = parseWhatsNewFeed(
            """
            { "releases": [
              { "version": "0.9.0", "entries": [{ "title": "a" }] },
              { "version": "0.11.2", "entries": [{ "title": "b" }] },
              { "version": "0.11.10", "entries": [{ "title": "c" }] },
              { "version": "0.11.2-alpha.1", "entries": [{ "title": "d" }] }
            ] }
            """.trimIndent(),
        )!!

        val ordered = feed.sortedByNewest().releases.map { it.version }
        // 0.11.10 > 0.11.2 numerically; the stable release beats its alpha.
        assertEquals(listOf("0.11.10", "0.11.2", "0.11.2-alpha.1", "0.9.0"), ordered)
    }

    @Test
    fun `merge lets the newer feed override per version and unions the rest`() {
        val older = parseWhatsNewFeed(
            """
            { "releases": [
              { "version": "0.11.0", "entries": [{ "title": "old A" }] },
              { "version": "0.11.1", "entries": [{ "title": "old B" }] }
            ] }
            """.trimIndent(),
        )!!
        val newer = parseWhatsNewFeed(
            """
            { "releases": [
              { "version": "0.11.1", "entries": [{ "title": "corrected B" }] }
            ] }
            """.trimIndent(),
        )!!

        val merged = mergeWhatsNewFeeds(older, newer)
        assertEquals(listOf("0.11.1", "0.11.0"), merged.releases.map { it.version })
        // The fetched entry set replaces the bundled one wholesale.
        assertEquals("corrected B", merged.releaseFor("0.11.1")?.entries?.single()?.title)
        assertEquals("old A", merged.releaseFor("0.11.0")?.entries?.single()?.title)
    }

    @Test
    fun `empty releases array decodes to an empty feed`() {
        val feed = parseWhatsNewFeed("""{"releases": []}""")!!
        assertTrue(feed.releases.isEmpty())
    }

    @Test
    fun `a body-only release survives with entries derived from its table`() {
        val feed = parseWhatsNewFeed(
            """
            { "releases": [
              { "version": "0.11.2", "date": "2026-09-26",
                "body": "## What's New\n\n| Title | Category |\n|---|---|\n| Card one | new |" }
            ] }
            """.trimIndent(),
        )!!

        val release = feed.releases.single()
        assertEquals("0.11.2", release.version)
        assertEquals("Card one", release.entries.single().title)
        assertTrue(release.body!!.startsWith("## What's New"))
    }

    @Test
    fun `a release with neither entries nor a body is dropped`() {
        val feed = parseWhatsNewFeed(
            """
            { "releases": [
              { "version": "0.11.2", "entries": [], "body": " " }
            ] }
            """.trimIndent(),
        )!!

        assertTrue(feed.releases.isEmpty())
    }
}
