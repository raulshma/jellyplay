package com.raulshma.jellyplay.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Invariants pinned for the home content list's render-model bundle (the
 * values [HomeSurfaceTest] constructs implicitly, asserted directly here):
 *  - [HomeFeed.Offline] derives `sections` from its [OfflineHomeContent] — the
 *    offline branch's constructor IS the mask; there is no second section list
 *    to drift out of sync.
 *  - [HomeContentState.online] / [HomeContentState.offlineContent] are exact
 *    cross-casts: exactly one is non-null per feed, so the "render one branch"
 *    invariant holds at the read sites too.
 *  - [HomeFeed.Online] carries the online-only surfaces (partialLoadError,
 *    newsletterBannerVisible) that the offline feed cannot express.
 */
class HomeFeedContractTest {

    private fun section(type: HomeSectionType) = HomeSection(
        id = type.name,
        title = type.displayName,
        type = type,
        items = emptyList(),
    )

    private fun offlineItem(id: String) = OfflineMediaItem(
        id = id,
        name = id,
        mediaType = MediaType.MOVIE,
    )

    private fun onlineFeed() = HomeFeed.Online(
        sections = listOf(section(HomeSectionType.CONTINUE_WATCHING)),
        isLoading = false,
        partialLoadError = true,
        newsletterBannerVisible = true,
    )

    private fun offlineFeed(): HomeFeed.Offline {
        val content = buildOfflineHomeContent(
            library = listOf(offlineItem("d1")),
            episodes = emptyList(),
            homeMode = HomeMode.VIDEO,
            titles = OfflineHomeSectionTitles(
                continueWatching = "Continue Watching",
                nextUp = "Next Up",
                recentlyDownloaded = "Downloaded",
                movies = "Movies",
                series = "Series",
                music = "Music",
            ),
            prefs = OfflineHomeSectionPrefs(),
        )
        return HomeFeed.Offline(content = content)
    }

    private fun state(feed: HomeFeed) = HomeContentState(
        feed = feed,
        homeHeroEnabled = true,
        homeBackdropEnabled = true,
        discoverEnabled = false,
        experimentalCardClippingEnabled = false,
        featuredItem = null,
        backgroundColor = Color.Black,
        contentPad = 0.dp,
        headerHeight = 0.dp,
        isLightTheme = false,
        continueWatchingClickBehavior = ContinueWatchingClickBehavior.DETAILS,
        discoverRows = emptyList<List<SeerrSearchItem>>(),
        allDiscoverItems = emptyList(),
        recentlyGrabbed = emptyList(),
    )

    @Test
    fun offlineFeed_sectionsDelegateToTheDerivedContent() {
        val feed = offlineFeed()

        assertTrue(feed.content.sections.isNotEmpty(), "downloads present → derived sections exist")
        assertEquals(
            feed.content.sections,
            feed.sections,
            "Offline.sections must be exactly the content's derived sections",
        )
    }

    @Test
    fun contentState_onlineAndOfflineContent_areExclusiveCrossCasts() {
        val online = state(onlineFeed())
        val offline = state(offlineFeed())

        val onlineFeedView = online.online
        val offlineContent = online.offlineContent
        assertTrue(onlineFeedView != null)
        assertNull(offlineContent, "an online feed must cast to no offline content")
        assertTrue(onlineFeedView!!.partialLoadError)
        assertTrue(onlineFeedView.newsletterBannerVisible)

        assertTrue(offline.offlineContent != null)
        assertNull(offline.online, "an offline feed must cast to no online feed")
    }

    @Test
    fun offlineFeed_isLoading_defaultsFalse_andCanBePending() {
        assertFalse(offlineFeed().isLoading, "the derived feed renders; no pending window")
        val pending = offlineFeed().copy(isLoading = true)
        assertTrue(pending.isLoading, "FallbackPending window after the gate, before the first emission")
    }

    @Test
    fun onlineFeed_sectionsPassThrough() {
        val feed = onlineFeed()
        assertEquals(HomeSectionType.CONTINUE_WATCHING, feed.sections.single().type)
    }
}
