package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the [DetailSectionAdmission] fold that replaced [DetailContentBody]'s
 * inline `if` / `visible` gates: for every media family × origin × capability
 * edge, the ordered section list and each section's delayIndex must match the
 * former inline emission exactly.
 *
 * Two classes of behavior are pinned here:
 *  1. membership — sections admitted for the specific media family (SEASONS
 *     for series/episodes/seasons, COLLECTION_ITEMS for collections,
 *     ALBUM_TRACKS for audio, BOOK_READING_CARD for books);
 *  2. the stagger indices — byte-identical to the literals the body used to
 *     pass to [StaggeredDetailSection] (they drive the shared entrance
 *     animation arithmetic).
 */
class DetailSectionAdmissionTest {

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun allCapabilities() = DetailCapabilities(
        remoteDiscovery = true,
        remoteStreamSelection = true,
        localSubtitleSelection = true,
        localStreamInfo = true,
        personNavigation = true,
        studioNavigation = true,
        smartPlay = true,
        remoteWorkAllowed = true,
        localDownloadManagement = true,
        tagNavigation = true,
        chapters = true,
    )

    /** Offline/local capability set: every remote-only lever off, local levers on. */
    private fun localCapabilities() = DetailCapabilities(
        remoteDiscovery = false,
        remoteStreamSelection = false,
        localSubtitleSelection = true,
        localStreamInfo = true,
        personNavigation = false,
        studioNavigation = false,
        smartPlay = false,
        remoteWorkAllowed = false,
        localDownloadManagement = true,
        tagNavigation = false,
        chapters = true,
    )

    private fun admission(
        mediaType: MediaType,
        isLocalOrigin: Boolean = false,
        capabilities: DetailCapabilities = allCapabilities(),
        showActionButtons: Boolean = true,
        showMediaInfo: Boolean = true,
        hasBookState: Boolean = false,
        hasBookToc: Boolean = false,
        hasChapters: Boolean = true,
        hasAlbumTracks: Boolean = true,
        hasSmartPlayTarget: Boolean = true,
        showDetailUpNext: Boolean = true,
        seerrDataAvailable: Boolean = true,
        hasSeerrRecommendations: Boolean = true,
        hasSeerrSimilar: Boolean = true,
        hasSpecialFeatures: Boolean = true,
        hasAttachedDownload: Boolean = false,
        hasPluginSimilar: Boolean = false,
        suppressStockSimilar: Boolean = false,
        hasFranchiseCollections: Boolean = false,
        showMoreInCollection: Boolean = true,
        hasUpcomingEpisodes: Boolean = false,
        showUpcomingStrip: Boolean = true,
    ): List<DetailSectionKind> = DetailSectionAdmission(
        mediaType = mediaType,
        isLocalOrigin = isLocalOrigin,
        capabilities = capabilities,
        showActionButtons = showActionButtons,
        showMediaInfo = showMediaInfo,
        hasBookState = hasBookState,
        hasBookToc = hasBookToc,
        hasChapters = hasChapters,
        hasAlbumTracks = hasAlbumTracks,
        hasSmartPlayTarget = hasSmartPlayTarget,
        showDetailUpNext = showDetailUpNext,
        seerrDataAvailable = seerrDataAvailable,
        hasSeerrRecommendations = hasSeerrRecommendations,
        hasSeerrSimilar = hasSeerrSimilar,
        hasSpecialFeatures = hasSpecialFeatures,
        hasAttachedDownload = hasAttachedDownload,
        hasPluginSimilar = hasPluginSimilar,
        suppressStockSimilar = suppressStockSimilar,
        hasFranchiseCollections = hasFranchiseCollections,
        showMoreInCollection = showMoreInCollection,
        hasUpcomingEpisodes = hasUpcomingEpisodes,
        showUpcomingStrip = showUpcomingStrip,
    ).admit()

    private fun List<DetailSectionKind>.indices(): List<Int> = map { it.delayIndex }

    // ── MOVIE ─────────────────────────────────────────────────────────────

    @Test
    fun remoteMovie_fullAdmission_matchesInlineEmission() {
        // PLUGIN_RATINGS and TMDB_REVIEWS left the fold in the wave-1 section
        // module pilot (their modules own those rows now) — this pin covers the
        // hand-written sections only; the merged emission is pinned by
        // DetailSectionRegistryTest.
        assertEquals(
            listOf(
                DetailSectionKind.HEADER,
                DetailSectionKind.ACTION_ROW,
                DetailSectionKind.MEDIA_INFO,
                DetailSectionKind.OVERVIEW,
                DetailSectionKind.CHAPTERS_OR_TOC,
                DetailSectionKind.CAST,
                DetailSectionKind.RELATED_VIDEOS,
                DetailSectionKind.MORE_LIKE_THIS,
                DetailSectionKind.SEERR_RECOMMENDATIONS,
                DetailSectionKind.SEERR_SIMILAR,
                DetailSectionKind.SPECIAL_FEATURES,
            ),
            admission(MediaType.MOVIE, hasAttachedDownload = false),
        )
        assertEquals(
            listOf(0, 1, 2, 3, 4, 8, 9, 10, 11, 12, 13),
            admission(MediaType.MOVIE).indices(),
        )
    }

    @Test
    fun remoteMovie_withAttachedDownload_appendsFooterAt15() {
        val sections = admission(MediaType.MOVIE, hasAttachedDownload = true)
        assertEquals(DetailSectionKind.DOWNLOAD_FOOTER, sections.last())
        assertEquals(15, sections.last().delayIndex)
    }

    @Test
    fun remoteMovie_withoutDownload_orLocalPresence_hasNoFooter() {
        assertFalse(admission(MediaType.MOVIE, hasAttachedDownload = false).contains(DetailSectionKind.DOWNLOAD_FOOTER))
    }

    @Test
    fun localMovie_offlineCapabilities_admitsLocalSectionsOnly() {
        // Local origin: Seerr rows and special features are remote-only (dropped),
        // the footer always renders, MORE_LIKE_THIS/RELATED_VIDEOS/CAST stay
        // admitted (their content gates are render-side).
        assertEquals(
            listOf(
                DetailSectionKind.HEADER,
                DetailSectionKind.ACTION_ROW,
                DetailSectionKind.MEDIA_INFO,
                DetailSectionKind.OVERVIEW,
                DetailSectionKind.CHAPTERS_OR_TOC,
                DetailSectionKind.CAST,
                DetailSectionKind.RELATED_VIDEOS,
                DetailSectionKind.MORE_LIKE_THIS,
                DetailSectionKind.DOWNLOAD_FOOTER,
            ),
            admission(
                mediaType = MediaType.MOVIE,
                isLocalOrigin = true,
                capabilities = localCapabilities(),
                seerrDataAvailable = false,
                hasSeerrRecommendations = false,
                hasSeerrSimilar = false,
                hasSpecialFeatures = false,
            ),
        )
    }

    @Test
    fun localMovie_footerAdmitted_evenWithoutAttachedDownload() {
        val sections = admission(MediaType.MOVIE, isLocalOrigin = true, hasAttachedDownload = false)
        assertEquals(DetailSectionKind.DOWNLOAD_FOOTER, sections.last())
    }

    @Test
    fun movie_landscapeBody_dropsActionRow_only() {
        val sections = admission(MediaType.MOVIE, showActionButtons = false)
        assertFalse(sections.contains(DetailSectionKind.ACTION_ROW))
        assertEquals(0, sections.first().delayIndex)
        // Everything else keeps its exact slot. Slot 14 (TMDB_REVIEWS) is
        // registry-owned since the section-module wave — pinned by
        // DetailSectionRegistryTest's merged-emission test, not by the fold.
        assertEquals(listOf(0, 2, 3, 4, 8, 9, 10, 11, 12, 13), sections.indices())
    }

    @Test
    fun movie_mediaInfoSuppressed_dropsOnlyMediaInfo() {
        val sections = admission(MediaType.MOVIE, showMediaInfo = false)
        assertFalse(sections.contains(DetailSectionKind.MEDIA_INFO))
        assertEquals(3, sections.first { it == DetailSectionKind.OVERVIEW }.delayIndex)
    }

    @Test
    fun movie_chaptersGate_isCapabilityAndContentConjunction() {
        // No chapter content, no capability → section gone.
        assertFalse(
            admission(MediaType.MOVIE, capabilities = localCapabilities().copy(chapters = false), hasChapters = false)
                .contains(DetailSectionKind.CHAPTERS_OR_TOC),
        )
        // Content without the capability → gone.
        assertFalse(
            admission(MediaType.MOVIE, capabilities = allCapabilities().copy(chapters = false), hasChapters = true)
                .contains(DetailSectionKind.CHAPTERS_OR_TOC),
        )
        // Capability without content → gone.
        assertFalse(
            admission(MediaType.MOVIE, capabilities = allCapabilities().copy(chapters = true), hasChapters = false)
                .contains(DetailSectionKind.CHAPTERS_OR_TOC),
        )
        // Both → admitted at slot 4.
        assertTrue(admission(MediaType.MOVIE).contains(DetailSectionKind.CHAPTERS_OR_TOC))
        assertEquals(4, admission(MediaType.MOVIE).first { it == DetailSectionKind.CHAPTERS_OR_TOC }.delayIndex)
    }

    // ── EPISODE ───────────────────────────────────────────────────────────

    @Test
    fun remoteEpisode_seasonsAdmitted_upNextNever() {
        val sections = admission(MediaType.EPISODE)
        assertTrue(sections.contains(DetailSectionKind.SEASONS))
        assertFalse(sections.contains(DetailSectionKind.COLLECTION_ITEMS))
        assertFalse(sections.contains(DetailSectionKind.UP_NEXT))
        assertFalse(sections.contains(DetailSectionKind.BOOK_READING_CARD))
        // Slot 14 (TMDB_REVIEWS) is registry-owned since the section-module
        // wave — pinned by DetailSectionRegistryTest's merged-emission test.
        assertEquals(
            listOf(0, 1, 2, 3, 4, 6, 8, 9, 10, 11, 12, 13),
            sections.indices(),
        )
    }

    @Test
    fun localEpisode_seriesAggregateFooter_pinnedViaLocalOrigin() {
        // An episode is the canonical local-series member: the download footer
        // renders from the LOCAL origin alone (the header aggregate stays a
        // render-side gate — it reads DetailContext data the fold doesn't need).
        val sections = admission(
            mediaType = MediaType.EPISODE,
            isLocalOrigin = true,
            capabilities = localCapabilities(),
            seerrDataAvailable = false,
            hasSeerrRecommendations = false,
            hasSeerrSimilar = false,
            hasSpecialFeatures = false,
        )
        assertEquals(DetailSectionKind.DOWNLOAD_FOOTER, sections.last())
        assertEquals(15, sections.last().delayIndex)
    }

    // ── SERIES ────────────────────────────────────────────────────────────

    @Test
    fun remoteSeries_upNextAdmittedBeforeSeasons_atSharedSlot6() {
        val sections = admission(MediaType.SERIES)
        val upNext = sections.indexOf(DetailSectionKind.UP_NEXT)
        val seasons = sections.indexOf(DetailSectionKind.SEASONS)
        assertTrue(upNext in 0 until seasons, "UP_NEXT must be emitted before SEASONS")
        assertEquals(6, sections[upNext].delayIndex)
        assertEquals(6, sections[seasons].delayIndex)
    }

    @Test
    fun series_upNext_hiddenByPreference_dropsOnlyUpNext() {
        val sections = admission(MediaType.SERIES, showDetailUpNext = false)
        assertFalse(sections.contains(DetailSectionKind.UP_NEXT))
        assertTrue(sections.contains(DetailSectionKind.SEASONS))
        // SEASONS keeps its shared slot-6 index.
        assertEquals(6, sections.first { it == DetailSectionKind.SEASONS }.delayIndex)
    }

    @Test
    fun series_upNext_withoutSmartTarget_dropped() {
        assertFalse(
            admission(MediaType.SERIES, hasSmartPlayTarget = false)
                .contains(DetailSectionKind.UP_NEXT),
        )
        // smartPlay capability is NOT a fold gate — the target presence is
        // (capabilities.smartPlay is resolved upstream into the target).
        assertTrue(
            admission(
                MediaType.SERIES,
                capabilities = allCapabilities().copy(smartPlay = false),
                hasSmartPlayTarget = true,
            ).contains(DetailSectionKind.UP_NEXT),
        )
    }

    // ── BOOK ──────────────────────────────────────────────────────────────

    @Test
    fun localBook_withToc_readingCardSharesSlot2_castDropped() {
        val sections = admission(
            mediaType = MediaType.BOOK,
            isLocalOrigin = true,
            capabilities = localCapabilities(),
            hasBookState = true,
            hasBookToc = true,
            seerrDataAvailable = false,
            hasSeerrRecommendations = false,
            hasSeerrSimilar = false,
            hasSpecialFeatures = false,
        )
        assertEquals(
            listOf(
                DetailSectionKind.HEADER,
                DetailSectionKind.ACTION_ROW,
                DetailSectionKind.BOOK_READING_CARD,
                // Books are not an audio type: the media-info slot still emits
                // (its branch selection renders nothing for a book) — the
                // reading card occupies it visually, both at delayIndex 2.
                DetailSectionKind.MEDIA_INFO,
                DetailSectionKind.OVERVIEW,
                DetailSectionKind.CHAPTERS_OR_TOC,
                // RELATED_VIDEOS / MORE_LIKE_THIS stay admitted: their list
                // choice (localRelatedItems vs relatedItems) and empty gates
                // are render-side, exactly as before.
                DetailSectionKind.RELATED_VIDEOS,
                DetailSectionKind.MORE_LIKE_THIS,
                DetailSectionKind.DOWNLOAD_FOOTER,
            ),
            sections,
        )
        assertEquals(2, sections[2].delayIndex)
        assertEquals(2, sections[3].delayIndex)
        assertFalse(sections.contains(DetailSectionKind.CAST))
        assertFalse(sections.contains(DetailSectionKind.SEASONS))
        assertFalse(sections.contains(DetailSectionKind.COLLECTION_ITEMS))
    }

    @Test
    fun book_withoutBookState_dropsReadingCard_fallsBackToChapterGate() {
        val sections = admission(
            mediaType = MediaType.BOOK,
            hasBookState = false,
            hasBookToc = false,
            hasChapters = false,
            capabilities = localCapabilities().copy(chapters = false),
        )
        assertFalse(sections.contains(DetailSectionKind.BOOK_READING_CARD))
        assertFalse(sections.contains(DetailSectionKind.CHAPTERS_OR_TOC))
    }

    @Test
    fun book_tocAdmitsContentsSection_withoutChapterCapability() {
        // The TOC branch does not read capabilities.chapters — the local TOC
        // cache is the authority for books.
        val sections = admission(
            mediaType = MediaType.BOOK,
            hasBookState = true,
            hasBookToc = true,
            capabilities = localCapabilities().copy(chapters = false),
        )
        assertTrue(sections.contains(DetailSectionKind.CHAPTERS_OR_TOC))
        assertEquals(4, sections.first { it == DetailSectionKind.CHAPTERS_OR_TOC }.delayIndex)
    }

    @Test
    fun book_stillNotAudio_mediaInfoSlotStaysAdmitted() {
        // Pins the pre-existing behavior the old comment ("never renders for
        // books") drifted from: the visible flag only excluded audio types.
        assertTrue(admission(MediaType.BOOK, hasBookState = true).contains(DetailSectionKind.MEDIA_INFO))
    }

    // ── ALBUM / audio ─────────────────────────────────────────────────────

    @Test
    fun remoteAlbum_tracksAdmitted_mediaInfoDropped() {
        // Albums are an audio type: the stream-info slot never admits, the
        // track list takes delayIndex 5.
        val sections = admission(
            mediaType = MediaType.ALBUM,
            hasChapters = false,
            hasAlbumTracks = true,
        )
        assertEquals(
            listOf(
                DetailSectionKind.HEADER,
                DetailSectionKind.ACTION_ROW,
                DetailSectionKind.OVERVIEW,
                DetailSectionKind.ALBUM_TRACKS,
                DetailSectionKind.CAST,
                DetailSectionKind.RELATED_VIDEOS,
                DetailSectionKind.MORE_LIKE_THIS,
                DetailSectionKind.SEERR_RECOMMENDATIONS,
                DetailSectionKind.SEERR_SIMILAR,
                DetailSectionKind.SPECIAL_FEATURES,
            ),
            sections,
        )
        assertEquals(5, sections.first { it == DetailSectionKind.ALBUM_TRACKS }.delayIndex)
        assertFalse(sections.contains(DetailSectionKind.SEASONS))
        assertFalse(sections.contains(DetailSectionKind.COLLECTION_ITEMS))
    }

    @Test
    fun audioTrack_withoutTracks_dropsTrackList_andKeepsMediaInfoDropped() {
        val sections = admission(MediaType.AUDIO, hasAlbumTracks = false, hasChapters = false)
        assertFalse(sections.contains(DetailSectionKind.ALBUM_TRACKS))
        assertFalse(sections.contains(DetailSectionKind.MEDIA_INFO))
        assertFalse(sections.contains(DetailSectionKind.CHAPTERS_OR_TOC))
    }

    // ── COLLECTION ────────────────────────────────────────────────────────

    @Test
    fun remoteCollection_itemsSlotAdmittedOnlyForCollections() {
        val sections = admission(MediaType.COLLECTION)
        assertTrue(sections.contains(DetailSectionKind.COLLECTION_ITEMS))
        assertFalse(sections.contains(DetailSectionKind.SEASONS))
        assertEquals(7, sections.first { it == DetailSectionKind.COLLECTION_ITEMS }.delayIndex)
        // The empty-items gate is render-side: admission checks only the
        // media type, never the collection members.
    }

    // ── Seerr / special-features gates ────────────────────────────────────

    @Test
    fun seerrSections_requireConnectionAndRecommendationEnabled() {
        val none = admission(MediaType.MOVIE, seerrDataAvailable = false)
        assertFalse(none.contains(DetailSectionKind.SEERR_RECOMMENDATIONS))
        assertFalse(none.contains(DetailSectionKind.SEERR_SIMILAR))

        val recsOnly = admission(
            MediaType.MOVIE,
            seerrDataAvailable = true,
            hasSeerrRecommendations = true,
            hasSeerrSimilar = false,
        )
        assertTrue(recsOnly.contains(DetailSectionKind.SEERR_RECOMMENDATIONS))
        assertFalse(recsOnly.contains(DetailSectionKind.SEERR_SIMILAR))

        val simOnly = admission(
            MediaType.MOVIE,
            seerrDataAvailable = true,
            hasSeerrRecommendations = false,
            hasSeerrSimilar = true,
        )
        assertFalse(simOnly.contains(DetailSectionKind.SEERR_RECOMMENDATIONS))
        assertTrue(simOnly.contains(DetailSectionKind.SEERR_SIMILAR))

        // Slot order preserved: recommendations (11) before similar (12).
        val both = admission(MediaType.MOVIE)
        assertEquals(11, both.first { it == DetailSectionKind.SEERR_RECOMMENDATIONS }.delayIndex)
        assertEquals(12, both.first { it == DetailSectionKind.SEERR_SIMILAR }.delayIndex)
    }

    @Test
    fun specialFeatures_requireRemoteDiscovery() {
        assertFalse(
            admission(MediaType.MOVIE, capabilities = allCapabilities().copy(remoteDiscovery = false))
                .contains(DetailSectionKind.SPECIAL_FEATURES),
        )
        assertEquals(
            13,
            admission(MediaType.MOVIE).first { it == DetailSectionKind.SPECIAL_FEATURES }.delayIndex,
        )
    }

    // (The PLUGIN_RATINGS / TMDB_REVIEWS admission pins moved to
    // DetailSectionRegistryTest — the wave-1 section modules own those rows.)

    // ── delayIndex ratchet ────────────────────────────────────────────────

    @Test
    fun everySectionKind_delayIndexMatchesTheFormerInlineLiterals() {
        val expected = mapOf(
            DetailSectionKind.HEADER to 0,
            DetailSectionKind.ACTION_ROW to 1,
            DetailSectionKind.BOOK_READING_CARD to 2,
            DetailSectionKind.MEDIA_INFO to 2,
            DetailSectionKind.OVERVIEW to 3,
            DetailSectionKind.PLUGIN_RATINGS to 3,
            DetailSectionKind.CHAPTERS_OR_TOC to 4,
            DetailSectionKind.ALBUM_TRACKS to 5,
            DetailSectionKind.UP_NEXT to 6,
            DetailSectionKind.UPCOMING_EPISODES to 6,
            DetailSectionKind.SEASONS to 6,
            DetailSectionKind.COLLECTION_ITEMS to 7,
            DetailSectionKind.CAST to 8,
            DetailSectionKind.RELATED_VIDEOS to 9,
            DetailSectionKind.MORE_IN_COLLECTION to 10,
            DetailSectionKind.MORE_LIKE_THIS to 10,
            DetailSectionKind.JELLYPLAY_SIMILAR to 10,
            DetailSectionKind.SEERR_RECOMMENDATIONS to 11,
            DetailSectionKind.SEERR_SIMILAR to 12,
            DetailSectionKind.SPECIAL_FEATURES to 13,
            DetailSectionKind.TMDB_REVIEWS to 14,
            DetailSectionKind.DOWNLOAD_FOOTER to 15,
        )
        assertEquals(expected.size, DetailSectionKind.entries.size, "unmapped DetailSectionKind entry")
        DetailSectionKind.entries.forEach { kind ->
            assertEquals(expected[kind], kind.delayIndex, "delayIndex drift for $kind")
            assertTrue(kind.delayIndex in 0..DETAIL_MAX_STAGGER_INDEX)
        }
        // The entrance driver animates to 1f + MAX * STEP so the highest slot
        // still settles at full alpha — the footer must never exceed it.
        assertEquals(15, DETAIL_MAX_STAGGER_INDEX)
    }

    // ── D7: the UPCOMING_EPISODES strip ───────────────────────────────────

    @Test
    fun upcomingStrip_admitsInTheSeriesTrio_sharedSlot6() {
        val sections = admission(MediaType.SERIES, hasUpcomingEpisodes = true)
        val upNext = sections.indexOf(DetailSectionKind.UP_NEXT)
        val upcoming = sections.indexOf(DetailSectionKind.UPCOMING_EPISODES)
        val seasons = sections.indexOf(DetailSectionKind.SEASONS)
        // Emission order: the unaired strip sits between the smart-play card
        // and the season tree; all three share the stagger band 6.
        assertTrue(upNext in 0 until upcoming, "UP_NEXT must be emitted before UPCOMING_EPISODES")
        assertTrue(upcoming in 0 until seasons, "UPCOMING_EPISODES must be emitted before SEASONS")
        assertEquals(6, sections[upcoming].delayIndex)
        assertEquals(6, sections[upNext].delayIndex)
        assertEquals(6, sections[seasons].delayIndex)
    }

    @Test
    fun upcomingStrip_emptyOrPreferenceOff_dropsOnlyUpcoming() {
        // No strip content (the enrichment's silent absence) → no slot.
        assertFalse(admission(MediaType.SERIES, hasUpcomingEpisodes = false).contains(DetailSectionKind.UPCOMING_EPISODES))
        // Preference off → no slot even with content; UP_NEXT/SEASONS stand.
        val off = admission(MediaType.SERIES, hasUpcomingEpisodes = true, showUpcomingStrip = false)
        assertFalse(off.contains(DetailSectionKind.UPCOMING_EPISODES))
        assertTrue(off.contains(DetailSectionKind.UP_NEXT))
        assertTrue(off.contains(DetailSectionKind.SEASONS))
        // Admission is content-driven, NOT media-type-driven: a movie with
        // (impossible-by-enrichment) content would admit — the fold stays a
        // pure input table. The SERIES-only rule lives on the enrichment.
        assertTrue(admission(MediaType.MOVIE, hasUpcomingEpisodes = true).contains(DetailSectionKind.UPCOMING_EPISODES))
    }

    // ── D6: the MORE_IN_COLLECTION franchise rows ─────────────────────────

    @Test
    fun franchiseRow_admittedJustBeforeTheSimilarRows_sharedSlot10() {
        val sections = admission(MediaType.MOVIE, hasFranchiseCollections = true)
        val franchise = sections.indexOf(DetailSectionKind.MORE_IN_COLLECTION)
        val relatedVideos = sections.indexOf(DetailSectionKind.RELATED_VIDEOS)
        val moreLikeThis = sections.indexOf(DetailSectionKind.MORE_LIKE_THIS)
        assertTrue(relatedVideos in 0 until franchise, "RELATED_VIDEOS must precede the franchise rows")
        assertTrue(franchise in 0 until moreLikeThis, "the franchise rows precede MORE_LIKE_THIS")
        assertEquals(10, sections[franchise].delayIndex)
        assertEquals(10, sections[moreLikeThis].delayIndex)
    }

    @Test
    fun franchiseRow_emptyOrPreferenceOff_dropsOnlyFranchise() {
        assertFalse(
            admission(MediaType.MOVIE, hasFranchiseCollections = false).contains(DetailSectionKind.MORE_IN_COLLECTION),
        )
        val off = admission(MediaType.MOVIE, hasFranchiseCollections = true, showMoreInCollection = false)
        assertFalse(off.contains(DetailSectionKind.MORE_IN_COLLECTION))
        // The similar rows are untouched by the franchise preference.
        assertTrue(off.contains(DetailSectionKind.MORE_LIKE_THIS))
    }

    @Test
    fun franchiseRow_admittedForSeriesToo() {
        // The D6 gate is MOVIE || SERIES (the enrichment's rule); the fold
        // itself is content-driven for both.
        assertTrue(admission(MediaType.SERIES, hasFranchiseCollections = true).contains(DetailSectionKind.MORE_IN_COLLECTION))
    }

    // ── the state mapper: hidden rows never admit ─────────────────────────

    @Test
    fun fromState_hiddenFranchiseRows_excludeFromAdmission() {
        val item = MediaItem(id = "i3", name = "Movie", mediaType = MediaType.MOVIE)
        val detail = MediaDetail(item = item)
        fun stateWith(hidden: Map<String, Set<String>>) = DetailContentState(
            itemId = "i3",
            detail = detail,
            seasons = emptyList(),
            episodes = emptyMap(),
            fetchedSeasonIds = emptySet(),
            smartPlayTarget = null,
            selectedSubtitleIndex = null,
            selectedAudioIndex = null,
            isDownloading = false,
            isDownloadingSeries = false,
            loadState = DetailUiLoadState.Loaded,
            albumTracks = emptyList(),
            collectionItems = emptyList(),
            relatedItems = emptyList(),
            relatedVideos = emptyList(),
            seerrRecommendations = emptyList(),
            seerrSimilar = emptyList(),
            isSeerrConnected = false,
            isSeerrRecommendationsEnabled = false,
            preferences = DetailPreferences(),
            canManageSeries = false,
            origin = DetailOrigin.REMOTE,
            franchiseCollections = listOf(
                DetailUiState.FranchiseCollection(id = "box1", name = "Franchise", members = listOf(item)),
                DetailUiState.FranchiseCollection(id = "box2", name = "Shared universe", members = listOf(item)),
            ),
            hiddenFranchiseCollections = hidden,
        )

        // No hides → admitted.
        assertTrue(DetailSectionAdmission.from(stateWith(emptyMap()), detail).hasFranchiseCollections)
        // One row hidden → the other keeps the slot admitted.
        val oneHidden = DetailSectionAdmission.from(stateWith(mapOf("i3" to setOf("box1"))), detail)
        assertTrue(oneHidden.hasFranchiseCollections)
        // Every row hidden → the slot drops (no empty gap in the body).
        val allHidden = DetailSectionAdmission.from(stateWith(mapOf("i3" to setOf("box1", "box2"))), detail)
        assertFalse(allHidden.hasFranchiseCollections)
        assertFalse(allHidden.admit().contains(DetailSectionKind.MORE_IN_COLLECTION))
        // Hides are keyed per item: another item's hides don't leak in.
        val otherItemsHide = DetailSectionAdmission.from(stateWith(mapOf("other" to setOf("box1", "box2"))), detail)
        assertTrue(otherItemsHide.hasFranchiseCollections)
    }

    // ── the state mapper ──────────────────────────────────────────────────

    @Test
    fun fromState_derivesInputsFromDetailContentState() {
        val item = MediaItem(id = "i1", name = "Album", mediaType = MediaType.ALBUM)
        val detail = MediaDetail(item = item)
        val state = DetailContentState(
            itemId = "i1",
            detail = detail,
            seasons = emptyList(),
            episodes = emptyMap(),
            fetchedSeasonIds = emptySet(),
            smartPlayTarget = null,
            selectedSubtitleIndex = null,
            selectedAudioIndex = null,
            isDownloading = false,
            isDownloadingSeries = false,
            loadState = DetailUiLoadState.Loaded,
            albumTracks = listOf(item),
            collectionItems = emptyList(),
            relatedItems = emptyList(),
            relatedVideos = emptyList(),
            seerrRecommendations = emptyList(),
            seerrSimilar = emptyList(),
            isSeerrConnected = true,
            isSeerrRecommendationsEnabled = false,
            preferences = DetailPreferences(),
            canManageSeries = false,
            origin = DetailOrigin.REMOTE,
        )

        val inputs = DetailSectionAdmission.from(state, detail)
        assertEquals(MediaType.ALBUM, inputs.mediaType)
        assertEquals(false, inputs.isLocalOrigin)
        assertFalse(inputs.hasBookState)
        assertFalse(inputs.hasBookToc)
        assertFalse(inputs.hasChapters)
        assertTrue(inputs.hasAlbumTracks)
        assertFalse(inputs.hasSmartPlayTarget)
        assertFalse(inputs.seerrDataAvailable, "connected but recommendations disabled must fold to false")
        assertFalse(inputs.hasAttachedDownload)
        assertTrue(inputs.showActionButtons)
        assertTrue(inputs.showMediaInfo)

        val sections = inputs.admit()
        assertFalse(sections.contains(DetailSectionKind.MEDIA_INFO))
        assertTrue(sections.contains(DetailSectionKind.ALBUM_TRACKS))
    }

    @Test
    fun fromState_localOrigins_foldToLocalPredicate() {
        val item = MediaItem(id = "i2", name = "Movie", mediaType = MediaType.MOVIE)
        val detail = MediaDetail(item = item)
        DetailOrigin.entries.forEach { origin ->
            val state = DetailContentState(
                itemId = "i2",
                detail = detail,
                seasons = emptyList(),
                episodes = emptyMap(),
                fetchedSeasonIds = emptySet(),
                smartPlayTarget = null,
                selectedSubtitleIndex = null,
                selectedAudioIndex = null,
                isDownloading = false,
                isDownloadingSeries = false,
                loadState = DetailUiLoadState.Loaded,
                albumTracks = emptyList(),
                collectionItems = emptyList(),
                relatedItems = emptyList(),
                relatedVideos = emptyList(),
                seerrRecommendations = emptyList(),
                seerrSimilar = emptyList(),
                isSeerrConnected = false,
                isSeerrRecommendationsEnabled = false,
                preferences = DetailPreferences(),
                canManageSeries = false,
                origin = origin,
            )
            assertEquals(
                origin.isLocal,
                DetailSectionAdmission.from(state, detail).isLocalOrigin,
                "origin $origin",
            )
        }
    }

    // ── JellyPlay plugin sections (ADR 0010) ────────────────────────────

    @Test
    fun pluginSimilar_admitsOnlyWhenItsContentArrived() {
        // Silent absence: no plugin data → the plugin row not in the fold at
        // all (the PLUGIN_RATINGS row's pins moved to DetailSectionRegistryTest
        // with its module).
        val bare = admission(MediaType.MOVIE)
        assertFalse(bare.contains(DetailSectionKind.JELLYPLAY_SIMILAR))

        // Scored similar present → the plugin row admits (same slot 10 as the
        // stock row), but suppression is NOT tied to its presence: on hosts
        // where the plugin's scorer did NOT register into the stock pipeline
        // (pre-Jellyfin-12 / older plugin) the two lists differ and BOTH rows
        // render.
        val withSimilar = admission(MediaType.MOVIE, hasPluginSimilar = true)
        assertTrue(withSimilar.contains(DetailSectionKind.JELLYPLAY_SIMILAR))
        assertTrue(withSimilar.contains(DetailSectionKind.MORE_LIKE_THIS), "no pipeline → stock row stays")
        assertEquals(10, DetailSectionKind.JELLYPLAY_SIMILAR.delayIndex)

        // Pipeline host (capabilities said the plugin registered into the
        // host's similar-items pipeline) → the stock endpoint returns the
        // SAME scored list; the stock row stands down to avoid the duplicate.
        val withSimilarOnPipeline = admission(MediaType.MOVIE, hasPluginSimilar = true, suppressStockSimilar = true)
        assertTrue(withSimilarOnPipeline.contains(DetailSectionKind.JELLYPLAY_SIMILAR))
        assertFalse(withSimilarOnPipeline.contains(DetailSectionKind.MORE_LIKE_THIS), "pipeline host → stock row suppressed")

        // Suppression never suppresses the plugin row itself (an odd
        // capability/empty-list combination just reverts to stock).
        assertTrue(admission(MediaType.MOVIE, suppressStockSimilar = true).contains(DetailSectionKind.MORE_LIKE_THIS))

        // Empty plugin hydration → the stock row stays admitted (bare above
        // already contains MORE_LIKE_THIS — re-asserted here against the
        // plugin row's absence).
        assertTrue(bare.contains(DetailSectionKind.MORE_LIKE_THIS))
    }
}

// ── showsSeasonTree (the SEASONS slot's render-side gate, #168) ──────────

class ShowsSeasonTreeTest {

    @Test
    fun `series episode and season entries render the season tree`() {
        assertTrue(showsSeasonTree(MediaType.SERIES))
        assertTrue(showsSeasonTree(MediaType.EPISODE))
        assertTrue(showsSeasonTree(MediaType.SEASON))
    }

    @Test
    fun `non-tree entry types do not render the season tree`() {
        assertFalse(showsSeasonTree(MediaType.MOVIE))
        assertFalse(showsSeasonTree(MediaType.BOOK))
        assertFalse(showsSeasonTree(MediaType.ALBUM))
        assertFalse(showsSeasonTree(MediaType.FOLDER))
        assertFalse(showsSeasonTree(MediaType.UNKNOWN))
    }
}

// ── showsParentSeriesContext (entries rendering a parent series' context) ─

class ShowsParentSeriesContextTest {

    @Test
    fun `episode and season entries render parent series context`() {
        assertTrue(showsParentSeriesContext(MediaType.EPISODE))
        assertTrue(showsParentSeriesContext(MediaType.SEASON))
    }

    @Test
    fun `series owns the context and other types never consume it`() {
        assertFalse(showsParentSeriesContext(MediaType.SERIES))
        assertFalse(showsParentSeriesContext(MediaType.MOVIE))
        assertFalse(showsParentSeriesContext(MediaType.BOOK))
        assertFalse(showsParentSeriesContext(MediaType.FOLDER))
    }
}
