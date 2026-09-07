package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueImpl
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.util.SystemTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Pins the DetailCacheGroup key-grammar invariant (the repo's file-private
 * detail/similar/tracks/themes cache cluster): invalidating an item must
 * evict EVERY cached shape of that item — every limit variant of its similar
 * items plus its theme songs and album tracks.
 *
 * This is the drift class the group exists to kill: the get keys and the
 * invalidation prefixes used to be hand-synced at two sites per key family
 * (the historical bug — `getSimilarItems` stored under
 * `similar_${id}_$limit` while the invalidation removed `similar_$id`, a
 * no-op that pinned every limit variant for the full TTL). Existing suites
 * cover the similar limit variants for `markPlayed`; these add the themes /
 * tracks families and the other `invalidateItem` entry points
 * (markUnplayed's user-data funnel, the force lever) plus the zero-coverage
 * wholesale theme-songs clear.
 *
 * Same fake/mock construction as [MediaRepositoryImplTest] (real HomeSession
 * + SessionCacheRegistry + EpisodeCatalogueImpl over a mocked apiClient;
 * this suite never switches identity).
 */
class MediaRepositoryDetailCacheGroupTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val homeSectionCacheDao: HomeSectionCacheDao = mockk(relaxed = true)
    private val playedStateSync: PlayedStateSync = mockk(relaxed = true)
    private val offlineRepository: OfflineRepository = mockk(relaxed = true)
    private val userDataRealtimeChannel: UserDataRealtimeChannel = mockk(relaxed = true)

    private lateinit var repository: MediaRepositoryImpl

    @BeforeTest
    fun setup() {
        every { apiClient.session } returns MutableStateFlow(null)
        coEvery { playedStateSync.flip(any(), any()) } returns Result.success(Unit)
        coEvery { playedStateSync.toggleFavorite(any()) } returns Result.success(true)
        val homeSession = HomeSession(
            apiClient,
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        val sessionCacheRegistry = SessionCacheRegistry(
            homeSession,
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        val episodeCatalogue = EpisodeCatalogueImpl(
            apiClient,
            offlineRepository,
            homeSession,
            sessionCacheRegistry,
        )
        repository = MediaRepositoryImpl(
            apiClient,
            homeSectionCacheDao,
            playedStateSync,
            episodeCatalogue,
            userDataRealtimeChannel,
            SystemTimeSource(),
            homeSession,
            sessionCacheRegistry,
        )
    }

    @Test
    fun `a user-data mutation evicts every similar limit variant plus theme songs and album tracks`() = runTest {
        coEvery { apiClient.getSimilarItems("item-1", any()) } returns Result.success(listOf(mediaItem("s1")))
        coEvery { apiClient.getThemeSongs("item-1") } returns Result.success(emptyList())
        coEvery { apiClient.getAlbumTracks("item-1") } returns Result.success(listOf(mediaItem("t1")))

        // Populate every cached shape of the item: two similar limit variants
        // (detail screen = 12, widget = 9), theme songs, album tracks.
        repository.getSimilarItems("item-1", limit = 12)
        repository.getSimilarItems("item-1", limit = 9)
        repository.getThemeSongs("item-1")
        repository.getAlbumTracks("item-1")
        coVerify(exactly = 1) { apiClient.getSimilarItems("item-1", 12) }
        coVerify(exactly = 1) { apiClient.getSimilarItems("item-1", 9) }
        coVerify(exactly = 1) { apiClient.getThemeSongs("item-1") }
        coVerify(exactly = 1) { apiClient.getAlbumTracks("item-1") }

        // The user-data funnel routes through the group's composite eviction
        // (tracks removed + full per-item invalidation).
        repository.markUnplayed("item-1")

        // Every shape of the item re-fetches — the eviction dropped all of
        // them (regression: a get key / evict prefix that stops agreeing
        // silently pins entries for the full TTL).
        repository.getSimilarItems("item-1", limit = 12)
        repository.getSimilarItems("item-1", limit = 9)
        repository.getThemeSongs("item-1")
        repository.getAlbumTracks("item-1")

        coVerify(exactly = 2) { apiClient.getSimilarItems("item-1", 12) }
        coVerify(exactly = 2) { apiClient.getSimilarItems("item-1", 9) }
        coVerify(exactly = 2) { apiClient.getThemeSongs("item-1") }
        coVerify(exactly = 2) { apiClient.getAlbumTracks("item-1") }
    }

    @Test
    fun `a forced detail re-read evicts the item's similar limit variants and theme songs`() = runTest {
        coEvery { apiClient.getMediaDetail("item-1") } returns Result.success(
            MediaDetail(item = mediaItem("item-1"))
        )
        coEvery { apiClient.getSimilarItems("item-1", any()) } returns Result.success(listOf(mediaItem("s1")))
        coEvery { apiClient.getThemeSongs("item-1") } returns Result.success(emptyList())

        repository.getMediaDetail("item-1")
        repository.getSimilarItems("item-1", limit = 12)
        repository.getSimilarItems("item-1", limit = 9)
        repository.getThemeSongs("item-1")

        // The force lever is the invalidate-then-read sequence — and the
        // invalidation half must cover the item's companion shapes too.
        repository.getMediaDetail("item-1", force = true)

        repository.getSimilarItems("item-1", limit = 12)
        repository.getSimilarItems("item-1", limit = 9)
        repository.getThemeSongs("item-1")

        coVerify(exactly = 2) { apiClient.getMediaDetail("item-1") }
        coVerify(exactly = 2) { apiClient.getSimilarItems("item-1", 12) }
        coVerify(exactly = 2) { apiClient.getSimilarItems("item-1", 9) }
        coVerify(exactly = 2) { apiClient.getThemeSongs("item-1") }
    }

    @Test
    fun `invalidateCaches clears theme songs wholesale`() = runTest {
        // Theme songs had zero wholesale coverage (similar/tracks are pinned
        // by MediaRepositoryImplTest) — the family that historically took
        // five edits to wire everywhere.
        coEvery { apiClient.getThemeSongs("item-1") } returns Result.success(emptyList())

        repository.getThemeSongs("item-1")
        coVerify(exactly = 1) { apiClient.getThemeSongs("item-1") }

        repository.invalidateCaches()

        repository.getThemeSongs("item-1")
        coVerify(exactly = 2) { apiClient.getThemeSongs("item-1") }
    }
}

private fun mediaItem(id: String) = MediaItem(
    id = id,
    name = id,
    mediaType = MediaType.MOVIE,
)
