package com.raulshma.jellyplay.feature.requests

import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestStatus
import com.raulshma.jellyplay.feature.requests.generated.resources.Res
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_available
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_declined
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_deleted
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_failed
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_partially_available
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_pending
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_processing
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_unknown
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Pins the request-status presentation table branch for branch — the
 * label/color `when` [RequestListItem] and [RequestDetailBottomSheet]
 * previously duplicated verbatim. Every branch of the table is asserted,
 * including the precedence arms (request status outranking media status) and
 * the theme-role passthrough for UNKNOWN.
 */
class RequestStatusPresentationTest {

    private val unknownColor = Color(0xFFABCDEF)

    private fun presentation(
        requestStatus: SeerrRequestStatus,
        mediaStatus: SeerrMediaStatus,
    ): Pair<org.jetbrains.compose.resources.StringResource, Color> =
        requestStatusPresentation(requestStatus, mediaStatus, unknownColor)

    // ── Request-status precedence arms (outrank the media status) ──────

    @Test
    fun `declined request shows declined even when media is available`() {
        assertEquals(
            Res.string.requests_status_declined to StatusColors.error,
            presentation(SeerrRequestStatus.DECLINED, SeerrMediaStatus.AVAILABLE),
        )
    }

    @Test
    fun `failed request shows failed regardless of media status`() {
        assertEquals(
            Res.string.requests_status_failed to StatusColors.error,
            presentation(SeerrRequestStatus.FAILED, SeerrMediaStatus.PROCESSING),
        )
    }

    @Test
    fun `pending request on deleted media shows pending not deleted`() {
        // Waiting for approval to re-request — "deleted" would mislabel it.
        assertEquals(
            Res.string.requests_status_pending to StatusColors.pending,
            presentation(SeerrRequestStatus.PENDING, SeerrMediaStatus.DELETED),
        )
    }

    // ── Media-status arms (request status defers) ──────────────────────

    @Test
    fun `available media shows available`() {
        assertEquals(
            Res.string.requests_status_available to StatusColors.available,
            presentation(SeerrRequestStatus.APPROVED, SeerrMediaStatus.AVAILABLE),
        )
    }

    @Test
    fun `processing media shows processing with info color`() {
        assertEquals(
            Res.string.requests_status_processing to StatusColors.info,
            presentation(SeerrRequestStatus.APPROVED, SeerrMediaStatus.PROCESSING),
        )
    }

    @Test
    fun `partially available media shows partially available with pendingLight`() {
        assertEquals(
            Res.string.requests_status_partially_available to StatusColors.pendingLight,
            presentation(SeerrRequestStatus.COMPLETED, SeerrMediaStatus.PARTIALLY_AVAILABLE),
        )
    }

    @Test
    fun `pending media shows pending`() {
        assertEquals(
            Res.string.requests_status_pending to StatusColors.pending,
            presentation(SeerrRequestStatus.APPROVED, SeerrMediaStatus.PENDING),
        )
    }

    @Test
    fun `deleted media shows deleted for non-pending requests`() {
        assertEquals(
            Res.string.requests_status_deleted to StatusColors.error,
            presentation(SeerrRequestStatus.COMPLETED, SeerrMediaStatus.DELETED),
        )
    }

    @Test
    fun `unknown media shows unknown with the caller's theme color`() {
        // The one theme-role branch: the table passes the caller-resolved
        // onSurfaceVariant through untouched.
        assertEquals(
            Res.string.requests_status_unknown to unknownColor,
            presentation(SeerrRequestStatus.COMPLETED, SeerrMediaStatus.UNKNOWN),
        )
    }

    @Test
    fun `table covers every media status for a non-pending request`() {
        // Exhaustiveness guard: every SeerrMediaStatus maps to some label
        // (the UNKNOWN arm included) — the destructured pair is never null.
        SeerrMediaStatus.entries.forEach { mediaStatus ->
            val (labelRes, color) = presentation(SeerrRequestStatus.COMPLETED, mediaStatus)
            assertEquals(mediaStatus == SeerrMediaStatus.UNKNOWN, color == unknownColor)
            assertEquals(
                when (mediaStatus) {
                    SeerrMediaStatus.AVAILABLE -> Res.string.requests_status_available
                    SeerrMediaStatus.PROCESSING -> Res.string.requests_status_processing
                    SeerrMediaStatus.PARTIALLY_AVAILABLE -> Res.string.requests_status_partially_available
                    SeerrMediaStatus.PENDING -> Res.string.requests_status_pending
                    SeerrMediaStatus.DELETED -> Res.string.requests_status_deleted
                    SeerrMediaStatus.UNKNOWN -> Res.string.requests_status_unknown
                },
                labelRes,
            )
        }
    }
}
