package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.EditableItemMetadata
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MetadataRefreshParams
import com.raulshma.jellyplay.core.model.RemoteImageResult
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.MetadataApiClient
import com.raulshma.jellyplay.core.network.api.PlaybackApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

class MetadataEditorRepositoryImplTest {

    private val libraryApiClient: LibraryApiClient = mockk(relaxed = true)
    private val metadataApiClient: MetadataApiClient = mockk(relaxed = true)
    private val playbackApiClient: PlaybackApiClient = mockk(relaxed = true)
    private val repository = MetadataEditorRepositoryImpl(libraryApiClient, metadataApiClient, playbackApiClient)

    @Test
    fun `getMediaDetail passes success through`() = runTest {
        val detail = MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))
        coEvery { libraryApiClient.getMediaDetail("m1") } returns Result.success(detail)

        val result = repository.getMediaDetail("m1")

        assertTrue(result.isSuccess)
        assertSame(detail, result.getOrNull())
    }

    @Test
    fun `updateItem forwards the full field set to the client`() = runTest {
        coEvery { metadataApiClient.updateItem(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(Unit)

        val result = repository.updateItem(
            itemId = "m1",
            metadata = EditableItemMetadata(
                name = "Name",
                overview = "O",
                genres = listOf("G"),
                communityRating = 8f,
                officialRating = "PG",
                productionYear = 2020,
            ),
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            metadataApiClient.updateItem("m1", "Name", null, null, "O", null, listOf("G"), emptyList(), emptyList(), 8f, null, "PG", null, 2020, null, null, null, null, null, null, null, emptyList(), null, emptyList(), emptyMap(), false, emptyList(), null, null, emptyList(), emptyList(), null, "Unknown")
        }
    }

    @Test
    fun `image operations delegate to the client`() = runTest {
        coEvery { metadataApiClient.getItemImageInfo("m1") } returns Result.success(listOf(ImageInfo(imageType = "Primary", imageIndex = 0)))
        coEvery { metadataApiClient.setItemImage("m1", "Backdrop", any()) } returns Result.success(Unit)
        coEvery { metadataApiClient.deleteItemImage("m1", "Backdrop", 2) } returns Result.success(Unit)
        coEvery { metadataApiClient.downloadRemoteImage("m1", "Primary", "https://img") } returns Result.success(Unit)
        coEvery { metadataApiClient.getRemoteImages("m1", "Primary", null, null, 50) } returns
            Result.success(RemoteImageResult(images = emptyList(), totalRecordCount = 0, providers = emptyList()))

        repository.getItemImageInfo("m1")
        repository.setItemImage("m1", "Backdrop", byteArrayOf(1))
        repository.deleteItemImage("m1", "Backdrop", 2)
        repository.downloadRemoteImage("m1", "Primary", "https://img")
        repository.getRemoteImages("m1", "Primary", null, null, 50)

        coVerify(exactly = 1) { metadataApiClient.getItemImageInfo("m1") }
        coVerify(exactly = 1) { metadataApiClient.setItemImage("m1", "Backdrop", any()) }
        coVerify(exactly = 1) { metadataApiClient.deleteItemImage("m1", "Backdrop", 2) }
        coVerify(exactly = 1) { metadataApiClient.downloadRemoteImage("m1", "Primary", "https://img") }
        coVerify(exactly = 1) { metadataApiClient.getRemoteImages("m1", "Primary", null, null, 50) }
    }

    @Test
    fun `subtitle operations delegate with the same arguments`() = runTest {
        coEvery { metadataApiClient.uploadSubtitle(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { metadataApiClient.deleteSubtitle("m1", 3) } returns Result.success(Unit)
        coEvery { metadataApiClient.searchRemoteSubtitles("m1", "eng") } returns Result.success(listOf(RemoteSubtitleInfo(id = "s1")))
        coEvery { playbackApiClient.downloadRemoteSubtitle("m1", "sub-1") } returns Result.success(Unit)

        repository.uploadSubtitle("m1", "ZGF0YQ==", "en.srt", "eng", isForced = false, isHearingImpaired = true)
        repository.deleteSubtitle("m1", 3)
        repository.searchRemoteSubtitles("m1", "eng")
        repository.downloadRemoteSubtitle("m1", "sub-1")

        coVerify(exactly = 1) { metadataApiClient.uploadSubtitle("m1", "ZGF0YQ==", "en.srt", "eng", false, true) }
        coVerify(exactly = 1) { metadataApiClient.deleteSubtitle("m1", 3) }
        coVerify(exactly = 1) { metadataApiClient.searchRemoteSubtitles("m1", "eng") }
        coVerify(exactly = 1) { playbackApiClient.downloadRemoteSubtitle("m1", "sub-1") }
    }

    @Test
    fun `getItemImageUrl forwards variant parameters`() {
        every { libraryApiClient.getImageUrl("m1", "Backdrop", 400, 2, "tag-9") } returns "https://server/img"

        val url = repository.getItemImageUrl("m1", "Backdrop", 400, 2, "tag-9")

        assertEquals("https://server/img", url)
    }

    @Test
    fun `refreshItemMetadata keeps the editor's explicit refresh modes`() = runTest {
        coEvery { metadataApiClient.refreshItemMetadata("m1", "FullRefresh", "FullRefresh", true, false) } returns
            Result.success(Unit)

        val result = repository.refreshItemMetadata("m1", MetadataRefreshParams("FullRefresh", "FullRefresh", replaceAllMetadata = true, replaceAllImages = false))

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { metadataApiClient.refreshItemMetadata("m1", "FullRefresh", "FullRefresh", true, false) }
    }
}
