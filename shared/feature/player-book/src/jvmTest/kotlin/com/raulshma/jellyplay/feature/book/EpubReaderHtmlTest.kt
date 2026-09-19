package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.feature.book.epub.EpubReaderHtml
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Pins [EpubReaderHtml] — the pure halves of the self-contained reader page:
 * the FNV artifact-name hash (a stable value: the desktop writer only
 * rewrites `reader-*.html` when the name moves, so a hash drift silently
 * rewrites on every boot or never again) and the `</script>` escaping that
 * keeps vendored bundles from ending the inline tag early. The end-to-end
 * build test then pins the marker contract against the real template.
 */
class EpubReaderHtmlTest {

    @Test
    fun `file name hash is stable for known inputs`() {
        assertEquals("reader--340d631b7bdddcdb.html", EpubReaderHtml.fileName(""))
        assertEquals("reader--5bcf27b97f5542f5.html", EpubReaderHtml.fileName("hello"))
    }

    @Test
    fun `file name is deterministic and sensitive to the document bytes`() {
        val html = "<html><body>reader page</body></html>"
        assertEquals(EpubReaderHtml.fileName(html), EpubReaderHtml.fileName(html))
        // One byte moves the name — a resource change must produce a new file.
        assertNotEquals(EpubReaderHtml.fileName(html), EpubReaderHtml.fileName("$html "))
        assertNotEquals(EpubReaderHtml.fileName("a"), EpubReaderHtml.fileName("b"))
    }

    @Test
    fun `inlined scripts escape embedded closers`() {
        // A literal `</script>` inside a JS string would end the inline tag
        // early; `<\/script>` is a no-op escape in JS strings/regexes/templates.
        assertEquals(
            "<script>document.write('<\\/script>')</script>",
            EpubReaderHtml.inlineScript("document.write('</script>')"),
        )
        // The substitution is blind by design — any `</script` prefix would
        // break the tag, escaped or not.
        assertEquals(
            "<script>var s = '<\\/scripting>';</script>",
            EpubReaderHtml.inlineScript("var s = '</scripting>';"),
        )
        // Nothing to escape: the body rides verbatim between the tags.
        assertEquals(
            "<script>var ok = 1;</script>",
            EpubReaderHtml.inlineScript("var ok = 1;"),
        )
    }

    @Test
    fun `the template carries exactly the three injection markers`() {
        // The replace contract is only as good as the markers it targets —
        // read the shipped template (the EpubReaderScriptTest convention).
        val template = File("src/commonMain/composeResources/files/epubjs/reader.html").readText()
        listOf("jszip", "epub", "reader").forEach { name ->
            assertTrue(
                "<!--JELLYPLAY-INJECT:$name-->" in template,
                "reader.html must keep the $name injection marker",
            )
        }
    }

    @Test
    fun `build inlines every marker and leaves only the tag closers`() = runTest {
        val page = EpubReaderHtml.build()
        assertTrue("JELLYPLAY-INJECT" !in page, "every marker must be replaced by its script body")
        assertEquals(3, Regex("<script>").findAll(page).count(), "jszip + epub + reader inline")
        // Exactly the three closers the inliner appends: any other raw
        // `</script` would have ended a tag mid-bundle.
        assertEquals(3, Regex("</script").findAll(page).count(), "only the three appended closers may stay raw")
        // Deterministic document → deterministic artifact name (desktop keys
        // its cache file on it).
        assertEquals(EpubReaderHtml.fileName(page), EpubReaderHtml.fileName(EpubReaderHtml.build()))
    }
}
