package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.EditableItemMetadata
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.RemoteImageResult
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataEditorRepositoryImplTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val repository = MetadataEditorRepositoryImpl(apiClient)

    @Test
    fun `getMediaDetail passes success through`() = runTest {
        val detail = MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))
        coEvery { apiClient.getMediaDetail("m1") } returns Result.success(detail)

        val result = repository.getMediaDetail("m1")

        assertTrue(result.isSuccess)
        assertSame(detail, result.getOrNull())
    }

    @Test
    fun `updateItem forwards the full field set to the client`() = runTest {
        coEvery { apiClient.updateItem(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
            apiClient.updateItem("m1", "Name", null, null, "O", null, listOf("G"), emptyList(), emptyList(), 8f, null, "PG", null, 2020, null, null, null, null, null, null, null, emptyList(), null, emptyList(), emptyMap(), false, emptyList(), null, null, emptyList(), emptyList(), null, "Unknown")
        }
    }

    @Test
    fun `image operations delegate to the client`() = runTest {
        coEvery { apiClient.getItemImageInfo("m1") } returns Result.success(listOf(ImageInfo(imageType = "Primary", imageIndex = 0)))
        coEvery { apiClient.setItemImage("m1", "Backdrop", any()) } returns Result.success(Unit)
        coEvery { apiClient.deleteItemImage("m1", "Backdrop", 2) } returns Result.success(Unit)
        coEvery { apiClient.downloadRemoteImage("m1", "Primary", "https://img") } returns Result.success(Unit)
        coEvery { apiClient.getRemoteImages("m1", "Primary", null, null, 50) } returns
            Result.success(RemoteImageResult(images = emptyList(), totalRecordCount = 0, providers = emptyList()))

        repository.getItemImageInfo("m1")
        repository.setItemImage("m1", "Backdrop", byteArrayOf(1))
        repository.deleteItemImage("m1", "Backdrop", 2)
        repository.downloadRemoteImage("m1", "Primary", "https://img")
        repository.getRemoteImages("m1", "Primary", null, null, 50)

        coVerify(exactly = 1) { apiClient.getItemImageInfo("m1") }
        coVerify(exactly = 1) { apiClient.setItemImage("m1", "Backdrop", any()) }
        coVerify(exactly = 1) { apiClient.deleteItemImage("m1", "Backdrop", 2) }
        coVerify(exactly = 1) { apiClient.downloadRemoteImage("m1", "Primary", "https://img") }
        coVerify(exactly = 1) { apiClient.getRemoteImages("m1", "Primary", null, null, 50) }
    }

    @Test
    fun `subtitle operations delegate with the same arguments`() = runTest {
        coEvery { apiClient.uploadSubtitle(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { apiClient.deleteSubtitle("m1", 3) } returns Result.success(Unit)
        coEvery { apiClient.searchRemoteSubtitles("m1", "eng") } returns Result.success(listOf(RemoteSubtitleInfo(id = "s1")))
        coEvery { apiClient.downloadRemoteSubtitle("m1", "sub-1") } returns Result.success(Unit)

        repository.uploadSubtitle("m1", "ZGF0YQ==", "en.srt", "eng", isForced = false, isHearingImpaired = true)
        repository.deleteSubtitle("m1", 3)
        repository.searchRemoteSubtitles("m1", "eng")
        repository.downloadRemoteSubtitle("m1", "sub-1")

        coVerify(exactly = 1) { apiClient.uploadSubtitle("m1", "ZGF0YQ==", "en.srt", "eng", false, true) }
        coVerify(exactly = 1) { apiClient.deleteSubtitle("m1", 3) }
        coVerify(exactly = 1) { apiClient.searchRemoteSubtitles("m1", "eng") }
        coVerify(exactly = 1) { apiClient.downloadRemoteSubtitle("m1", "sub-1") }
    }

    @Test
    fun `getItemImageUrl forwards variant parameters`() {
        every { apiClient.getImageUrl("m1", "Backdrop", 400, 2, "tag-9") } returns "https://server/img"

        val url = repository.getItemImageUrl("m1", "Backdrop", 400, 2, "tag-9")

        assertEquals("https://server/img", url)
    }

    @Test
    fun `refreshItemMetadata keeps the editor's explicit refresh modes`() = runTest {
        coEvery { apiClient.refreshItemMetadata("m1", "FullRefresh", "FullRefresh", true, false) } returns
            Result.success(Unit)

        val result = repository.refreshItemMetadata("m1", "FullRefresh", "FullRefresh", replaceAllMetadata = true, replaceAllImages = false)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { apiClient.refreshItemMetadata("m1", "FullRefresh", "FullRefresh", true, false) }
    }
}
