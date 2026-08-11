package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import com.raulshma.jellyplay.core.model.OfflineSubtitleEntry
import com.raulshma.jellyplay.core.model.OfflineSubtitleManifest
import com.raulshma.jellyplay.core.network.api.ApiException
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-policy + reactivity tests for [UnifiedMediaDetailProviderImpl], driven
 * through the [MediaDetailProvider] seam with fake/mock dependencies. Pins the
 * remote/local/offline source-policy matrix.
 */
class UnifiedMediaDetailProviderImplTest {

    private val mediaRepository: com.raulshma.jellyplay.core.data.repository.MediaRepository = mockk(relaxed = true)
    private val offlineRepository: com.raulshma.jellyplay.core.data.repository.OfflineRepository = mockk(relaxed = true)
    private val downloadRepository: com.raulshma.jellyplay.core.data.repository.DownloadRepository = mockk(relaxed = true)
    private val episodeCatalogue: EpisodeCatalogue = mockk(relaxed = true)
    private val playbackSourceResolver: PlaybackSourceResolver = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk()

    private fun TestScope.buildProvider() = UnifiedMediaDetailProviderImpl(
        mediaRepository = mediaRepository,
        offlineRepository = offlineRepository,
        downloadRepository = downloadRepository,
        episodeCatalogue = episodeCatalogue,
        playbackSourceResolver = playbackSourceResolver,
        offlineModeManager = offlineModeManager,
        appScope = this,
    )

    private fun movieDetail(id: String = "m1"): MediaDetail = MediaDetail(
        item = MediaItem(id = id, name = "Movie", mediaType = MediaType.MOVIE),
        studios = emptyList(),
    )

    private fun localMovie(id: String = "m1"): OfflineMediaItem = OfflineMediaItem(
        id = id,
        name = "Movie",
        mediaType = MediaType.MOVIE,
        posterPath = "/data/offline/$id/poster.jpg",
        backdropPath = "/data/offline/$id/backdrop.jpg",
        downloadPath = "/data/offline/$id/movie.mkv",
        downloadStatus = DownloadStatus.COMPLETED,
        totalSizeBytes = 1_000L,
        createdAt = 1_700_000_000_000L,
        cast = listOf(
            OfflinePersonInfo(id = "p1", name = "Actor", localImagePath = "/data/offline/$id/cast/p1.jpg"),
        ),
        externalUrls = listOf(ExternalUrl("Site", "https://e.com")),
    )

    private fun completedDownload(id: String = "m1"): DownloadItem = DownloadItem(
        id = "dl-$id",
        mediaItemId = id,
        name = "Movie",
        mediaType = MediaType.MOVIE,
        downloadPath = "/data/offline/$id/movie.mkv",
        downloadUrl = "",
        totalSizeBytes = 1_000L,
        downloadedBytes = 1_000L,
        status = DownloadStatus.COMPLETED,
    )

    private fun seriesDetail(id: String = "s1"): MediaDetail = MediaDetail(
        item = MediaItem(id = id, name = "Series", mediaType = MediaType.SERIES),
        studios = emptyList(),
    )

    /** A catalogue snapshot for a series with [seasonIds] seasons, each with
     *  [episodesPerSeason] episodes. All episodes start unplayed. */
    private fun seriesCatalogueSnapshot(
        seriesId: String = "s1",
        seasonIds: List<String> = listOf("season1"),
        episodesPerSeason: Int = 2,
    ): EpisodeCatalogueSnapshot {
        val seasons = seasonIds.map { sid ->
            MediaItem(id = sid, name = "Season $sid", mediaType = MediaType.SEASON, seriesId = seriesId)
        }
        val episodesBySeason = seasonIds.associateWith { sid ->
            (1..episodesPerSeason).map { n ->
                MediaItem(
                    id = "$sid-ep$n",
                    name = "Episode $n",
                    mediaType = MediaType.EPISODE,
                    seriesId = seriesId,
                    seasonId = sid,
                    episodeNumber = n,
                    seasonNumber = 1,
                )
            }
        }
        return EpisodeCatalogueSnapshot(
            seriesId = seriesId,
            seasons = seasons,
            episodesBySeason = episodesBySeason,
            fetchedSeasonIds = seasonIds.toSet(),
            sortedEpisodes = episodesBySeason.values.flatten(),
            epoch = 0L,
        )
    }

    /** Wires the standard reactive stubs for [itemId]. */
    private fun wireStubs(
        itemId: String,
        mode: OfflineMode = OfflineMode.ONLINE,
        localItem: OfflineMediaItem? = null,
        download: DownloadItem? = null,
    ) {
        every { offlineModeManager.offlineMode } returns MutableStateFlow(mode)
        every { offlineRepository.getOfflineDetail(itemId) } returns MutableStateFlow(localItem)
        every { downloadRepository.getDownloadByMediaItemIdFlow(itemId) } returns MutableStateFlow(download)
        every { offlineRepository.getOfflineSyncState(itemId) } returns flowOf(null)
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns Result.success(
            EpisodeCatalogueSnapshot.empty("unused"),
        )
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns download?.takeIf {
            it.status == DownloadStatus.COMPLETED
        }
        coEvery { downloadRepository.loadLocalSubtitleManifest(any()) } returns null
    }

    private suspend fun firstResolved(provider: MediaDetailProvider, itemId: String): DetailLoadState =
        provider.observe(itemId).first { it !is DetailLoadState.Loading }

    @Test
    fun `remote wins over a completed download while online`() = runTest {
        wireStubs("m1", mode = OfflineMode.ONLINE, localItem = localMovie(), download = completedDownload())
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.success(movieDetail())

        val state = firstResolved(buildProvider(), "m1")

        assertTrue(state is DetailLoadState.Loaded)
        val snapshot = (state as DetailLoadState.Loaded).snapshot
        assertEquals(DetailOrigin.REMOTE, snapshot.context.origin)
        // Completed download is attached, but origin stays REMOTE.
        assertNotNull(snapshot.context.download)
        assertTrue(snapshot.capabilities.remoteDiscovery)
        assertTrue(snapshot.capabilities.remoteStreamSelection)
        assertTrue(snapshot.capabilities.localDownloadManagement) // completed -> manageable
    }

    @Test
    fun `manual offline mode does not contact the server`() = runTest {
        wireStubs("m1", mode = OfflineMode.OFFLINE_MANUAL, localItem = localMovie())

        val state = firstResolved(buildProvider(), "m1")

        assertTrue(state is DetailLoadState.Loaded)
        assertEquals(
            DetailOrigin.LOCAL_OFFLINE_MODE,
            (state as DetailLoadState.Loaded).snapshot.context.origin,
        )
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    @Test
    fun `remote failure falls back in place to local detail`() = runTest {
        wireStubs("m1", mode = OfflineMode.ONLINE, localItem = localMovie())
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.failure(
            ApiException(isRetryable = false, message = "boom"),
        )

        val state = firstResolved(buildProvider(), "m1")

        assertTrue(state is DetailLoadState.Loaded)
        assertEquals(
            DetailOrigin.LOCAL_REMOTE_FAILURE,
            (state as DetailLoadState.Loaded).snapshot.context.origin,
        )
    }

    @Test
    fun `offline mode with no local row is unavailable offline`() = runTest {
        wireStubs("m1", mode = OfflineMode.OFFLINE_AUTO, localItem = null)

        val state = firstResolved(buildProvider(), "m1")

        assertTrue(state is DetailLoadState.Error)
        assertTrue((state as DetailLoadState.Error).error.isUnavailableOffline)
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    @Test
    fun `remote failure with no local row preserves access-denied classification`() = runTest {
        wireStubs("m1", mode = OfflineMode.ONLINE, localItem = null)
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.failure(
            ApiException(isRetryable = false, httpCode = 403, isAccessDenied = true, message = "forbidden"),
        )

        val state = firstResolved(buildProvider(), "m1")

        assertTrue(state is DetailLoadState.Error)
        val err = (state as DetailLoadState.Error).error
        assertTrue(err.isAccessDenied)
        assertFalse(err.isUnavailableOffline)
    }

    @Test
    fun `local snapshot carries local artwork and manifest-backed subtitles only`() = runTest {
        val local = localMovie()
        wireStubs("m1", mode = OfflineMode.OFFLINE_MANUAL, localItem = local, download = completedDownload())
        coEvery { downloadRepository.loadLocalSubtitleManifest(any()) } returns OfflineSubtitleManifest(
            subtitles = listOf(
                OfflineSubtitleEntry(index = 2, fileName = "sub.srt", displayTitle = "English", isDefault = true),
            ),
        )

        val state = firstResolved(buildProvider(), "m1")

        val snapshot = (state as DetailLoadState.Loaded).snapshot
        assertEquals("/data/offline/m1/poster.jpg", snapshot.assets.posterPath)
        assertEquals("/data/offline/m1/backdrop.jpg", snapshot.assets.backdropPath)
        assertEquals("/data/offline/m1/cast/p1.jpg", snapshot.assets.castImages["p1"])
        assertEquals(1, snapshot.localSubtitles.size)
        assertEquals(2, snapshot.localSubtitles.first().index)
        // No synthesized media source / audio selection for local.
        assertTrue(snapshot.detail.mediaSources.isEmpty())
        assertTrue(snapshot.capabilities.localSubtitleSelection)
        assertFalse(snapshot.capabilities.remoteStreamSelection)
        assertFalse(snapshot.capabilities.personNavigation)
        assertFalse(snapshot.capabilities.studioNavigation)
        assertFalse(snapshot.capabilities.remoteDiscovery)
    }

    @Test
    fun `reconnect after a local fallback retries remote resolution`() = runTest {
        val mode = MutableStateFlow(OfflineMode.ONLINE)
        every { offlineModeManager.offlineMode } returns mode
        every { offlineRepository.getOfflineDetail("m1") } returns MutableStateFlow(localMovie())
        every { downloadRepository.getDownloadByMediaItemIdFlow("m1") } returns MutableStateFlow(null)
        every { offlineRepository.getOfflineSyncState("m1") } returns flowOf(null)
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns Result.success(
            EpisodeCatalogueSnapshot.empty("unused"),
        )
        coEvery { playbackSourceResolver.resolveUsableDownload("m1") } returns null
        coEvery { downloadRepository.loadLocalSubtitleManifest(any()) } returns null

        var attempts = 0
        coEvery { mediaRepository.getMediaDetail("m1") } answers {
            attempts++
            if (attempts == 1) Result.failure(ApiException(isRetryable = false, message = "net"))
            else Result.success(movieDetail())
        }

        val provider = buildProvider()
        val states = mutableListOf<DetailLoadState>()
        val job = launch { provider.observe("m1").collect { states += it } }
        advanceUntilIdle()
        assertEquals(
            DetailOrigin.LOCAL_REMOTE_FAILURE,
            states.filterIsInstance<DetailLoadState.Loaded>().last().snapshot.context.origin,
        )

        // Flip offline -> online to trigger a reconnect retry.
        mode.value = OfflineMode.OFFLINE_MANUAL
        advanceUntilIdle()
        mode.value = OfflineMode.ONLINE
        advanceUntilIdle()

        assertEquals(
            DetailOrigin.REMOTE,
            states.filterIsInstance<DetailLoadState.Loaded>().last().snapshot.context.origin,
        )
        coVerify(atLeast = 2) { mediaRepository.getMediaDetail("m1") }
        job.cancel()
    }

    // ── New seam methods: optimistic rewrite, expand, canonical ids,
    //    invalidate. These are the first tests to exercise a SERIES through the
    //    provider — the 7 tests above all use movies. ──────────────────────

    @Test
    fun `applyOptimisticSeasonRewrite re-emits rewritten episodes and invalidates the series`() = runTest {
        val snapshot = seriesCatalogueSnapshot()
        wireStubs("s1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("s1") } returns Result.success(seriesDetail())
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(snapshot)
        // The catalogue rewrites the season + rebuilds sortedEpisodes; mirror that.
        val rewritten = snapshot.copy(
            episodesBySeason = snapshot.episodesBySeason.mapValues { (_, eps) ->
                eps.map { it.copy(isPlayed = true) }
            },
            sortedEpisodes = snapshot.episodesBySeason.values.flatten().map { it.copy(isPlayed = true) },
        )
        coEvery { episodeCatalogue.updateSeasonEpisodes("s1", "season1", any()) } returns rewritten

        val provider = buildProvider()
        val states = mutableListOf<DetailLoadState>()
        val job = launch { provider.observe("s1").collect { states += it } }
        advanceUntilIdle()

        // First Loaded carries unplayed episodes.
        val before = states.filterIsInstance<DetailLoadState.Loaded>().last()
        assertTrue(before.snapshot.episodesBySeason["season1"]?.all { !it.isPlayed } ?: false)

        // The initial resolution's force-invalidate (lastTick starts at -1) also
        // calls invalidateSeries; clear the call log so the assertions below
        // isolate the rewrite's own calls. Stubs (answers) are preserved.
        clearMocks(episodeCatalogue, answers = false, recordedCalls = true, childMocks = false)

        provider.applyOptimisticSeasonRewrite("s1", "season1") { episodes ->
            episodes.map { it.copy(isPlayed = true) }
        }
        advanceUntilIdle()

        val after = states.filterIsInstance<DetailLoadState.Loaded>().last()
        assertTrue(after.snapshot.episodesBySeason["season1"]?.all { it.isPlayed } ?: false)
        // The rewrite mirrored through the catalogue and dropped its cache.
        coVerify(exactly = 1) { episodeCatalogue.updateSeasonEpisodes("s1", "season1", any()) }
        coVerify(exactly = 1) { episodeCatalogue.invalidateSeries("s1") }
        job.cancel()
    }

    @Test
    fun `applyOptimisticSeasonRewrite is a no-op when the season has no episodes`() = runTest {
        val snapshot = seriesCatalogueSnapshot(seasonIds = listOf("season1"))
        wireStubs("s1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("s1") } returns Result.success(seriesDetail())
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(snapshot)

        val provider = buildProvider()
        val job = launch { provider.observe("s1").collect { } }
        advanceUntilIdle()

        // Clear the initial resolution's calls so the assertions isolate the
        // no-op rewrite's behavior.
        clearMocks(episodeCatalogue, answers = false, recordedCalls = true, childMocks = false)

        // "seasonX" is not in the snapshot — nothing to rewrite.
        provider.applyOptimisticSeasonRewrite("s1", "seasonX") { it }
        advanceUntilIdle()

        coVerify(exactly = 0) { episodeCatalogue.updateSeasonEpisodes(any(), any(), any()) }
        coVerify(exactly = 0) { episodeCatalogue.invalidateSeries(any()) }
        job.cancel()
    }

    @Test
    fun `expandSeason merges a new season, re-emits, and returns its episodes`() = runTest {
        val initial = seriesCatalogueSnapshot(seasonIds = listOf("season1"), episodesPerSeason = 2)
        wireStubs("s1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("s1") } returns Result.success(seriesDetail())
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(initial)
        val season2Episodes = listOf(
            MediaItem(
                id = "season2-ep1",
                name = "S2 E1",
                mediaType = MediaType.EPISODE,
                seriesId = "s1",
                seasonId = "season2",
                episodeNumber = 1,
                seasonNumber = 2,
            ),
        )
        coEvery { episodeCatalogue.loadSeasonEpisodes("s1", "season2", any()) } returns Result.success(season2Episodes)

        val provider = buildProvider()
        val states = mutableListOf<DetailLoadState>()
        val job = launch { provider.observe("s1").collect { states += it } }
        advanceUntilIdle()

        val before = states.filterIsInstance<DetailLoadState.Loaded>().last()
        assertFalse(before.snapshot.fetchedSeasonIds.contains("season2"))

        val returned = provider.expandSeason("s1", "season2")
        advanceUntilIdle()

        assertEquals(season2Episodes.map { it.id }, returned.map { it.id })
        val after = states.filterIsInstance<DetailLoadState.Loaded>().last()
        assertTrue(after.snapshot.fetchedSeasonIds.contains("season2"))
        assertEquals(1, after.snapshot.episodesBySeason["season2"]?.size)
        job.cancel()
    }

    @Test
    fun `canonicalEpisodeIds serves from a loaded session without a cold load`() = runTest {
        val snapshot = seriesCatalogueSnapshot()
        wireStubs("s1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("s1") } returns Result.success(seriesDetail())
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(snapshot)

        val provider = buildProvider()
        val job = launch { provider.observe("s1").collect { } }
        advanceUntilIdle()

        val ids = provider.canonicalEpisodeIds("s1")
        assertEquals(snapshot.allEpisodeIds, ids)
        // Only the resolution's load fires — canonicalEpisodeIds reads from the
        // session content, not the catalogue.
        coVerify(exactly = 1) { episodeCatalogue.loadSeriesEpisodes("s1", any()) }
        job.cancel()
    }

    @Test
    fun `canonicalEpisodeIds cold-loads when no session is loaded`() = runTest {
        val snapshot = seriesCatalogueSnapshot()
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(snapshot)

        val ids = buildProvider().canonicalEpisodeIds("s1")

        assertEquals(snapshot.allEpisodeIds, ids)
    }

    @Test
    fun `invalidate delegates to the catalogue`() = runTest {
        buildProvider().invalidate("s1")
        coVerify(exactly = 1) { episodeCatalogue.invalidateSeries("s1") }
    }
}
