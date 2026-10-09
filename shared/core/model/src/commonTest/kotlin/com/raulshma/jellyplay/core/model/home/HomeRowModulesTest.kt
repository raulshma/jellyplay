package com.raulshma.jellyplay.core.model.home

import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.descriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the home row registry: every [HomeSectionType] is registered (a new
 * enum constant without a module fails here, not at fetch time), the
 * registration order IS the default layout order and stays pinned to the
 * wire-safe [HomeSectionType.CONFIGURABLE] list, and the per-type gate facts
 * every consumer layer reads (chassis, ordering use case, refresh arms) match
 * the membership the scattered dispatches used to spell.
 */
class HomeRowModulesTest {

    // ── Completeness ──

    @Test
    fun everySectionType_isRegistered() {
        for (type in HomeSectionType.entries) {
            val module = HomeRowModules[type]
            assertEquals(type, module.type, "type=$type")
            assertEquals(type.descriptor, module.descriptor, "descriptor identity for $type")
        }
        assertEquals(HomeSectionType.entries.size, HomeRowModules.all.size)
    }

    @Test
    fun defaultOrder_matchesTheWireSafeConfigurableList() {
        // Registration order derives the default layout order; the enum's hand
        // list stays the wire-safe authority — the two must never drift.
        assertEquals(HomeSectionType.CONFIGURABLE, HomeRowModules.defaultOrder)
    }

    // ── Gate facts (the membership the former per-type dispatches spelled) ──

    @Test
    fun wideRow_exactlyContinueWatchingAndNextUp() {
        for (type in HomeSectionType.entries) {
            assertEquals(
                type == HomeSectionType.CONTINUE_WATCHING || type == HomeSectionType.NEXT_UP,
                HomeRowModules[type].wideRow,
                "type=$type",
            )
        }
    }

    @Test
    fun resumeLike_exactlyContinueWatchingAndContinueReading() {
        for (type in HomeSectionType.entries) {
            assertEquals(
                type == HomeSectionType.CONTINUE_WATCHING || type == HomeSectionType.CONTINUE_READING,
                HomeRowModules[type].resumeLike,
                "type=$type",
            )
        }
    }

    @Test
    fun hasSeeAll_exactlyRecentlyAddedAndLatestMedia() {
        for (type in HomeSectionType.entries) {
            assertEquals(
                type == HomeSectionType.RECENTLY_ADDED || type == HomeSectionType.LATEST_MEDIA,
                HomeRowModules[type].hasSeeAll,
                "type=$type",
            )
        }
    }

    @Test
    fun edgeRefreshable_exactlyTheSingleRowFetchRows() {
        // The set the chassis's edge-pull gate and the refresh arms agreed on.
        val refreshable = setOf(
            HomeSectionType.CONTINUE_WATCHING,
            HomeSectionType.CONTINUE_READING,
            HomeSectionType.NEXT_UP,
            HomeSectionType.RECENTLY_ADDED,
            HomeSectionType.LATEST_MEDIA,
            HomeSectionType.PINNED,
            HomeSectionType.DISCOVER,
            HomeSectionType.PLUGIN_ROW,
        )
        for (type in HomeSectionType.entries) {
            assertEquals(type in refreshable, HomeRowModules[type].edgeRefreshable, "type=$type")
        }
    }

    @Test
    fun mergesIntoContinueWatching_exactlyNextUp() {
        for (type in HomeSectionType.entries) {
            assertEquals(
                type == HomeSectionType.NEXT_UP,
                HomeRowModules[type].mergesIntoContinueWatching,
                "type=$type",
            )
        }
    }

    // ── Offline projections ──

    @Test
    fun offlineDownloadedProjection_partitionsTheLibrary() {
        val groups = HomeRowModules[HomeSectionType.DOWNLOADED].offline
            .downloadedGroups(listOf(movie("m1"), series("s1"), music("a1")))
        assertNotNull(groups)
        assertEquals(listOf("m1"), groups.movies.map { it.id })
        assertEquals(listOf("s1"), groups.series.map { it.id })
        assertEquals(listOf("a1"), groups.music.map { it.id })
    }

    @Test
    fun offlineResumeProjections_dropPlayedAndRespectCandidates() {
        val library = listOf(
            movie("watched").copy(isPlayed = true),
            movie("inprogress").copy(playbackPositionTicks = 1L, playedPercentage = 5.0),
            series("echo").copy(playbackPositionTicks = 1L, playedPercentage = 5.0),
        )
        val cw = HomeRowModules[HomeSectionType.CONTINUE_WATCHING].offline
            .resumeItems(library, emptyList(), hiddenItemIds = emptySet())
        assertEquals(listOf("inprogress"), cw.map { it.id }, "series are hierarchy echoes, not resume points")

        val hidden = HomeRowModules[HomeSectionType.CONTINUE_WATCHING].offline
            .resumeItems(library, emptyList(), hiddenItemIds = setOf("inprogress"))
        assertTrue(hidden.isEmpty(), "hidden items drop from the resume rows")
    }

    @Test
    fun offlineInertProjections_defaultToNoFallbackCompute() {
        for (type in HomeSectionType.entries - HomeSectionType.DOWNLOADED) {
            val module = HomeRowModules[type]
            assertTrue(
                module.offline.resumeItems(emptyList(), emptyList(), emptySet()).isEmpty(),
                "type=$type has no offline resume compute",
            )
            assertTrue(
                module.offline.nextUpItems(emptyList(), emptySet(), maxDays = 0, rewatching = false).isEmpty(),
                "type=$type has no offline next-up compute",
            )
        }
    }

    // ── Fixtures ──

    private fun movie(id: String) = item(id, MediaType.MOVIE)

    private fun series(id: String) = item(id, MediaType.SERIES)

    private fun music(id: String) = item(id, MediaType.MUSIC)

    private fun item(id: String, mediaType: MediaType) = OfflineMediaItem(
        id = id,
        name = id,
        mediaType = mediaType,
    )
}
