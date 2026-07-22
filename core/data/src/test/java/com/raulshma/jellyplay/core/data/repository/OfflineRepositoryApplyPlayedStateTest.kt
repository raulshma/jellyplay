package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests [OfflineRepositoryImpl.applyPlayedState] — the wrapper around the
 * batch hierarchy UPDATE that mirrors Jellyfin's markPlayed/markUnplayed
 * cascade into the local offline store.
 *
 * The DAO is mocked; the SQL cascade itself is covered by
 * [com.raulshma.jellyplay.core.database.dao.OfflineMediaDaoTest]. Here we
 * verify the repository stamps `lastPlayedDate` correctly on play vs. unplay
 * and forwards the right args.
 */
class OfflineRepositoryApplyPlayedStateTest {

    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)

    private val repository by lazy {
        OfflineRepositoryImpl(offlineMediaDao, downloadDao, database)
    }

    @Test
    fun `applyPlayedState true stamps a lastPlayedDate and forwards isPlayed true`() = runTest {
        repository.applyPlayedState("season-1", isPlayed = true)

        coVerify(exactly = 1) {
            offlineMediaDao.applyPlayedStateToHierarchy(
                itemId = "season-1",
                isPlayed = true,
                lastPlayedDate = match { it.isNotBlank() },
            )
        }
    }

    @Test
    fun `applyPlayedState false clears lastPlayedDate and forwards isPlayed false`() = runTest {
        repository.applyPlayedState("season-1", isPlayed = false)

        coVerify(exactly = 1) {
            offlineMediaDao.applyPlayedStateToHierarchy(
                itemId = "season-1",
                isPlayed = false,
                lastPlayedDate = null,
            )
        }
    }

    @Test
    fun `applyPlayedState works for episode itemId`() = runTest {
        repository.applyPlayedState("episode-42", isPlayed = true)

        coVerify { offlineMediaDao.applyPlayedStateToHierarchy("episode-42", true, any()) }
    }

    @Test
    fun `applyPlayedState works for series itemId`() = runTest {
        repository.applyPlayedState("series-7", isPlayed = false)

        coVerify { offlineMediaDao.applyPlayedStateToHierarchy("series-7", false, null) }
    }
}
