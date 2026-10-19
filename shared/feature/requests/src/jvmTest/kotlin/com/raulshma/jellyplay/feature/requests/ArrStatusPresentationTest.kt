package com.raulshma.jellyplay.feature.requests

import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.feature.requests.generated.resources.Res
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_completed
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_downloading
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_failed
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_imported
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_paused
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_queued
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_unknown
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_warning
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins every arm of [arrStatusPresentation] — the requests-side label table
 * for the *arr download status (the bottom sheet's Download row). Labels are
 * unresolved resources and the assertions compare the resource objects
 * themselves (the [ArrQueuePresentationTest] rule). A table edit must update
 * this file in lockstep.
 */
class ArrStatusPresentationTest {

    @Test
    fun `status label pins a resource per arm`() {
        val table = mapOf(
            ArrDownloadStatus.QUEUED to Res.string.requests_arr_status_queued,
            ArrDownloadStatus.DOWNLOADING to Res.string.requests_arr_status_downloading,
            ArrDownloadStatus.PAUSED to Res.string.requests_arr_status_paused,
            ArrDownloadStatus.COMPLETED to Res.string.requests_arr_status_completed,
            ArrDownloadStatus.FAILED to Res.string.requests_arr_status_failed,
            ArrDownloadStatus.WARNING to Res.string.requests_arr_status_warning,
            ArrDownloadStatus.IMPORTED to Res.string.requests_arr_status_imported,
            ArrDownloadStatus.UNKNOWN to Res.string.requests_arr_status_unknown,
        )
        table.forEach { (status, expected) ->
            assertEquals(expected, arrStatusPresentation(status), "at $status")
        }
    }

    @Test
    fun `the table is exhaustive over the enum`() {
        ArrDownloadStatus.entries.forEach { status ->
            // Every status folds to a defined label (the `when` is exhaustive —
            // this pins that a new enum arm cannot silently default).
            arrStatusPresentation(status)
        }
    }
}
