package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Pins the PRODUCTION row-chassis dispatch ([homeRowChassis]) — the
 * offline-mirror precedence that used to live only inside HomeContentList's
 * if/else chain — plus the See-All gate ([sectionHasSeeAll]) and the shared
 * CW click routing ([cwRowClick]) the online and offline wide rows must not
 * drift apart on.
 */
class HomeRowChassisTest {

    /** Every section type the offline mirror claims when not wide. */
    private val posterTypes = HomeSectionType.entries.filter {
        it != HomeSectionType.CONTINUE_WATCHING &&
            it != HomeSectionType.NEXT_UP &&
            it != HomeSectionType.DOWNLOADED
    }

    private fun section(type: HomeSectionType) =
        HomeSection(id = "s1", title = "Section", type = type, items = emptyList())

    private fun item(type: MediaType) = MediaItem(id = "i1", name = "Item", mediaType = type)

    // ── homeRowChassis ──

    @Test
    fun downloaded_isOfflinePoster_evenOnline() {
        // Pinned precedence: DOWNLOADED matched the FIRST predicate of the
        // former chain, before the offline-mirror clause — it routes to the
        // offline poster row regardless of which feed renders (the online
        // branch was defensive; the online feed has no DOWNLOADED sections).
        for (hasOfflineContent in listOf(false, true)) {
            val section = section(HomeSectionType.DOWNLOADED)
            assertEquals(
                HomeRowChassis.OfflinePoster(section),
                homeRowChassis(section, hasOfflineContent),
                "hasOfflineContent=$hasOfflineContent",
            )
        }
    }

    @Test
    fun offlineMirror_claimsEveryNonWideSection_whileOffline() {
        for (type in posterTypes) {
            val section = section(type)
            assertEquals(
                HomeRowChassis.OfflinePoster(section),
                homeRowChassis(section, hasOfflineContent = true),
                "type=$type",
            )
        }
    }

    @Test
    fun online_posterTypes_renderOnlinePoster() {
        // RECOMMENDATIONS included — the type most likely to be "obviously
        // online" but still mirror-claimed while the offline feed renders.
        for (type in posterTypes) {
            val section = section(type)
            assertEquals(
                HomeRowChassis.OnlinePoster(section),
                homeRowChassis(section, hasOfflineContent = false),
                "type=$type",
            )
        }
    }

    @Test
    fun offlineContinueWatchingAndNextUp_renderOfflineWide() {
        for (type in listOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP)) {
            val section = section(type)
            assertEquals(
                HomeRowChassis.OfflineWide(section),
                homeRowChassis(section, hasOfflineContent = true),
                "type=$type",
            )
        }
    }

    @Test
    fun onlineContinueWatchingAndNextUp_renderOnlineWide() {
        for (type in listOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP)) {
            val section = section(type)
            assertEquals(
                HomeRowChassis.OnlineWide(section),
                homeRowChassis(section, hasOfflineContent = false),
                "type=$type",
            )
        }
    }

    // ── sectionHasSeeAll ──

    @Test
    fun sectionHasSeeAll_trueOnlyForRecentlyAddedAndLatestMedia() {
        for (type in HomeSectionType.entries) {
            assertEquals(
                type == HomeSectionType.RECENTLY_ADDED || type == HomeSectionType.LATEST_MEDIA,
                sectionHasSeeAll(type),
                "type=$type",
            )
        }
    }

    // ── cwRowClick ──

    @Test
    fun cwRowClick_continueWatching_routesByBehavior() {
        val mediaItem = item(MediaType.MOVIE)

        for (behavior in ContinueWatchingClickBehavior.entries) {
            val expectedSink = when (behavior) {
                ContinueWatchingClickBehavior.DETAILS -> "details"
                ContinueWatchingClickBehavior.PLAY -> "play"
                ContinueWatchingClickBehavior.ASK -> "ask"
            }
            var sink: String? = null
            val click: (MediaItem) -> Unit = cwRowClick(
                sectionType = HomeSectionType.CONTINUE_WATCHING,
                behavior = behavior,
                toMediaItem = { it },
                onDetails = { sink = "details" },
                onPlay = { sink = "play" },
                onAsk = { sink = "ask" },
            )

            click(mediaItem)

            assertEquals(expectedSink, sink, "behavior=$behavior")
        }
    }

    @Test
    fun cwRowClick_ask_mapsTheItemBeforeHandingToTheAskSink() {
        val mediaItem = item(MediaType.MOVIE)
        var asked: MediaItem? = null

        val click: (MediaItem) -> Unit = cwRowClick(
            sectionType = HomeSectionType.CONTINUE_WATCHING,
            behavior = ContinueWatchingClickBehavior.ASK,
            toMediaItem = { it.copy(name = "lifted") },
            onDetails = { fail("details must not fire on ASK") },
            onPlay = { fail("play must not fire on ASK") },
            onAsk = { asked = it },
        )

        click(mediaItem)

        // The offline site's mapper lifts the item before the dialog sees it —
        // the sink receives the LIFTED MediaItem, not the raw T.
        assertEquals("lifted", asked?.name)
    }

    @Test
    fun cwRowClick_offContinueWatchingSection_alwaysOpensDetails() {
        val mediaItem = item(MediaType.MOVIE)

        // NEXT_UP is the pinned case: it shares the wide row with Continue
        // Watching but ALWAYS opens details, whatever the behavior pref says.
        for (sectionType in listOf(HomeSectionType.NEXT_UP, HomeSectionType.RECENTLY_ADDED)) {
            for (behavior in ContinueWatchingClickBehavior.entries) {
                var details: MediaItem? = null
                val click: (MediaItem) -> Unit = cwRowClick(
                    sectionType = sectionType,
                    behavior = behavior,
                    toMediaItem = { it.copy(name = "lifted") },
                    onDetails = { details = it },
                    onPlay = { fail("play must not fire off Continue Watching") },
                    onAsk = { fail("ask must not fire off Continue Watching") },
                )

                click(mediaItem)

                assertEquals("lifted", details?.name, "sectionType=$sectionType behavior=$behavior")
            }
        }
    }
}
