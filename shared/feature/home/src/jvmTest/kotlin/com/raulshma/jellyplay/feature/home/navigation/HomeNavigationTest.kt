package com.raulshma.jellyplay.feature.home.navigation

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.home.HomeCallbacks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test

class HomeNavigationTest {

    private lateinit var navigator: Navigator

    @BeforeTest
    fun setUp() {
        navigator = mockk(relaxed = true)
    }

    @Test
    fun onPlayClick_photoType_navigatesPhotoAware() {
        val callbacks = buildTestCallbacks(navigator)
        callbacks.onPlayClick("photo-1", null, 0L, MediaType.PHOTO, null, "Photo 1")

        verify { navigator.navigate(any<Route>()) }
    }

    @Test
    fun onPlayClick_remotePlayActive_callsLoadMedia() {
        val playOnStrategy = mockk<HomePlayOnRedirect>(relaxed = true)
        every { playOnStrategy.playOn(any(), any()) } returns true

        val callbacks = buildTestCallbacks(navigator, playOnStrategy = playOnStrategy)
        callbacks.onPlayClick("item-1", null, 20_000_000L, MediaType.MOVIE, null, "Movie")

        verify { playOnStrategy.playOn(itemId = "item-1", startPositionMs = 2_000L) }
        verify(exactly = 0) { navigator.navigate(any<Route>()) }
    }

    @Test
    fun onPlayClick_liveTvChannel_navigatesToLiveTvChannelPlayer() {
        val callbacks = buildTestCallbacks(navigator)
        callbacks.onPlayClick("ch-10", null, 0L, MediaType.CHANNEL, null, "BBC One")

        verify { navigator.navigate(Route.LiveTvChannelPlayer("ch-10", "BBC One")) }
    }

    @Test
    fun onPlayClick_videoItem_navigatesToVideoPlayer() {
        val callbacks = buildTestCallbacks(navigator)
        callbacks.onPlayClick("mov-1", "src-1", 10_000_000L, MediaType.MOVIE, null, "Inception")

        verify { navigator.navigate(Route.VideoPlayer("mov-1", "src-1", 10_000_000L)) }
    }

    @Test
    fun onSeerrItemClick_navigatesToSeerrDetail() {
        val callbacks = buildTestCallbacks(navigator)
        callbacks.onSeerrItemClick(12345, "movie")

        verify { navigator.navigate(Route.SeerrDetail(12345, "movie")) }
    }

    @Test
    fun onConfigureHomeLayout_navigatesToAppearanceSettings() {
        val callbacks = buildTestCallbacks(navigator)
        callbacks.onConfigureHomeLayout()

        verify { navigator.navigate(Route.AppearanceSettings("home_section_layout")) }
    }

    @Test
    fun onConfigureLibraries_navigatesToLibraryHomeSections() {
        val callbacks = buildTestCallbacks(navigator)
        callbacks.onConfigureLibraries()

        verify { navigator.navigate(Route.LibraryHomeSections("configure_libraries")) }
    }

    private fun buildTestCallbacks(
        navigator: Navigator,
        playOnStrategy: HomePlayOnRedirect? = null,
        onModeChange: (HomeMode) -> Unit = {},
    ): HomeCallbacks {
        return HomeCallbacks(
            onItemClick = { itemId, mediaType, parentId, itemName ->
                navigator.navigate(Route.MediaDetail(itemId))
            },
            onPlayClick = { itemId, mediaSourceId, startPosition, mediaType, parentId, itemName ->
                if (mediaType == MediaType.PHOTO) {
                    navigator.navigate(Route.PhotoViewer(itemId, parentId ?: ""))
                } else if (playOnStrategy?.playOn(
                        itemId = itemId,
                        startPositionMs = startPosition / 10_000,
                    ) == true
                ) {
                    // Flinged to the remote session — same shape as the
                    // production homeSection routing (HomePlayOnRedirect seam).
                } else if (mediaType == MediaType.CHANNEL || mediaType == MediaType.LIVE_TV) {
                    navigator.navigate(Route.LiveTvChannelPlayer(itemId, itemName))
                } else {
                    navigator.navigate(Route.VideoPlayer(itemId, mediaSourceId, startPosition))
                }
            },
            onOfflineLibraryClick = { navigator.navigate(Route.OfflineLibrary) },
            onSeerrItemClick = { tmdbId, mediaType ->
                navigator.navigate(Route.SeerrDetail(tmdbId, mediaType))
            },
            onModeChange = onModeChange,
            onSearchSeerrClick = { tmdbId, mediaType ->
                navigator.navigate(Route.SeerrDetail(tmdbId, mediaType))
            },
            onSettingsSearchItemClick = { route -> navigator.navigate(route) },
            onNewsletterClick = { navigator.navigate(Route.Newsletter) },
            onConfigureHomeLayout = { navigator.navigate(Route.AppearanceSettings("home_section_layout")) },
            onConfigureLibraries = { navigator.navigate(Route.LibraryHomeSections("configure_libraries")) },
        )
    }
}
