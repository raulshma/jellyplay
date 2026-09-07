package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.data.util.TimeSource
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests [OfflineRepositoryImpl.applyPlayedState] — the wrapper around the
 * batch hierarchy UPDATE that mirrors Jellyfin's markPlayed/markUnplayed
 * cascade into the local offline store.
 *
 * The DAO is mocked; the SQL cascade itself is covered by
 * [com.raulshma.jellyplay.core.database.dao.OfflineMediaDaoTest]. Here we
 * verify the repository stamps `lastPlayedDate` correctly on play vs. unplay
 * and forwards the right args. The stamp comes from the injected [TimeSource],
 * so a fake clock pins it exactly (no real `now()` reads).
 */
class OfflineRepositoryApplyPlayedStateTest {

    /** Fixed wall-clock read the stamps are asserted against. */
    private val fakeNowMillis = 1_770_000_000_000L

    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)

    private val repository by lazy {
        OfflineRepositoryImpl(
            offlineMediaDao,
            playbackStateDao,
            syncBaselineDao,
            downloadDao,
            database,
            timeSource = object : TimeSource {
                override fun nowEpochMillis(): Long = fakeNowMillis
                override fun nowElapsedRealtimeMillis(): Long = fakeNowMillis
                override fun today(zone: ZoneId): java.time.LocalDate = java.time.LocalDate.of(2026, 2, 1)
            },
        )
    }

    /** The exact stamp the repository must produce for [fakeNowMillis] (same conversion as the impl). */
    private val fakeNowStamp: String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(fakeNowMillis), ZoneId.systemDefault()).toString()

    @Test
    fun `applyPlayedState true stamps lastPlayedDate with the injected clock's now`() = runTest {
        repository.applyPlayedState("season-1", isPlayed = true)

        coVerify(exactly = 1) {
            playbackStateDao.applyPlayedStateToHierarchy(
                itemId = "season-1",
                isPlayed = true,
                lastPlayedDate = fakeNowStamp,
            )
        }
    }

    @Test
    fun `applyPlayedState false clears lastPlayedDate and forwards isPlayed false`() = runTest {
        repository.applyPlayedState("season-1", isPlayed = false)

        coVerify(exactly = 1) {
            playbackStateDao.applyPlayedStateToHierarchy(
                itemId = "season-1",
                isPlayed = false,
                lastPlayedDate = null,
            )
        }
    }

    @Test
    fun `applyPlayedState works for episode itemId`() = runTest {
        repository.applyPlayedState("episode-42", isPlayed = true)

        coVerify { playbackStateDao.applyPlayedStateToHierarchy("episode-42", true, any()) }
    }

    @Test
    fun `applyPlayedState works for series itemId`() = runTest {
        repository.applyPlayedState("series-7", isPlayed = false)

        coVerify { playbackStateDao.applyPlayedStateToHierarchy("series-7", false, null) }
    }
}
