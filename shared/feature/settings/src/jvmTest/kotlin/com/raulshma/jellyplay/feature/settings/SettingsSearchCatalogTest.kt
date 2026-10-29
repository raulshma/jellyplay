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
     * The per-group size pins: every screen group's declared item count is
     * pinned individually, so a silent addition/removal fails at its own
     * group — a bump here is a deliberate, reviewed change at that one
     * group, and the per-item history lives in the VCS, not in this file.
     */
    @Test
    fun `aggregation preserves the verbatim move - the full catalog in flat order`() {
        val items = SettingsSearchCatalog.items
        // The per-group size pins, in [SettingsScreenGroups.all]'s curated
        // flat catalog order.
        assertEquals(4, SettingsScreenGroups.account.items.size)
        assertEquals(3, SettingsScreenGroups.integrations.items.size)
        assertEquals(5, SettingsScreenGroups.activityInsights.items.size)
        assertEquals(2, SettingsScreenGroups.systemCore.items.size)
        assertEquals(8, SettingsScreenGroups.systemScreensaver.items.size)
        assertEquals(2, SettingsScreenGroups.systemIdleAmbient.items.size)
        assertEquals(1, SettingsScreenGroups.systemDiscordPresence.items.size)
        assertEquals(6, SettingsScreenGroups.systemHooks.items.size)
        assertEquals(2, SettingsScreenGroups.jellyplaySync.items.size)
        assertEquals(14, SettingsScreenGroups.jellyplayFeatures.items.size)
        assertEquals(20, SettingsScreenGroups.homeDisplay.items.size)
        assertEquals(4, SettingsScreenGroups.homeNextUp.items.size)
        assertEquals(5, SettingsScreenGroups.homeLayout.items.size)
        assertEquals(8, SettingsScreenGroups.homeCards.items.size)
        assertEquals(20, SettingsScreenGroups.appearanceTheme.items.size)
        assertEquals(2, SettingsScreenGroups.appearanceNavigation.items.size)
        assertEquals(24, SettingsScreenGroups.appearanceLibrary.items.size)
        assertEquals(2, SettingsScreenGroups.appearancePerformance.items.size)
        assertEquals(2, SettingsScreenGroups.appearanceEyeCare.items.size)
        assertEquals(3, SettingsScreenGroups.appearanceNewsletter.items.size)
        assertEquals(53, SettingsScreenGroups.playbackPlayer.items.size)
        assertEquals(26, SettingsScreenGroups.playbackAdvancedVideo.items.size)
        assertEquals(42, SettingsScreenGroups.playbackEngine.items.size)
        assertEquals(3, SettingsScreenGroups.playbackSyncPlay.items.size)
        assertEquals(3, SettingsScreenGroups.playbackCasting.items.size)
        assertEquals(3, SettingsScreenGroups.playbackDvr.items.size)
        assertEquals(9, SettingsScreenGroups.playbackMediaSegments.items.size)
        assertEquals(29, SettingsScreenGroups.audio.items.size)
        assertEquals(6, SettingsScreenGroups.audioCache.items.size)
        assertEquals(3, SettingsScreenGroups.languageGeneral.items.size)
        assertEquals(4, SettingsScreenGroups.languageTrackSelection.items.size)
        assertEquals(13, SettingsScreenGroups.languageSubtitles.items.size)
        assertEquals(14, SettingsScreenGroups.notifications.items.size)
        assertEquals(7, SettingsScreenGroups.storageCache.items.size)
        assertEquals(11, SettingsScreenGroups.storageNetwork.items.size)
        assertEquals(16, SettingsScreenGroups.storageDownloads.items.size)
        assertEquals(7, SettingsScreenGroups.security.items.size)
        assertEquals(4, SettingsScreenGroups.backup.items.size)
        assertEquals(1, SettingsScreenGroups.about.items.size)
        assertEquals(5, SettingsScreenGroups.experimental.items.size)
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
