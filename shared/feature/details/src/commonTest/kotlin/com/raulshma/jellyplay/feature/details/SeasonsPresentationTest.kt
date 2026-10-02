package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure fold tests for [SeasonsPresentation] — the four derivations the
 * seasons-section adapter used to hand-splice inline: the skip-specials /
 * hide-thumbnails local-origin neutralization, the downloaded-episode
 * three-way fold, the SEASON entry filter, and the current-item/current-season
 * pair. No Android / Compose — the fold depends only on `core.model` state
 * types, so this is a plain JUnit suite.
 */
class SeasonsPresentationTest {

    // ── Skip-specials + hide-thumbnails (local-origin neutralization) ──

    @Test
    fun `remote origin with skipSpecials drops season-zero episodes`() {
        val state = state(
            preferences = DetailPreferences(skipSpecials = true),
            episodes = mapOf("s1" to listOf(special(), episode("e1", seasonNumber = 1))),
        )

        val presentation = SeasonsPresentation.from(state, series(), isLocalOrigin = false)

        assertEquals(listOf("e1"), presentation.episodes["s1"]?.map { it.id })
    }

    @Test
    fun `local origin renders every season and always shows art`() {
        val state = state(
            preferences = DetailPreferences(skipSpecials = true, hideEpisodeThumbnails = true),
            episodes = mapOf("s1" to listOf(special(), episode("e1", seasonNumber = 1))),
        )

        val presentation = SeasonsPresentation.from(state, series(), isLocalOrigin = true)

        // Specials kept (S0 filtering is online-only) and thumbnails shown
        // (hiding without guaranteed local artwork yields blank tiles).
        assertEquals(listOf("e0", "e1"), presentation.episodes["s1"]?.map { it.id })
        assertFalse(presentation.hideEpisodeThumbnails)
    }

    @Test
    fun `remote origin honors hideEpisodeThumbnails`() {
        val state = state(preferences = DetailPreferences(hideEpisodeThumbnails = true))

        val presentation = SeasonsPresentation.from(state, series(), isLocalOrigin = false)

        assertTrue(presentation.hideEpisodeThumbnails)
    }

    @Test
    fun `remote origin without the spoiler preference shows thumbnails`() {
        val presentation = SeasonsPresentation.from(state(), series(), isLocalOrigin = false)

        assertFalse(presentation.hideEpisodeThumbnails)
    }

    // ── Downloaded-episode three-way fold ──

    @Test
    fun `local origin treats every episode as downloaded`() {
        val state = state(
            episodes = mapOf(
                "s1" to listOf(episode("e1"), episode("e2")),
                "s2" to listOf(episode("e3")),
            ),
        )

        val presentation = SeasonsPresentation.from(state, series(), isLocalOrigin = true)

        assertEquals(setOf("e1", "e2", "e3"), presentation.downloadedEpisodeIds)
    }

    @Test
    fun `remote origin surfaces the loaded downloaded set when non-empty`() {
        val state = state(downloadedEpisodeIds = setOf("e2"))

        val presentation = SeasonsPresentation.from(state, series(), isLocalOrigin = false)

        assertEquals(setOf("e2"), presentation.downloadedEpisodeIds)
    }

    @Test
    fun `remote origin with no downloads hides the delete affordance entirely`() {
        val presentation = SeasonsPresentation.from(state(), series(), isLocalOrigin = false)

        assertNull(presentation.downloadedEpisodeIds)
    }

    // ── SEASON entry filter + current-id pair ──

    @Test
    fun `season page renders only its own tab and preselects itself`() {
        val state = state(seasons = listOf(season("s1"), season("s2")))
        val page = season("s2")

        val presentation = SeasonsPresentation.from(state, page, isLocalOrigin = false)

        assertEquals(listOf("s2"), presentation.seasons.map { it.id })
        assertEquals("s2", presentation.currentSeasonId)
        assertNull(presentation.currentItemId)
    }

    @Test
    fun `episode page highlights itself and anchors its season tab`() {
        val state = state(seasons = listOf(season("s1"), season("s2")))
        val page = MediaItem(id = "e1", name = "Episode 1", mediaType = MediaType.EPISODE, seasonId = "s1")

        val presentation = SeasonsPresentation.from(state, page, isLocalOrigin = false)

        assertEquals(listOf("s1", "s2"), presentation.seasons.map { it.id })
        assertEquals("e1", presentation.currentItemId)
        assertEquals("s1", presentation.currentSeasonId)
    }

    @Test
    fun `series and movie pages pass null current ids`() {
        val state = state(seasons = listOf(season("s1")))

        val seriesPage = SeasonsPresentation.from(state, series(), isLocalOrigin = false)
        val moviePage = SeasonsPresentation.from(
            state,
            MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            isLocalOrigin = false,
        )

        assertNull(seriesPage.currentItemId)
        assertNull(seriesPage.currentSeasonId)
        assertNull(moviePage.currentItemId)
        assertNull(moviePage.currentSeasonId)
    }

    // ── Preference + asset pass-throughs ──

    @Test
    fun `episode sort and compact list pass through for both origins`() {
        val prefs = DetailPreferences(episodesDescending = false, compactEpisodeList = true)

        val remote = SeasonsPresentation.from(state(preferences = prefs), series(), isLocalOrigin = false)
        val local = SeasonsPresentation.from(state(preferences = prefs), series(), isLocalOrigin = true)

        assertFalse(remote.episodesDescending)
        assertTrue(remote.compactEpisodeList)
        assertFalse(local.episodesDescending)
        assertTrue(local.compactEpisodeList)
    }

    @Test
    fun `episode local image paths come from the snapshot assets`() {
        val state = state(assets = DetailAssets(episodeImages = mapOf("e1" to "/downloads/e1.jpg")))

        val presentation = SeasonsPresentation.from(state, series(), isLocalOrigin = false)

        assertEquals("/downloads/e1.jpg", presentation.episodeLocalImagePaths["e1"])
    }

    // ── Fixtures (mirrors DetailSectionAdmissionTest's builder style) ──

    private fun series() = MediaItem(id = "sr1", name = "Series", mediaType = MediaType.SERIES)

    private fun season(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.SEASON)

    private fun episode(id: String, seasonNumber: Int? = null) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.EPISODE,
        seasonNumber = seasonNumber,
    )

    /** A specials entry: season 0. */
    private fun special() = episode("e0", seasonNumber = 0)

    private fun state(
        preferences: DetailPreferences = DetailPreferences(),
        seasons: List<MediaItem> = emptyList(),
        episodes: Map<String, List<MediaItem>> = emptyMap(),
        downloadedEpisodeIds: Set<String> = emptySet(),
        assets: DetailAssets = DetailAssets(),
    ) = DetailContentState(
        itemId = "sr1",
        detail = MediaDetail(item = series()),
        seasons = seasons,
        episodes = episodes,
        fetchedSeasonIds = emptySet(),
        smartPlayTarget = null,
        selectedSubtitleIndex = null,
        selectedAudioIndex = null,
        isDownloading = false,
        isDownloadingSeries = false,
        activeDownload = null,
        loadState = DetailUiLoadState.Loaded,
        albumTracks = emptyList(),
        collectionItems = emptyList(),
        relatedItems = emptyList(),
        relatedVideos = emptyList(),
        seerrRecommendations = emptyList(),
        seerrSimilar = emptyList(),
        isSeerrConnected = true,
        isSeerrRecommendationsEnabled = false,
        preferences = preferences,
        canManageSeries = false,
        origin = DetailOrigin.REMOTE,
        downloadedEpisodeIds = downloadedEpisodeIds,
        assets = assets,
    )
}
