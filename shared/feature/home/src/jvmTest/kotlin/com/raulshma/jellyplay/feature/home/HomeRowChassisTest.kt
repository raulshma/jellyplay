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
 * if/else chain — plus the See-All gate ([sectionHasSeeAll]), the shared
 * CW click routing ([resumeRowClick]) the online and offline wide rows must not
 * drift apart on, and its poster-row selection ([posterRowClick]).
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

    /**
     * Drives [buildClick]'s click once per click-behavior pref and asserts
     * each behavior routed to its own sink — the shared loop of the
     * behavior-routing pins ([resumeRowClick] and [posterRowClick] both
     * honor the pref for the same resume sections).
     */
    private fun assertRoutesByBehavior(
        mediaItem: MediaItem,
        buildClick: (ContinueWatchingClickBehavior, ResumeRowSinks) -> (MediaItem) -> Unit,
    ) {
        for (behavior in ContinueWatchingClickBehavior.entries) {
            val expectedSink = when (behavior) {
                ContinueWatchingClickBehavior.DETAILS -> "details"
                ContinueWatchingClickBehavior.PLAY -> "play"
                ContinueWatchingClickBehavior.ASK -> "ask"
            }
            var sink: String? = null
            buildClick(
                behavior,
                ResumeRowSinks(
                    onDetails = { sink = "details" },
                    onPlay = { sink = "play" },
                    onAsk = { sink = "ask" },
                ),
            )(mediaItem)
            assertEquals(expectedSink, sink, "behavior=$behavior")
        }
    }

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

    // ── isResumeSection ──

    @Test
    fun isResumeSection_trueOnlyForContinueWatchingAndContinueReading() {
        // The ONE predicate both click folds key on — the next resume
        // section joins by editing this membership, nothing else.
        for (type in HomeSectionType.entries) {
            assertEquals(
                type == HomeSectionType.CONTINUE_WATCHING || type == HomeSectionType.CONTINUE_READING,
                isResumeSection(type),
                "type=$type",
            )
        }
    }

    // ── resumeRowClick ──

    @Test
    fun resumeRowClick_continueWatching_routesByBehavior() {
        assertRoutesByBehavior(item(MediaType.MOVIE)) { behavior, sinks ->
            resumeRowClick(
                sectionType = HomeSectionType.CONTINUE_WATCHING,
                behavior = behavior,
                toMediaItem = { it },
                sinks = sinks,
            )
        }
    }

    @Test
    fun resumeRowClick_ask_mapsTheItemBeforeHandingToTheAskSink() {
        val mediaItem = item(MediaType.MOVIE)
        var asked: MediaItem? = null

        val click: (MediaItem) -> Unit = resumeRowClick(
            sectionType = HomeSectionType.CONTINUE_WATCHING,
            behavior = ContinueWatchingClickBehavior.ASK,
            toMediaItem = { it.copy(name = "lifted") },
            sinks = ResumeRowSinks(
                onDetails = { fail("details must not fire on ASK") },
                onPlay = { fail("play must not fire on ASK") },
                onAsk = { asked = it },
            ),
        )

        click(mediaItem)

        // The offline site's mapper lifts the item before the dialog sees it —
        // the sink receives the LIFTED MediaItem, not the raw T.
        assertEquals("lifted", asked?.name)
    }

    @Test
    fun resumeRowClick_continueReading_alsoRoutesByBehavior() {
        // The Continue Reading row honors the SAME click-behavior pref as
        // Continue Watching — PLAY resumes the reader through the play
        // funnel's BOOK fork.
        assertRoutesByBehavior(item(MediaType.BOOK)) { behavior, sinks ->
            resumeRowClick(
                sectionType = HomeSectionType.CONTINUE_READING,
                behavior = behavior,
                toMediaItem = { it },
                sinks = sinks,
            )
        }
    }

    @Test
    fun resumeRowClick_offContinueWatchingSection_alwaysOpensDetails() {
        val mediaItem = item(MediaType.MOVIE)

        // NEXT_UP is the pinned case: it shares the wide row with Continue
        // Watching but ALWAYS opens details, whatever the behavior pref says.
        for (sectionType in listOf(HomeSectionType.NEXT_UP, HomeSectionType.RECENTLY_ADDED)) {
            for (behavior in ContinueWatchingClickBehavior.entries) {
                var details: MediaItem? = null
                val click: (MediaItem) -> Unit = resumeRowClick(
                    sectionType = sectionType,
                    behavior = behavior,
                    toMediaItem = { it.copy(name = "lifted") },
                    sinks = ResumeRowSinks(
                        onDetails = { details = it },
                        onPlay = { fail("play must not fire off Continue Watching") },
                        onAsk = { fail("ask must not fire off Continue Watching") },
                    ),
                )

                click(mediaItem)

                assertEquals("lifted", details?.name, "sectionType=$sectionType behavior=$behavior")
            }
        }
    }

    // ── posterRowClick ──

    @Test
    fun posterRowClick_continueReading_routesByBehavior() {
        // The ONLY poster row that honors the resume-row behavior pref —
        // the selection both poster render sites delegate to, so they cannot
        // drift on which section types honor it.
        assertRoutesByBehavior(item(MediaType.BOOK)) { behavior, sinks ->
            posterRowClick(
                sectionType = HomeSectionType.CONTINUE_READING,
                behavior = behavior,
                toMediaItem = { it },
                sinks = sinks,
                onPlainClick = { fail("plain click must not fire on Continue Reading") },
            )
        }
    }

    @Test
    fun posterRowClick_everyOtherPosterType_opensThePlainClickSink() {
        // The plain-click sink is source-specific (offline routes by id,
        // online hands the item) — the selection itself must not consult the
        // behavior pref for any non-Continue-Reading poster type.
        val plainPosterTypes = posterTypes.filter { it != HomeSectionType.CONTINUE_READING }
        for (sectionType in plainPosterTypes) {
            for (behavior in ContinueWatchingClickBehavior.entries) {
                var plain: MediaItem? = null
                val click: (MediaItem) -> Unit = posterRowClick(
                    sectionType = sectionType,
                    behavior = behavior,
                    toMediaItem = { it },
                    sinks = ResumeRowSinks(
                        onDetails = { fail("details must not fire on a plain poster row") },
                        onPlay = { fail("play must not fire on a plain poster row") },
                        onAsk = { fail("ask must not fire on a plain poster row") },
                    ),
                    onPlainClick = { plain = it },
                )

                click(item(MediaType.MOVIE))

                assertEquals("i1", plain?.id, "sectionType=$sectionType behavior=$behavior")
            }
        }
    }
}
