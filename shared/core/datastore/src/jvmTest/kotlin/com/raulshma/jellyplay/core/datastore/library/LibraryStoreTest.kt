package com.raulshma.jellyplay.core.datastore.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.LibraryViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Exercises the library-browsing preference store, focusing on the
 * read-modify-write JSON map setters.
 */
class LibraryStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: LibraryStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = LibraryStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.library.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.library.first()
        assertEquals(LibraryViewMode.GRID, slice.libraryViewMode)
        assertEquals(true, slice.episodesDescending)
        assertEquals(false, slice.hideEpisodeThumbnails)
        assertEquals(false, slice.skipSpecials)
        assertEquals(false, slice.compactEpisodeList)
        assertEquals(true, slice.showDetailUpNext)
        assertEquals(false, slice.showMissingEpisodes)
        assertEquals(emptyMap<String, String>(), slice.defaultLibrarySortOrders)
        // The detail-screen display toggles: true unless the
        // feature intentionally preserves today's off behavior.
        assertEquals(true, slice.showExternalLinks)
        assertEquals(true, slice.showTagline)
        assertEquals(false, slice.preferLocalTrailers)
        assertEquals(true, slice.showMoreInCollection)
        assertEquals(true, slice.showUpcomingStrip)
        assertEquals(true, slice.playThemeMusic)
        assertEquals(true, slice.showReviews)
        assertEquals(true, slice.showEpisodeStreamBadges)
        assertEquals(true, slice.showFilmographyRoles)
        assertEquals(true, slice.showFrequentCostars)
        assertEquals(true, slice.trailerPreviewOnFocus)
        assertEquals(true, slice.showPromotedActionsLandscape)
        assertEquals(true, slice.showDownloadProgress)
    }

    @Test
    fun `setLibraryViewMode round-trips`() = runTest {
        store.setLibraryViewMode(LibraryViewMode.LIST)
        assertEquals(LibraryViewMode.LIST, store.library.first().libraryViewMode)
    }

    @Test
    fun `setShowDetailUpNext round-trips`() = runTest {
        store.setShowDetailUpNext(false)
        assertEquals(false, store.library.first().showDetailUpNext)
        store.setShowDetailUpNext(true)
        assertEquals(true, store.library.first().showDetailUpNext)
    }

    @Test
    fun `setCompactEpisodeList round-trips`() = runTest {
        store.setCompactEpisodeList(true)
        assertEquals(true, store.library.first().compactEpisodeList)
        store.setCompactEpisodeList(false)
        assertEquals(false, store.library.first().compactEpisodeList)
    }

    @Test
    fun `setShowMissingEpisodes round-trips`() = runTest {
        // Hide (false) is the persisted default; showing must round-trip both ways.
        assertEquals(false, store.library.first().showMissingEpisodes)
        store.setShowMissingEpisodes(true)
        assertEquals(true, store.library.first().showMissingEpisodes)
        store.setShowMissingEpisodes(false)
        assertEquals(false, store.library.first().showMissingEpisodes)
    }

    @Test
    fun `setPreferLogos round-trips`() = runTest {
        assertEquals(false, store.library.first().preferLogos)
        store.setPreferLogos(true)
        assertEquals(true, store.library.first().preferLogos)
        store.setPreferLogos(false)
        assertEquals(false, store.library.first().preferLogos)
    }

    @Test
    fun `restore round-trips preferLogos through the slice`() = runTest {
        store.restore(LibrarySlice(preferLogos = true))
        assertEquals(true, store.library.first().preferLogos)
        store.restore(LibrarySlice(preferLogos = false))
        assertEquals(false, store.library.first().preferLogos)
    }

    @Test
    fun `detail display toggles default off-state round-trips`() = runTest {
        // The one default-off toggle flips both ways like its neighbors.
        assertEquals(false, store.library.first().preferLocalTrailers)
        store.setPreferLocalTrailers(true)
        assertEquals(true, store.library.first().preferLocalTrailers)
        store.setPreferLocalTrailers(false)
        assertEquals(false, store.library.first().preferLocalTrailers)
    }

    @Test
    fun `detail display toggles round-trip both ways`() = runTest {
        // Every default-on detail toggle:
        // flip off → observed, flip back on → observed (the setters are the
        // settings rows' only write path).
        val cases: List<Triple<String, suspend (Boolean) -> Unit, suspend () -> Boolean>> = listOf(
            Triple("showExternalLinks", store::setShowExternalLinks, { store.library.first().showExternalLinks }),
            Triple("showTagline", store::setShowTagline, { store.library.first().showTagline }),
            Triple("showMoreInCollection", store::setShowMoreInCollection, { store.library.first().showMoreInCollection }),
            Triple("showUpcomingStrip", store::setShowUpcomingStrip, { store.library.first().showUpcomingStrip }),
            Triple("playThemeMusic", store::setPlayThemeMusic, { store.library.first().playThemeMusic }),
            Triple("showReviews", store::setShowReviews, { store.library.first().showReviews }),
            Triple("showEpisodeStreamBadges", store::setShowEpisodeStreamBadges, { store.library.first().showEpisodeStreamBadges }),
            Triple("showFilmographyRoles", store::setShowFilmographyRoles, { store.library.first().showFilmographyRoles }),
            Triple("showFrequentCostars", store::setShowFrequentCostars, { store.library.first().showFrequentCostars }),
            Triple("trailerPreviewOnFocus", store::setTrailerPreviewOnFocus, { store.library.first().trailerPreviewOnFocus }),
            Triple("showPromotedActionsLandscape", store::setShowPromotedActionsLandscape, { store.library.first().showPromotedActionsLandscape }),
            Triple("showDownloadProgress", store::setShowDownloadProgress, { store.library.first().showDownloadProgress }),
        )
        cases.forEach { (name, setter, read) ->
            setter(false)
            assertEquals(false, read(), "$name did not round-trip to false")
            setter(true)
            assertEquals(true, read(), "$name did not round-trip back to true")
        }
    }

    @Test
    fun `restore round-trips the detail display toggles through the slice`() = runTest {
        store.restore(LibrarySlice(showTagline = false, playThemeMusic = false, showDownloadProgress = false))
        val slice = store.library.first()
        assertEquals(false, slice.showTagline)
        assertEquals(false, slice.playThemeMusic)
        assertEquals(false, slice.showDownloadProgress)
        store.restore(LibrarySlice())
        assertEquals(true, store.library.first().showTagline)
    }

    @Test
    fun `setDefaultLibrarySortOrder merges into the map`() = runTest {
        store.setDefaultLibrarySortOrder("lib_a", "SortName")
        store.setDefaultLibrarySortOrder("lib_b", "DateCreated")
        val slice = store.library.first()
        assertEquals(slice.defaultLibrarySortOrders["lib_a"], "SortName")
        assertEquals(slice.defaultLibrarySortOrders["lib_b"], "DateCreated")
    }
}
