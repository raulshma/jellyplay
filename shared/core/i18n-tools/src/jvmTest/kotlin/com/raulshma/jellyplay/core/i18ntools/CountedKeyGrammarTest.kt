package com.raulshma.jellyplay.core.i18ntools

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the counted-key grammar in isolation, against synthetic tables. The
 * ratchet pins the read against the live tree and the propagator test the
 * write; what nothing else pins is the PENDING-region read
 * ([CountedKeyGrammar.translatedKeys] vs [CountedKeyGrammar.presentKeys] —
 * the rule that keeps a repo-wide propagation from faking parity) and the
 * union grammar's element-shape handling (paired + self-closing, plurals
 * count once, exempt classes).
 */
class CountedKeyGrammarTest {

    /** Writes one qualifier dir with a single `strings.xml` over [elements]. */
    private fun writeValues(root: File, dir: String, vararg elements: String): File =
        root.resolve(dir).apply {
            mkdirs()
            resolve("strings.xml").writeText(
                buildString {
                    appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                    appendLine("<resources>")
                    elements.forEach { appendLine(it) }
                    appendLine("</resources>")
                },
            )
        }

    @Test
    fun `pending-region entries count present but not translated`() {
        val root = createTempDirectory("counted-key-grammar-test").toFile()
        writeValues(root, "values", "<string name=\"alpha\">Alpha</string>")
        // A locale table carrying one translated key plus a pending block in
        // exactly the shape the propagator writes (marker comment, then the
        // verbatim copies, before the closing tag).
        writeValues(
            root, "values-de",
            "<string name=\"beta\">Beta DE</string>",
            "<!-- ${CountedKeyGrammar.PENDING_MARKER}: copied verbatim from the default locale by " +
                "StringsPropagator — translate each entry and fold it into the groups above. -->",
            "<string name=\"alpha\">Alpha</string>",
        )

        assertEquals(setOf("alpha"), CountedKeyGrammar.countedKeys(root.resolve("values")))
        // The writer's read sees the pending copy (never insert it twice)…
        assertEquals(
            setOf("beta", "alpha"),
            CountedKeyGrammar.presentKeys(root.resolve("values-de")),
        )
        // …the ratchet's read still counts it missing.
        assertEquals(setOf("beta"), CountedKeyGrammar.translatedKeys(root.resolve("values-de")))
        root.deleteRecursively()
    }

    @Test
    fun `counted grammar unions paired and self-closing shapes and applies exemptions`() {
        val root = createTempDirectory("counted-key-grammar-test").toFile()
        writeValues(
            root, "values",
            "<string name=\"paired\">text</string>",
            "<string name=\"empty\"/>",
            "<plurals name=\"days\"><item quantity=\"one\">1</item><item quantity=\"other\">%d</item></plurals>",
            "<string name=\"brand\" translatable=\"false\">JellyPlay</string>",
            "<string name=\"diff_reset\">default-only</string>",
            "<string name=\"settings_jellyplay\">JellyPlay</string>",
        )
        assertEquals(
            setOf("paired", "empty", "days"),
            CountedKeyGrammar.countedKeys(root.resolve("values")),
        )
        root.deleteRecursively()
    }
}
