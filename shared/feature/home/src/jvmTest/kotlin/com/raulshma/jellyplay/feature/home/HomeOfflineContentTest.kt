package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class HomeOfflineContentTest {

    @Test
    fun offlineMediaItem_groupingByType() {
        val items = listOf(
            OfflineMediaItem(
                id = "off1",
                name = "Offline Movie",
                mediaType = MediaType.MOVIE,
                totalSizeBytes = 1_000_000_000L,
                downloadPath = "/downloads/m1.mp4",
            ),
            OfflineMediaItem(
                id = "off2",
                name = "Offline Show",
                mediaType = MediaType.SERIES,
                totalSizeBytes = 2_000_000_000L,
                downloadPath = "/downloads/s1.mp4",
            ),
        )

        val movies = items.filter { it.mediaType == MediaType.MOVIE }
        val series = items.filter { it.mediaType == MediaType.SERIES }

        assertEquals(1, movies.size)
        assertEquals("Offline Movie", movies.first().name)
        assertEquals(1, series.size)
        assertEquals("Offline Show", series.first().name)
    }

    @Test
    fun offlineMediaItem_totalSizeBytes() {
        val item1 = OfflineMediaItem(
            id = "1",
            name = "Item 1",
            mediaType = MediaType.MOVIE,
            totalSizeBytes = 500_000L,
            downloadPath = "/path1",
        )
        val item2 = OfflineMediaItem(
            id = "2",
            name = "Item 2",
            mediaType = MediaType.MOVIE,
            totalSizeBytes = 1_500_000L,
            downloadPath = "/path2",
        )

        val total = listOf(item1, item2).sumOf { it.totalSizeBytes }
        assertEquals(2_000_000L, total)
    }
}
