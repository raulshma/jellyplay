package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Invariants pinned for the [HomeUiEvent] intent surface and its
 * callback-carrying defaults — the API contract [HomeViewModel.onEvent]
 * routes on (no behaviour, but the defaults are load-bearing):
 *  - [HomeUiEvent.DownloadItem]'s default [HomeUiEvent.DownloadItem.onOpenDetail]
 *    is a safe no-op (call sites that don't navigate must not crash on the
 *    NeedsDetailScreen path).
 *  - [HomeUiEvent.PrefetchSeerrDetails]'s default `onDone` is likewise a no-op.
 *  - [HomeUiEvent.RequestSeerrMedia]'s radarr/sonarr picker fields default to
 *    unset (null) so a bare request targets the server defaults.
 *  - [SeriesPlayResolution] carries the resolved episode + resume ticks, or
 *    the fallback series — the two outcomes the smart-play rule can emit.
 */
class HomeUiEventTest {

    private fun movie(id: String = "m1") = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)

    @Test
    fun downloadItem_defaultOnOpenDetail_isSafeNoOp() {
        val event = HomeUiEvent.DownloadItem(item = movie())

        // The default lambda must be invocable without navigation side effects.
        event.onOpenDetail("any-id", true)
    }

    @Test
    fun downloadItem_carriesItemAndCustomNavigation() {
        var received: Pair<String, Boolean>? = null
        val event = HomeUiEvent.DownloadItem(item = movie("m2")) { id, sheet ->
            received = id to sheet
        }

        event.onOpenDetail("season-9", false)

        assertEquals("season-9" to false, received)
        assertEquals("m2", event.item.id)
    }

    @Test
    fun prefetchSeerrDetails_defaultOnDone_isSafeNoOp() {
        val event = HomeUiEvent.PrefetchSeerrDetails(tmdbId = 42, mediaType = "movie")

        event.onDone()
        assertEquals(42, event.tmdbId)
        assertEquals("movie", event.mediaType)
    }

    @Test
    fun requestSeerrMedia_pickerFieldsDefaultToUnset() {
        val item = com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem(
            id = 10,
            mediaType = "movie",
            title = "Test",
        )
        val bare = HomeUiEvent.RequestSeerrMedia(item)

        assertNull(bare.seasons)
        assertNull(bare.serverId)
        assertNull(bare.profileId)
        assertNull(bare.rootFolder)
        assertNull(bare.tags)

        val full = HomeUiEvent.RequestSeerrMedia(
            item = item,
            seasons = listOf(1),
            serverId = 1,
            profileId = 2,
            rootFolder = "/movies",
            tags = listOf(7),
        )
        assertEquals(listOf(1), full.seasons)
        assertEquals("/movies", full.rootFolder)
    }

    @Test
    fun seriesPlayResolution_episodeCarriesTargetAndResumeTicks() {
        val target = MediaItem(
            id = "ep-2",
            name = "ep-2",
            mediaType = MediaType.EPISODE,
            playbackPositionTicks = 600_000_000L,
        )
        val resolution = SeriesPlayResolution.Episode(item = target, startPositionTicks = 600_000_000L)

        assertEquals("ep-2", resolution.item.id)
        assertEquals(600_000_000L, resolution.startPositionTicks)
    }

    @Test
    fun seriesPlayResolution_detailsCarriesTheFallbackSeries() {
        val series = MediaItem(id = "s1", name = "s1", mediaType = MediaType.SERIES)
        val resolution = SeriesPlayResolution.Details(series)

        assertEquals("s1", resolution.series.id)
    }

    @Test
    fun sectionConfigEvents_carryTypeVisibilityAndOrdering() {
        val visibility = HomeUiEvent.SetSectionVisible(HomeSectionType.NEXT_UP, visible = false)
        val move = HomeUiEvent.MoveSection(HomeSectionType.NEXT_UP, up = true)
        val perLibrary = HomeUiEvent.SetLibrarySectionVisible(
            libraryId = "movies",
            type = HomeSectionType.LATEST_MEDIA,
            visible = true,
        )

        assertEquals(HomeSectionType.NEXT_UP, visibility.type)
        assertTrue(move.up)
        assertEquals("movies", perLibrary.libraryId)
        assertEquals(HomeSectionType.LATEST_MEDIA, perLibrary.type)
        assertTrue(perLibrary.visible)
    }
}
