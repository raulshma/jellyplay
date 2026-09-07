package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * State-machine pins for [SettingsSearchPanelState] — the holder that owns the
 * settings screen's search-panel interactions (open → type → filter →
 * tap-through → dismiss) and the recents add/dedupe/clear commands. Pure JVM:
 * the holder's snapshot state reads/writes fine outside composition and the
 * persistence sinks are recorded by lambdas, so no stores, dispatchers or
 * Compose machinery are involved (the [ReorderStateTest] pattern).
 */
class SettingsSearchPanelStateTest {

    private val recordedRecents = mutableListOf<String>()
    private var recentsCleared = 0

    private fun state(): SettingsSearchPanelState = SettingsSearchPanelState(
        recordRecentSink = { recordedRecents += it },
        clearRecentsSink = { recentsCleared++ },
    )

    /** Catalog-independent fixture — the holder only reads [ResolvedSettingsItem.category]. */
    private fun resolved(id: String, category: String = "Playback") = ResolvedSettingsItem(
        item = SettingsSearchItem(
            id = id,
            // A real StringResource ref that is never resolved here.
            titleRes = Res.string.settings_title,
            subtitleRes = Res.string.settings_title,
            categoryRes = Res.string.settings_title,
            keywords = emptyList(),
            route = Route.Settings,
            icon = dummyIcon,
        ),
        title = "title:$id",
        subtitle = "subtitle:$id",
        category = category,
    )

    private val dummyIcon get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "test",
        defaultWidth = androidx.compose.ui.unit.Dp.Unspecified,
        defaultHeight = androidx.compose.ui.unit.Dp.Unspecified,
        viewportWidth = 1f,
        viewportHeight = 1f,
    ).build()

    // ------------------------------------------------- open → type → filter → tap → dismiss

    @Test
    fun `the panel starts closed, blank and unfiltered`() {
        val s = state()
        assertFalse(s.isSearchActive)
        assertEquals("", s.searchQuery)
        assertNull(s.selectedCategory)
        assertTrue(s.recentIds.isEmpty())
    }

    @Test
    fun `open then type drives the query while the panel stays up`() {
        val s = state()
        s.open()
        assertTrue(s.isSearchActive)

        s.onQueryChange("theme")
        assertEquals("theme", s.searchQuery)
        assertTrue(s.isSearchActive, "typing must not collapse the panel")
    }

    @Test
    fun `clearQuery empties the query without collapsing the panel`() {
        val s = state().apply {
            open()
            onQueryChange("theme")
        }

        s.clearQuery()

        assertEquals("", s.searchQuery)
        assertTrue(s.isSearchActive)
    }

    @Test
    fun `displayItems filters by the selected category and All restores everything`() {
        val s = state()
        val items = listOf(
            resolved("theme_mode", category = "Appearance"),
            resolved("audio_passthrough", category = "Audio"),
            resolved("oled_mode", category = "Appearance"),
        )

        assertEquals(items, s.displayItems(items), "no category selected — the full match list shows")

        s.toggleCategory("Appearance")
        assertEquals(listOf("theme_mode", "oled_mode"), s.displayItems(items).map { it.id })

        s.selectAllCategories()
        assertEquals(items, s.displayItems(items))
    }

    @Test
    fun `tapping the active category chip toggles the filter off`() {
        val s = state()

        s.toggleCategory("Audio")
        assertEquals("Audio", s.selectedCategory)

        s.toggleCategory("Audio")
        assertNull(s.selectedCategory)
    }

    @Test
    fun `tap-through records the recent then dismisses and resets`() {
        val s = state().apply {
            submitRecents(emptyList())
            open()
            onQueryChange("theme")
            toggleCategory("Appearance")
        }

        s.recordRecent("theme_mode")
        s.dismiss()

        assertEquals(listOf("theme_mode"), s.recentIds, "the tap is recorded before the collapse")
        assertEquals(listOf("theme_mode"), recordedRecents)
        assertFalse(s.isSearchActive)
        assertEquals("", s.searchQuery, "dismiss resets the query")
        assertNull(s.selectedCategory, "dismiss resets the category filter")
    }

    @Test
    fun `dismiss clears the panel state without touching persistence`() {
        val s = state().apply {
            submitRecents(listOf("theme_mode"))
            open()
            onQueryChange("pin")
        }

        s.dismiss()

        assertEquals(listOf("theme_mode"), s.recentIds, "dismiss is a panel reset — recents survive")
        assertEquals(0, recentsCleared)
        assertTrue(recordedRecents.isEmpty())
    }

    // ------------------------------------------------- recents add / dedupe / clear

    @Test
    fun `recordRecent prepends most-recent first and forwards persistence`() {
        val s = state()

        s.recordRecent("theme_mode")
        s.recordRecent("audio")

        assertEquals(listOf("audio", "theme_mode"), s.recentIds)
        assertEquals(listOf("theme_mode", "audio"), recordedRecents, "every tap reaches the store sink")
    }

    @Test
    fun `re-recording an id dedupes and moves it to the front`() {
        val s = state()
        s.recordRecent("theme_mode")
        s.recordRecent("audio")
        s.recordRecent("security")

        s.recordRecent("theme_mode")

        assertEquals(listOf("theme_mode", "security", "audio"), s.recentIds)
        assertEquals(4, recordedRecents.size, "the store still hears every tap — it owns persistence")
    }

    @Test
    fun `submitRecents re-seeds the mirror from the store flow`() {
        val s = state()
        s.recordRecent("audio")

        s.submitRecents(listOf("backup", "audio", "theme_mode"))

        assertEquals(listOf("backup", "audio", "theme_mode"), s.recentIds)
    }

    @Test
    fun `clearRecents empties the mirror and forwards the store clear`() {
        val s = state()
        s.recordRecent("audio")
        s.recordRecent("theme_mode")

        s.clearRecents()

        assertTrue(s.recentIds.isEmpty())
        assertEquals(1, recentsCleared)
    }
}
