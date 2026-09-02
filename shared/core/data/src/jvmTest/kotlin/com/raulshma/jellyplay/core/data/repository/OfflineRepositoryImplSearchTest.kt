package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaWithPlayback
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Unit tests for [OfflineRepositoryImpl.searchOffline]. Verifies query
 * trimming/length guard, LIKE-pattern escaping, and result mapping.
 *
 * The DAO is mocked — the SQL itself is exercised by Room's integration test
 * surface. Here we verify the repository contract.
 */
class OfflineRepositoryImplSearchTest {

    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)

    private lateinit var repository: OfflineRepositoryImpl

    private val matrixEntity = OfflineMediaEntity(
        id = "movie-1",
        name = "The Matrix",
        mediaType = "MOVIE",
        year = 1999,
    )
    private val matrixReloadedEntity = OfflineMediaEntity(
        id = "movie-2",
        name = "The Matrix Reloaded",
        mediaType = "MOVIE",
        year = 2003,
    )
    private val pinkFloydEntity = OfflineMediaEntity(
        id = "album-1",
        name = "The Dark Side of the Moon",
        mediaType = "ALBUM",
        seriesName = "Pink Floyd",
    )

    @BeforeTest
    fun setup() {
        repository = OfflineRepositoryImpl(offlineMediaDao, playbackStateDao, syncBaselineDao, downloadDao, database)
    }

    @Test
    fun `blank query returns empty list and never hits the dao`() = runTest {
        val result = repository.searchOffline("   ")

        assertTrue(result.isEmpty())
        io.mockk.coVerify(exactly = 0) { offlineMediaDao.search(any(), any(), any()) }
    }

    @Test
    fun `single-character query returns empty list`() = runTest {
        val result = repository.searchOffline("M")

        assertTrue(result.isEmpty())
        io.mockk.coVerify(exactly = 0) { offlineMediaDao.search(any(), any(), any()) }
    }

    @Test
    fun `non-positive limit returns empty list`() = runTest {
        val result = repository.searchOffline("Matrix", limit = 0)

        assertTrue(result.isEmpty())
        io.mockk.coVerify(exactly = 0) { offlineMediaDao.search(any(), any(), any()) }
    }

    @Test
    fun `valid query builds substring pattern and maps results`() = runTest {
        coEvery {
            offlineMediaDao.search(pattern = "%Matrix%", prefixPattern = "Matrix%", limit = 20)
        } returns listOf(
            OfflineMediaWithPlayback(media = matrixEntity, playbackPositionTicks = null, playedPercentage = null, isPlayed = null, isFavorite = null, lastPlayedDate = null),
            OfflineMediaWithPlayback(media = matrixReloadedEntity, playbackPositionTicks = null, playedPercentage = null, isPlayed = null, isFavorite = null, lastPlayedDate = null),
        )

        val result = repository.searchOffline("Matrix")

        assertEquals(2, result.size)
        assertEquals("movie-1", result[0].id)
        assertEquals("The Matrix", result[0].name)
        assertEquals(MediaType.MOVIE, result[0].mediaType)
        assertEquals("movie-2", result[1].id)
    }

    @Test
    fun `query is trimmed before pattern building`() = runTest {
        coEvery {
            offlineMediaDao.search(pattern = "%Matrix%", prefixPattern = "Matrix%", limit = 10)
        } returns listOf(OfflineMediaWithPlayback(media = matrixEntity, playbackPositionTicks = null, playedPercentage = null, isPlayed = null, isFavorite = null, lastPlayedDate = null))

        repository.searchOffline("  Matrix  ", limit = 10)

        io.mockk.coVerify { offlineMediaDao.search(pattern = "%Matrix%", prefixPattern = "Matrix%", limit = 10) }
    }

    @Test
    fun `percent sign in query is escaped`() = runTest {
        repository.searchOffline("50%")

        io.mockk.coVerify {
            offlineMediaDao.search(pattern = "%50\\%%", prefixPattern = "50\\%%", limit = 20)
        }
    }

    @Test
    fun `underscore in query is escaped`() = runTest {
        repository.searchOffline("spaced_out")

        io.mockk.coVerify {
            offlineMediaDao.search(pattern = "%spaced\\_out%", prefixPattern = "spaced\\_out%", limit = 20)
        }
    }

    @Test
    fun `backslash in query is escaped`() = runTest {
        repository.searchOffline("a\\b")

        io.mockk.coVerify {
            offlineMediaDao.search(pattern = "%a\\\\b%", prefixPattern = "a\\\\b%", limit = 20)
        }
    }

    @Test
    fun `unknown mediaType falls back to UNKNOWN`() = runTest {
        val weirdEntity = OfflineMediaEntity(
            id = "x",
            name = "Mystery",
            mediaType = "NOT_A_REAL_TYPE",
        )
        coEvery { offlineMediaDao.search(any(), any(), any()) } returns listOf(OfflineMediaWithPlayback(media = weirdEntity, playbackPositionTicks = null, playedPercentage = null, isPlayed = null, isFavorite = null, lastPlayedDate = null))

        val result = repository.searchOffline("Mystery")

        assertEquals(1, result.size)
        assertEquals(MediaType.UNKNOWN, result[0].mediaType)
    }

    @Test
    fun `genres string is split and trimmed`() = runTest {
        val entity = OfflineMediaEntity(
            id = "album-1",
            name = "The Dark Side of the Moon",
            mediaType = "ALBUM",
            genres = "Rock , Progressive, ",
        )
        coEvery { offlineMediaDao.search(any(), any(), any()) } returns listOf(OfflineMediaWithPlayback(media = entity, playbackPositionTicks = null, playedPercentage = null, isPlayed = null, isFavorite = null, lastPlayedDate = null))

        val result = repository.searchOffline("Dark")

        assertEquals(listOf("Rock", "Progressive"), result[0].genres)
    }
}
