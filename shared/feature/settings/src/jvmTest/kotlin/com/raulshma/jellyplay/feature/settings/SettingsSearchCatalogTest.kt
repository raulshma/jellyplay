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

    @Test
    fun `aggregation preserves the verbatim move - all 257 items in flat order`() {
        val items = SettingsSearchCatalog.items
        // The old core/ui registry held 259 items; the aggregation must have
        // kept every one (the 260th is the wave-18C video-cache-size row).
        // v0.10.6 then consolidated the 5 synthwave/soothing/monochrome mode
        // + accent entries into theme_style + style_accent (257).
        // Bump this count when you deliberately add items.
        assertEquals(257, items.size)
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
