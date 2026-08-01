package com.raulshma.jellyplay.core.ui.settingssearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the fuzzy matcher against the real [SettingsSearchRegistry] — so coverage reflects actual
 * production data (titles, keywords, advanced flags) rather than hand-rolled fixtures.
 *
 * The registry stores `@StringRes` ids, so the test resolves them to their default-locale (English)
 * text by reading the bundled `values/strings.xml` and mapping each `R.string` field name to its
 * value. This keeps the matcher test JVM-pure (no Robolectric/Android Context) while still running
 * against real production text exactly as the app resolves it at runtime.
 */
class SettingsSearchMatcherTest {

    /**
     * Build a resolver `(Int) -> String` over the real `R.string` ids by reflecting the field names
     * (e.g. `ss_logout_title`) and reading each value from the bundled default `strings.xml`.
     */
    private val resolve: (Int) -> String = run {
        // name -> resource id, via reflection on the generated R.string class.
        val nameToId: Map<String, Int> = com.raulshma.jellyplay.core.ui.R.string::class.java
            .declaredFields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.name to (it.get(null) as Int) }
        // name -> English value, read from the default resource file on disk.
        val nameToValue: Map<String, String> = run {
            val file = java.io.File("src/main/res/values/strings.xml")
            require(file.exists()) { "Cannot locate default strings.xml at ${file.absolutePath}" }
            val root = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
                .documentElement
            val map = mutableMapOf<String, String>()
            val nodes = root.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as org.w3c.dom.Element
                val n = el.getAttribute("name")
                if (n.isNotEmpty()) map[n] = el.textContent ?: ""
            }
            map
        }
        // resource id -> English value
        val idToValue: Map<Int, String> = buildMap {
            nameToId.forEach { (name, id) -> nameToValue[name]?.let { put(id, it) } }
        }
        return@run { id: Int -> idToValue[id] ?: "" }
    }

    private val items = SettingsSearchRegistry.items.resolve(resolve)

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
