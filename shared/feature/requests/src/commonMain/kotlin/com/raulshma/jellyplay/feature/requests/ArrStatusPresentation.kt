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
import org.jetbrains.compose.resources.StringResource

/**
 * The requests-side presentation table for an [ArrDownloadStatus] — the
 * label half of the status fold [RequestDetailBottomSheet]'s Download row
 * renders when direct *arr progress is available (the former file-private
 * `arrStatusRes`, moved here beside [requestStatusPresentation] so the JVM
 * lane can pin it like the rest of the module's presentation tables).
 *
 * Labels are THIS feature's compose-resources (the requests `Res` class) —
 * deliberately NOT the arrqueue module's table: `ArrQueuePresentation`
 * documents the two-adapter rule (two screens, two adapters — its labels are
 * the arrqueue feature's own resources and must not be shared), so this is
 * the requests adapter's own row of the same enum. The sheet shows no tint
 * for this row (plain `DetailRow` text), so the table is label-only.
 */
internal fun arrStatusPresentation(status: ArrDownloadStatus): StringResource = when (status) {
    ArrDownloadStatus.QUEUED -> Res.string.requests_arr_status_queued
    ArrDownloadStatus.DOWNLOADING -> Res.string.requests_arr_status_downloading
    ArrDownloadStatus.PAUSED -> Res.string.requests_arr_status_paused
    ArrDownloadStatus.COMPLETED -> Res.string.requests_arr_status_completed
    ArrDownloadStatus.FAILED -> Res.string.requests_arr_status_failed
    ArrDownloadStatus.WARNING -> Res.string.requests_arr_status_warning
    ArrDownloadStatus.IMPORTED -> Res.string.requests_arr_status_imported
    ArrDownloadStatus.UNKNOWN -> Res.string.requests_arr_status_unknown
}
