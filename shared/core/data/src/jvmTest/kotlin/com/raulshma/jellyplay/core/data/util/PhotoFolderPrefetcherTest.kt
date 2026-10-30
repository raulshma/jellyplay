package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.data.repository.PhotoFolderReads
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [PhotoFolderPrefetcher]'s bounded fan-out:
 *  1. only PHOTO_FOLDER items not present in `alreadyFetched` are fetched;
 *  2. results come back keyed by folder id;
 *  3. a folder with no children contributes an empty list, not a missing key;
 *  4. an all-skipped input costs zero repository calls and returns emptyMap.
 */
class PhotoFolderPrefetcherTest {

    private lateinit var photoFolderReads: PhotoFolderReads
    private lateinit var prefetcher: PhotoFolderPrefetcher

    @BeforeTest
    fun setup() {
        photoFolderReads = mockk()
        prefetcher = PhotoFolderPrefetcher(photoFolderReads)
    }

    private fun folder(id: String) = MediaItem(id = id, name = "Folder $id", mediaType = MediaType.PHOTO_FOLDER)
    private fun photo(id: String) = MediaItem(id = id, name = "Photo $id", mediaType = MediaType.PHOTO)

    @Test
    fun `fetches only unfetched photo folders and keys results by folder id`() = runTest {
        coEvery { photoFolderReads.getPhotoFolderChildImageUrls("f1", any()) } returns listOf("u1", "u2")
        coEvery { photoFolderReads.getPhotoFolderChildImageUrls("f2", any()) } returns listOf("u3")

        val result = prefetcher.prefetch(
            items = listOf(folder("f1"), photo("p1"), folder("f2")),
            alreadyFetched = emptySet(),
        )

        assertEquals(mapOf("f1" to listOf("u1", "u2"), "f2" to listOf("u3")), result)
        coVerify(exactly = 0) { photoFolderReads.getPhotoFolderChildImageUrls("p1", any()) }
    }

    @Test
    fun `skips folders already fetched`() = runTest {
        coEvery { photoFolderReads.getPhotoFolderChildImageUrls("f2", any()) } returns listOf("u3")

        val result = prefetcher.prefetch(
            items = listOf(folder("f1"), folder("f2")),
            alreadyFetched = setOf("f1"),
        )

        assertEquals(mapOf("f2" to listOf("u3")), result)
        coVerify(exactly = 0) { photoFolderReads.getPhotoFolderChildImageUrls("f1", any()) }
    }

    @Test
    fun `an all-skipped input costs zero repository calls`() = runTest {
        val result = prefetcher.prefetch(items = listOf(folder("f1")), alreadyFetched = setOf("f1"))

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { photoFolderReads.getPhotoFolderChildImageUrls(any(), any()) }
    }

    @Test
    fun `non-photo-folder items are never fetched even when unfetched`() = runTest {
        val result = prefetcher.prefetch(items = listOf(photo("p1"), photo("p2")))

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { photoFolderReads.getPhotoFolderChildImageUrls(any(), any()) }
    }
}
