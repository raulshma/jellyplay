package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionPrefs
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.descriptor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [spliceDiscoverSeerrRows]: the feature-layer Seerr discover rows join
 * the ordered section list at the DISCOVER block position, with the user's
 * row-config order as the single ordering authority across BOTH sources.
 */
class DiscoverRowSpliceTest {

    private fun section(id: String, type: HomeSectionType, title: String = id) =
        HomeSection(id = id, title = title, type = type, items = emptyList())

    private fun discoverSection(rowId: String) =
        section(HomeSectionType.DISCOVER.descriptor.idFor(rowId), HomeSectionType.DISCOVER)

    private fun row(rowId: String) = DiscoverRowConfig(id = rowId, title = rowId)

    private fun prefs(rows: List<DiscoverRowConfig>, enabled: Boolean = true) = HomeSectionPrefs(
        query = HomeSectionQuery(
            enabledSections = if (enabled) {
                HomeSectionType.CONFIGURABLE.toSet()
            } else {
                HomeSectionType.CONFIGURABLE.toSet() - HomeSectionType.DISCOVER
            },
            discoverRows = rows,
        ),
        homeSectionOrder = HomeSectionType.CONFIGURABLE,
    )

    @Test
    fun `seerr rows interleave with jellyfin rows in config order`() {
        val jellyfinRows = listOf(discoverSection("a"), discoverSection("c"))
        val seerrRows = listOf(
            discoverSection("b").copy(seerrItems = listOf(seerrItem(1))),
            discoverSection("d").copy(seerrItems = listOf(seerrItem(2))),
        )
        val sections = listOf(
            section("continue_watching", HomeSectionType.CONTINUE_WATCHING),
            section("next_up", HomeSectionType.NEXT_UP),
        ) + jellyfinRows

        val spliced = spliceDiscoverSeerrRows(
            sections = sections,
            seerrRows = seerrRows,
            prefs = prefs(listOf(row("a"), row("b"), row("c"), row("d"))),
        )

        // The DISCOVER block stays at its position (after the standard rows)
        // and orders by row-config index across both sources.
        assertEquals(
            listOf("continue_watching", "next_up", "discover_a", "discover_b", "discover_c", "discover_d"),
            spliced.map { it.id },
        )
        assertEquals(1, spliced.first { it.id == "discover_b" }.seerrItems.size)
    }

    @Test
    fun `seerr rows insert where the DISCOVER block belongs when no jellyfin row rendered`() {
        val sections = listOf(
            section("continue_watching", HomeSectionType.CONTINUE_WATCHING),
            section("recommendations", HomeSectionType.RECOMMENDATIONS),
            section("pinned_x", HomeSectionType.PINNED),
        )
        val spliced = spliceDiscoverSeerrRows(
            sections = sections,
            seerrRows = listOf(discoverSection("z").copy(seerrItems = listOf(seerrItem(9)))),
            prefs = prefs(listOf(row("z"))),
        )
        // DISCOVER sits after RECOMMENDATIONS (its default-order position)
        // and before the pinned row (unknown types sort last).
        assertEquals(
            listOf("continue_watching", "recommendations", "discover_z", "pinned_x"),
            spliced.map { it.id },
        )
    }

    @Test
    fun `disabled DISCOVER section type leaves the list untouched`() {
        val sections = listOf(section("continue_watching", HomeSectionType.CONTINUE_WATCHING))
        val spliced = spliceDiscoverSeerrRows(
            sections = sections,
            seerrRows = listOf(discoverSection("z").copy(seerrItems = listOf(seerrItem(9)))),
            prefs = prefs(listOf(row("z")), enabled = false),
        )
        assertEquals(sections, spliced)
    }

    @Test
    fun `empty seerr rows are a no-op`() {
        val sections = listOf(discoverSection("a"))
        assertEquals(
            sections,
            spliceDiscoverSeerrRows(sections = sections, seerrRows = emptyList(), prefs = prefs(listOf(row("a")))),
        )
    }

    private fun seerrItem(id: Int) = com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem(id = id)
}
