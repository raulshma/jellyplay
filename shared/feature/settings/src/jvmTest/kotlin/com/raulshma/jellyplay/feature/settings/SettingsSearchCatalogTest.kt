package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integrity tests for the settings-search catalog: every aggregated item must
 * have a unique, non-blank id and carry resources + keywords. The item lists
 * moved verbatim from core/ui's old `SettingsSearchRegistry`; this suite pins
 * that nothing was lost or corrupted in the move and that future items keep
 * the invariants.
 *
 * Legacy version (Android unit test) reflected `R.string` ids and parsed
 * `strings.xml` off disk to prove each item resolved a non-blank string. The
 * catalog now holds Compose-Resources [org.jetbrains.compose.resources.StringResource]s
 * whose accessors are generated from those same strings.xml files, so
 * resolvability is compile-time guaranteed — a stale resource reference
 * breaks the build, not this test. The XML parsing therefore died with the R
 * ids; what remains pinned is id uniqueness, resource/category cardinality,
 * keywords, and the verbatim-move shape (count + flat order).
 */
class SettingsSearchCatalogTest {

    @Test
    fun `every item has a unique non-blank id`() {
        val ids = SettingsSearchCatalog.items.map { it.id }
        assertTrue(ids.all { it.isNotBlank() }, "blank ids present")
        assertEquals(
            ids.size,
            ids.toSet().size,
            "duplicate ids: " + ids.groupBy { it }.filterValues { it.size > 1 }.keys,
        )
    }

    @Test
    fun `items span multiple distinct categories`() {
        // Compile-time resolvability guarantees each categoryRes is a real
        // generated accessor; what is worth pinning at runtime is that the
        // catalog did not collapse onto a single category (copy-paste guard).
        // Distinct accessors are distinct lazy objects, so identity is a
        // faithful distinct-category proxy.
        val categories = SettingsSearchCatalog.items.map { it.categoryRes }.toSet()
        assertTrue(categories.size >= 2, "expected several ss_cat_* groups, got ${categories.size}")
    }

    @Test
    fun `every item carries keywords`() {
        SettingsSearchCatalog.items.forEach { item ->
            assertTrue(item.keywords.isNotEmpty(), "empty keywords for ${item.id}")
        }
    }

    /**
     * The catalog-wide size pin: it exists to catch silent catalog-wide item
     * additions/removals — a bump here is a deliberate, reviewed change, and
     * the per-item history lives in the VCS, not in this file.
     */
    @Test
    fun `aggregation preserves the verbatim move - the full catalog in flat order`() {
        val items = SettingsSearchCatalog.items
        assertEquals(314, items.size)
        // Curated flat order starts with the account/session pair that used to
        // open the old registry, and the aggregation is a pure concatenation
        // of the decorated per-screen groups (no dedup, no reordering).
        assertEquals("logout", items.first().id)
        // The aggregation is a pure concatenation of the derived groups —
        // no dedup, no reordering, no filtering. (The retired hand-written
        // per-val sum no longer partitions: post-fusion, vals like
        // ExternalEngineSearchItems are slices of their group, not disjoint
        // members.)
        assertEquals(SettingsScreenGroups.all.sumOf { it.items.size }, items.size)
        assertEquals(ExperimentalSettingsSearchItems.last().id, items.last().id)
    }
}
