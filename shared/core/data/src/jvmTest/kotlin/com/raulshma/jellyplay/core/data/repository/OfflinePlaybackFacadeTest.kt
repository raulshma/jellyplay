package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [OfflinePlaybackFacade]'s download-vs-offline routing — the player's
 * single offline vocabulary:
 *  1. progress recording always goes to the offline store (no-op semantics for
 *     non-downloaded items stay the DAO's concern);
 *  2. `deleteDownload` returns true only when a download row existed;
 *  3. `getResumePositionTicks` honors ONLY COMPLETED downloads with a strictly
 *     positive recorded position — anything else seeds the player at 0;
 *  4. `getDownloadPath` passes the on-disk path through (null without a row);
 *  5. `loadSegments` surfaces the bundled local segment cache (null = fall
 *     back to the server).
 */
class OfflinePlaybackFacadeTest {

    private lateinit var downloadRepository: DownloadRepository
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var facade: OfflinePlaybackFacade

    @BeforeTest
    fun setup() {
        downloadRepository = mockk()
        offlineRepository = mockk(relaxed = true)
        facade = OfflinePlaybackFacade(downloadRepository, offlineRepository)
    }

    @Test
    fun `progress recording routes to the offline store`() = runTest {
        facade.recordProgress(ITEM, positionTicks = 500L, percentage = 25.0, isPlayed = false)

        coVerify(exactly = 1) {
            offlineRepository.updatePlaybackProgress(ITEM, 500L, 25.0, false)
        }
        coVerify(exactly = 0) { downloadRepository.getDownloadByMediaItemId(any()) }
    }

    @Test
    fun `recordPlayed marks the item fully watched offline`() = runTest {
        facade.recordPlayed(ITEM)

        coVerify(exactly = 1) {
            offlineRepository.updatePlaybackProgress(ITEM, null, 100.0, true)
        }
    }

    @Test
    fun `deleteDownload returns true and deletes only when a row exists`() = runTest {
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM) } returns downloadRow(DownloadStatus.COMPLETED)
        coEvery { downloadRepository.deleteDownload(DOWNLOAD_ID) } returns Result.success(Unit)

        assertTrue(facade.deleteDownload(ITEM))
        coVerify(exactly = 1) { downloadRepository.deleteDownload(DOWNLOAD_ID) }
    }

    @Test
    fun `deleteDownload is a no-op returning false without a download`() = runTest {
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM) } returns null

        assertFalse(facade.deleteDownload(ITEM))
        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
    }

    @Test
    fun `resume position comes only from a completed download with a positive position`() = runTest {
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM) } returns
            downloadRow(status = DownloadStatus.COMPLETED)
        coEvery { offlineRepository.getOfflineItem(ITEM) } returns
            OfflineMediaItem(
                id = ITEM,
                name = "Movie",
                mediaType = MediaType.MOVIE,
                playbackPositionTicks = 600_000_000L,
            )

        assertEquals(600_000_000L, facade.getResumePositionTicks(ITEM))
    }

    @Test
    fun `an in-progress download never seeds a resume position`() = runTest {
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM) } returns
            downloadRow(status = DownloadStatus.DOWNLOADING)
        coEvery { offlineRepository.getOfflineItem(ITEM) } returns
            OfflineMediaItem(
                id = ITEM,
                name = "Movie",
                mediaType = MediaType.MOVIE,
                playbackPositionTicks = 600_000_000L,
            )

        assertEquals(0L, facade.getResumePositionTicks(ITEM))
    }

    @Test
    fun `a zero or missing offline position seeds the player at 0`() = runTest {
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM) } returns
            downloadRow(status = DownloadStatus.COMPLETED)
        coEvery { offlineRepository.getOfflineItem(ITEM) } returns
            OfflineMediaItem(
                id = ITEM,
                name = "Movie",
                mediaType = MediaType.MOVIE,
                playbackPositionTicks = 0L,
            )

        assertEquals(0L, facade.getResumePositionTicks(ITEM))
    }

    @Test
    fun `a missing download row has no resume position and no path`() = runTest {
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM) } returns null

        assertEquals(0L, facade.getResumePositionTicks(ITEM))
        assertNull(facade.getDownloadPath(ITEM))
    }

    @Test
    fun `loadSegments surfaces the bundled local segment cache`() = runTest {
        val segments = listOf(
            MediaSegment(
                id = "s1",
                itemId = ITEM,
                type = MediaSegmentType.INTRO,
                startTicks = 0L,
                endTicks = 50_000_000L,
            ),
        )
        coEvery { downloadRepository.loadLocalSegments(ITEM) } returns segments

        assertEquals(segments, facade.loadSegments(ITEM))
    }

    @Test
    fun `loadSegments returns null when no local cache exists`() = runTest {
        coEvery { downloadRepository.loadLocalSegments(ITEM) } returns null

        assertNull(facade.loadSegments(ITEM))
    }

    private fun downloadRow(status: DownloadStatus) = DownloadItem(
        id = DOWNLOAD_ID,
        mediaItemId = ITEM,
        name = "Movie",
        mediaType = MediaType.MOVIE,
        downloadPath = "/downloads/item1/video.mkv",
        downloadUrl = "https://server/video",
        totalSizeBytes = 1000L,
        downloadedBytes = 1000L,
        status = status,
    )

    private companion object {
        const val ITEM = "item-1"
        const val DOWNLOAD_ID = "dl-1"
    }
}
