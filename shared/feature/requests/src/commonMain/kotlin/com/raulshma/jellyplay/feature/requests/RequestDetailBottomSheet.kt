package com.raulshma.jellyplay.feature.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Ban
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ExternalLink
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.arr.ArrDownloadSummary
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestStatus
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.requests.generated.resources.Res
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_action_approve
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_action_blocklist_search
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_action_decline
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_action_delete_confirm
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_action_delete_request
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_action_remove_from_service
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_action_remove_search
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_action_retry_request
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_action_view_media_details
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_completed
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_downloading
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_failed
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_imported
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_paused
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_queued
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_unknown
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_arr_status_warning
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_detail_row_download
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_detail_row_quality_profile
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_detail_row_request_id
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_detail_row_requested
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_detail_row_requested_by
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_detail_row_root_folder
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_detail_row_seasons
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_detail_row_tmdb_id
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_detail_row_type
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_detail_year_type
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_download_in_queue
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_download_time_left
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_fallback_title
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_service_radarr
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_service_sonarr
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_available
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_declined
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_deleted
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_failed
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_partially_available
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_pending
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_processing
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_status_unknown
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailBottomSheet(
    request: SeerrRequestItem,
    mediaInfo: RequestMediaInfo?,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onApprove: () -> Unit = {},
    onDecline: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRemoveFromService: () -> Unit = {},
    onNavigateToDetail: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    /** Direct *arr download progress; null when the feature is off or no download exists. */
    downloadProgress: ArrDownloadSummary? = null,
    /**
     * When non-null + [downloadProgress] present, renders queue-management
     * actions (remove / blocklist + search). Receives whether to add the
     * release to the blocklist and whether to search for a replacement.
     */
    onRemoveFromQueue: ((blocklist: Boolean, searchAgain: Boolean) -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val requestStatus = remember(request.status) { SeerrRequestStatus.fromValue(request.status) }
    var isConfirmingDelete by remember(request.id) { mutableStateOf(false) }
    var isConfirmingRemoveFromService by remember(request.id) { mutableStateOf(false) }

    LaunchedEffect(isConfirmingDelete) {
        if (isConfirmingDelete) {
            delay(3000)
            isConfirmingDelete = false
        }
    }

    LaunchedEffect(isConfirmingRemoveFromService) {
        if (isConfirmingRemoveFromService) {
            delay(3000)
            isConfirmingRemoveFromService = false
        }
    }
    val effectiveMediaStatus = if (request.is4k) request.media.status4k else request.media.status
    val mediaStatus = remember(effectiveMediaStatus) { SeerrMediaStatus.fromValue(effectiveMediaStatus) }
    val displayTitle = mediaInfo?.title ?: stringResource(Res.string.requests_fallback_title, request.media.tmdbId)

    val (statusLabelRes, statusColor) = when {
        requestStatus == SeerrRequestStatus.DECLINED -> Res.string.requests_status_declined to StatusColors.error
        requestStatus == SeerrRequestStatus.FAILED -> Res.string.requests_status_failed to StatusColors.error
        requestStatus == SeerrRequestStatus.PENDING && mediaStatus == SeerrMediaStatus.DELETED -> Res.string.requests_status_pending to StatusColors.pending
        else -> when (mediaStatus) {
            SeerrMediaStatus.AVAILABLE -> Res.string.requests_status_available to StatusColors.available
            SeerrMediaStatus.PROCESSING -> Res.string.requests_status_processing to StatusColors.info
            SeerrMediaStatus.PARTIALLY_AVAILABLE -> Res.string.requests_status_partially_available to StatusColors.pendingLight
            SeerrMediaStatus.PENDING -> Res.string.requests_status_pending to StatusColors.pending
            SeerrMediaStatus.DELETED -> Res.string.requests_status_deleted to StatusColors.error
            SeerrMediaStatus.UNKNOWN -> Res.string.requests_status_unknown to colorScheme.onSurfaceVariant
        }
    }
    val statusLabel = stringResource(statusLabelRes)

    val formattedDate = remember(request.createdAt) {
        try {
            val parsed = LocalDateTime.parse(request.createdAt, DateTimeFormatter.ISO_DATE_TIME)
            parsed.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        } catch (_: Exception) {
            request.createdAt.take(10)
        }
    }

    val isTv = LocalTvMode.current
    val content: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 108.dp)
                        .clip(ShapeCache.smooth10)
                        .background(colorScheme.onSurface.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (mediaInfo?.posterUrl != null) {
                        MediaImage(
                            url = mediaInfo.posterUrl,
                            contentDescription = displayTitle,
                            modifier = Modifier.size(width = 72.dp, height = 108.dp),
                            contentScale = ContentScale.Crop,
                            crossfade = true,
                        )
                    } else {
                        Text(
                            text = if (request.type.equals("movie", ignoreCase = true)) "🎬" else "📺",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (mediaInfo?.year != null) {
                        Text(
                            text = stringResource(
                                Res.string.requests_detail_year_type,
                                mediaInfo.year,
                                request.type.replaceFirstChar { it.uppercase() },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!mediaInfo?.overview.isNullOrBlank()) {
                Text(
                    text = mediaInfo!!.overview!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(stringResource(Res.string.requests_detail_row_request_id), "#${request.id}")
                DetailRow(stringResource(Res.string.requests_detail_row_type), request.type.replaceFirstChar { it.uppercase() })
                DetailRow(stringResource(Res.string.requests_detail_row_tmdb_id), request.media.tmdbId.toString())
                request.requestedBy.username?.let { DetailRow(stringResource(Res.string.requests_detail_row_requested_by), it) }
                DetailRow(stringResource(Res.string.requests_detail_row_requested), formattedDate)
                request.profileName?.let { DetailRow(stringResource(Res.string.requests_detail_row_quality_profile), it) }
                request.rootFolder?.let { DetailRow(stringResource(Res.string.requests_detail_row_root_folder), it) }
                if (request.seasons.isNotEmpty()) {
                    DetailRow(
                        stringResource(Res.string.requests_detail_row_seasons),
                        request.seasons.joinToString(", ") { "S${it.seasonNumber}" },
                    )
                }
                if (downloadProgress != null) {
                    // Direct *arr: show live percent + time-left.
                    val arrStatusText = stringResource(arrStatusRes(downloadProgress.status))
                    val text = buildString {
                        append(arrStatusText)
                        append(" · ")
                        append(downloadProgress.percent)
                        append('%')
                        downloadProgress.timeLeft?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(stringResource(Res.string.requests_download_time_left, it))
                        }
                    }
                    DetailRow(stringResource(Res.string.requests_detail_row_download), text)
                } else if (request.media.downloadStatus.isNotEmpty()) {
                    val downloadStatusText = request.media.downloadStatus
                        .mapNotNull { it.status }
                        .joinToString(", ")
                        .ifBlank { stringResource(Res.string.requests_download_in_queue) }
                    DetailRow(stringResource(Res.string.requests_detail_row_download), downloadStatusText)
                }
            }

            // ── Direct *arr queue management actions ──
            // Shown only when a live queue item exists for this request and
            // the caller wires the callbacks. Each action confirms first
            // because they are destructive (remove download client data).
            if (downloadProgress != null && onRemoveFromQueue != null) {
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onRemoveFromQueue(false, true) },
                        modifier = Modifier.weight(1f),
                        shape = ShapeCache.smooth12,
                    ) {
                        Icon(Tabler.Outline.X, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.requests_action_remove_search))
                    }
                    OutlinedButton(
                        onClick = { onRemoveFromQueue(true, true) },
                        modifier = Modifier.weight(1f),
                        shape = ShapeCache.smooth12,
                    ) {
                        Icon(Tabler.Outline.Ban, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.requests_action_blocklist_search))
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))

            OutlinedButton(
                onClick = { onNavigateToDetail(request.media.tmdbId, request.type) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusIndicator(),
                shape = ShapeCache.smooth12,
            ) {
                Icon(
                    Tabler.Outline.ExternalLink,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.requests_action_view_media_details))
            }

            if (isAdmin) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        requestStatus == SeerrRequestStatus.FAILED -> {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusIndicator(),
                                shape = ShapeCache.smooth12,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StatusColors.info,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Icon(Tabler.Outline.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(Res.string.requests_action_retry_request), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        requestStatus == SeerrRequestStatus.PENDING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Button(
                                    onClick = onApprove,
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusIndicator(),
                                    shape = ShapeCache.smooth12,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StatusColors.available,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) {
                                    Icon(Tabler.Outline.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(Res.string.requests_action_approve), style = MaterialTheme.typography.labelLarge)
                                }
                                Button(
                                    onClick = onDecline,
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusIndicator(),
                                    shape = ShapeCache.smooth12,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StatusColors.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                ) {
                                    Icon(Tabler.Outline.X, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(Res.string.requests_action_decline), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    if (requestStatus != SeerrRequestStatus.PENDING) {
                        OutlinedButton(
                            onClick = {
                                if (isConfirmingDelete) {
                                    onDelete()
                                    isConfirmingDelete = false
                                } else {
                                    isConfirmingDelete = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusIndicator(),
                            shape = ShapeCache.smooth12,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorScheme.error,
                            ),
                        ) {
                            Icon(Tabler.Outline.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    if (isConfirmingDelete) Res.string.requests_action_delete_confirm
                                    else Res.string.requests_action_delete_request
                                )
                            )
                        }
                    }

                    // "Remove from Radarr/Sonarr" is only valid when Seerr reports
                    // the media as removable (canRemove). It is intentionally NOT
                    // gated on request status: a user may want to remove the
                    // download files even while a request is still pending.
                    if (request.canRemove) {
                        val serviceLabel = stringResource(
                            if (request.type.equals("movie", ignoreCase = true)) Res.string.requests_service_radarr
                            else Res.string.requests_service_sonarr
                        )
                        OutlinedButton(
                            onClick = {
                                if (isConfirmingRemoveFromService) {
                                    onRemoveFromService()
                                    isConfirmingRemoveFromService = false
                                } else {
                                    isConfirmingRemoveFromService = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusIndicator(),
                            shape = ShapeCache.smooth12,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorScheme.error,
                            ),
                        ) {
                            Icon(Tabler.Outline.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    if (isConfirmingRemoveFromService) Res.string.requests_action_delete_confirm
                                    else Res.string.requests_action_remove_from_service,
                                    serviceLabel
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
    if (isTv) {
        com.raulshma.jellyplay.core.ui.components.TvSafeSheet(
            onDismissRequest = onDismiss,
            content = content,
        )
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = ShapeCache.smoothTop28,
            containerColor = colorScheme.surfaceContainer,
            dragHandle = { com.raulshma.jellyplay.core.ui.components.SheetDragHandle() },
            content = content,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * String resource for an [ArrDownloadStatus], surfaced in the Download
 * row of [RequestDetailBottomSheet] when direct *arr progress is available.
 */
@androidx.compose.runtime.Composable
private fun arrStatusRes(status: ArrDownloadStatus): StringResource = when (status) {
    ArrDownloadStatus.QUEUED -> Res.string.requests_arr_status_queued
    ArrDownloadStatus.DOWNLOADING -> Res.string.requests_arr_status_downloading
    ArrDownloadStatus.PAUSED -> Res.string.requests_arr_status_paused
    ArrDownloadStatus.COMPLETED -> Res.string.requests_arr_status_completed
    ArrDownloadStatus.FAILED -> Res.string.requests_arr_status_failed
    ArrDownloadStatus.WARNING -> Res.string.requests_arr_status_warning
    ArrDownloadStatus.IMPORTED -> Res.string.requests_arr_status_imported
    ArrDownloadStatus.UNKNOWN -> Res.string.requests_arr_status_unknown
}
