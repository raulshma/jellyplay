package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integrity tests for the settings-search catalog: every aggregated item must
 * have a unique, non-blank id and carry resources + keywords. This suite pins
 * id uniqueness, resource/category cardinality, keywords, and the catalog's
 * aggregation shape (count + flat order).
 *
 * The catalog holds Compose-Resources
 * [org.jetbrains.compose.resources.StringResource]s whose accessors are
 * generated from strings.xml, so resolvability is compile-time guaranteed — a
 * stale resource reference breaks the build, not this test.
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
        // +1: the playback_mode row (Issue #1 — the delivery method gained its
        // Settings row + search entry). +1: the auto_download_check_now row
        // (gh-181 — the tri-state policy gained its check-now action row).
        assertEquals(396, items.size)
        // Curated flat order starts with the account/session pair, and the
        // aggregation is a pure concatenation of the decorated per-screen
        // groups (no dedup, no reordering).
        assertEquals("logout", items.first().id)
        // The aggregation is a pure concatenation of the derived groups —
        // no dedup, no reordering, no filtering. Per-val sums do not
        // partition the catalog: fused groups let vals like
        // ExternalEngineSearchItems be slices of their group, not disjoint
        // members.
        assertEquals(SettingsScreenGroups.all.sumOf { it.items.size }, items.size)
        assertEquals(ExperimentalSettingsSearchItems.last().id, items.last().id)
    }
}
