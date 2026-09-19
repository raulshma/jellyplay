package com.raulshma.jellyplay.feature.shortcuts

import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.ui.navigation.Route
import io.mockk.mockk
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [ShortcutListPolicy] — the Compose-free list decisions extracted from
 * ShortcutsScreen: the query filter's case/field-set rules, the
 * null-means-unfiltered contract (blank query AND the unresolved-labels
 * window that would otherwise flash a false "No results" after a process
 * restore), the category-display fold (unfiltered map narrowing vs
 * search-result grouping), the list-empty decision, and the
 * no-results-vs-no-shortcuts empty-state selection.
 *
 * ShortcutItem's resource/icon fields are opaque to the policy — mocked
 * here; routes are real shared Route objects (objects, no construction).
 */
class ShortcutListPolicyTest {

    private fun item(category: ShortcutCategory): ShortcutItem = ShortcutItem(
        titleRes = mockk<StringResource>(),
        descriptionRes = mockk<StringResource>(),
        icon = mockk<ImageVector>(),
        route = Route.Downloads,
        category = category,
    )

    private val downloads = item(ShortcutCategory.LIBRARY)
    private val favorites = item(ShortcutCategory.LIBRARY)
    private val syncPlay = item(ShortcutCategory.SERVICES)

    private val allItems = listOf(downloads, favorites, syncPlay)

    private val labels = mapOf(
        downloads to ("Downloads" to "Manage offline files"),
        favorites to ("Favorites" to "Your saved media"),
        syncPlay to ("SyncPlay" to "Watch together in sync"),
    )

    private val categories = linkedMapOf(
        ShortcutCategory.LIBRARY to listOf(downloads, favorites),
        ShortcutCategory.SERVICES to listOf(syncPlay),
    )

    // ── query filter ──────────────────────────────────────────────────────

    @Test
    fun `query filter is case-insensitive and matches title OR description`() {
        // Title match, differing case both directions.
        assertEquals(listOf(downloads), ShortcutListPolicy.filteredByQuery(allItems, labels, "down"))
        assertEquals(listOf(downloads), ShortcutListPolicy.filteredByQuery(allItems, labels, "DOWNLOADS"))
        // Description match (the second field of the pair).
        assertEquals(listOf(downloads), ShortcutListPolicy.filteredByQuery(allItems, labels, "offline"))
        assertEquals(listOf(syncPlay), ShortcutListPolicy.filteredByQuery(allItems, labels, "together"))
        // A query spanning title AND description of different items still
        // matches per-field contains, not across fields.
        assertEquals(emptyList(), ShortcutListPolicy.filteredByQuery(allItems, labels, "downloads together"))
    }

    @Test
    fun `query filter trims the query before matching`() {
        assertEquals(listOf(downloads), ShortcutListPolicy.filteredByQuery(allItems, labels, "  down  "))
    }

    @Test
    fun `blank query is unfiltered (null), even with labels resolved`() {
        assertNull(ShortcutListPolicy.filteredByQuery(allItems, labels, ""))
        assertNull(ShortcutListPolicy.filteredByQuery(allItems, labels, "   "))
    }

    @Test
    fun `unresolved label map treats a non-blank query as unfiltered (false-no-results guard)`() {
        // A process restore can surface a non-blank query before the label
        // map resolves; an empty map must NOT read as "zero matches".
        assertNull(ShortcutListPolicy.filteredByQuery(allItems, emptyMap(), "downloads"))
    }

    @Test
    fun `items missing from the label map are excluded, not matched`() {
        val partial = mapOf(
            downloads to ("Downloads" to "Manage offline files"),
        )
        assertEquals(listOf(downloads), ShortcutListPolicy.filteredByQuery(allItems, partial, "load"))
    }

    @Test
    fun `a non-blank query matching nothing yields an EMPTY list, not null`() {
        val result = ShortcutListPolicy.filteredByQuery(allItems, labels, "zzz")
        assertEquals(emptyList(), result)
    }

    // ── category display fold ─────────────────────────────────────────────

    @Test
    fun `without a query, All keeps the catalog map and a category narrows it`() {
        assertEquals(
            categories,
            ShortcutListPolicy.displayedCategories(categories, ShortcutFilter.All, queryFiltered = null),
        )
        assertEquals(
            mapOf(ShortcutCategory.LIBRARY to listOf(downloads, favorites)),
            ShortcutListPolicy.displayedCategories(
                categories, ShortcutFilter.Category(ShortcutCategory.LIBRARY), queryFiltered = null,
            ),
        )
        assertEquals(
            emptyMap(),
            ShortcutListPolicy.displayedCategories(
                categories, ShortcutFilter.Category(ShortcutCategory.SYSTEM), queryFiltered = null,
            ),
        )
    }

    @Test
    fun `with a query result, items group by category and the filter still applies`() {
        val queryFiltered = listOf(syncPlay, favorites)

        assertEquals(
            linkedMapOf(
                ShortcutCategory.SERVICES to listOf(syncPlay),
                ShortcutCategory.LIBRARY to listOf(favorites),
            ),
            ShortcutListPolicy.displayedCategories(categories, ShortcutFilter.All, queryFiltered),
        )
        assertEquals(
            linkedMapOf(ShortcutCategory.SERVICES to listOf(syncPlay)),
            ShortcutListPolicy.displayedCategories(
                categories, ShortcutFilter.Category(ShortcutCategory.SERVICES), queryFiltered,
            ),
        )
        assertEquals(
            emptyMap(),
            ShortcutListPolicy.displayedCategories(
                categories, ShortcutFilter.Category(ShortcutCategory.LIBRARY), listOf(syncPlay),
            ),
        )
    }

    @Test
    fun `an empty (non-null) query result folds to an empty map — no results, not the catalog`() {
        assertEquals(
            emptyMap(),
            ShortcutListPolicy.displayedCategories(categories, ShortcutFilter.All, queryFiltered = emptyList()),
        )
    }

    // ── list-empty decision ───────────────────────────────────────────────

    @Test
    fun `isListEmpty holds for an empty map and for maps whose every slice is empty`() {        assertTrue(ShortcutListPolicy.isListEmpty(emptyMap()))
        assertTrue(
            ShortcutListPolicy.isListEmpty(
                mapOf(ShortcutCategory.LIBRARY to emptyList(), ShortcutCategory.SERVICES to emptyList()),
            ),
        )
    }

    @Test
    fun `isListEmpty is false once any category slice has content`() {
        assertFalse(ShortcutListPolicy.isListEmpty(mapOf(ShortcutCategory.LIBRARY to listOf(downloads))))
        assertFalse(
            ShortcutListPolicy.isListEmpty(
                mapOf(ShortcutCategory.LIBRARY to emptyList(), ShortcutCategory.SERVICES to listOf(syncPlay)),
            ),
        )
    }

    // ── empty-state selection ─────────────────────────────────────────────

    @Test
    fun `blank query means the no-shortcuts state, non-blank means no-results with the raw query`() {
        assertIs<ShortcutListPolicy.ShortcutEmptyState.NoShortcuts>(
            ShortcutListPolicy.emptyStateFor(""),
        )
        assertIs<ShortcutListPolicy.ShortcutEmptyState.NoShortcuts>(
            ShortcutListPolicy.emptyStateFor("   "),
        )
        val noResults = assertIs<ShortcutListPolicy.ShortcutEmptyState.NoResults>(
            ShortcutListPolicy.emptyStateFor("  lib "),
        )
        // The raw (untrimmed) query is carried through — the screen renders
        // exactly what the user typed, like HEAD did.
        assertEquals("  lib ", noResults.query)
    }
}
