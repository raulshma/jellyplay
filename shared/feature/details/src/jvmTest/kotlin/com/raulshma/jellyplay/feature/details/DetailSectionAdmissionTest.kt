package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.RemoteConnectivity
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the section modules' admission rows ([DetailSectionModule.isAdmitted])
 * that replaced [DetailContentBody]'s inline `if` / `visible` gates — the
 * former DetailSectionAdmission fold's rows moved out verbatim, wave by
 * wave, until every kind was module-owned and the fold retired: for every
 * media family × origin × capability edge, the merged section list and each
 * section's delayIndex must match the former inline emission exactly.
 *
 * Two classes of behavior are pinned here:
 *  1. membership — sections admitted for the specific media family (SEASONS
 *     for series/episodes/seasons, ALBUM_TRACKS for audio,
 *     BOOK_READING_CARD for books) and the content/preference-driven rows —
 *     via their modules directly or through the registry's merged emission;
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

    private fun List<DetailSectionKind>.indices(): List<Int> = map { it.delayIndex }

    /**
     * A [DetailContentState] carrying exactly the admission inputs the module
     * `isAdmitted` rows read (the module-owned kinds' tests build their pins
     * here — the migrated kinds read state directly, not the fold inputs).
     */
    private fun moduleState(
        mediaType: MediaType,
        capabilities: DetailCapabilities = allCapabilities(),
        isLocalOrigin: Boolean = false,
        hasBookState: Boolean = false,
        hasBookToc: Boolean = false,
        hasChapters: Boolean = true,
        hasAlbumTracks: Boolean = true,
        hasSmartPlayTarget: Boolean = true,
        showDetailUpNext: Boolean = true,
        seerrDataAvailable: Boolean = true,
        hasSeerrRecommendations: Boolean = false,
        hasSeerrSimilar: Boolean = false,
        hasPluginSimilar: Boolean = false,
        suppressStockSimilar: Boolean = false,
        hasAttachedDownload: Boolean = false,
    ): DetailContentState {
        val item = MediaItem(id = "i1", name = "Item", mediaType = mediaType)
        val detail = MediaDetail(
            item = item,
            chapters = if (hasChapters) {
                listOf(ChapterInfo(name = "Intro", startPositionTicks = 0L))
            } else {
                emptyList()
            },
        )
        return DetailContentState(
            core = DetailUiState(
                detail = detail,
                origin = if (isLocalOrigin) DetailOrigin.LOCAL_OFFLINE_MODE else DetailOrigin.REMOTE,
                capabilities = capabilities,
                book = if (hasBookState) {
                    DetailUiState.BookDetailState(
                        format = com.raulshma.jellyplay.core.model.BookFormat.EPUB,
                        toc = if (hasBookToc) {
                            listOf(
                                com.raulshma.jellyplay.core.model.BookTocEntry(
                                    label = "Chapter 1",
                                    href = "ch1.xhtml",
                                    page = null,
                                ),
                            )
                        } else {
                            emptyList()
                        },
                    )
                } else {
                    null
                },
                albumTracks = if (hasAlbumTracks) listOf(item) else emptyList(),
                smartPlayTarget = if (hasSmartPlayTarget) {
                    DetailUiState.SmartPlayTarget(episode = item, label = "Play", startPositionTicks = 0L)
                } else {
                    null
                },
                isSeerrConnected = seerrDataAvailable,
                isSeerrRecommendationsEnabled = seerrDataAvailable,
                seerrRecommendations = if (hasSeerrRecommendations) {
                    listOf(SeerrSearchItem(id = 1, mediaType = "movie"))
                } else {
                    emptyList()
                },
                seerrSimilar = if (hasSeerrSimilar) {
                    listOf(SeerrSearchItem(id = 2, mediaType = "movie"))
                } else {
                    emptyList()
                },
                pluginSimilarItems = if (hasPluginSimilar) {
                    listOf(MediaItem(id = "ps1", name = "Plugin similar", mediaType = MediaType.MOVIE))
                } else {
                    emptyList()
                },
                pluginSimilarSuppressesStock = suppressStockSimilar,
                detailContext = if (hasAttachedDownload) {
                    DetailContext(
                        origin = if (isLocalOrigin) DetailOrigin.LOCAL_OFFLINE_MODE else DetailOrigin.REMOTE,
                        connectivity = if (isLocalOrigin) RemoteConnectivity.BLOCKED else RemoteConnectivity.AVAILABLE,
                        download = com.raulshma.jellyplay.core.model.DownloadAttachment(
                            status = com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED,
                            downloadedBytes = 1L,
                            totalSizeBytes = 1L,
                            mediaSourceId = null,
                            container = null,
                            downloadPath = "/tmp/i1.epub",
                            createdAtEpochMillis = 0L,
                            isCompletedFilePresent = true,
                        ),
                        syncState = null,
                        seriesAggregate = null,
                    )
                } else {
                    null
                },
            ),
            itemId = "i1",
            preferences = DetailPreferences(showDetailUpNext = showDetailUpNext),
        )
    }

    private fun moduleDetail(state: DetailContentState): MediaDetail = requireNotNull(state.core.detail)

    // ── MOVIE ─────────────────────────────────────────────────────────────

    @Test
    fun remoteMovie_withAttachedDownload_appendsFooterAt15() {
        // The footer is module-owned (the `hasAttachedDownload || isLocalOrigin`
        // row moved verbatim); its last-in-body position rides the enum's
        // ordinal via the registry's merged emission.
        val movie = moduleState(MediaType.MOVIE, hasAttachedDownload = true)
        assertTrue(DownloadFooterSectionModule.isAdmitted(movie, moduleDetail(movie)))
        val sections = DetailSectionRegistry.admittedSections(state = movie, detail = moduleDetail(movie))
        assertEquals(DetailSectionKind.DOWNLOAD_FOOTER, sections.last())
        assertEquals(15, sections.last().delayIndex)
    }

    @Test
    fun remoteMovie_withoutDownload_orLocalPresence_hasNoFooter() {
        val movie = moduleState(MediaType.MOVIE, hasAttachedDownload = false)
        assertFalse(DownloadFooterSectionModule.isAdmitted(movie, moduleDetail(movie)))
    }

    @Test
    fun localMovie_offlineCapabilities_admitsLocalSectionsOnly() {
        // Local origin: Seerr rows are remote-only (dropped), the footer
        // always renders, MORE_LIKE_THIS/RELATED_VIDEOS/CAST stay admitted
        // (their content gates are render-side). The merged emission is the
        // exact list the pre-module local movie produced.
        val local = moduleState(
            MediaType.MOVIE,
            isLocalOrigin = true,
            capabilities = localCapabilities(),
            seerrDataAvailable = false,
        )
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
            DetailSectionRegistry.admittedSections(state = local, detail = moduleDetail(local)),
        )
    }

    @Test
    fun localMovie_footerAdmitted_evenWithoutAttachedDownload() {
        val local = moduleState(MediaType.MOVIE, isLocalOrigin = true, hasAttachedDownload = false)
        assertTrue(DownloadFooterSectionModule.isAdmitted(local, moduleDetail(local)))
    }

    @Test
    fun movie_landscapeBody_dropsActionRow_only() {
        // The portrait-only flag is the ACTION_ROW module's admission param
        // (the fold row moved verbatim with the module).
        val movie = moduleState(MediaType.MOVIE)
        val movieDetail = moduleDetail(movie)
        assertTrue(ActionRowSectionModule.isAdmitted(movie, movieDetail, showActionButtons = true))
        assertFalse(ActionRowSectionModule.isAdmitted(movie, movieDetail, showActionButtons = false))
    }

    @Test
    fun movie_mediaInfoSuppressed_dropsOnlyMediaInfo() {
        // The media-info slot's portrait flag is the MEDIA_INFO module's
        // admission param (the fold row moved verbatim with the module).
        val movie = moduleState(MediaType.MOVIE)
        val movieDetail = moduleDetail(movie)
        assertTrue(MediaInfoSectionModule.isAdmitted(movie, movieDetail, showMediaInfo = true))
        assertFalse(MediaInfoSectionModule.isAdmitted(movie, movieDetail, showMediaInfo = false))
    }

    @Test
    fun movie_chaptersGate_isCapabilityAndContentConjunction() {
        // The `(BOOK && hasBookToc) || (capabilities.chapters && hasChapters)`
        // fold row moved verbatim into the module.
        fun movieAdmits(capabilities: DetailCapabilities, hasChapters: Boolean): Boolean =
            ChaptersOrTocSectionModule.isAdmitted(
                moduleState(MediaType.MOVIE, capabilities = capabilities, hasChapters = hasChapters),
                moduleDetail(
                    moduleState(MediaType.MOVIE, capabilities = capabilities, hasChapters = hasChapters),
                ),
            )

        // No chapter content, no capability → section gone.
        assertFalse(movieAdmits(localCapabilities().copy(chapters = false), hasChapters = false))
        // Content without the capability → gone.
        assertFalse(movieAdmits(allCapabilities().copy(chapters = false), hasChapters = true))
        // Capability without content → gone.
        assertFalse(movieAdmits(allCapabilities().copy(chapters = true), hasChapters = false))
        // Both → admitted at slot 4.
        assertTrue(movieAdmits(allCapabilities().copy(chapters = true), hasChapters = true))
        assertEquals(4, DetailSectionKind.CHAPTERS_OR_TOC.delayIndex)
    }

    // ── EPISODE ───────────────────────────────────────────────────────────

    @Test
    fun remoteEpisode_seasonsAdmitted_upNextNever() {
        // SEASONS (the parent-series context tree, #168) is module-owned —
        // its [showsSeasonTree] gate moved verbatim. The merged emission
        // carries the tree (an EPISODE renders its parent series' context)
        // but never UP_NEXT (a SERIES-only card) or the book reading card.
        val episode = moduleState(MediaType.EPISODE, hasSmartPlayTarget = false)
        assertTrue(SeasonsSectionModule.isAdmitted(episode, moduleDetail(episode)))
        val sections = DetailSectionRegistry.admittedSections(state = episode, detail = moduleDetail(episode))
        assertEquals(
            listOf(
                DetailSectionKind.HEADER,
                DetailSectionKind.ACTION_ROW,
                DetailSectionKind.MEDIA_INFO,
                DetailSectionKind.OVERVIEW,
                DetailSectionKind.CHAPTERS_OR_TOC,
                DetailSectionKind.SEASONS,
                DetailSectionKind.CAST,
                DetailSectionKind.RELATED_VIDEOS,
                DetailSectionKind.MORE_LIKE_THIS,
            ),
            sections,
        )
        assertFalse(sections.contains(DetailSectionKind.COLLECTION_ITEMS))
        assertFalse(sections.contains(DetailSectionKind.BOOK_READING_CARD))
        // Slot 14 (TMDB_REVIEWS) is registry-owned since the section-module
        // wave — pinned by DetailSectionRegistryTest's merged-emission test.
        assertEquals(6, sections.first { it == DetailSectionKind.SEASONS }.delayIndex)
    }

    @Test
    fun localEpisode_seriesAggregateFooter_pinnedViaLocalOrigin() {
        // An episode is the canonical local-series member: the download footer
        // renders from the LOCAL origin alone (the header aggregate stays a
        // render-side gate — it reads DetailContext data the fold doesn't need).
        val episode = moduleState(
            MediaType.EPISODE,
            isLocalOrigin = true,
            hasSmartPlayTarget = false,
        )
        assertTrue(DownloadFooterSectionModule.isAdmitted(episode, moduleDetail(episode)))
        val sections = DetailSectionRegistry.admittedSections(state = episode, detail = moduleDetail(episode))
        assertEquals(DetailSectionKind.DOWNLOAD_FOOTER, sections.last())
        assertEquals(15, sections.last().delayIndex)
    }

    // ── SERIES ────────────────────────────────────────────────────────────

    @Test
    fun remoteSeries_upNextAdmittedBeforeSeasons_atSharedSlot6() {
        // UP_NEXT is module-owned (SEASONS still rides the fold): the order
        // pin runs over the registry's merged emission (module rows + the
        // fold's remaining rows, re-sorted by the enum).
        val series = moduleState(MediaType.SERIES)
        val seriesDetail = moduleDetail(series)
        assertTrue(UpNextSectionModule.isAdmitted(series, seriesDetail))
        val sections = DetailSectionRegistry.admittedSections(state = series, detail = seriesDetail)
        val upNext = sections.indexOf(DetailSectionKind.UP_NEXT)
        val seasons = sections.indexOf(DetailSectionKind.SEASONS)
        assertTrue(upNext in 0 until seasons, "UP_NEXT must be emitted before SEASONS")
        assertEquals(6, sections[upNext].delayIndex)
        assertEquals(6, sections[seasons].delayIndex)
    }

    @Test
    fun series_upNext_hiddenByPreference_dropsOnlyUpNext() {
        // The preference term moved verbatim into the module's row.
        val hidden = moduleState(MediaType.SERIES, showDetailUpNext = false)
        assertFalse(UpNextSectionModule.isAdmitted(hidden, moduleDetail(hidden)))
        val shown = moduleState(MediaType.SERIES)
        assertTrue(UpNextSectionModule.isAdmitted(shown, moduleDetail(shown)))
        assertTrue(SeasonsSectionModule.isAdmitted(shown, moduleDetail(shown)))
        // SEASONS keeps its shared slot-6 index.
        assertEquals(6, DetailSectionKind.SEASONS.delayIndex)
    }

    @Test
    fun series_upNext_withoutSmartTarget_dropped() {
        assertFalse(
            UpNextSectionModule.isAdmitted(
                moduleState(MediaType.SERIES, hasSmartPlayTarget = false),
                moduleDetail(moduleState(MediaType.SERIES, hasSmartPlayTarget = false)),
            ),
        )
        // smartPlay capability is NOT a gate — the target presence is
        // (capabilities.smartPlay is resolved upstream into the target).
        val capOff = moduleState(
            MediaType.SERIES,
            capabilities = allCapabilities().copy(smartPlay = false),
            hasSmartPlayTarget = true,
        )
        assertTrue(UpNextSectionModule.isAdmitted(capOff, moduleDetail(capOff)))
    }

    // ── BOOK ──────────────────────────────────────────────────────────────

    @Test
    fun localBook_withToc_readingCardSharesSlot2_castDropped() {
        // The reading card + media-info share the slot 2 stagger band (both
        // module-owned — the enum ratchet pins the indices, the modules the
        // admission); the merged emission is the exact list the pre-module
        // local book produced (CAST/SEASONS never admit for a BOOK).
        val book = moduleState(
            MediaType.BOOK,
            capabilities = localCapabilities(),
            isLocalOrigin = true,
            hasBookState = true,
            hasBookToc = true,
            seerrDataAvailable = false,
        )
        val sections = DetailSectionRegistry.admittedSections(state = book, detail = moduleDetail(book))
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
                // MORE_LIKE_THIS stays admitted: its list choice
                // (localRelatedItems vs relatedItems) and empty gate are
                // render-side, exactly as before. RELATED_VIDEOS and the
                // DOWNLOAD_FOOTER (this snapshot is LOCAL) are module-owned.
                DetailSectionKind.RELATED_VIDEOS,
                DetailSectionKind.MORE_LIKE_THIS,
                DetailSectionKind.DOWNLOAD_FOOTER,
            ),
            sections,
        )
        assertTrue(BookReadingCardSectionModule.isAdmitted(book, moduleDetail(book)))
        assertTrue(MediaInfoSectionModule.isAdmitted(book, moduleDetail(book)))
        assertEquals(2, sections.first { it == DetailSectionKind.BOOK_READING_CARD }.delayIndex)
        assertEquals(2, sections.first { it == DetailSectionKind.MEDIA_INFO }.delayIndex)
        assertFalse(sections.contains(DetailSectionKind.CAST))
        assertFalse(sections.contains(DetailSectionKind.SEASONS))
        assertFalse(sections.contains(DetailSectionKind.COLLECTION_ITEMS))
    }

    @Test
    fun book_withoutBookState_dropsReadingCard_fallsBackToChapterGate() {
        val bare = moduleState(
            MediaType.BOOK,
            capabilities = localCapabilities().copy(chapters = false),
        )
        // No book state → the reading-card module does not admit (the fold
        // row's `mediaType == BOOK && hasBookState` moved verbatim); neither
        // leg of the chapters module admits without TOC content or chapters.
        assertFalse(BookReadingCardSectionModule.isAdmitted(bare, moduleDetail(bare)))
        assertFalse(ChaptersOrTocSectionModule.isAdmitted(bare, moduleDetail(bare)))
    }

    @Test
    fun book_tocAdmitsContentsSection_withoutChapterCapability() {
        // The TOC branch does not read capabilities.chapters — the local TOC
        // cache is the authority for books. (CHAPTERS_OR_TOC is module-owned.)
        val book = moduleState(
            MediaType.BOOK,
            capabilities = localCapabilities().copy(chapters = false),
            hasBookState = true,
            hasBookToc = true,
        )
        assertTrue(ChaptersOrTocSectionModule.isAdmitted(book, moduleDetail(book)))
        assertEquals(4, DetailSectionKind.CHAPTERS_OR_TOC.delayIndex)
    }

    @Test
    fun book_stillNotAudio_mediaInfoSlotStaysAdmitted() {
        // Pins the pre-existing behavior the old comment ("never renders for
        // books") drifted from: the visible flag only excluded audio types.
        // (MEDIA_INFO is module-owned — the fold row moved verbatim.)
        val book = moduleState(MediaType.BOOK)
        assertTrue(MediaInfoSectionModule.isAdmitted(book, moduleDetail(book)))
    }

    // ── ALBUM / audio ─────────────────────────────────────────────────────

    @Test
    fun remoteAlbum_tracksAdmitted_mediaInfoDropped() {
        // Albums are an audio type: the stream-info slot never admits, the
        // track list takes delayIndex 5.
        val album = moduleState(MediaType.ALBUM, hasChapters = false, hasAlbumTracks = true)
        assertTrue(AlbumTracksSectionModule.isAdmitted(album, moduleDetail(album)))
        assertEquals(5, DetailSectionKind.ALBUM_TRACKS.delayIndex)
        // The merged emission: an album drops the stream-info slot (audio
        // type) and the chapter slot, and keeps the track list at 5.
        val sections = DetailSectionRegistry.admittedSections(state = album, detail = moduleDetail(album))
        assertEquals(
            listOf(
                DetailSectionKind.HEADER,
                DetailSectionKind.ACTION_ROW,
                DetailSectionKind.OVERVIEW,
                DetailSectionKind.ALBUM_TRACKS,
                DetailSectionKind.CAST,
                DetailSectionKind.RELATED_VIDEOS,
                DetailSectionKind.MORE_LIKE_THIS,
            ),
            sections,
        )
        assertFalse(sections.contains(DetailSectionKind.COLLECTION_ITEMS))
    }

    @Test
    fun audioTrack_withoutTracks_dropsTrackList_andKeepsMediaInfoDropped() {
        val bare = moduleState(MediaType.AUDIO, hasAlbumTracks = false, hasChapters = false)
        assertFalse(AlbumTracksSectionModule.isAdmitted(bare, moduleDetail(bare)))
        assertFalse(MediaInfoSectionModule.isAdmitted(bare, moduleDetail(bare)))
        assertFalse(ChaptersOrTocSectionModule.isAdmitted(bare, moduleDetail(bare)))
    }

    // ── COLLECTION ────────────────────────────────────────────────────────

    // (The COLLECTION_ITEMS admission pins moved to
    // DetailSectionEnrichmentArmsTest — the wave-2 section module owns that
    // row.)

    // ── Seerr / special-features gates ────────────────────────────────────

    @Test
    fun seerrSections_requireConnectionAndRecommendationEnabled() {
        // The `seerrDataAvailable && hasSeerr*` fold rows moved verbatim into
        // the two Seerr modules (the flags off `state.core`).
        fun movieAdmits(
            connected: Boolean,
            hasRecommendations: Boolean,
            hasSimilar: Boolean,
        ): Pair<Boolean, Boolean> {
            val state = moduleState(
                MediaType.MOVIE,
                seerrDataAvailable = connected,
                hasSeerrRecommendations = hasRecommendations,
                hasSeerrSimilar = hasSimilar,
            )
            val detail = moduleDetail(state)
            return SeerrRecommendationsSectionModule.isAdmitted(state, detail) to
                SeerrSimilarSectionModule.isAdmitted(state, detail)
        }

        // Disconnected: neither row admits, content or not.
        assertFalse(movieAdmits(connected = false, hasRecommendations = true, hasSimilar = true).first)
        assertFalse(movieAdmits(connected = false, hasRecommendations = true, hasSimilar = true).second)

        // Connected + recommendations enabled: each row admits on its own
        // content alone.
        val recsOnly = movieAdmits(connected = true, hasRecommendations = true, hasSimilar = false)
        assertTrue(recsOnly.first)
        assertFalse(recsOnly.second)

        val simOnly = movieAdmits(connected = true, hasRecommendations = false, hasSimilar = true)
        assertFalse(simOnly.first)
        assertTrue(simOnly.second)

        // Slot order preserved: recommendations (11) before similar (12).
        assertEquals(11, DetailSectionKind.SEERR_RECOMMENDATIONS.delayIndex)
        assertEquals(12, DetailSectionKind.SEERR_SIMILAR.delayIndex)
    }

    // (The PLUGIN_RATINGS / TMDB_REVIEWS admission pins moved to
    // DetailSectionRegistryTest — the wave-1 section modules own those rows.
    // The SPECIAL_FEATURES / UPCOMING_EPISODES / MORE_IN_COLLECTION pins
    // moved to DetailSectionEnrichmentArmsTest — the wave-2 modules own
    // theirs.)

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

    // (The UPCOMING_EPISODES strip and MORE_IN_COLLECTION franchise-row
    // admission pins — including the hidden-rows fold — moved to
    // DetailSectionEnrichmentArmsTest: the wave-2 section modules own those
    // rows, and their module-level pins exercise the same terms.)

    // ── JellyPlay plugin sections (ADR 0010) ────────────────────────────

    @Test
    fun pluginSimilar_admitsOnlyWhenItsContentArrived() {
        // The `hasPluginSimilar` / suppression fold rows moved verbatim into
        // the two similar modules (the state off `state.core`).
        fun movieAdmits(hasPluginSimilar: Boolean, suppressStockSimilar: Boolean): Pair<Boolean, Boolean> {
            val state = moduleState(
                MediaType.MOVIE,
                hasPluginSimilar = hasPluginSimilar,
                suppressStockSimilar = suppressStockSimilar,
            )
            val detail = moduleDetail(state)
            return JellyPlaySimilarSectionModule.isAdmitted(state, detail) to
                MoreLikeThisSectionModule.isAdmitted(state, detail)
        }

        // Silent absence: no plugin data → the plugin row not admitted at all
        // (the PLUGIN_RATINGS row's pins moved to DetailSectionRegistryTest
        // with its module).
        val bare = movieAdmits(hasPluginSimilar = false, suppressStockSimilar = false)
        assertFalse(bare.first)
        assertTrue(bare.second, "empty plugin hydration → the stock row stays admitted")

        // Scored similar present → the plugin row admits (same slot 10 as the
        // stock row), but suppression is NOT tied to its presence: on hosts
        // where the plugin's scorer did NOT register into the stock pipeline
        // (pre-Jellyfin-12 / older plugin) the two lists differ and BOTH rows
        // render.
        val withSimilar = movieAdmits(hasPluginSimilar = true, suppressStockSimilar = false)
        assertTrue(withSimilar.first)
        assertTrue(withSimilar.second, "no pipeline → stock row stays")
        assertEquals(10, DetailSectionKind.JELLYPLAY_SIMILAR.delayIndex)

        // Pipeline host (capabilities said the plugin registered into the
        // host's similar-items pipeline) → the stock endpoint returns the
        // SAME scored list; the stock row stands down to avoid the duplicate.
        val withSimilarOnPipeline = movieAdmits(hasPluginSimilar = true, suppressStockSimilar = true)
        assertTrue(withSimilarOnPipeline.first)
        assertFalse(withSimilarOnPipeline.second, "pipeline host → stock row suppressed")

        // Suppression never suppresses the plugin row itself (an odd
        // capability/empty-list combination just reverts to stock).
        val suppressedButEmpty = movieAdmits(hasPluginSimilar = false, suppressStockSimilar = true)
        assertTrue(suppressedButEmpty.second)
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
