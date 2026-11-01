package com.raulshma.jellyplay.core.datastore.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.DetailBackdropResolution
import com.raulshma.jellyplay.core.model.DetailChapterCardSize
import com.raulshma.jellyplay.core.model.DetailDensity
import com.raulshma.jellyplay.core.model.DetailStaggerStyle
import com.raulshma.jellyplay.core.model.FilmographySort
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.PromotedRailSource
import com.raulshma.jellyplay.core.model.SmartPlayPriority
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

    @Test
    fun `detail QoL knobs round-trip set then reset to defaults`() = runTest {
        // The 17 detail-screen QoL rows: set → assert the slice serves the
        // written value → reset (the empty-slice restore) → assert the spec
        // defaults again. One consolidated write-through pass beside the
        // per-row tests above (the recipe's one-test shape).
        // 1. Cast rail density (enum-by-name).
        store.setDetailCastItemSize(DetailDensity.LARGE)
        assertEquals(DetailDensity.LARGE, store.library.first().detailCastItemSize)
        // 2. Cast "See all" threshold (int, clamped 6..24).
        store.setDetailCastSeeAllLimit(18)
        assertEquals(18, store.library.first().detailCastSeeAllLimit)
        store.setDetailCastSeeAllLimit(2)
        assertEquals(6, store.library.first().detailCastSeeAllLimit, "setter must clamp to the row min")
        store.setDetailCastSeeAllLimit(99)
        assertEquals(24, store.library.first().detailCastSeeAllLimit, "setter must clamp to the row max")
        // 3. Backdrop parallax.
        store.setDetailBackdropParallax(false)
        assertEquals(false, store.library.first().detailBackdropParallax)
        // 4. Overview collapse lines (int, clamped 3..8).
        store.setDetailOverviewCollapseLines(5)
        assertEquals(5, store.library.first().detailOverviewCollapseLines)
        store.setDetailOverviewCollapseLines(1)
        assertEquals(3, store.library.first().detailOverviewCollapseLines, "setter must clamp to the row min")
        store.setDetailOverviewCollapseLines(99)
        assertEquals(8, store.library.first().detailOverviewCollapseLines, "setter must clamp to the row max")
        // 5. Hide watched episodes.
        store.setDetailHideWatchedEpisodes(true)
        assertEquals(true, store.library.first().detailHideWatchedEpisodes)
        // 6. Confirm mark played.
        store.setDetailConfirmMarkPlayed(true)
        assertEquals(true, store.library.first().detailConfirmMarkPlayed)
        // 7-10. The header chip toggles.
        store.setDetailShowStudioChips(false)
        assertEquals(false, store.library.first().detailShowStudioChips)
        store.setDetailShowGenreChips(false)
        assertEquals(false, store.library.first().detailShowGenreChips)
        store.setDetailShowTagChips(false)
        assertEquals(false, store.library.first().detailShowTagChips)
        store.setDetailShowSkipChips(false)
        assertEquals(false, store.library.first().detailShowSkipChips)
        // 11. Season tab artwork.
        store.setDetailSeasonTabsArtwork(true)
        assertEquals(true, store.library.first().detailSeasonTabsArtwork)
        // 12. Cast & Crew grid density (enum-by-name).
        store.setDetailPeopleGridDensity(DetailDensity.COMPACT)
        assertEquals(DetailDensity.COMPACT, store.library.first().detailPeopleGridDensity)
        // 13. Trailer badges.
        store.setDetailShowTrailerBadges(false)
        assertEquals(false, store.library.first().detailShowTrailerBadges)
        // 14. Persist section hides.
        store.setDetailPersistSectionHides(true)
        assertEquals(true, store.library.first().detailPersistSectionHides)
        // 15. The persisted franchise eye-hide memory (JSON id set).
        store.setDetailHiddenFranchiseIds(setOf("col-1", "col-2"))
        assertEquals(setOf("col-1", "col-2"), store.library.first().detailHiddenFranchiseIds)
        // 16. Similar limit (int, clamped 6..24).
        store.setDetailSimilarLimit(20)
        assertEquals(20, store.library.first().detailSimilarLimit)
        store.setDetailSimilarLimit(1)
        assertEquals(6, store.library.first().detailSimilarLimit, "setter must clamp to the row min")
        // 17. Upcoming limit (int, clamped 4..24).
        store.setDetailUpcomingLimit(16)
        assertEquals(16, store.library.first().detailUpcomingLimit)
        store.setDetailUpcomingLimit(1)
        assertEquals(4, store.library.first().detailUpcomingLimit, "setter must clamp to the row min")
        // ── Wave 2 ──
        // 18. Section order (CSV; trimmed, empties dropped, deduped).
        store.setDetailSectionOrder(listOf(" CAST ", "", "OVERVIEW", "CAST", "  "))
        assertEquals("CAST,OVERVIEW", store.library.first().detailSectionOrder)
        // 19. Stagger style (enum-by-name).
        store.setDetailStaggerStyle(DetailStaggerStyle.FAST)
        assertEquals(DetailStaggerStyle.FAST, store.library.first().detailStaggerStyle)
        // 20. Backdrop resolution (enum-by-name).
        store.setDetailBackdropResolution(DetailBackdropResolution.QHD_2560)
        assertEquals(DetailBackdropResolution.QHD_2560, store.library.first().detailBackdropResolution)
        // 21. Chapter card size (enum-by-name).
        store.setDetailChapterCardSize(DetailChapterCardSize.LARGE)
        assertEquals(DetailChapterCardSize.LARGE, store.library.first().detailChapterCardSize)
        // 22. Reviews preview lines (int, clamped 2..12).
        store.setDetailReviewsPreviewLines(9)
        assertEquals(9, store.library.first().detailReviewsPreviewLines)
        store.setDetailReviewsPreviewLines(1)
        assertEquals(2, store.library.first().detailReviewsPreviewLines, "setter must clamp to the row min")
        store.setDetailReviewsPreviewLines(99)
        assertEquals(12, store.library.first().detailReviewsPreviewLines, "setter must clamp to the row max")
        // 23. Filmography sort (enum-by-name).
        store.setDetailFilmographySort(FilmographySort.ALPHABETICAL)
        assertEquals(FilmographySort.ALPHABETICAL, store.library.first().detailFilmographySort)
        // 24. Franchise box limit (int, clamped 1..5).
        store.setDetailFranchiseBoxLimit(4)
        assertEquals(4, store.library.first().detailFranchiseBoxLimit)
        store.setDetailFranchiseBoxLimit(0)
        assertEquals(1, store.library.first().detailFranchiseBoxLimit, "setter must clamp to the row min")
        store.setDetailFranchiseBoxLimit(9)
        assertEquals(5, store.library.first().detailFranchiseBoxLimit, "setter must clamp to the row max")
        // 25. Franchise member limit (int, clamped 10..100).
        store.setDetailFranchiseMemberLimit(80)
        assertEquals(80, store.library.first().detailFranchiseMemberLimit)
        store.setDetailFranchiseMemberLimit(5)
        assertEquals(10, store.library.first().detailFranchiseMemberLimit, "setter must clamp to the row min")
        store.setDetailFranchiseMemberLimit(999)
        assertEquals(100, store.library.first().detailFranchiseMemberLimit, "setter must clamp to the row max")
        // 26. Smart play priority (enum-by-name).
        store.setDetailSmartPlayPriority(SmartPlayPriority.NEXT_UP_FIRST)
        assertEquals(SmartPlayPriority.NEXT_UP_FIRST, store.library.first().detailSmartPlayPriority)
        // 27. Stream fallback language (3-letter validation).
        store.setDetailStreamFallbackLanguage("  JPN ")
        assertEquals("jpn", store.library.first().detailStreamFallbackLanguage)
        store.setDetailStreamFallbackLanguage("toolong")
        assertEquals("eng", store.library.first().detailStreamFallbackLanguage, "non-3-letter token must reset to the default")
        store.setDetailStreamFallbackLanguage("e1!")
        assertEquals("eng", store.library.first().detailStreamFallbackLanguage, "non-letter token must reset to the default")
        // 28. Promoted rail source (enum-by-name).
        store.setDetailPromotedRailSource(PromotedRailSource.COMPACT)
        assertEquals(PromotedRailSource.COMPACT, store.library.first().detailPromotedRailSource)
        // 29. Episode overview preview.
        store.setDetailEpisodeOverviewPreview(true)
        assertEquals(true, store.library.first().detailEpisodeOverviewPreview)

        // Reset: restore the empty slice (every row back to its spec default)
        // and assert the defaults per row.
        store.restore(LibrarySlice())
        val reset = store.library.first()
        assertEquals(DetailDensity.DEFAULT, reset.detailCastItemSize)
        assertEquals(12, reset.detailCastSeeAllLimit)
        assertEquals(true, reset.detailBackdropParallax)
        assertEquals(3, reset.detailOverviewCollapseLines)
        assertEquals(false, reset.detailHideWatchedEpisodes)
        assertEquals(false, reset.detailConfirmMarkPlayed)
        assertEquals(true, reset.detailShowStudioChips)
        assertEquals(true, reset.detailShowGenreChips)
        assertEquals(true, reset.detailShowTagChips)
        assertEquals(true, reset.detailShowSkipChips)
        assertEquals(false, reset.detailSeasonTabsArtwork)
        assertEquals(DetailDensity.DEFAULT, reset.detailPeopleGridDensity)
        assertEquals(true, reset.detailShowTrailerBadges)
        assertEquals(false, reset.detailPersistSectionHides)
        assertEquals(emptySet<String>(), reset.detailHiddenFranchiseIds)
        assertEquals(12, reset.detailSimilarLimit)
        assertEquals(12, reset.detailUpcomingLimit)
        assertEquals("", reset.detailSectionOrder)
        assertEquals(DetailStaggerStyle.DEFAULT, reset.detailStaggerStyle)
        assertEquals(DetailBackdropResolution.FHD_1920, reset.detailBackdropResolution)
        assertEquals(DetailChapterCardSize.DEFAULT, reset.detailChapterCardSize)
        assertEquals(6, reset.detailReviewsPreviewLines)
        assertEquals(FilmographySort.DEFAULT, reset.detailFilmographySort)
        assertEquals(2, reset.detailFranchiseBoxLimit)
        assertEquals(50, reset.detailFranchiseMemberLimit)
        assertEquals(SmartPlayPriority.DEFAULT, reset.detailSmartPlayPriority)
        assertEquals("eng", reset.detailStreamFallbackLanguage)
        assertEquals(PromotedRailSource.DEFAULT, reset.detailPromotedRailSource)
        assertEquals(false, reset.detailEpisodeOverviewPreview)
    }
}
