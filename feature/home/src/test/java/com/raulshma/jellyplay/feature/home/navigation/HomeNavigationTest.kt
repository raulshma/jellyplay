package com.raulshma.jellyplay.feature.home.navigation

import com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.home.HomeCallbacks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeNavigationTest {

    private lateinit var navigator: Navigator

    @Before
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
        val playOnStrategy = mockk<JellyfinRemotePlayCastStrategy>(relaxed = true)
        every { playOnStrategy.isConnected } returns MutableStateFlow(true)

        val callbacks = buildTestCallbacks(navigator, playOnStrategy = playOnStrategy)
        callbacks.onPlayClick("item-1", null, 20_000_000L, MediaType.MOVIE, null, "Movie")

        verify { playOnStrategy.loadMedia(itemId = "item-1", startPositionMs = 2_000L) }
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
    fun onOfflineItemClick_series_navigatesToMediaDetail() {
        val callbacks = buildTestCallbacks(navigator)
        callbacks.onOfflineItemClick("series-1", MediaType.SERIES)

        verify { navigator.navigate(Route.MediaDetail("series-1")) }
    }

    @Test
    fun onOfflineItemClick_movie_navigatesToMediaDetail() {
        val callbacks = buildTestCallbacks(navigator)
        callbacks.onOfflineItemClick("movie-1", MediaType.MOVIE)

        verify { navigator.navigate(Route.MediaDetail("movie-1")) }
    }

    @Test
    fun onSeerrItemClick_navigatesToSeerrDetail() {
        val callbacks = buildTestCallbacks(navigator)
        callbacks.onSeerrItemClick(12345, "movie")

        verify { navigator.navigate(Route.SeerrDetail(12345, "movie")) }
    }

    @Test
    fun onSearchItemClick_navigatesToMediaDetail() {
        val callbacks = buildTestCallbacks(navigator)
        callbacks.onSearchItemClick("search-item-1")

        verify { navigator.navigate(Route.MediaDetail("search-item-1")) }
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
        playOnStrategy: JellyfinRemotePlayCastStrategy? = null,
        onPlayOnClick: () -> Unit = {},
        onModeChange: (HomeMode) -> Unit = {},
    ): HomeCallbacks {
        return HomeCallbacks(
            onItemClick = { itemId, mediaType, parentId, itemName ->
                navigator.navigate(Route.MediaDetail(itemId))
            },
            onPlayClick = { itemId, mediaSourceId, startPosition, mediaType, parentId, itemName ->
                if (mediaType == MediaType.PHOTO) {
                    navigator.navigate(Route.PhotoViewer(itemId, parentId ?: ""))
                } else if (playOnStrategy?.isConnected?.value == true) {
                    playOnStrategy.loadMedia(
                        itemId = itemId,
                        startPositionMs = startPosition / 10_000,
                    )
                } else if (mediaType == MediaType.CHANNEL || mediaType == MediaType.LIVE_TV) {
                    navigator.navigate(Route.LiveTvChannelPlayer(itemId, itemName))
                } else {
                    navigator.navigate(Route.VideoPlayer(itemId, mediaSourceId, startPosition))
                }
            },
            onSettingsClick = { navigator.navigate(Route.Settings) },
            onSyncPlayClick = { navigator.navigate(Route.SyncPlay) },
            onDownloadsClick = { navigator.navigate(Route.Downloads) },
            onPlayOnClick = onPlayOnClick,
            onOfflineLibraryClick = { navigator.navigate(Route.OfflineLibrary) },
            onOfflineItemClick = { itemId, mediaType ->
                navigator.navigate(Route.MediaDetail(itemId))
            },
            onSeerrItemClick = { tmdbId, mediaType ->
                navigator.navigate(Route.SeerrDetail(tmdbId, mediaType))
            },
            onModeChange = onModeChange,
            onSearchItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
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
