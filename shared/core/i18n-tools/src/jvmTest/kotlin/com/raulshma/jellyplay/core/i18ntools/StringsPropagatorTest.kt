package com.raulshma.jellyplay.core.i18ntools

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The zero-gap pin for the settings module's locale tables — the settings
 * tree with all locale entries present (259 keys × 8 locales),
 * kept there by `StringsPropagator` (the propagation tool whose root-build
 * repo-wide `checkStrings` task enforces the same at build level). Any
 * default-locale key added without its locale entries fails here with the
 * exact missing list; run `./gradlew propagateStrings` and translate the
 * PENDING TRANSLATION entries it inserts.
 */
class StringsPropagatorTest {

    /** The settings module's composeResources root, located from the repo root. */
    private fun composeResourcesDir(): File {
        val root = I18nResourceDiscovery.repoRoot(File(System.getProperty("user.dir")))
        val resources = root.resolve("shared/feature/settings/src/commonMain/composeResources")
        check(resources.isDirectory) { "settings composeResources missing: $resources" }
        return resources
    }

    @Test
    fun `every locale carries every counted default key`() {
        val gaps = StringsPropagator.findGaps(composeResourcesDir())
        assertEquals(
            emptyList(),
            gaps.map { gap -> gap.locale to gap.missingKeys },
            "locale tables drifted from the default locale — run " +
                "./gradlew propagateStrings and translate the PENDING entries",
        )
    }

    @Test
    fun `propagation fills gaps verbatim and never overwrites`() {
        val root = createTempDirectory("strings-propagator-test").toFile()
        val values = root.resolve("values").apply { mkdirs() }
        values.resolve("strings.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="settings_alpha">Alpha %1s</string>
                <string name="settings_beta" translatable="false">JellyPlay</string>
                <string name="diff_reset_only">default-only</string>
                <plurals name="settings_days">
                    <item quantity="one">%d day</item>
                    <item quantity="other">%d days</item>
                </plurals>
            </resources>
            """.trimIndent(),
        )
        val de = root.resolve("values-de").apply { mkdirs() }
        de.resolve("strings.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="settings_alpha">Alpha DE %1s</string>
            </resources>
            """.trimIndent(),
        )

        // Gap detection: only the untranslated counted key and the plurals —
        // the existing entry and the exempt classes are not gaps.
        val gaps = StringsPropagator.findGaps(root)
        assertEquals(listOf("de"), gaps.map { it.locale })
        assertEquals(listOf("settings_days"), gaps.single().missingKeys)

        // Write: the plurals element lands verbatim under the pending marker;
        // the existing translation is untouched; exempt keys never propagate.
        val counts = StringsPropagator.propagate(gaps)
        assertEquals(mapOf("de" to 1), counts)
        val propagated = de.resolve("strings.xml").readText()
        assertTrue("settings_alpha\">Alpha DE" in propagated, "existing translation must not be overwritten")
        assertTrue("<item quantity=\"one\">%d day</item>" in propagated, "the plurals element is copied verbatim")
        assertTrue(CountedKeyGrammar.PENDING_MARKER in propagated)
        assertTrue("settings_beta" !in propagated, "translatable=false keys never propagate")
        assertTrue("diff_reset_only" !in propagated, "diff_ keys never propagate")

        // Idempotent: a second pass finds no gap and writes nothing.
        assertEquals(emptyList(), StringsPropagator.findGaps(root).map { it.locale })
        assertEquals(emptyMap(), StringsPropagator.propagate(StringsPropagator.findGaps(root)))
        root.deleteRecursively()
    }
}
