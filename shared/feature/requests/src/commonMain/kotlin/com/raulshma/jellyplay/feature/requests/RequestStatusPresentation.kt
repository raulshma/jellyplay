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
import org.jetbrains.compose.resources.StringResource

/**
 * The request-status presentation decision table — label resource + color —
 * that [RequestListItem] and [RequestDetailBottomSheet] previously carried as
 * a verbatim ~18-line duplicate (alongside the is4k effective-status pick,
 * which now lives in core/model's `SeerrStatusDecisions` as
 * `SeerrRequestItem.effectiveMediaStatus`). The `when` below is that block
 * moved byte-identically: both call sites must keep rendering the same chip,
 * and now do so by construction — one table, two shells.
 *
 * Branch precedence (unchanged, just named now): the REQUEST status outranks
 * the media status for DECLINED and FAILED (an admin outcome always shows as
 * such), and a PENDING request on DELETED media shows "pending" — the request
 * is waiting for approval to re-request, so "deleted" would mislabel it.
 * Everything else defers to the effective media status.
 *
 * Placement (the topology call): the label strings are THIS feature's
 * compose-resources (the requests Res class), which core modules and sibling
 * features cannot see (star topology — features do not depend on each other),
 * so the label/color half of the Seerr status fold lives here rather than
 * beside the enum; the enum-level half (effective-status resolution +
 * availability predicates) lives in core/model where both requests and
 * details reach it. Color story: every branch is a static palette value from
 * core/designsystem's [StatusColors] except the UNKNOWN fallback, which is a
 * MaterialTheme role — the caller passes its resolved `onSurfaceVariant` in
 * as [unknownStatusColor], keeping this a pure function (no composable
 * reads) the JVM test lane can pin branch for branch.
 *
 * @return the (label resource, color) pair; destructured at both call sites.
 */
internal fun requestStatusPresentation(
    requestStatus: SeerrRequestStatus,
    mediaStatus: SeerrMediaStatus,
    unknownStatusColor: Color,
): Pair<StringResource, Color> = when {
    requestStatus == SeerrRequestStatus.DECLINED -> Res.string.requests_status_declined to StatusColors.error
    requestStatus == SeerrRequestStatus.FAILED -> Res.string.requests_status_failed to StatusColors.error
    requestStatus == SeerrRequestStatus.PENDING && mediaStatus == SeerrMediaStatus.DELETED ->
        Res.string.requests_status_pending to StatusColors.pending
    else -> when (mediaStatus) {
        SeerrMediaStatus.AVAILABLE -> Res.string.requests_status_available to StatusColors.available
        SeerrMediaStatus.PROCESSING -> Res.string.requests_status_processing to StatusColors.info
        SeerrMediaStatus.PARTIALLY_AVAILABLE -> Res.string.requests_status_partially_available to StatusColors.pendingLight
        SeerrMediaStatus.PENDING -> Res.string.requests_status_pending to StatusColors.pending
        SeerrMediaStatus.DELETED -> Res.string.requests_status_deleted to StatusColors.error
        SeerrMediaStatus.UNKNOWN -> Res.string.requests_status_unknown to unknownStatusColor
    }
}
