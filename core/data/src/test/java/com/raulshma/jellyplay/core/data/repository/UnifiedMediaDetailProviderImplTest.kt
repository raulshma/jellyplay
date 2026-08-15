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
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import com.raulshma.jellyplay.core.model.OfflineSubtitleEntry
import com.raulshma.jellyplay.core.model.OfflineSubtitleManifest
import com.raulshma.jellyplay.core.model.StreamType
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-policy + reactivity tests for [UnifiedMediaDetailProviderImpl], driven
 * through the [MediaDetailProvider] seam with fake/mock dependencies. Pins the
 * remote/local/offline source-policy matrix.
 */
class UnifiedMediaDetailProviderImplTest {

    private val mediaRepository: com.raulshma.jellyplay.core.data.repository.MediaRepository = mockk(relaxed = true)
    private val cacheInvalidation: com.raulshma.jellyplay.core.data.repository.MediaRepositoryCacheInvalidation =
        mockk(relaxed = true)
    private val offlineRepository: com.raulshma.jellyplay.core.data.repository.OfflineRepository = mockk(relaxed = true)
    private val downloadRepository: com.raulshma.jellyplay.core.data.repository.DownloadRepository = mockk(relaxed = true)
    private val episodeCatalogue: EpisodeCatalogue = mockk(relaxed = true)
    private val playbackSourceResolver: PlaybackSourceResolver = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk()
    private val localStreamProbe: LocalStreamProbe = mockk(relaxed = true)

    private fun TestScope.buildProvider() = UnifiedMediaDetailProviderImpl(
        mediaRepository = mediaRepository,
        cacheInvalidation = cacheInvalidation,
        offlineRepository = offlineRepository,
        downloadRepository = downloadRepository,
        episodeCatalogue = episodeCatalogue,
        playbackSourceResolver = playbackSourceResolver,
        offlineModeManager = offlineModeManager,
        localStreamProbe = localStreamProbe,
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
        coEvery { downloadRepository.loadLocalSubtitleManifest(any(), any()) } returns null
        // Default: no probed local tracks → no synthesized media source (preserves
        // the pre-feature local-snapshot shape). Per-test stubs override this.
        coEvery { localStreamProbe.probe(any()) } returns emptyList()
    }

    private suspend fun firstResolved(provider: MediaDetailProvider, itemId: String): DetailLoadState =
        provider.observe(itemId).first { it !is DetailLoadState.Loading }

    @Test
    fun `remote wins over a completed download while online`() = runTest {
        wireStubs("m1", mode = OfflineMode.ONLINE, localItem = localMovie(), download = completedDownload())
        coEvery { mediaRepository.getMediaDetail("m1", any()) } returns Result.success(movieDetail())

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
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
    }

    @Test
    fun `remote failure falls back in place to local detail`() = runTest {
        wireStubs("m1", mode = OfflineMode.ONLINE, localItem = localMovie())
        coEvery { mediaRepository.getMediaDetail("m1", any()) } returns Result.failure(
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
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
    }

    @Test
    fun `remote failure with no local row preserves access-denied classification`() = runTest {
        wireStubs("m1", mode = OfflineMode.ONLINE, localItem = null)
        coEvery { mediaRepository.getMediaDetail("m1", any()) } returns Result.failure(
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
        coEvery { downloadRepository.loadLocalSubtitleManifest(any(), any()) } returns OfflineSubtitleManifest(
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
        assertFalse(snapshot.capabilities.localStreamInfo) // probe returned empty (relaxed mock)
        assertFalse(snapshot.capabilities.remoteStreamSelection)
        assertFalse(snapshot.capabilities.personNavigation)
        assertFalse(snapshot.capabilities.studioNavigation)
        assertFalse(snapshot.capabilities.remoteDiscovery)
    }

    @Test
    fun `local snapshot injects probed streams and advertises localStreamInfo`() = runTest {
        val local = localMovie()
        wireStubs("m1", mode = OfflineMode.OFFLINE_MANUAL, localItem = local, download = completedDownload())
        // Probe returns the file's real tracks (a 4K HDR video + a 5.1 audio).
        coEvery { localStreamProbe.probe(local.downloadPath!!) } returns listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, height = 2160, videoRangeType = "HDR"),
            MediaStream(index = 1, type = StreamType.AUDIO, language = "eng", channels = 6),
        )

        val state = firstResolved(buildProvider(), "m1")

        val snapshot = (state as DetailLoadState.Loaded).snapshot
        // The probed tracks are surfaced as a synthesized media source so the
        // read-only quality/audio badges can render from the same data path the
        // remote MediaInfoSection uses.
        assertEquals(1, snapshot.detail.mediaSources.size)
        val source = snapshot.detail.mediaSources.first()
        assertEquals(2, source.mediaStreams.size)
        assertTrue(snapshot.capabilities.localStreamInfo)
        // Audio is still switched in the player, not on the detail screen.
        assertFalse(snapshot.capabilities.remoteStreamSelection)
        coVerify(exactly = 1) { localStreamProbe.probe(local.downloadPath!!) }
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
        coEvery { downloadRepository.loadLocalSubtitleManifest(any(), any()) } returns null

        var attempts = 0
        coEvery { mediaRepository.getMediaDetail("m1", any()) } answers {
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
        coVerify(atLeast = 2) { mediaRepository.getMediaDetail("m1", any()) }
        job.cancel()
    }

    // ── New seam methods: optimistic rewrite, expand, canonical ids,
    //    invalidate. These are the first tests to exercise a SERIES through the
    //    provider — the 7 tests above all use movies. ──────────────────────

    @Test
    fun `applyOptimisticSeasonRewrite re-emits rewritten episodes and invalidates the series`() = runTest {
        val snapshot = seriesCatalogueSnapshot()
        wireStubs("s1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("s1", any()) } returns Result.success(seriesDetail())
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
    fun `applyOptimisticSeasonRewrite recomputes the series header when all seasons are loaded`() = runTest {
        // The series header (isPlayed / unplayedItemCount) is derived from the
        // rebuilt full episode set so a mark-season flip shows immediately on the
        // current screen, not only after the next re-entry re-resolve.
        val snapshot = seriesCatalogueSnapshot() // 1 season, 2 episodes, all unplayed
        wireStubs("s1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("s1", any()) } returns Result.success(seriesDetail())
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(snapshot)
        val rewritten = snapshot.copy(
            episodesBySeason = snapshot.episodesBySeason.mapValues { (_, eps) ->
                eps.map { it.copy(isPlayed = true) }
            },
            sortedEpisodes = snapshot.sortedEpisodes.map { it.copy(isPlayed = true) },
        )
        coEvery { episodeCatalogue.updateSeasonEpisodes("s1", "season1", any()) } returns rewritten

        val provider = buildProvider()
        val states = mutableListOf<DetailLoadState>()
        val job = launch { provider.observe("s1").collect { states += it } }
        advanceUntilIdle()

        provider.applyOptimisticSeasonRewrite("s1", "season1") { episodes ->
            episodes.map { it.copy(isPlayed = true) }
        }
        advanceUntilIdle()

        val after = states.filterIsInstance<DetailLoadState.Loaded>().last()
        // Every episode played → series header reflects fully-watched.
        assertTrue(after.snapshot.detail.item.isPlayed)
        assertEquals(0, after.snapshot.detail.item.unplayedItemCount)
        job.cancel()
    }

    @Test
    fun `applyOptimisticSeasonRewrite leaves the series header alone when seasons are partially loaded`() = runTest {
        // Two seasons, only one fetched: a recompute from the partial episode set
        // would push a misleading unplayed count, so the header must stay untouched
        // (the next re-entry re-resolve reconciles it authoritatively).
        val full = seriesCatalogueSnapshot(seasonIds = listOf("season1", "season2"), episodesPerSeason = 2)
        val partial = full.copy(fetchedSeasonIds = setOf("season1"))
        wireStubs("s1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("s1", any()) } returns Result.success(seriesDetail())
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(partial)
        val rewritten = partial.copy(
            episodesBySeason = partial.episodesBySeason.mapValues { (_, eps) ->
                eps.map { it.copy(isPlayed = true) }
            },
            sortedEpisodes = partial.sortedEpisodes.map { it.copy(isPlayed = true) },
        )
        coEvery { episodeCatalogue.updateSeasonEpisodes("s1", "season1", any()) } returns rewritten

        val provider = buildProvider()
        val states = mutableListOf<DetailLoadState>()
        val job = launch { provider.observe("s1").collect { states += it } }
        advanceUntilIdle()

        provider.applyOptimisticSeasonRewrite("s1", "season1") { episodes ->
            episodes.map { it.copy(isPlayed = true) }
        }
        advanceUntilIdle()

        val after = states.filterIsInstance<DetailLoadState.Loaded>().last()
        // Header unchanged: the default series detail started isPlayed=false /
        // unplayedItemCount=null and must remain so for a partial load.
        assertFalse(after.snapshot.detail.item.isPlayed)
        assertNull(after.snapshot.detail.item.unplayedItemCount)
        job.cancel()
    }

    @Test
    fun `applyOptimisticSeasonRewrite is a no-op when the season has no episodes`() = runTest {
        val snapshot = seriesCatalogueSnapshot(seasonIds = listOf("season1"))
        wireStubs("s1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("s1", any()) } returns Result.success(seriesDetail())
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
        coEvery { mediaRepository.getMediaDetail("s1", any()) } returns Result.success(seriesDetail())
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
    fun `expandSeason merges an empty season, updates fetchedSeasonIds, and re-emits`() = runTest {
        val initial = seriesCatalogueSnapshot(seasonIds = listOf("season1"), episodesPerSeason = 2)
        wireStubs("s1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("s1", any()) } returns Result.success(seriesDetail())
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(initial)
        coEvery { episodeCatalogue.loadSeasonEpisodes("s1", "season2", any()) } returns Result.success(emptyList())

        val provider = buildProvider()
        val states = mutableListOf<DetailLoadState>()
        val job = launch { provider.observe("s1").collect { states += it } }
        advanceUntilIdle()

        val before = states.filterIsInstance<DetailLoadState.Loaded>().last()
        assertFalse(before.snapshot.fetchedSeasonIds.contains("season2"))

        val returned = provider.expandSeason("s1", "season2")
        advanceUntilIdle()

        assertTrue(returned.isEmpty())
        val after = states.filterIsInstance<DetailLoadState.Loaded>().last()
        assertTrue("season2 must be marked as fetched", after.snapshot.fetchedSeasonIds.contains("season2"))
        assertEquals(emptyList<MediaItem>(), after.snapshot.episodesBySeason["season2"])
        job.cancel()
    }

    @Test
    fun `canonicalEpisodeIds serves from a loaded session without a cold load`() = runTest {
        val snapshot = seriesCatalogueSnapshot()
        wireStubs("s1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("s1", any()) } returns Result.success(seriesDetail())
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

    // ── Attachment derivation: totalSize + createdAt provenance ───────────────
    //
    // buildAttachment derives totalSizeBytes from the download row, falling back
    // to the local OfflineMediaItem when the download row reports <= 0 (a known
    // gap for some legacy rows), and sources createdAtEpochMillis exclusively
    // from the local row (DownloadItem carries no creation timestamp).

    @Test
    fun `attachment totalSizeBytes falls back to local row when download reports zero`() = runTest {
        val local = localMovie().copy(totalSizeBytes = 5_000L)
        val download = completedDownload().copy(totalSizeBytes = 0L, downloadedBytes = 0L)
        wireStubs("m1", mode = OfflineMode.ONLINE, localItem = local, download = download)
        coEvery { mediaRepository.getMediaDetail("m1", any()) } returns Result.success(movieDetail())

        val snapshot = (firstResolved(buildProvider(), "m1") as DetailLoadState.Loaded).snapshot

        assertEquals(5_000L, snapshot.context.download?.totalSizeBytes)
    }

    @Test
    fun `attachment totalSizeBytes prefers the download row when it is positive`() = runTest {
        val local = localMovie().copy(totalSizeBytes = 5_000L)
        val download = completedDownload().copy(totalSizeBytes = 3_000L, downloadedBytes = 3_000L)
        wireStubs("m1", mode = OfflineMode.ONLINE, localItem = local, download = download)
        coEvery { mediaRepository.getMediaDetail("m1", any()) } returns Result.success(movieDetail())

        val snapshot = (firstResolved(buildProvider(), "m1") as DetailLoadState.Loaded).snapshot

        assertEquals(3_000L, snapshot.context.download?.totalSizeBytes)
    }

    @Test
    fun `attachment createdAtEpochMillis is sourced from the local row`() = runTest {
        val local = localMovie().copy(createdAt = 1_700_000_000_000L)
        wireStubs("m1", mode = OfflineMode.ONLINE, localItem = local, download = completedDownload())
        coEvery { mediaRepository.getMediaDetail("m1", any()) } returns Result.success(movieDetail())

        val snapshot = (firstResolved(buildProvider(), "m1") as DetailLoadState.Loaded).snapshot

        assertEquals(1_700_000_000_000L, snapshot.context.download?.createdAtEpochMillis)
    }

    @Test
    fun `attachment createdAtEpochMillis is zero when no local row exists`() = runTest {
        // A remote-only item (no local row) attaches a download but has no
        // creation timestamp to source — must default to 0, never a fake value.
        wireStubs("m1", mode = OfflineMode.ONLINE, localItem = null, download = completedDownload())
        coEvery { mediaRepository.getMediaDetail("m1", any()) } returns Result.success(movieDetail())

        val snapshot = (firstResolved(buildProvider(), "m1") as DetailLoadState.Loaded).snapshot

        assertEquals(0L, snapshot.context.download?.createdAtEpochMillis)
    }

    // ── Album tracks: the online/offline fork at the album-children level ────

    private fun albumDetail(id: String = "a1") = MediaDetail(
        item = MediaItem(id = id, name = "Album", mediaType = MediaType.ALBUM),
    )

    @Test
    fun `remote ALBUM snapshot loads album tracks from the server`() = runTest {
        wireStubs("a1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("a1", any()) } returns Result.success(albumDetail())
        val tracks = listOf(
            MediaItem(id = "t1", name = "Track 1", mediaType = MediaType.AUDIO),
            MediaItem(id = "t2", name = "Track 2", mediaType = MediaType.AUDIO),
        )
        coEvery { mediaRepository.getAlbumTracks("a1") } returns Result.success(tracks)

        val snapshot = (firstResolved(buildProvider(), "a1") as DetailLoadState.Loaded).snapshot

        assertEquals(tracks.map { it.id }, snapshot.albumTracks.map { it.id })
        coVerify(exactly = 0) { offlineRepository.getChildren(any()) }
    }

    @Test
    fun `local ALBUM snapshot loads album tracks from offline children`() = runTest {
        val localAlbum = OfflineMediaItem(
            id = "a1",
            name = "Album",
            mediaType = MediaType.ALBUM,
            downloadPath = "/data/offline/a1/album",
        )
        wireStubs("a1", mode = OfflineMode.OFFLINE_MANUAL, localItem = localAlbum)
        val child = OfflineMediaItem(
            id = "t1",
            name = "Track 1",
            mediaType = MediaType.AUDIO,
            downloadPath = "/data/offline/a1/t1",
        )
        every { offlineRepository.getChildren("a1") } returns MutableStateFlow(listOf(child))

        val snapshot = (firstResolved(buildProvider(), "a1") as DetailLoadState.Loaded).snapshot

        assertEquals(listOf("t1"), snapshot.albumTracks.map { it.id })
        coVerify(exactly = 0) { mediaRepository.getAlbumTracks(any()) }
    }

    // ── Local SERIES aggregate + per-episode artwork ─────────────────────────
    //
    // publishLocal derives the series header ("N episodes · size") and the
    // per-episode image map from a single pass over the raw OfflineMediaItem
    // rows (the catalogue's MediaItem projection drops posterPath/totalSizeBytes).

    @Test
    fun `local SERIES snapshot carries aggregate counts and per-episode artwork`() = runTest {
        val localSeries = OfflineMediaItem(
            id = "s1",
            name = "Series",
            mediaType = MediaType.SERIES,
            downloadPath = "/data/offline/s1/series",
        )
        wireStubs("s1", mode = OfflineMode.OFFLINE_MANUAL, localItem = localSeries)
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(
            seriesCatalogueSnapshot(seriesId = "s1", seasonIds = listOf("season1"), episodesPerSeason = 0),
        )
        val seasonRow = OfflineMediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON)
        val epRow = OfflineMediaItem(
            id = "season1-ep1",
            name = "Pilot",
            mediaType = MediaType.EPISODE,
            posterPath = "/data/offline/s1/ep1.jpg",
            totalSizeBytes = 2_000L,
        )
        every { offlineRepository.getSeasonsForSeries("s1") } returns MutableStateFlow(listOf(seasonRow))
        every { offlineRepository.getEpisodesForSeason("season1") } returns MutableStateFlow(listOf(epRow))

        val snapshot = (firstResolved(buildProvider(), "s1") as DetailLoadState.Loaded).snapshot

        val aggregate = snapshot.context.seriesAggregate
        assertNotNull(aggregate)
        assertEquals(1, aggregate!!.downloadedEpisodeCount)
        assertEquals(2_000L, aggregate.totalSizeBytes)
        // The catalogue projection drops posterPath; the artwork map carries it.
        assertEquals("/data/offline/s1/ep1.jpg", snapshot.assets.episodeImages["season1-ep1"])
    }

    @Test
    fun `local SERIES aggregate episodeSizeBytes is keyed by episode id and sums to totalSizeBytes`() = runTest {
        // The per-episode size map rides alongside the aggregate so the
        // delete-downloaded-episodes sheet can report an exact freed-space figure
        // for partial selections, not just a whole-series total.
        val localSeries = OfflineMediaItem(
            id = "s1",
            name = "Series",
            mediaType = MediaType.SERIES,
            downloadPath = "/data/offline/s1/series",
        )
        wireStubs("s1", mode = OfflineMode.OFFLINE_MANUAL, localItem = localSeries)
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(
            seriesCatalogueSnapshot(seriesId = "s1", seasonIds = listOf("season1"), episodesPerSeason = 0),
        )
        val seasonRow = OfflineMediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON)
        val ep1 = OfflineMediaItem(
            id = "season1-ep1",
            name = "Pilot",
            mediaType = MediaType.EPISODE,
            totalSizeBytes = 2_000L,
        )
        val ep2 = OfflineMediaItem(
            id = "season1-ep2",
            name = "E2",
            mediaType = MediaType.EPISODE,
            totalSizeBytes = 3_000L,
        )
        every { offlineRepository.getSeasonsForSeries("s1") } returns MutableStateFlow(listOf(seasonRow))
        every { offlineRepository.getEpisodesForSeason("season1") } returns MutableStateFlow(listOf(ep1, ep2))

        val snapshot = (firstResolved(buildProvider(), "s1") as DetailLoadState.Loaded).snapshot

        val aggregate = snapshot.context.seriesAggregate
        assertNotNull(aggregate)
        val episodeSizes = aggregate!!.episodeSizeBytes
        // Keyed by episode id with each episode's on-disk size.
        assertEquals(2_000L, episodeSizes["season1-ep1"])
        assertEquals(3_000L, episodeSizes["season1-ep2"])
        // The per-episode values sum to the aggregate total — same single pass
        // over the offline rows, so the two stay consistent.
        assertEquals(aggregate.totalSizeBytes, episodeSizes.values.sum())
    }

    @Test
    fun `local non-series snapshot carries no series aggregate`() = runTest {
        // A local MOVIE is not a series — the aggregate is null even with a row.
        wireStubs("m1", mode = OfflineMode.OFFLINE_MANUAL, localItem = localMovie())

        val snapshot = (firstResolved(buildProvider(), "m1") as DetailLoadState.Loaded).snapshot

        assertNull(snapshot.context.seriesAggregate)
    }

    // ── Capability derivation: studio navigation + smart-play ────────────────

    @Test
    fun `remote detail with studios advertises studio navigation`() = runTest {
        wireStubs("m1", mode = OfflineMode.ONLINE)
        val withStudios = movieDetail().copy(
            studios = listOf(com.raulshma.jellyplay.core.model.StudioInfo(name = "Studio A", id = "st-1")),
        )
        coEvery { mediaRepository.getMediaDetail("m1", any()) } returns Result.success(withStudios)

        val snapshot = (firstResolved(buildProvider(), "m1") as DetailLoadState.Loaded).snapshot

        // studioNavigation requires REMOTE origin AND a non-empty studios list.
        assertTrue(snapshot.capabilities.studioNavigation)
    }

    @Test
    fun `local origin suppresses smart-play capability`() = runTest {
        // smartPlay is derived from the remote origin; a local projection must
        // not advertise it even for a series with episodes.
        wireStubs("m1", mode = OfflineMode.OFFLINE_MANUAL, localItem = localMovie())

        val snapshot = (firstResolved(buildProvider(), "m1") as DetailLoadState.Loaded).snapshot

        assertFalse(snapshot.capabilities.smartPlay)
        assertFalse(snapshot.capabilities.remoteDiscovery)
    }

    // ── refresh() forces a re-resolution and invalidates the detail cache ────

    @Test
    fun `refresh re-resolves and invalidates the detail cache`() = runTest {
        wireStubs("m1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("m1", any()) } returns Result.success(movieDetail())

        val provider = buildProvider()
        val job = launch { provider.observe("m1").collect { } }
        advanceUntilIdle()

        // The initial resolution (lastTick starts at -1) already force-resolved
        // once; clear the call log so the assertion isolates the refresh's own
        // forced re-resolution. Stub answers are preserved.
        clearMocks(mediaRepository, answers = false, recordedCalls = true, childMocks = false)

        provider.refresh("m1")
        advanceUntilIdle()

        // refresh forces a re-resolution → the force-read seam bypasses the
        // detail cache + the repo-internal per-type dispatch runs.
        coVerify(atLeast = 1) { mediaRepository.getMediaDetail("m1", force = true) }
        job.cancel()
    }

    // ── Re-entry re-resolution: a re-attached observer reuses the session but
    //    must NOT replay stale state — it forces a fresh resolve. This is the
    //    fix for the Activity-scoped DetailViewModel replaying a pre-mutation
    //    snapshot on back→home→detail. ───────────────────────────────────────

    @Test
    fun `re-observing an already-loaded item forces a re-resolve instead of replaying stale state`() = runTest {
        wireStubs("m1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("m1", any()) } returns Result.success(movieDetail())

        val provider = buildProvider()
        val firstJob = launch { provider.observe("m1").collect { } }
        advanceUntilIdle()

        // Initial entry force-resolved once; clear the call log so the assertions
        // isolate the second observer's re-resolve. Stub answers are preserved.
        clearMocks(mediaRepository, answers = false, recordedCalls = true, childMocks = false)

        // A second observer on the same item reuses the existing session (the
        // Activity-scoped VM pattern: back→home→detail). The reused session must
        // re-resolve rather than replay its cached snapshot.
        val secondJob = launch { provider.observe("m1").collect { } }
        advanceUntilIdle()

        coVerify(atLeast = 1) { mediaRepository.getMediaDetail("m1", force = true) }
        firstJob.cancel()
        secondJob.cancel()
    }

    // ── expandSeason idempotency ─────────────────────────────────────────────

    @Test
    fun `expandSeason is idempotent for an already-fetched season`() = runTest {
        val initial = seriesCatalogueSnapshot(seasonIds = listOf("season1"), episodesPerSeason = 2)
        wireStubs("s1", mode = OfflineMode.ONLINE)
        coEvery { mediaRepository.getMediaDetail("s1", any()) } returns Result.success(seriesDetail())
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(initial)
        coEvery { episodeCatalogue.loadSeasonEpisodes("s1", "season2", any()) } returns Result.success(
            listOf(
                MediaItem(id = "season2-ep1", name = "S2 E1", mediaType = MediaType.EPISODE, seriesId = "s1", seasonId = "season2"),
            ),
        )

        val provider = buildProvider()
        val states = mutableListOf<DetailLoadState>()
        val job = launch { provider.observe("s1").collect { states += it } }
        advanceUntilIdle()

        provider.expandSeason("s1", "season2")
        advanceUntilIdle()
        val genAfterFirst = states.filterIsInstance<DetailLoadState.Loaded>().last().snapshot.contentGeneration

        // Re-expanding the now-fetched season returns the same episodes but must
        // NOT bump the content generation (no spurious re-emission).
        provider.expandSeason("s1", "season2")
        advanceUntilIdle()
        val genAfterSecond = states.filterIsInstance<DetailLoadState.Loaded>().last().snapshot.contentGeneration

        assertEquals(genAfterFirst, genAfterSecond)
        job.cancel()
    }

    // ── canonicalEpisodeIds cold-load failure ────────────────────────────────

    @Test
    fun `canonicalEpisodeIds returns empty when the cold load fails`() = runTest {
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.failure(RuntimeException("net"))

        val ids = buildProvider().canonicalEpisodeIds("s1")

        assertTrue(ids.isEmpty())
    }

    // ── Inactive-session no-ops for the seam methods ─────────────────────────
    //
    // applyOptimisticSeasonRewrite / expandSeason early-return when no session is
    // active for the item (the server mutation still lands; the next load picks
    // up the post-cascade state). They must not touch the catalogue.

    @Test
    fun `expandSeason without an active session returns empty and skips the catalogue`() = runTest {
        val ids = buildProvider().expandSeason("s1", "season1")

        assertTrue(ids.isEmpty())
        coVerify(exactly = 0) { episodeCatalogue.loadSeasonEpisodes(any(), any(), any()) }
    }

    @Test
    fun `applyOptimisticSeasonRewrite without an active session is a no-op`() = runTest {
        buildProvider().applyOptimisticSeasonRewrite("s1", "season1") { it }

        coVerify(exactly = 0) { episodeCatalogue.updateSeasonEpisodes(any(), any(), any()) }
        coVerify(exactly = 0) { episodeCatalogue.invalidateSeries(any()) }
    }
}
