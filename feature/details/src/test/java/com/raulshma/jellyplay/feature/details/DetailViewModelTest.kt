package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var mediaRepository: MediaRepository
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var preferencesStore: UserPreferencesStore
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var seerrRequestDelegate: SeerrRequestDelegate
    private lateinit var audioPlaybackManager: AudioPlaybackManager
    private lateinit var tmdbApiClient: TmdbApiClient

    private lateinit var viewModel: DetailViewModel

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        preferencesStore = mockk(relaxed = true)
        offlineModeManager = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        seerrRequestDelegate = mockk(relaxed = true)
        audioPlaybackManager = mockk(relaxed = true)
        tmdbApiClient = mockk(relaxed = true)

        every { preferencesStore.preferences } returns MutableStateFlow(UserPreferences())
        every { seerrRepository.isConnected() } returns flowOf(false)
        every { seerrRepository.isRecommendationsEnabled() } returns flowOf(false)

        viewModel = DetailViewModel(
            context = context,
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            downloadRepository = downloadRepository,
            preferencesStore = preferencesStore,
            offlineModeManager = offlineModeManager,
            seerrRepository = seerrRepository,
            seerrRequestDelegate = seerrRequestDelegate,
            audioPlaybackManager = audioPlaybackManager,
            tmdbApiClient = tmdbApiClient,
        )
    }

    @Test
    fun loadItem_failure_setsErrorMessage() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.failure(RuntimeException("boom"))

        viewModel.loadItem("m1")
        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.detail)
    }

    @Test
    fun loadItem_movie_clearsSmartPlayTarget() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)),
        )

        viewModel.loadItem("m1")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.detail)
        assertNull(viewModel.uiState.value.smartPlayTarget)
    }

    @Test
    fun smartPlay_seriesWithUnplayedFirstEpisode_showsPlayLabel() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep1 = episode("e1", 1, 1, isPlayed = false)
        val ep2 = episode("e2", 1, 2, isPlayed = false)
        stubSeries("s1", season, listOf(ep1, ep2))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget
        assertNotNull(target)
        assertEquals("Play S1:E1", target!!.label)
        assertEquals("e1", target.episode.id)
        assertEquals(0L, target.startPositionTicks)
    }

    @Test
    fun smartPlay_seriesWithResumeProgress_showsResumeLabel() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep1 = episode("e1", 1, 1, isPlayed = false)
        val ep2 = episode("e2", 1, 2, isPlayed = false, positionTicks = 50_000_000L)
        stubSeries("s1", season, listOf(ep1, ep2))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget!!
        assertEquals("Resume S1:E2", target.label)
        assertEquals("e2", target.episode.id)
        assertEquals(50_000_000L, target.startPositionTicks)
    }

    @Test
    fun smartPlay_seriesWhereEarlierEpisodePlayed_showsNextUpLabel() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep1 = episode("e1", 1, 1, isPlayed = true)
        val ep2 = episode("e2", 1, 2, isPlayed = false)
        stubSeries("s1", season, listOf(ep1, ep2))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget!!
        assertEquals("Next Up S1:E2", target.label)
    }

    @Test
    fun smartPlay_allPlayed_showsReplayLabel() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep1 = episode("e1", 1, 1, isPlayed = true)
        val ep2 = episode("e2", 1, 2, isPlayed = true)
        stubSeries("s1", season, listOf(ep1, ep2))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget!!
        assertEquals("Replay S1:E1", target.label)
        assertEquals("e1", target.episode.id)
    }

    @Test
    fun smartPlay_resumeTakesPrecedenceOverNextUp() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep1 = episode("e1", 1, 1, isPlayed = false, positionTicks = 10_000_000L)
        val ep2 = episode("e2", 1, 2, isPlayed = false)
        stubSeries("s1", season, listOf(ep1, ep2))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget!!
        assertEquals("Resume S1:E1", target.label)
    }

    @Test
    fun smartPlay_episodesUnsortedInResponse_areSortedBeforeSelection() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep2 = episode("e2", 1, 2, isPlayed = false)
        val ep1 = episode("e1", 1, 1, isPlayed = false)
        stubSeries("s1", season, listOf(ep2, ep1))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget!!
        assertEquals("e1", target.episode.id)
    }

    @Test
    fun resolveTmdbId_providerIdTmdb_returnsParsed() = runTest(mainDispatcherRule.testDispatcher) {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            providerIds = mapOf("tmdb" to "12345"),
        )
        assertEquals(12345, callResolveTmdbId(detail))
    }

    @Test
    fun resolveTmdbId_providerIdTmdbid_returnsParsed() = runTest(mainDispatcherRule.testDispatcher) {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            providerIds = mapOf("tmdbid" to "67890"),
        )
        assertEquals(67890, callResolveTmdbId(detail))
    }

    @Test
    fun resolveTmdbId_externalUrlWithTmdbHost_extractsId() = runTest(mainDispatcherRule.testDispatcher) {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            externalUrls = listOf(ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/55512")),
        )
        assertEquals(55512, callResolveTmdbId(detail))
    }

    @Test
    fun resolveTmdbId_noProviderOrTmdbUrl_returnsNull() = runTest(mainDispatcherRule.testDispatcher) {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            externalUrls = listOf(ExternalUrl(name = "IMDb", url = "https://www.imdb.com/title/tt123")),
        )
        assertNull(callResolveTmdbId(detail))
    }

    @Test
    fun resolveTmdbId_nonNumericProviderId_returnsNull() = runTest(mainDispatcherRule.testDispatcher) {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            providerIds = mapOf("tmdb" to "abc-not-a-number"),
        )
        assertNull(callResolveTmdbId(detail))
    }

    private fun callResolveTmdbId(detail: MediaDetail): Int? {
        val fn = DetailViewModel::class.java.getDeclaredMethod("resolveTmdbId", MediaDetail::class.java)
        fn.isAccessible = true
        return fn.invoke(viewModel, detail) as Int?
    }

    private fun stubSeries(seriesId: String, season: MediaItem, episodes: List<MediaItem>) {
        coEvery { mediaRepository.getMediaDetail(seriesId) } returns Result.success(
            MediaDetail(item = MediaItem(id = seriesId, name = "Show", mediaType = MediaType.SERIES)),
        )
        coEvery { mediaRepository.getSeasons(seriesId) } returns Result.success(listOf(season))
        coEvery { mediaRepository.getEpisodes(seriesId, season.id) } returns Result.success(episodes)
    }

    private fun episode(
        id: String,
        season: Int,
        episode: Int,
        isPlayed: Boolean = false,
        positionTicks: Long? = null,
    ) = MediaItem(
        id = id,
        name = "Episode $episode",
        mediaType = MediaType.EPISODE,
        seasonNumber = season,
        episodeNumber = episode,
        indexNumber = episode,
        isPlayed = isPlayed,
        playbackPositionTicks = positionTicks,
        seriesId = "s1",
        seasonId = "season1",
    )
}
