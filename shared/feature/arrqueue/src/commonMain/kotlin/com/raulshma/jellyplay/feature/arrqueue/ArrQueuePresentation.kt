package com.raulshma.jellyplay.feature.arrqueue

import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.Res
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_brand_radarr
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_brand_sonarr
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_completed
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_downloading
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_failed
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_imported
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_paused
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_queued
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_unknown
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_warning
import org.jetbrains.compose.resources.StringResource

/**
 * Compose-free presentation tables for [ArrQueueScreen] (the
 * `ShortcutListPolicy` precedent): the status→color and kind→label/service
 * folds the queue screen renders — written once and pinned by
 * [ArrQueuePresentationTest], with the screen's composables keeping only
 * the drawing. Labels are unresolved [StringResource]s so localization
 * stays a render-time concern.
 *
 * The requests module's `RequestListItem` keeps its own inline table
 * (one-adapter rule: two screens, two adapters — this object must not be
 * shared with it).
 */
internal object ArrQueuePresentation {

    /** A chip/badge's unresolved label plus its tint. */
    internal data class Badge(
        val label: StringResource,
        val color: Color,
    )

    /** The service badge row: brand label + tint per *arr kind. */
    fun serviceBadge(kind: ArrServiceKind): Badge = when (kind) {
        ArrServiceKind.RADARR -> Badge(Res.string.arrqueue_brand_radarr, StatusColors.requested)
        ArrServiceKind.SONARR -> Badge(Res.string.arrqueue_brand_sonarr, StatusColors.pending)
    }

    /** The status chip row: status label + tint per download status. */
    fun statusChip(status: ArrDownloadStatus): Badge = when (status) {
        ArrDownloadStatus.DOWNLOADING -> Badge(Res.string.arrqueue_status_downloading, StatusColors.available)
        ArrDownloadStatus.QUEUED -> Badge(Res.string.arrqueue_status_queued, StatusColors.info)
        ArrDownloadStatus.PAUSED -> Badge(Res.string.arrqueue_status_paused, StatusColors.pending)
        ArrDownloadStatus.COMPLETED -> Badge(Res.string.arrqueue_status_completed, StatusColors.available)
        ArrDownloadStatus.IMPORTED -> Badge(Res.string.arrqueue_status_imported, StatusColors.success)
        ArrDownloadStatus.FAILED -> Badge(Res.string.arrqueue_status_failed, StatusColors.error)
        ArrDownloadStatus.WARNING -> Badge(Res.string.arrqueue_status_warning, StatusColors.warning)
        ArrDownloadStatus.UNKNOWN -> Badge(Res.string.arrqueue_status_unknown, StatusColors.debug)
    }

    /** The progress-bar tint per download status (deliberately its own ladder). */
    fun progressColor(status: ArrDownloadStatus): Color = when (status) {
        ArrDownloadStatus.COMPLETED -> StatusColors.available
        ArrDownloadStatus.IMPORTED -> StatusColors.success
        ArrDownloadStatus.PAUSED -> StatusColors.pending
        ArrDownloadStatus.DOWNLOADING -> StatusColors.requested
        ArrDownloadStatus.FAILED -> StatusColors.error
        ArrDownloadStatus.WARNING -> StatusColors.warning
        else -> StatusColors.info
    }

    /** The service name for inline prose (e.g. the import dialog message). */
    fun serviceName(kind: ArrServiceKind): StringResource = when (kind) {
        ArrServiceKind.RADARR -> Res.string.arrqueue_brand_radarr
        ArrServiceKind.SONARR -> Res.string.arrqueue_brand_sonarr
    }
}
