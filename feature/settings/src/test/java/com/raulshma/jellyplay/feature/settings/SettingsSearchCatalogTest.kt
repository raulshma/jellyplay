package com.raulshma.jellyplay.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integrity tests for the settings-search catalog: every aggregated item must
 * have a unique, non-blank id and resolvable strings. The item lists moved
 * verbatim from core/ui's old `SettingsSearchRegistry`; this suite pins that
 * nothing was lost or corrupted in the move and that future items keep the
 * invariants (id uniqueness, resolvable resources).
 *
 * JVM-only, using the same trick the old core/ui matcher test used: the
 * `R.string` ids are reflected back to resource names, and the default-locale
 * values are parsed from `strings.xml` on disk — title/subtitle strings live
 * in this module, the `ss_cat_*` categories in core/ui.
 */
class SettingsSearchCatalogTest {

    /** resource name -> English value, parsed from a default `values/strings.xml` on disk. */
    private fun parseStrings(path: String): Map<String, String> {
        val file = java.io.File(path)
        require(file.exists()) { "Cannot locate $path at ${file.absolutePath}" }
        val root = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement
        val map = mutableMapOf<String, String>()
        val nodes = root.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as org.w3c.dom.Element
            val name = el.getAttribute("name")
            if (name.isNotEmpty()) map[name] = el.textContent ?: ""
        }
        return map
    }

    private val settingsStrings = parseStrings("src/main/res/values/strings.xml")
    private val coreUiStrings = parseStrings("../../core/ui/src/main/res/values/strings.xml")

    /** resource id -> value, for `R` classes of both modules that own catalog strings. */
    private fun idToValue(rClass: Class<*>, values: Map<String, String>): Map<Int, String> {
        val nameToId = rClass.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.name to (it.get(null) as Int) }
        return buildMap { nameToId.forEach { (name, id) -> values[name]?.let { put(id, it) } } }
    }

    private val settingsValues = idToValue(R.string::class.java, settingsStrings)
    private val coreUiValues = idToValue(
        com.raulshma.jellyplay.core.ui.R.string::class.java,
        coreUiStrings,
    )

    /**
     * A handful of items reuse pre-existing core/ui strings as their
     * title/subtitle (e.g. the synthwave accent rows and the media-segment
     * labels), so titles resolve against EITHER module's resources.
     */
    private val titleValues = settingsValues + coreUiValues

    @Test
    fun `every item has a unique non-blank id`() {
        val ids = SettingsSearchCatalog.items.map { it.id }
        assertTrue("blank ids present", ids.all { it.isNotBlank() })
        assertEquals(
            "duplicate ids: " + ids.groupBy { it }.filterValues { it.size > 1 }.keys,
            ids.size,
            ids.toSet().size,
        )
    }

    @Test
    fun `every item resolves a non-blank title and subtitle`() {
        SettingsSearchCatalog.items.forEach { item ->
            val title = titleValues[item.titleRes]
            val subtitle = titleValues[item.subtitleRes]
            assertTrue("unresolved title for ${item.id}", !title.isNullOrBlank())
            assertTrue("unresolved subtitle for ${item.id}", !subtitle.isNullOrBlank())
        }
    }

    @Test
    fun `every item resolves a non-blank category from core ui`() {
        SettingsSearchCatalog.items.forEach { item ->
            val category = coreUiValues[item.categoryRes]
            assertTrue("unresolved category for ${item.id}", !category.isNullOrBlank())
        }
    }

    @Test
    fun `every item carries keywords`() {
        SettingsSearchCatalog.items.forEach { item ->
            assertTrue("empty keywords for ${item.id}", item.keywords.isNotEmpty())
        }
    }

    @Test
    fun `aggregation preserves the verbatim move - all 259 items in flat order`() {
        val items = SettingsSearchCatalog.items
        // The old core/ui registry held 259 items; the aggregation must have
        // kept every one. Bump this count when you deliberately add items.
        assertEquals(259, items.size)
        // Curated flat order starts with the account/session pair that used to
        // open the old registry, and the aggregation is a pure concatenation
        // of the per-screen lists (no dedup, no reordering).
        assertEquals("logout", items.first().id)
        assertEquals(
            AccountSearchItems.size + IntegrationsSearchItems.size +
                ActivityInsightsSearchItems.size + SystemSearchItems.size +
                AppearanceSettingsSearchItems.size + PlaybackSettingsSearchItems.size +
                MpvEngineSearchItems.size + VlcEngineSearchItems.size +
                ExoPlayerEngineSearchItems.size + SyncPlaySearchItems.size +
                CastingSearchItems.size + LiveTvSearchItems.size +
                AudioSettingsSearchItems.size + LanguageSettingsSearchItems.size +
                NotificationSettingsSearchItems.size + StorageSettingsSearchItems.size +
                SecuritySettingsSearchItems.size + BackupSettingsSearchItems.size +
                AboutSearchItems.size + ExperimentalSettingsSearchItems.size,
            items.size,
        )
        assertEquals(ExperimentalSettingsSearchItems.last().id, items.last().id)
    }
}
