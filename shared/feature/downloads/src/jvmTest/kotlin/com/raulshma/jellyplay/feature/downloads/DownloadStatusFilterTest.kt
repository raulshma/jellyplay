package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the downloads-list status filter (gh-178): Active folds the four
 * in-flight statuses, Completed/Failed match their single status, All admits
 * everything, and the tolerant decode falls back to ALL.
 */
class DownloadStatusFilterTest {

    private fun item(status: DownloadStatus) = DownloadItem(
        id = "x",
        mediaItemId = "x",
        name = "Item",
        mediaType = MediaType.MOVIE,
        downloadPath = "/data/x",
        downloadUrl = "https://server/x",
        totalSizeBytes = 0L,
        downloadedBytes = 0L,
        status = status,
        priority = 0,
    )

    @Test
    fun all_admits_every_status() {
        DownloadStatus.entries.forEach { status ->
            assertTrue(DownloadStatusFilter.ALL.accepts(item(status)), "ALL admission for $status")
        }
    }

    @Test
    fun active_admits_the_in_flight_statuses_only() {
        val expected = mapOf(
            DownloadStatus.PENDING to true,
            DownloadStatus.QUEUED to true,
            DownloadStatus.DOWNLOADING to true,
            DownloadStatus.PAUSED to true,
            DownloadStatus.COMPLETED to false,
            DownloadStatus.FAILED to false,
            DownloadStatus.CANCELLED to false,
        )
        expected.forEach { (status, admitted) ->
            assertEquals(admitted, DownloadStatusFilter.ACTIVE.accepts(item(status)), "ACTIVE admission for $status")
        }
    }

    @Test
    fun active_statuses_match_the_cancel_admission_set() {
        // The Active view exists so in-flight rows can be controlled in-app;
        // its status set must stay in lockstep with the CANCEL admission the
        // controls dispatch through.
        val cancellable = setOf(
            DownloadStatus.PENDING,
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.PAUSED,
        )
        assertEquals(cancellable, DownloadStatusFilter.ACTIVE_STATUSES)
    }

    @Test
    fun completed_and_failed_admit_only_their_own_status() {
        DownloadStatus.entries.forEach { status ->
            assertEquals(
                status == DownloadStatus.COMPLETED,
                DownloadStatusFilter.COMPLETED.accepts(item(status)),
                "COMPLETED admission for $status",
            )
            assertEquals(
                status == DownloadStatus.FAILED,
                DownloadStatusFilter.FAILED.accepts(item(status)),
                "FAILED admission for $status",
            )
        }
    }

    @Test
    fun fromName_tolerates_unknown_and_empty_values() {
        assertEquals(DownloadStatusFilter.ALL, DownloadStatusFilter.fromName("nope"))
        assertEquals(DownloadStatusFilter.ALL, DownloadStatusFilter.fromName(""))
        assertEquals(DownloadStatusFilter.ALL, DownloadStatusFilter.fromName(null))
        assertEquals(DownloadStatusFilter.ACTIVE, DownloadStatusFilter.fromName("ACTIVE"))
        assertEquals(DownloadStatusFilter.COMPLETED, DownloadStatusFilter.fromName("COMPLETED"))
        assertEquals(DownloadStatusFilter.FAILED, DownloadStatusFilter.fromName("FAILED"))
        assertFalse(DownloadStatusFilter.entries.isEmpty())
    }
}
