package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.JellyPlayPluginRating
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaDetailSnapshot
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.RemoteConnectivity
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import com.raulshma.jellyplay.core.network.api.JellyPlayRatingEntry
import com.raulshma.jellyplay.core.network.api.JellyPlayRatingsResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the detail section registry (the wave-1 pilot): the registry's order
 * follows the [DetailSectionKind] declaration order, the merged emission
 * (hand fold + module admissions) is byte-identical to the pre-pilot
 * full-admission movie emission, the two pilot modules own their admission
 * rows (moved out of DetailSectionAdmission's fold), and the plugin ratings
 * module's enrichment arm preserves the fetch it took over.
 */
class DetailSectionRegistryTest {

    // ── fixtures ──────────────────────────────────────────────────────────

    private val movieItem = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

    private val movieDetail = MediaDetail(
        item = movieItem,
        // Admits CHAPTERS_OR_TOC (the capabilities.chapters && hasChapters
        // conjunction), matching the former full-admission pin.
        chapters = listOf(ChapterInfo(name = "Intro", startPositionTicks = 0L)),
    )

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

    private fun state(
        tmdbReviews: List<TmdbReview> = emptyList(),
        pluginRatings: List<JellyPlayPluginRating> = emptyList(),
        showReviews: Boolean = true,
        seerrDataAvailable: Boolean = false,
    ): DetailContentState = DetailContentState(
        itemId = "m1",
        detail = movieDetail,
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
        seerrRecommendations = if (seerrDataAvailable) listOf(seerrItem(1)) else emptyList(),
        seerrSimilar = if (seerrDataAvailable) listOf(seerrItem(2)) else emptyList(),
        isSeerrConnected = seerrDataAvailable,
        isSeerrRecommendationsEnabled = seerrDataAvailable,
        preferences = DetailPreferences(showReviews = showReviews),
        canManageSeries = false,
        origin = DetailOrigin.REMOTE,
        capabilities = allCapabilities(),
        tmdbReviews = tmdbReviews,
        pluginRatings = pluginRatings,
        specialFeatures = if (seerrDataAvailable) {
            listOf(MediaItem(id = "sf1", name = "Extra", mediaType = MediaType.MOVIE))
        } else {
            emptyList()
        },
    )

    private fun seerrItem(id: Int) = SeerrSearchItem(id = id, mediaType = "movie")

    /** A REMOTE movie detail snapshot whose discovery is allowed (the arm's gate). */
    private fun armInputs(itemId: String = "m1") = DetailEnrichmentInputs(
        itemId = itemId,
        snapshot = MediaDetailSnapshot(
            detail = MediaDetail(
                item = MediaItem(id = itemId, name = "Movie", mediaType = MediaType.MOVIE),
                // The ratings arm resolves its fetch key from the detail's
                // provider ids (resolveImdbId) — without one the arm's silent
                // absence kicks in before the fake host is ever read.
                providerIds = mapOf("imdb" to "tt1"),
            ),
            context = DetailContext(
                origin = DetailOrigin.REMOTE,
                connectivity = RemoteConnectivity.AVAILABLE,
                download = null,
                syncState = null,
                seriesAggregate = null,
            ),
            capabilities = DetailCapabilities(
                remoteDiscovery = true,
                remoteStreamSelection = true,
                localSubtitleSelection = false,
                localStreamInfo = false,
                personNavigation = true,
                studioNavigation = true,
                smartPlay = true,
                remoteWorkAllowed = true,
                localDownloadManagement = false,
                tagNavigation = true,
                chapters = false,
            ),
            assets = DetailAssets(),
        ),
    )

    /** A [DetailSectionEnrichmentHost] fake recording the state writes. */
    private class FakeHost(
        private val probe: Boolean,
        private val result: Result<JellyPlayRatingsResult?>?,
    ) : DetailSectionEnrichmentHost {
        var written: DetailUiState? = null

        override fun isCurrent(itemId: String): Boolean = true

        override fun updateState(reduce: (DetailUiState) -> DetailUiState) {
            written = reduce(DetailUiState())
        }

        override suspend fun pluginFeature(feature: String, itemId: String): Boolean = probe

        override suspend fun mdbListRatings(
            imdbId: String,
        ): Result<JellyPlayRatingsResult?>? = result
    }

    // ── registry order ────────────────────────────────────────────────────

    @Test
    fun registry_followsTheEnumDeclarationOrder() {
        // The registry list itself must read in emission order…
        val kinds = DetailSectionRegistry.modules.map { it.kind }
        assertEquals(listOf(DetailSectionKind.PLUGIN_RATINGS, DetailSectionKind.TMDB_REVIEWS), kinds)
        // …and the merged emission re-sorts by the enum, so nothing can ever
        // reorder visually regardless of how the list is written.
        val merged = DetailSectionRegistry.admittedSections(
            handFold = DetailSectionAdmission.from(state(), movieDetail).admit(),
            state = state(),
            detail = movieDetail,
        )
        assertEquals(merged, merged.sortedBy { it.ordinal })
    }

    @Test
    fun mergedAdmission_reproducesThePrePilotEmission_forAfullyLoadedMovie() {
        // A movie with every optional row hydrated: the merged list is the
        // exact emission the pre-pilot fold produced (including the ratings
        // row right after OVERVIEW at slot 3 and reviews at slot 14).
        val full = state(
            tmdbReviews = listOf(TmdbReview(id = "r1", content = "…")),
            pluginRatings = listOf(JellyPlayPluginRating(source = "IMDb", score = 8.1)),
            showReviews = true,
            seerrDataAvailable = true,
        )
        val sections = DetailSectionRegistry.admittedSections(
            handFold = DetailSectionAdmission.from(full, movieDetail).admit(),
            state = full,
            detail = movieDetail,
        )
        assertEquals(
            listOf(
                DetailSectionKind.HEADER,
                DetailSectionKind.ACTION_ROW,
                DetailSectionKind.MEDIA_INFO,
                DetailSectionKind.OVERVIEW,
                DetailSectionKind.PLUGIN_RATINGS,
                DetailSectionKind.CHAPTERS_OR_TOC,
                DetailSectionKind.CAST,
                DetailSectionKind.RELATED_VIDEOS,
                DetailSectionKind.MORE_LIKE_THIS,
                DetailSectionKind.SEERR_RECOMMENDATIONS,
                DetailSectionKind.SEERR_SIMILAR,
                DetailSectionKind.SPECIAL_FEATURES,
                DetailSectionKind.TMDB_REVIEWS,
            ),
            sections,
        )
        // The stagger slots are unchanged — the pilot rows render in the exact
        // position/delay they rendered in before the migration.
        assertEquals(
            listOf(0, 1, 2, 3, 3, 4, 8, 9, 10, 11, 12, 13, 14),
            sections.map { it.delayIndex },
        )
    }

    // ── pilot admissions (the fold rows that left DetailSectionAdmission) ──

    @Test
    fun tmdbReviews_admitsOnContent_andTheD9PreferenceGatesTheWholeSlot() {
        val detail = movieDetail
        // No reviews fetched → silent absence (the former hasTmdbReviews row).
        assertFalse(TmdbReviewsSectionModule.isAdmitted(state(), detail))
        // Reviews fetched + pref on → admitted.
        assertTrue(
            TmdbReviewsSectionModule.isAdmitted(
                state(tmdbReviews = listOf(TmdbReview(id = "r1"))),
                detail,
            ),
        )
        // show_reviews off hides the section even with reviews fetched (D9).
        assertFalse(
            TmdbReviewsSectionModule.isAdmitted(
                state(tmdbReviews = listOf(TmdbReview(id = "r1")), showReviews = false),
                detail,
            ),
        )
        // …and the registry reports the same verdict the body consumes.
        assertTrue(
            DetailSectionRegistry.admittedKinds(
                state(tmdbReviews = listOf(TmdbReview(id = "r1"))),
                detail,
            ).contains(DetailSectionKind.TMDB_REVIEWS),
        )
    }

    @Test
    fun pluginRatings_admitsOnlyOnContent() {
        val detail = movieDetail
        // No plugin data → no slot at all (the silent-absence contract).
        assertFalse(PluginRatingsSectionModule.isAdmitted(state(), detail))
        assertTrue(
            PluginRatingsSectionModule.isAdmitted(
                state(pluginRatings = listOf(JellyPlayPluginRating(source = "RT", score = 96.0))),
                detail,
            ),
        )
        // The fold itself never emits the migrated kinds — the registry owns
        // them.
        assertFalse(DetailSectionAdmission.from(state(), detail).admit().contains(DetailSectionKind.PLUGIN_RATINGS))
        assertTrue(
            DetailSectionRegistry.admittedKinds(
                state(pluginRatings = listOf(JellyPlayPluginRating(source = "RT", score = 96.0))),
                detail,
            ).contains(DetailSectionKind.PLUGIN_RATINGS),
        )
    }

    // ── the plugin ratings enrichment arm ─────────────────────────────────

    @Test
    fun pluginRatings_arm_mapsAndDropsScorelessEntries() = runTest {
        val host = FakeHost(
            probe = true,
            result = Result.success(
                JellyPlayRatingsResult(
                    imdbId = "tt1",
                    ratings = listOf(
                        JellyPlayRatingEntry(source = "RT", score = 96.0),
                        JellyPlayRatingEntry(source = "MC", score = null),
                    ),
                ),
            ),
        )
        val enrichment = PluginRatingsSectionModule.enrichments(host).single()
        assertEquals("pluginRatings", enrichment.name)
        assertTrue(enrichment.gate(armInputs()), "remote discovery allowed → the arm may start")

        enrichment.run(armInputs())

        // Scoreless entries would render an empty chip — dropped at the write.
        assertEquals(
            listOf(JellyPlayPluginRating(source = "RT", score = 96.0)),
            host.written?.pluginRatings,
        )
    }

    @Test
    fun pluginRatings_arm_silentAbsence_onProbeMissUnwiredSeamOrFailedFetch() = runTest {
        val inputs = armInputs()

        // Feature probe false (unwired store / user toggle) → no write.
        val probedOff = FakeHost(probe = false, result = Result.success(JellyPlayRatingsResult("tt1")))
        PluginRatingsSectionModule.enrichments(probedOff).single().run(inputs)
        assertNull(probedOff.written)

        // Seam unwired (direct-construction tests) → silent absence.
        val unwired = FakeHost(probe = true, result = null)
        PluginRatingsSectionModule.enrichments(unwired).single().run(inputs)
        assertNull(unwired.written)

        // Fetch failed → silent absence.
        val failed = FakeHost(probe = true, result = Result.failure(RuntimeException("plugin down")))
        PluginRatingsSectionModule.enrichments(failed).single().run(inputs)
        assertNull(failed.written)
    }

    @Test
    fun pluginRatings_arm_gate_requiresRemoteDiscovery() {
        // A LOCAL origin (or a discovery-capped snapshot) never starts the arm.
        val localInputs = DetailEnrichmentInputs(
            itemId = "m1",
            snapshot = MediaDetailSnapshot(
                detail = MediaDetail(item = movieItem),
                context = DetailContext(
                    origin = DetailOrigin.LOCAL_OFFLINE_MODE,
                    connectivity = RemoteConnectivity.BLOCKED,
                    download = null,
                    syncState = null,
                    seriesAggregate = null,
                ),
                capabilities = DetailCapabilities(
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
                ),
                assets = DetailAssets(),
            ),
        )
        val host = FakeHost(probe = true, result = Result.success(JellyPlayRatingsResult("tt1")))
        assertFalse(PluginRatingsSectionModule.enrichments(host).single().gate(localInputs))
    }
}
