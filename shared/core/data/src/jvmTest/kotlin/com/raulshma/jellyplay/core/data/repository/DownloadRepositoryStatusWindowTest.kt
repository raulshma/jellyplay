package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.download.TrackDownloadStatusWindow
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The repository-level pin that replaced the deleted
 * JvmTrackDownloadStatusWindowTest (the id-honesty pin the former player-audio
 * window test carried, ported once more when the promoted-interface pass made
 * [DownloadRepositoryImpl] the JVM [TrackDownloadStatusWindow] itself): the
 * player's window is the single now-playing track today, but `downloadsFor`
 * must answer for EVERY requested id, in the DAO's emission order, through
 * ONE `getDownloadsByMediaItemIdsFlow` IN-query — never N per-id flows (the
 * re-expressed N-flow shape the deleted JvmTrackDownloadStatusWindow adapter
 * carried is the divergence this pin keeps reverted), and never the whole
 * table.
 */
class DownloadRepositoryStatusWindowTest {

    private val downloadDao: DownloadDao = mockk(relaxed = true)

    private val window: TrackDownloadStatusWindow = DownloadRepositoryImpl(
        downloadDao = downloadDao,
        offlineMediaDao = mockk(relaxed = true),
        playbackStateDao = mockk(relaxed = true),
        syncBaselineDao = mockk(relaxed = true),
        database = mockk(relaxed = true),
        mediaRepository = mockk(relaxed = true),
        episodeCatalogue = mockk(relaxed = true),
        playbackRepository = mockk(relaxed = true),
        playbackIdentity = mockk(relaxed = true),
        httpClient = mockk(relaxed = true),
        downloadsStore = mockk(relaxed = true),
        json = mockk(relaxed = true),
        downloadDelegate = lazy { mockk(relaxed = true) },
        storagePolicy = mockk(relaxed = true),
        downloadEnqueuer = mockk(relaxed = true),
        storageLayout = mockk(relaxed = true),
        syncComparator = mockk(relaxed = true),
        progressNotifier = mockk(relaxed = true),
        imagePreloader = mockk(relaxed = true),
        timeSource = mockk(relaxed = true),
    )

    private fun entity(mediaItemId: String) = DownloadEntity(
        id = "download-$mediaItemId",
        mediaItemId = mediaItemId,
        name = "Track $mediaItemId",
        mediaType = MediaType.AUDIO.name,
        downloadPath = "/tmp/$mediaItemId",
        downloadUrl = "http://example/$mediaItemId",
        totalSizeBytes = 100L,
        downloadedBytes = 100L,
        status = DownloadStatus.COMPLETED.name,
    )

    private fun item(mediaItemId: String) = DownloadItem(
        id = "download-$mediaItemId",
        mediaItemId = mediaItemId,
        name = "Track $mediaItemId",
        mediaType = MediaType.AUDIO,
        downloadPath = "/tmp/$mediaItemId",
        downloadUrl = "http://example/$mediaItemId",
        totalSizeBytes = 100L,
        downloadedBytes = 100L,
        status = DownloadStatus.COMPLETED,
    )

    @Test
    fun downloadsFor_answersThroughTheSingleInQueryInEmissionOrder() = runTest {
        val b = entity("b")
        val c = entity("c")
        val ids = listOf("a", "b", "c")
        every { downloadDao.getDownloadsByMediaItemIdsFlow(ids) } returns flowOf(listOf(b, c))

        val rows = window.downloadsFor(ids).first()

        // Every requested id answered by the IN query, rows in emission order,
        // missing ids (a) simply absent — the album screen's original shape.
        assertEquals(listOf(item("b"), item("c")), rows)
        verify(exactly = 1) { downloadDao.getDownloadsByMediaItemIdsFlow(ids) }
        verify(exactly = 0) { downloadDao.getDownloadByMediaItemIdFlow(any()) }
    }

    @Test
    fun downloadsFor_neverFansOutToPerIdFlows() = runTest {
        val ids = listOf("a", "b")
        every { downloadDao.getDownloadsByMediaItemIdsFlow(ids) } returns flowOf(emptyList())

        assertTrue(window.downloadsFor(ids).first().isEmpty())

        verify(exactly = 0) { downloadDao.getDownloadByMediaItemIdFlow(any()) }
        verify(exactly = 0) { downloadDao.getAllDownloads(any()) }
    }

    @Test
    fun downloadsFor_emptyIds_isAnEmptyInQuery() = runTest {
        every { downloadDao.getDownloadsByMediaItemIdsFlow(emptyList()) } returns flowOf(emptyList())

        assertTrue(window.downloadsFor(emptyList()).first().isEmpty())

        verify(exactly = 1) { downloadDao.getDownloadsByMediaItemIdsFlow(emptyList()) }
        verify(exactly = 0) { downloadDao.getDownloadByMediaItemIdFlow(any()) }
    }
}
