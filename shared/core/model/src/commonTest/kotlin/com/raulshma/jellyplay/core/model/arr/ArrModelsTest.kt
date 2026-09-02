package com.raulshma.jellyplay.core.model.arr

import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

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
        assertEquals(
seerr.mediaType,
"movie",
)
        assertEquals(
seerr.title,
"Test Movie",
)
        assertNull(seerr.name)
        assertEquals(
seerr.overview,
"overview",
)
        assertEquals(
seerr.releaseDate,
"2026-08-15",
)
        assertNull(seerr.firstAirDate)
        // displayName falls back to title then name
        assertEquals(
seerr.displayName,
"Test Movie",
)
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
        assertTrue(
seerr.id != 0,
"synthetic id must be non-zero",
)
        assertTrue(
seerr.id < 0,
"synthetic id must be negative (out of TMDB range)",
)
        assertEquals(
seerr.mediaType,
"tv",
)
        assertNull(seerr.title)
        assertEquals(
seerr.name,
"Test Show",
)
        assertEquals(
seerr.firstAirDate,
"2026-09-01",
)
        assertEquals(
seerr.displayName,
"Test Show",
)
    }

    @Test
    fun `id-less calendar items get distinct non-zero ids`() {
        // Regression guard: multiple Sonarr episodes with no tmdb AND no tvdb
        // previously all collapsed to id = 0, crashing Compose LazyRow keys.
        val a = ArrCalendarItem(title = "Ep A", mediaType = ArrMediaType.SERIES).toSeerrSearchItem().id
        val b = ArrCalendarItem(title = "Ep B", mediaType = ArrMediaType.SERIES).toSeerrSearchItem().id
        assertTrue(
a != 0,
"id must be non-zero: $a",
)
        assertTrue(
b != 0,
"id must be non-zero: $b",
)
        assertTrue(
a != b,
"distinct id-less rows must not share an id: $a vs $b",
)
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
        assertEquals(
seerr.posterPath,
"/abc.jpg",
)
    }
}
