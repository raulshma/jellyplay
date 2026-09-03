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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the widget data-store write discipline behind the recommendation
 * workers:
 *
 *  - an unchanged item set (same ids, order-insensitive) re-persists the
 *    items under the PREVIOUS version — the version stays a
 *    "content changed" signal.
 *  - a changed item set persists under a version derived from the wall
 *    clock (monotonically newer than any previously stored version).
 *  - `versionBumpOnly` always increments the previous version by one, even
 *    when the content is identical (the explicit launcher-refresh signal).
 *  - the seerr store follows the same rules keyed by tmdb id.
 *
 * No widgets are bound in these tests (Robolectric's empty widget manager),
 * so the notify fan-out iterates the empty id set and the store interactions
 * alone pin the branch behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WidgetPersistHelperTest {

    // Real application context: the dedup branch still probes
    // AppWidgetManager for bound widgets (empty under Robolectric), which a
    // relaxed Context mock would not answer faithfully.
    private val context: Context = org.robolectric.RuntimeEnvironment.getApplication()
    private val store: WidgetDataStore = mockk(relaxed = true)

    private val libraryItems = MutableStateFlow<List<LibraryWidgetItem>>(emptyList())
    private val libraryVersion = MutableStateFlow(7L)
    private val seerrItems = MutableStateFlow<List<SeerrWidgetItem>>(emptyList())
    private val seerrVersion = MutableStateFlow(3L)

    @Before
    fun setUp() {
        every { store.libraryWidgetItems } returns libraryItems
        every { store.libraryWidgetVersion } returns libraryVersion
        every { store.seerrWidgetItems } returns seerrItems
        every { store.seerrWidgetVersion } returns seerrVersion
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
    fun `changed library content persists under a wall-clock version`() = runTest {
        libraryItems.value = listOf(libraryItem("a"))
        libraryVersion.value = 7L
        val items = slot<List<LibraryWidgetItem>>()
        val version = slot<Long>()

        WidgetPersistHelper.persistLibraryItems(
            context, store, listOf(libraryItem("a"), libraryItem("b")), versionBumpOnly = false,
        )

        coVerify(exactly = 1) { store.setLibraryWidgetItems(capture(items), capture(version), any()) }
        assertEquals(listOf("a", "b"), items.captured.map { it.itemId })
        assertTrue("wall-clock version must exceed the stored version", version.captured > 7L)
    }

    @Test
    fun `unchanged library content re-persists under the previous version`() = runTest {
        libraryItems.value = listOf(libraryItem("a"), libraryItem("b"))
        val version = slot<Long>()

        WidgetPersistHelper.persistLibraryItems(
            context, store, listOf(libraryItem("b"), libraryItem("a")), versionBumpOnly = false,
        )

        coVerify(exactly = 1) { store.setLibraryWidgetItems(any(), capture(version), any()) }
        assertEquals("unchanged ids keep the stored version", 7L, version.captured)
    }

    @Test
    fun `a size change counts as changed content even with the same ids`() = runTest {
        libraryItems.value = listOf(libraryItem("a"), libraryItem("b"))
        val version = slot<Long>()

        WidgetPersistHelper.persistLibraryItems(
            context, store, listOf(libraryItem("a")), versionBumpOnly = false,
        )

        coVerify(exactly = 1) { store.setLibraryWidgetItems(any(), capture(version), any()) }
        assertTrue(version.captured > 7L)
    }

    @Test
    fun `versionBumpOnly increments the stored version by exactly one`() = runTest {
        libraryItems.value = listOf(libraryItem("a"))
        val version = slot<Long>()

        WidgetPersistHelper.persistLibraryItems(
            context, store, listOf(libraryItem("a")), versionBumpOnly = true,
        )

        coVerify(exactly = 1) { store.setLibraryWidgetItems(any(), capture(version), any()) }
        assertEquals(8L, version.captured)
    }

    @Test
    fun `first write with an empty store persists under a wall-clock version`() = runTest {
        val version = slot<Long>()

        WidgetPersistHelper.persistLibraryItems(
            context, store, listOf(libraryItem("x")), versionBumpOnly = false,
        )

        coVerify(exactly = 1) { store.setLibraryWidgetItems(any(), capture(version), any()) }
        assertTrue(version.captured > 7L)
    }

    // ── seerr ──────────────────────────────────────────────────────────────

    @Test
    fun `unchanged seerr content keyed by tmdb id keeps the stored version`() = runTest {
        seerrItems.value = listOf(seerrItem(1), seerrItem(2))
        val version = slot<Long>()

        WidgetPersistHelper.persistSeerrItems(
            context, store, listOf(seerrItem(2), seerrItem(1)), versionBumpOnly = false,
        )

        coVerify(exactly = 1) { store.setSeerrWidgetItems(any(), capture(version), any()) }
        assertEquals(3L, version.captured)
    }

    @Test
    fun `changed seerr content persists under a wall-clock version`() = runTest {
        seerrItems.value = listOf(seerrItem(1))
        val items = slot<List<SeerrWidgetItem>>()
        val version = slot<Long>()

        WidgetPersistHelper.persistSeerrItems(
            context, store, listOf(seerrItem(1), seerrItem(9)), versionBumpOnly = false,
        )

        coVerify(exactly = 1) { store.setSeerrWidgetItems(capture(items), capture(version), any()) }
        assertEquals(listOf(1, 9), items.captured.map { it.tmdbId })
        assertTrue(version.captured > 3L)
    }

    @Test
    fun `seerr versionBumpOnly increments by one`() = runTest {
        val version = slot<Long>()

        WidgetPersistHelper.persistSeerrItems(
            context, store, listOf(seerrItem(1)), versionBumpOnly = true,
        )

        coVerify(exactly = 1) { store.setSeerrWidgetItems(any(), capture(version), any()) }
        assertEquals(4L, version.captured)
    }
}
