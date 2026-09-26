package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the `## What's New` body-table grammar: the single authoring artifact
 * is the GitHub release body, so this parser is what turns it into the guided
 * entry cards (update sheet, post-update sheet, Settings archive).
 */
class WhatsNewBodyParsingTest {

    private val sampleBody = """
        ## Highlights

        Prose that must be ignored by the card parser.

        ## What's New

        | Title | Category | Summary | Where to find it | Icon | Link |
        |---|---|---|---|---|---|
        | Discover Rows | new | Build your own Home rows from saved filters | Settings → Home Screen → Discover Rows | rows | settings.discoverRows |
        | Steadier Home | fix | Surprise picks no longer flicker while Home fetches | | | |
        | Smarter audio focus | | Playback coordinates across players | While playing | speech | |

        ## Fixes

        - trailing sections are out of scope
    """.trimIndent()

    @Test
    fun `parses the whats-new table into entries with mapped columns`() {
        val entries = parseWhatsNewEntries(sampleBody)

        assertEquals(3, entries.size)

        val discover = entries[0]
        assertEquals("discover-rows", discover.id)
        assertEquals(WhatsNewCategory.NEW, discover.category)
        assertEquals("Discover Rows", discover.title)
        assertEquals("Build your own Home rows from saved filters", discover.summary)
        assertEquals("Settings → Home Screen → Discover Rows", discover.howTo)
        assertEquals("rows", discover.icon)
        assertEquals("settings.discoverRows", discover.target)

        // Blank optional cells become null, not empty strings.
        val steadier = entries[1]
        assertEquals("steadier-home", steadier.id)
        assertEquals(WhatsNewCategory.FIX, steadier.category)
        assertNull(steadier.howTo)
        assertNull(steadier.icon)
        assertNull(steadier.target)

        // Unknown/blank category degrades to IMPROVEMENT.
        assertEquals(WhatsNewCategory.IMPROVEMENT, entries[2].category)
    }

    @Test
    fun `column order is free and unknown columns are ignored`() {
        val body = """
            ## What's New

            | Link | Icon | Where | Summary | Category | Title | Notes |
            |---|---|---|---|---|---|---|
            | settings.home | home | Settings → Home Screen | One hub for Home settings | new | Home Screen hub | ignored |
        """.trimIndent()

        val entry = parseWhatsNewEntries(body).single()

        assertEquals("Home Screen hub", entry.title)
        assertEquals("One hub for Home settings", entry.summary)
        assertEquals("Settings → Home Screen", entry.howTo)
        assertEquals("home", entry.icon)
        assertEquals("settings.home", entry.target)
    }

    @Test
    fun `bold and code wrappers are stripped from cells`() {
        val body = """
            ## What's New

            | Title | Summary |
            |---|---|
            | **Reader 2.0** | `faster` page turns |
        """.trimIndent()

        val entry = parseWhatsNewEntries(body).single()

        assertEquals("Reader 2.0", entry.title)
        assertEquals("faster page turns", entry.summary)
    }

    @Test
    fun `escaped pipes stay in their cell and unescape to a literal pipe`() {
        val body = """
            ## What's New

            | Title | Summary | Where to find it |
            |---|---|---|
            | Filters \| sort | Tune Home \| Saved filters | Settings → Home |
        """.trimIndent()

        val entry = parseWhatsNewEntries(body).single()

        assertEquals("Filters | sort", entry.title)
        assertEquals("Tune Home | Saved filters", entry.summary)
        assertEquals("Settings → Home", entry.howTo)
    }

    @Test
    fun `an escaped pipe at the end of a row does not eat the closing pipe`() {
        val body = """
            ## What's New

            | Title | Summary |
            |---|---|
            | Trailing escape \| | still the title |
        """.trimIndent()

        val entry = parseWhatsNewEntries(body).single()

        assertEquals("Trailing escape |", entry.title)
        assertEquals("still the title", entry.summary)
    }

    @Test
    fun `an escaped backslash before a pipe keeps the pipe a delimiter`() {
        val body = """
            ## What's New

            | Title | Summary |
            |---|---|
            | Path C:\\Movies | two cells |
        """.trimIndent()

        val entry = parseWhatsNewEntries(body).single()

        assertEquals("Path C:\\Movies", entry.title)
        assertEquals("two cells", entry.summary)
    }

    @Test
    fun `rows without a title are dropped`() {
        val body = """
            ## What's New

            | Title | Summary |
            |---|---|
            | Kept | good |
            | | no title, dropped |
            | Also kept | fine |
        """.trimIndent()

        assertEquals(listOf("Kept", "Also kept"), parseWhatsNewEntries(body).map { it.title })
    }

    @Test
    fun `heading depth and apostrophe variants are tolerated`() {
        val body = """
            Intro line.

            ### What’s New

            | Title |
            |---|
            | Deep heading entry |
        """.trimIndent()

        assertEquals(listOf("Deep heading entry"), parseWhatsNewEntries(body).map { it.title })
    }

    @Test
    fun `section ends at the next heading so later tables are ignored`() {
        val body = """
            ## What's New

            No table here — just prose.

            ## Fixes

            | Title |
            |---|
            | Must not become a card |
        """.trimIndent()

        assertTrue(parseWhatsNewEntries(body).isEmpty())
    }

    @Test
    fun `a delimiter-looking row without a real header is rejected`() {
        // `|---|---|` as the FIRST line has no header row above it to map
        // columns from, so there is nothing parseable.
        val body = """
            ## What's New

            |---|---|
            | Title | Summary |
        """.trimIndent()

        assertTrue(parseWhatsNewEntries(body).isEmpty())
    }

    @Test
    fun `blank bodies and bodies without the section yield no entries`() {
        assertTrue(parseWhatsNewEntries(null).isEmpty())
        assertTrue(parseWhatsNewEntries("").isEmpty())
        assertTrue(parseWhatsNewEntries("## Highlights\n\n- no whats-new section").isEmpty())
        // A table before the section never counts.
        assertTrue(parseWhatsNewEntries("| Title |\n|---|\n| Before |\n\n## What's New\n\nprose").isEmpty())
    }

    @Test
    fun `a title column is required`() {
        val body = """
            ## What's New

            | Summary | Category |
            |---|---|
            | No title column anywhere | new |
        """.trimIndent()

        assertTrue(parseWhatsNewEntries(body).isEmpty())
    }

    // ---- whatsNewReleaseFromNotes ----

    @Test
    fun `maps a GitHub release onto a feed release deriving entries from the body`() {
        val release = whatsNewReleaseFromNotes(
            version = "v0.11.2",
            date = "2026-09-26",
            title = "Fresh coat of paint",
            body = sampleBody,
        )!!

        assertEquals("0.11.2", release.version)
        assertEquals("2026-09-26", release.date)
        assertEquals("Fresh coat of paint", release.title)
        assertEquals(sampleBody, release.body)
        assertEquals(3, release.entries.size)
    }

    @Test
    fun `a body without the table maps to an entry-less release that still has prose`() {
        val release = whatsNewReleaseFromNotes(
            version = "0.11.0",
            date = "2026-09-05",
            title = null,
            body = "## Highlights\n\n- plain old release notes",
        )!!

        assertTrue(release.entries.isEmpty())
        assertEquals("- plain old release notes", release.body!!.lines().last())
    }

    @Test
    fun `blank version or body yields null`() {
        assertNull(whatsNewReleaseFromNotes("", "2026-09-26", "t", "body"))
        assertNull(whatsNewReleaseFromNotes("0.11.2", "2026-09-26", "t", "   "))
        assertNull(whatsNewReleaseFromNotes(null, null, null, null))
    }
}
