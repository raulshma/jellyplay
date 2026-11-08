package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_performance_profile_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_performance_profile_title
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
        assertEquals(43, SettingsScreenGroups.homeDisplay.items.size)
        assertEquals(6, SettingsScreenGroups.homeNextUp.items.size)
        assertEquals(5, SettingsScreenGroups.homeLayout.items.size)
        assertEquals(12, SettingsScreenGroups.homeCards.items.size)
        assertEquals(21, SettingsScreenGroups.appearanceTheme.items.size)
        assertEquals(13, SettingsScreenGroups.appearanceNavigation.items.size)
        assertEquals(52, SettingsScreenGroups.appearanceLibrary.items.size)
        // The master level row lives in the hub's level group now; the
        // appearance screen's group keeps only the Reduce Motion toggle.
        assertEquals(1, SettingsScreenGroups.appearancePerformance.items.size)
        assertEquals(2, SettingsScreenGroups.appearanceEyeCare.items.size)
        assertEquals(3, SettingsScreenGroups.appearanceNewsletter.items.size)
        // The Performance hub's five groups: the always-visible master dial,
        // then the four expert groups (3 motion + 5 images + 3 ambient + the
        // playback-domain buffer quartet).
        assertEquals(1, SettingsScreenGroups.performanceLevel.items.size)
        assertEquals(3, SettingsScreenGroups.performanceMotion.items.size)
        assertEquals(5, SettingsScreenGroups.performanceImages.items.size)
        assertEquals(3, SettingsScreenGroups.performanceAmbient.items.size)
        assertEquals(4, SettingsScreenGroups.playbackBuffers.items.size)
        assertEquals(83, SettingsScreenGroups.playbackPlayer.items.size)
        // 26 + the issue #186 pair: the spec-backed performance-profile row
        // beside Streaming Quality and the residual Re-detect action row
        // (admitted only while the profile is AUTO, but always declared).
        assertEquals(28, SettingsScreenGroups.playbackAdvancedVideo.items.size)
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

    /**
     * The video-performance-profile search entry (issue #186): the spec side
     * declares the semantics + resource keys in core:datastore, and the fused
     * UI row (`PlaybackRows.VideoPerformanceProfile`, a separate wave) binds
     * them into the catalog — the binding's `verifyResourceKey` fails fast at
     * catalog init unless the declared keys match these generated resources.
     * Pinned here so the binding contract is stated and checked before the
     * row lands; the catalog's per-group count pin above flips with the row.
     */
    @Test
    fun `the video performance profile spec entry declares the search faces the fused row must bind`() {
        val spec = PlaybackPreferenceSpecs.searchEntries.single { it.id == "video_performance_profile" }
        assertEquals("ss_performance_profile_title", spec.titleKey)
        assertEquals("ss_performance_profile_subtitle", spec.subtitleKey)
        assertEquals("ss_cat_playback", spec.categoryKey)
        assertEquals(PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS, spec.routeKind)
        assertTrue(spec.isAdvanced, "the profile row hides behind the advanced-settings gate")
        assertTrue(spec.keywords.isNotEmpty(), "the profile entry carries no keywords")
        // The declared keys ARE the generated resources' keys — the resource
        // exists, and the future binding cannot silently swap a different
        // string in under the spec.
        assertEquals("ss_performance_profile_title", Res.string.ss_performance_profile_title.key)
        assertEquals("ss_performance_profile_subtitle", Res.string.ss_performance_profile_subtitle.key)
    }
}
