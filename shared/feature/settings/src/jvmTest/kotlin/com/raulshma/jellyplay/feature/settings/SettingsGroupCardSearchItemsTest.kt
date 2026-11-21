package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_audio_player
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_storage
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Code
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_network_diagnostics
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_verbose_logging_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_verbose_logging_title
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The group-card search-item derivation (`SettingsSearchItemGroup.cardItem` —
 * ADR-0017's group-grain rule): the rendered labeled card of an advancedOnly
 * group becomes ONE searchable item that lands on the group's FIRST member
 * row. This suite pins the derivation's honesty against the real declared
 * group (storage.networkTuning — the "Network diagnostics" card), the
 * fail-fast legs (partial faces, non-uniform member routes, id collision,
 * member disagreement), the dispatch fold, and the recents round-trip with a
 * persisted card id.
 *
 * The catalog-level pins (which cards exist, their faces and landing ids)
 * live in `SettingsSearchCatalogTest` and
 * `SettingsCatalogScreenContractTest.groupCardItems`; this file holds the
 * DERIVATION's contract — what the group machine does with the faces it is
 * handed.
 */
class SettingsGroupCardSearchItemsTest {

    // ── 1. The honest derivation, against the real declared group ──────

    @Test
    fun `the network diagnostics card derives its faces from its member rows`() {
        val group = SettingsScreenGroups.storageNetworkTuning
        val card = requireNotNull(group.cardItem)
        val first = group.items.first()

        // GROUP-grain identity: the card id IS the group's id, never a row
        // id, and it enters no itemIds/admissions (the screens' scroll,
        // expand and count derivations stay row-id-shaped).
        assertEquals("storage.networkTuning", card.id)
        assertTrue(card.id !in group.itemIdSet)
        assertEquals("settings_network_diagnostics", card.titleRes.key)
        assertEquals("settings_storage_network", card.subtitleRes.key)
        assertEquals(listOf("diagnostics", "network", "cellular", "metered", "timeout", "verbose"), card.keywords)

        // Shared member facts, not second declarations.
        assertSame(first.categoryRes, card.categoryRes)
        assertSame(first.icon, card.icon)
        assertEquals(first.platforms, card.platforms)

        // The advancedOnly slot shape: the hit auto-arms the gate exactly
        // like its member rows' hits.
        assertTrue(card.isAdvanced)
        assertTrue(group.items.all { it.isAdvanced })

        // The landing: the hosting route with the FIRST member row id baked
        // in — the expand + scroll + glow path is the existing row-id
        // machinery.
        val route = assertIs<Route.StorageSettings>(card.route)
        assertEquals(first.id, route.highlightSettingId)
        assertEquals("cellular_download_warning", route.highlightSettingId)
        assertSame(StorageRows.CellularDownloadWarning.icon, card.icon)
    }

    @Test
    fun `every declared card lands on its group's first member row`() {
        SettingsScreenGroups.all.forEach { group ->
            val card = group.cardItem ?: return@forEach
            val baked = when (val route = card.route) {
                is Route.HomeSettings -> route.highlightSettingId
                is Route.AudioSettings -> route.highlightSettingId
                is Route.DetailDisplaySettings -> route.highlightSettingId
                is Route.StorageSettings -> route.highlightSettingId
                is Route.AppearanceSettings -> route.highlightSettingId
                else -> null
            }
            assertEquals(
                group.itemIds.first(),
                baked,
                "${card.id}'s baked landing drifted from its group's first member row",
            )
            assertTrue(group.itemIdSet.contains(baked), "${card.id}'s landing id must be a member row id")
        }
    }

    // ── 2. The landing fold ─────────────────────────────────────────────

    @Test
    fun `the landing fold maps card ids to the first member row and row ids to themselves`() {
        assertEquals(
            "cellular_download_warning",
            groupCardLandingRowId("storage.networkTuning"),
        )
        // A row id folds to identity (null → the dispatch keeps the id)…
        assertNull(groupCardLandingRowId(StorageRows.CellularDownloadWarning.id))
        assertNull(groupCardLandingRowId("__unknown_id__"))
        // …every declared card folds to its first member.
        SettingsScreenGroups.all.forEach { group ->
            val card = group.cardItem ?: return@forEach
            assertEquals(group.itemIds.first(), groupCardLandingRowId(card.id))
        }
    }

    @Test
    fun `the settings dispatch lands a card hit on the first member row and arms the gate`() {
        val card = requireNotNull(SettingsScreenGroups.storageNetworkTuning.cardItem)
        val click = settingsResultClickAction(
            id = card.id,
            route = card.route,
            isAdvanced = card.isAdvanced,
            showAdvancedSettings = false,
        )
        val nav = assertIs<SettingsSearchResultAction.NavigateToScreen>(click.action)
        assertEquals(
            Route.StorageSettings(highlightSettingId = StorageRows.CellularDownloadWarning.id),
            nav.route,
            "a card hit must deep-link the first member row id — the group id expands and glows nothing",
        )
        assertTrue(click.enableAdvanced, "the card hit must auto-arm the power-user gate")
        assertTrue(click.recordRecent, "the card id enters the recents list (it resolves in the catalog)")
        // With the gate already on, no arming leg.
        assertFalse(
            settingsResultClickAction(card.id, card.route, isAdvanced = true, showAdvancedSettings = true)
                .enableAdvanced,
        )
    }

    // ── 3. The recents round-trip with a persisted card id ──────────────

    @Test
    fun `a persisted card id resolves through the recents projection and stale ids drop`() = runTest {
        val resolved = SettingsSearchCatalog.recentItems(
            listOf("storage.networkTuning", "long_deleted_row"),
        ) { items ->
            items.map { ResolvedSettingsItem(it, "t", "s", "c") }
        }
        assertEquals(listOf("storage.networkTuning"), resolved.map { it.id })
        assertSame(SettingsScreenGroups.storageNetworkTuning.cardItem, resolved.single().item)
        // The empty short-circuit never touches the catalog.
        val empty = SettingsSearchCatalog.recentItems(emptyList()) { items ->
            throw AssertionError("the empty recents list must short-circuit, resolved ${items.size} items")
        }
        assertTrue(empty.isEmpty())
    }

    // ── 4. The fail-fast legs — the group derives honestly or dies ──────

    /** One minimal residual-shaped search item, for the fail-fast harnesses below. */
    private fun item(
        id: String,
        route: Route,
        platforms: Set<PlatformKind> = PlatformKind.entries.toSet(),
    ) = SettingsSearchItem(
        id = id,
        titleRes = Res.string.ss_verbose_logging_title,
        subtitleRes = Res.string.ss_verbose_logging_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("probe"),
        route = route,
        icon = Tabler.Outline.Code,
        platforms = platforms,
    )

    private val cardFaceTitle = Res.string.settings_network_diagnostics

    @Test
    fun `a card face without a title dies at init`() {
        // Title-only leg isolation: subtitle AND keywords present, ONLY the
        // title unset — the failure cannot be a downstream missing face.
        assertFailsWith<IllegalArgumentException> {
            SettingsSearchItemGroup(
                id = "probe.card",
                items = listOf(item("probe_row_a", Route.StorageSettings())),
                cardSubtitleRes = cardFaceTitle,
                cardKeywords = listOf("orphan"),
            )
        }
        // The partial-face variant: a subtitle alone is also a faceless-title
        // declaration.
        assertFailsWith<IllegalArgumentException> {
            SettingsSearchItemGroup(
                id = "probe.card",
                items = listOf(item("probe_row_a", Route.StorageSettings())),
                cardSubtitleRes = cardFaceTitle,
            )
        }
    }

    @Test
    fun `a card face without a subtitle or keywords dies at init`() {
        // Subtitle-only leg isolation: title AND keywords present, ONLY the
        // subtitle unset.
        assertFailsWith<IllegalArgumentException> {
            SettingsSearchItemGroup(
                id = "probe.card",
                items = listOf(item("probe_row_a", Route.StorageSettings())),
                cardTitleRes = cardFaceTitle,
                cardKeywords = listOf("probe"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SettingsSearchItemGroup(
                id = "probe.card",
                items = listOf(item("probe_row_a", Route.StorageSettings())),
                cardTitleRes = cardFaceTitle,
                cardSubtitleRes = cardFaceTitle,
                cardKeywords = emptyList(),
            )
        }
    }

    @Test
    fun `a card face over non-uniform member routes dies at init`() {
        val error = assertFailsWith<IllegalArgumentException> {
            SettingsSearchItemGroup(
                id = "probe.card",
                items = listOf(
                    item("probe_row_a", Route.StorageSettings()),
                    item("probe_row_b", Route.AudioSettings()),
                ),
                cardTitleRes = cardFaceTitle,
                cardSubtitleRes = cardFaceTitle,
                cardKeywords = listOf("probe"),
            )
        }
        assertTrue(
            "probe_row_b" in error.message!!,
            "the failure must name the disagreeing member, got: ${error.message}",
        )
    }

    @Test
    fun `a card id colliding with a member row id dies at init`() {
        assertFailsWith<IllegalArgumentException> {
            SettingsSearchItemGroup(
                id = "probe_row_a",
                items = listOf(item("probe_row_a", Route.StorageSettings())),
                cardTitleRes = cardFaceTitle,
                cardSubtitleRes = cardFaceTitle,
                cardKeywords = listOf("probe"),
            )
        }
    }

    @Test
    fun `member rows disagreeing on platforms or category die at init`() {
        val disagreeingPlatforms = assertFailsWith<IllegalArgumentException> {
            SettingsSearchItemGroup(
                id = "probe.card",
                items = listOf(
                    item("probe_row_a", Route.StorageSettings()),
                    item("probe_row_b", Route.StorageSettings(), platforms = setOf(PlatformKind.ANDROID)),
                ),
                cardTitleRes = cardFaceTitle,
                cardSubtitleRes = cardFaceTitle,
                cardKeywords = listOf("probe"),
            )
        }
        assertTrue("platforms" in disagreeingPlatforms.message!!)
        // A second category resource accessors ≠ identity, so a member with
        // a different category object fails the same way (the real groups
        // share one categoryRes by construction).
        val disagreeingCategory = assertFailsWith<IllegalArgumentException> {
            SettingsSearchItemGroup(
                id = "probe.card2",
                items = listOf(
                    item("probe_row_c", Route.StorageSettings()),
                    item("probe_row_d", Route.StorageSettings())
                        .copy(categoryRes = CoreUiRes.string.ss_cat_audio_player),
                ),
                cardTitleRes = cardFaceTitle,
                cardSubtitleRes = cardFaceTitle,
                cardKeywords = listOf("probe"),
            )
        }
        assertTrue("category" in disagreeingCategory.message!!)
    }

    @Test
    fun `a faceless group derives no card and the icon faces the first member`() {
        val faceless = SettingsSearchItemGroup(
            id = "probe.plain",
            items = listOf(item("probe_row_a", Route.StorageSettings(), platforms = setOf(PlatformKind.DESKTOP))),
        )
        assertNull(faceless.cardItem)
        // The healthy two-row group derives the card from the FIRST member's
        // icon (the AlertTriangle stand-in) — a derived face, not a second
        // declaration.
        val healthy = SettingsSearchItemGroup(
            id = "probe.card3",
            items = listOf(
                item("probe_row_a", Route.StorageSettings()).copy(icon = Tabler.Outline.AlertTriangle),
                item("probe_row_b", Route.StorageSettings()),
            ),
            cardTitleRes = cardFaceTitle,
            cardSubtitleRes = cardFaceTitle,
            cardKeywords = listOf("probe"),
        )
        assertSame(Tabler.Outline.AlertTriangle, healthy.cardItem?.icon)
    }
}
