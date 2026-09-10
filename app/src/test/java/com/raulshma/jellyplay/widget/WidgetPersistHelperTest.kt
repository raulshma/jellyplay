package com.raulshma.jellyplay.widget

import android.content.Context
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.LibraryWidgetItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the widget data-store write discipline behind the recommendation
 * workers: the rows are ALWAYS re-persisted — the unchanged-id branch skips
 * only the launcher re-render — and the id comparison is order-insensitive,
 * keyed by itemId (library) / tmdb id (seerr), with a size change counting
 * as changed content.
 *
 * No widgets are bound in these tests (Robolectric's empty widget manager),
 * so the changed-content notify fan-out iterates the empty id set and the
 * store interactions alone pin the branch behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WidgetPersistHelperTest {

    // Real application context: the notify branch probes AppWidgetManager for
    // bound widgets (empty under Robolectric), which a relaxed Context mock
    // would not answer faithfully.
    private val context: Context = org.robolectric.RuntimeEnvironment.getApplication()
    private val store: WidgetDataStore = mockk(relaxed = true)

    private val libraryItems = MutableStateFlow<List<LibraryWidgetItem>>(emptyList())
    private val seerrItems = MutableStateFlow<List<SeerrWidgetItem>>(emptyList())

    @Before
    fun setUp() {
        every { store.libraryWidgetItems } returns libraryItems
        every { store.seerrWidgetItems } returns seerrItems
    }

    private fun libraryItem(id: String) = LibraryWidgetItem(
        itemId = id,
        name = "Item $id",
        mediaType = MediaType.MOVIE,
    )

    private fun seerrItem(id: Int) = SeerrWidgetItem(
        tmdbId = id,
        mediaType = "movie",
        title = "Seerr $id",
    )

    // ── library ────────────────────────────────────────────────────────────

    @Test
    fun `changed library content persists the items and runs the notify fan-out`() = runTest {
        libraryItems.value = listOf(libraryItem("a"))
        val items = slot<List<LibraryWidgetItem>>()

        WidgetPersistHelper.persistLibraryItems(context, store, listOf(libraryItem("a"), libraryItem("b")))

        coVerify(exactly = 1) { store.setLibraryWidgetItems(capture(items)) }
        assertEquals(listOf("a", "b"), items.captured.map { it.itemId })
    }

    @Test
    fun `unchanged library content still re-persists the write`() = runTest {
        libraryItems.value = listOf(libraryItem("a"), libraryItem("b"))

        WidgetPersistHelper.persistLibraryItems(context, store, listOf(libraryItem("b"), libraryItem("a")))

        coVerify(exactly = 1) { store.setLibraryWidgetItems(any()) }
    }

    @Test
    fun `a size change counts as changed content even with the same ids`() = runTest {
        libraryItems.value = listOf(libraryItem("a"), libraryItem("b"))

        WidgetPersistHelper.persistLibraryItems(context, store, listOf(libraryItem("a")))

        coVerify(exactly = 1) { store.setLibraryWidgetItems(any()) }
    }

    @Test
    fun `first write with an empty store persists the items`() = runTest {
        val items = slot<List<LibraryWidgetItem>>()

        WidgetPersistHelper.persistLibraryItems(context, store, listOf(libraryItem("x")))

        coVerify(exactly = 1) { store.setLibraryWidgetItems(capture(items)) }
        assertEquals(listOf("x"), items.captured.map { it.itemId })
    }

    // ── seerr ──────────────────────────────────────────────────────────────

    @Test
    fun `unchanged seerr content keyed by tmdb id still re-persists the write`() = runTest {
        seerrItems.value = listOf(seerrItem(1), seerrItem(2))

        WidgetPersistHelper.persistSeerrItems(context, store, listOf(seerrItem(2), seerrItem(1)))

        coVerify(exactly = 1) { store.setSeerrWidgetItems(any()) }
    }

    @Test
    fun `changed seerr content persists the items`() = runTest {
        seerrItems.value = listOf(seerrItem(1))
        val items = slot<List<SeerrWidgetItem>>()

        WidgetPersistHelper.persistSeerrItems(context, store, listOf(seerrItem(1), seerrItem(9)))

        coVerify(exactly = 1) { store.setSeerrWidgetItems(capture(items)) }
        assertEquals(listOf(1, 9), items.captured.map { it.tmdbId })
    }
}
