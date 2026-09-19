package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [DownloadIntake.flipTrack]'s semantics ONCE — against the real
 * desktop intake (the Android DownloadIntakeImpl shares the interface's
 * default implementation, so the resolve→start routing is pinned here, over
 * the same leg [startFromItem] owns):
 *
 *  - the detail resolves → the start happens at the user's default download
 *    quality and the flip reports [TrackFlipResult.Started];
 *  - the detail is unresolvable → [TrackFlipResult.Skipped] and the start is
 *    NEVER attempted (the id-only flip's silent-skip contract, consumed by
 *    [TrackDownloadActions]);
 *  - a start with no usable media source also folds into [TrackFlipResult.Skipped]
 *    — a track row has no error surface, so there is no Failed variant.
 *
 * The delegation envelope around this seam (swallowed throws, cancellation
 * rethrow, bulk admission) is pinned in TrackDownloadActionsTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopDownloadIntakeTest {

    private val delegate: DownloadDelegate = mockk()
    private val downloadRepository: DownloadRepository = mockk()
    private val mediaRepository: MediaRepository = mockk()
    private val downloadsStore: DownloadsStore = mockk {
        every { downloads } returns MutableStateFlow(
            DownloadsSlice(downloadQuality = DownloadQuality.HIGH_1080P),
        )
    }
    private val intake = DesktopDownloadIntake(
        delegate = delegate,
        downloadRepository = downloadRepository,
        mediaRepository = mediaRepository,
        downloadsStore = downloadsStore,
    )

    private fun trackDetail(id: String = "track-1") = MediaDetail(
        // MediaDetail is a data class — constructing one directly keeps the test
        // independent of how a real detail is sourced.
        item = MediaItem(id = id, name = "Track $id", mediaType = MediaType.AUDIO),
        mediaSources = emptyList(),
    )

    @Test
    fun `flipTrack starts the resolved detail at the default quality`() = runTest {
        val detail = trackDetail()
        coEvery { mediaRepository.getMediaDetail("track-1") } returns Result.success(detail)
        coEvery { delegate.startOne(detail, 8_000_000, null, null) } returns
            DownloadResult(downloadItem = mockk(), error = null)

        val result = intake.flipTrack("track-1")

        assertEquals(TrackFlipResult.Started, result)
        coVerify(exactly = 1) { delegate.startOne(detail, 8_000_000, null, null) }
    }

    @Test
    fun `flipTrack skips without starting when the detail cannot be resolved`() = runTest {
        coEvery { mediaRepository.getMediaDetail("track-1") } returns
            Result.failure(RuntimeException("offline"))

        val result = intake.flipTrack("track-1")

        assertEquals(TrackFlipResult.Skipped, result)
        coVerify(exactly = 0) { delegate.startOne(any(), any(), any(), any()) }
    }

    @Test
    fun `flipTrack folds a start with no usable media source into Skipped`() = runTest {
        val detail = trackDetail()
        coEvery { mediaRepository.getMediaDetail("track-1") } returns Result.success(detail)
        coEvery { delegate.startOne(detail, 8_000_000, null, null) } returns
            DownloadResult(downloadItem = null, error = "No media source available for download")

        val result = intake.flipTrack("track-1")

        assertEquals(TrackFlipResult.Skipped, result)
    }
}
