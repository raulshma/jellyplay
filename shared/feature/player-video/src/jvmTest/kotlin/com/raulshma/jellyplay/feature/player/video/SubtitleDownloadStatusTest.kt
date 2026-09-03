package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * Pins the per-subtitle download status model: the exact lifecycle constant
 * set of [SubtitleDownloadState] (the sheets switch exhaustively over it) and
 * the [SubtitleDownloadStatus] data-class invariants.
 */
class SubtitleDownloadStatusTest {

    @Test
    fun downloadState_exposesExactlyTheFiveLifecycleConstants() {
        assertEquals(
            listOf(
                SubtitleDownloadState.DOWNLOADING,
                SubtitleDownloadState.DOWNLOADED,
                SubtitleDownloadState.DOWNLOADED_DEVICE_ONLY,
                SubtitleDownloadState.DELAYED,
                SubtitleDownloadState.FAILED,
            ),
            SubtitleDownloadState.entries,
        )
    }

    @Test
    fun status_errorMessageDefaultsToNull() {
        val status = SubtitleDownloadStatus(subtitleId = "sub-1", state = SubtitleDownloadState.DOWNLOADED)

        assertEquals("sub-1", status.subtitleId)
        assertEquals(SubtitleDownloadState.DOWNLOADED, status.state)
        assertNull(status.errorMessage)
    }

    @Test
    fun status_failedCarriesInlineMessage() {
        val status = SubtitleDownloadStatus(
            subtitleId = "sub-2",
            state = SubtitleDownloadState.FAILED,
            errorMessage = "server unreachable",
        )

        assertEquals("server unreachable", status.errorMessage)

        val cleared = status.copy(state = SubtitleDownloadState.DOWNLOADING, errorMessage = null)
        assertEquals(SubtitleDownloadState.DOWNLOADING, cleared.state)
        assertNull(cleared.errorMessage)
        assertEquals("sub-2", cleared.subtitleId) // row key survives a state transition
    }

    @Test
    fun status_isIdentityKeyed_andStructurallyEqual() {
        val a = SubtitleDownloadStatus("sub-3", SubtitleDownloadState.DELAYED)
        val b = SubtitleDownloadStatus("sub-3", SubtitleDownloadState.DELAYED)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
