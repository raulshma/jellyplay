package com.raulshma.jellyplay.feature.details

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure tests for the promoted-rail option filters: the stable [MediaOption.id]
 * contract, [promotedOptions] (fixed-order resolution, missing ids ignored) and
 * [excludingIds] (the ⋮-menu dupe suppression, pass-through when the rail is
 * hidden). No Android / Compose rendering — the filters only shuffle the list.
 */
class MediaOptionPromotionTest {

    @Test
    fun `promoted set is the fixed v1 ids`() {
        assertEquals(
            listOf("download", "mark_watched", "trailer", "watch_party"),
            PromotedMediaOptionIds,
        )
    }

    @Test
    fun `promotedOptions resolve in the fixed order regardless of menu order`() {
        val menu = listOf(
            option("technical_info"),
            option("watch_party"),
            option("share"),
            option("download"),
        )

        val rail = menu.promotedOptions()

        // Rail order is stable even though the menu lists them back-to-front;
        // "mark_watched" / "trailer" are absent today and simply ignored.
        assertEquals(listOf("download", "watch_party"), rail.map { it.id })
    }

    @Test
    fun `promotedOptions ignore ids the menu does not carry`() {
        val menu = listOf(option("download"))

        assertTrue(menu.promotedOptions().map { it.id } == listOf("download"))
        assertTrue(emptyList<MediaOption>().promotedOptions().isEmpty())
    }

    @Test
    fun `promotedOptions default to the fixed set`() {
        val menu = listOf(
            option("download"),
            option("watch_party"),
            option("mark_watched"),
            option("trailer"),
        )

        assertEquals(PromotedMediaOptionIds, menu.promotedOptions().map { it.id })
    }

    @Test
    fun `excludingIds drops exactly the promoted entries while the rail shows them`() {
        val menu = listOf(
            option("download"),
            option("share"),
            option("watch_party"),
            option("technical_info"),
        )

        val overflow = menu.excludingIds(PromotedMediaOptionIds.toSet())

        assertEquals(listOf("share", "technical_info"), overflow.map { it.id })
        // No dupes: the rail's ids and the ⋮ list's ids are disjoint.
        val railIds = menu.promotedOptions().map { it.id }.toSet()
        assertTrue(overflow.map { it.id }.intersect(railIds).isEmpty())
    }

    @Test
    fun `excludingIds is a pass-through when the rail shows nothing`() {
        val menu = listOf(option("download"), option("share"))

        // Gate off / portrait body → empty promoted set → the ⋮ list keeps
        // every entry (same instance, not a filtered copy).
        assertTrue(menu.excludingIds(emptySet()) === menu)
    }

    @Test
    fun `a disabled promoted option stays promoted and stays disabled`() {
        // The downloading ⋮ row is the disabled variant of the download slot —
        // availability logic is unchanged, only the surface moves.
        val downloading = option("download", enabled = false)
        val menu = listOf(downloading, option("share"))

        val rail = menu.promotedOptions()
        val overflow = menu.excludingIds(PromotedMediaOptionIds.toSet())

        assertEquals(listOf(downloading), rail)
        assertFalse(rail.single().enabled)
        assertTrue(overflow.none { it.id == "download" })
    }

    @Test
    fun `option ids are unique per menu slot`() {
        // A menu built from every gate-on shape must not carry one id twice —
        // promotedOptions resolves first-match, so a duplicated id would
        // silently shadow its twin.
        val menu = listOf(
            option("download"),
            option("share"),
            option("watch_party"),
            option("technical_info"),
        )
        assertEquals(menu.map { it.id }.size, menu.map { it.id }.toSet().size)
    }

    // ── Fixtures ──

    private val icon: ImageVector = ImageVector.Builder(
        name = "test-icon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).build()

    private fun option(id: String, enabled: Boolean = true) = MediaOption(
        id = id,
        label = id,
        icon = icon,
        enabled = enabled,
        onClick = {},
    )
}
