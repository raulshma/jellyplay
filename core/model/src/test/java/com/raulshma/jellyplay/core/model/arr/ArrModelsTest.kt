package com.raulshma.jellyplay.core.model.arr

import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrModelsTest {

    // ── ArrDownloadStatus.fromApi ──────────────────────────────────────────

    @Test
    fun `fromApi returns IMPORTED when trackedDownloadState is imported`() {
        assertEquals(
            ArrDownloadStatus.IMPORTED,
            ArrDownloadStatus.fromApi("Completed", trackedDownloadState = "imported"),
        )
    }

    @Test
    fun `fromApi returns IMPORTED when status is imported`() {
        assertEquals(
            ArrDownloadStatus.IMPORTED,
            ArrDownloadStatus.fromApi("imported"),
        )
    }

    @Test
    fun `fromApi returns FAILED for tracked error`() {
        assertEquals(
            ArrDownloadStatus.FAILED,
            ArrDownloadStatus.fromApi("Completed", trackedDownloadStatus = "error"),
        )
    }

    @Test
    fun `fromApi returns WARNING for tracked warning`() {
        assertEquals(
            ArrDownloadStatus.WARNING,
            ArrDownloadStatus.fromApi("Completed", trackedDownloadStatus = "warning"),
        )
    }

    @Test
    fun `fromApi maps core download client statuses`() {
        assertEquals(ArrDownloadStatus.QUEUED, ArrDownloadStatus.fromApi("queued"))
        assertEquals(ArrDownloadStatus.DOWNLOADING, ArrDownloadStatus.fromApi("downloading"))
        assertEquals(ArrDownloadStatus.DOWNLOADING, ArrDownloadStatus.fromApi("leeching"))
        assertEquals(ArrDownloadStatus.PAUSED, ArrDownloadStatus.fromApi("paused"))
        assertEquals(ArrDownloadStatus.COMPLETED, ArrDownloadStatus.fromApi("completed"))
        assertEquals(ArrDownloadStatus.COMPLETED, ArrDownloadStatus.fromApi("seeding"))
        assertEquals(ArrDownloadStatus.FAILED, ArrDownloadStatus.fromApi("failed"))
        assertEquals(ArrDownloadStatus.WARNING, ArrDownloadStatus.fromApi("warning"))
    }

    @Test
    fun `fromApi is case-insensitive`() {
        assertEquals(ArrDownloadStatus.DOWNLOADING, ArrDownloadStatus.fromApi("  DOWNLOADING  "))
    }

    @Test
    fun `fromApi returns UNKNOWN for null or unrecognized status`() {
        assertEquals(ArrDownloadStatus.UNKNOWN, ArrDownloadStatus.fromApi(null))
        assertEquals(ArrDownloadStatus.UNKNOWN, ArrDownloadStatus.fromApi("potato"))
    }

    // ── ArrQueueItem derived fields ────────────────────────────────────────

    @Test
    fun `percent clamps to 0-100 range`() {
        assertEquals(0, ArrQueueItem(queueId = 1, title = "x", status = ArrDownloadStatus.QUEUED, progress = -0.5f).percent)
        assertEquals(100, ArrQueueItem(queueId = 1, title = "x", status = ArrDownloadStatus.QUEUED, progress = 1.5f).percent)
        assertEquals(45, ArrQueueItem(queueId = 1, title = "x", status = ArrDownloadStatus.DOWNLOADING, progress = 0.45f).percent)
    }

    @Test
    fun `downloadedBytes computes from sizeBytes and sizeLeft`() {
        val item = ArrQueueItem(
            queueId = 1, title = "x", status = ArrDownloadStatus.DOWNLOADING,
            sizeBytes = 1_000L, sizeLeft = 400L,
        )
        assertEquals(600L, item.downloadedBytes)
    }

    @Test
    fun `downloadedBytes null when either input null`() {
        assertNull(
            ArrQueueItem(queueId = 1, title = "x", status = ArrDownloadStatus.DOWNLOADING, sizeBytes = 1_000L).downloadedBytes,
        )
        assertNull(
            ArrQueueItem(queueId = 1, title = "x", status = ArrDownloadStatus.DOWNLOADING, sizeLeft = 1_000L).downloadedBytes,
        )
    }

    // ── ArrCalendarItem.toSeerrSearchItem ──────────────────────────────────

    @Test
    fun `movie calendar item maps to movie SeerrSearchItem with title and releaseDate`() {
        val item = ArrCalendarItem(
            tmdbId = 123,
            title = "Test Movie",
            mediaType = ArrMediaType.MOVIE,
            airDateUtc = "2026-08-15",
            overview = "overview",
            posterPath = "/abc.jpg",
        )
        val seerr = item.toSeerrSearchItem()
        assertEquals(123, seerr.id)
        assertEquals("movie", seerr.mediaType)
        assertEquals("Test Movie", seerr.title)
        assertNull(seerr.name)
        assertEquals("overview", seerr.overview)
        assertEquals("2026-08-15", seerr.releaseDate)
        assertNull(seerr.firstAirDate)
        // displayName falls back to title then name
        assertEquals("Test Movie", seerr.displayName)
    }

    @Test
    fun `series calendar item maps to tv SeerrSearchItem with name and firstAirDate`() {
        val item = ArrCalendarItem(
            tvdbId = 789,
            title = "Test Show",
            mediaType = ArrMediaType.SERIES,
            airDateUtc = "2026-09-01",
        )
        val seerr = item.toSeerrSearchItem()
        // tvdb-only item synthesizes a stable negative id (out of the TMDB
        // range) so it can't collide with movie tmdbId keys.
        assertTrue("synthetic id must be non-zero", seerr.id != 0)
        assertTrue("synthetic id must be negative (out of TMDB range)", seerr.id < 0)
        assertEquals("tv", seerr.mediaType)
        assertNull(seerr.title)
        assertEquals("Test Show", seerr.name)
        assertEquals("2026-09-01", seerr.firstAirDate)
        assertEquals("Test Show", seerr.displayName)
    }

    @Test
    fun `id-less calendar items get distinct non-zero ids`() {
        // Regression guard: multiple Sonarr episodes with no tmdb AND no tvdb
        // previously all collapsed to id = 0, crashing Compose LazyRow keys.
        val a = ArrCalendarItem(title = "Ep A", mediaType = ArrMediaType.SERIES).toSeerrSearchItem().id
        val b = ArrCalendarItem(title = "Ep B", mediaType = ArrMediaType.SERIES).toSeerrSearchItem().id
        assertTrue("id must be non-zero: $a", a != 0)
        assertTrue("id must be non-zero: $b", b != 0)
        assertTrue("distinct id-less rows must not share an id: $a vs $b", a != b)
    }

    @Test
    fun `calendar item synthetic id is stable across calls`() {
        val item = ArrCalendarItem(tvdbId = 5, title = "Repeat", mediaType = ArrMediaType.SERIES)
        assertEquals(item.toSeerrSearchItem().id, item.toSeerrSearchItem().id)
    }

    @Test
    fun `posterPath is normalized to leading slash`() {
        val seerr = ArrCalendarItem(
            tmdbId = 1, title = "x", mediaType = ArrMediaType.MOVIE, posterPath = "abc.jpg",
        ).toSeerrSearchItem()
        assertEquals("/abc.jpg", seerr.posterPath)
    }
}
