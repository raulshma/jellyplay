package com.raulshma.jellyplay.feature.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestStatus
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.requests.R
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.OffsetDateTime

@Composable
fun RequestListItem(
    request: SeerrRequestItem,
    mediaInfo: RequestMediaInfo?,
    isAdmin: Boolean,
    actionInProgress: Boolean,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onApprove: () -> Unit = {},
    onDecline: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val requestStatus = remember(request.status) { SeerrRequestStatus.fromValue(request.status) }
    var isConfirmingDelete by remember(request.id) { mutableStateOf(false) }
    var pendingApproval by remember(request.id) { mutableStateOf<SeerrRequestStatus?>(null) }

    LaunchedEffect(isConfirmingDelete) {
        if (isConfirmingDelete) {
            delay(3000)
            isConfirmingDelete = false
        }
    }

    val effectiveMediaStatus = if (request.is4k) request.media.status4k else request.media.status
    val mediaStatus = remember(effectiveMediaStatus) { SeerrMediaStatus.fromValue(effectiveMediaStatus) }
    val displayTitle = mediaInfo?.title ?: stringResource(R.string.requests_fallback_title, request.media.tmdbId)

    val (statusLabelRes, statusColor) = when {
        requestStatus == SeerrRequestStatus.DECLINED -> R.string.requests_status_declined to StatusColors.error
        requestStatus == SeerrRequestStatus.FAILED -> R.string.requests_status_failed to StatusColors.error
        requestStatus == SeerrRequestStatus.PENDING && mediaStatus == SeerrMediaStatus.DELETED -> R.string.requests_status_pending to StatusColors.pending
        else -> when (mediaStatus) {
            SeerrMediaStatus.AVAILABLE -> R.string.requests_status_available to StatusColors.available
            SeerrMediaStatus.PROCESSING -> R.string.requests_status_processing to StatusColors.info
            SeerrMediaStatus.PARTIALLY_AVAILABLE -> R.string.requests_status_partially_available to StatusColors.pendingLight
            SeerrMediaStatus.PENDING -> R.string.requests_status_pending to StatusColors.pending
            SeerrMediaStatus.DELETED -> R.string.requests_status_deleted to StatusColors.error
            SeerrMediaStatus.UNKNOWN -> R.string.requests_status_unknown to colorScheme.onSurfaceVariant
        }
    }
    val statusLabel = stringResource(statusLabelRes)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .focusIndicator()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = ShapeCache.smooth16,
        color = if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.5f)
        else colorScheme.surfaceContainer.copy(alpha = 0.6f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selection checkbox — only rendered while in selection mode.
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) colorScheme.primary else colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Text(
                            "✓",
                            color = colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 72.dp)
                    .clip(ShapeCache.smooth8)
                    .background(colorScheme.onSurface.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                if (mediaInfo?.posterUrl != null) {
                    MediaImage(
                        url = mediaInfo.posterUrl,
                        contentDescription = displayTitle,
                        modifier = Modifier.size(width = 48.dp, height = 72.dp),
                        contentScale = ContentScale.Crop,
                        crossfade = true,
                    )
                } else {
                    Text(
                        text = if (request.type.equals("movie", ignoreCase = true)) "🎬" else "📺",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurface,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = request.type.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    if (mediaInfo?.year != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = mediaInfo.year.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (request.seasons.isNotEmpty()) {
                    Text(
                        text = request.seasons.joinToString(", ") { "S${it.seasonNumber}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val metadataSeparator = " · "
                val modifiedPrefix = stringResource(R.string.requests_metadata_modified, "").trim()
                val relativeTimeFormats = rememberRelativeTimeFormats()
                val metadataText = remember(request, modifiedPrefix, relativeTimeFormats) {
                    buildString {
                        append(request.requestedBy.username ?: request.requestedBy.email)
                        val requestedAgo = formatRelativeTime(request.createdAt, relativeTimeFormats)
                        if (requestedAgo != null) {
                            append(metadataSeparator)
                            append(requestedAgo)
                        }
                        if (request.modifiedBy != null && request.updatedAt != request.createdAt) {
                            val modifiedAgo = formatRelativeTime(request.updatedAt, relativeTimeFormats)
                            if (modifiedAgo != null) {
                                append(metadataSeparator)
                                append(if (modifiedPrefix.isEmpty()) modifiedAgo else "$modifiedPrefix $modifiedAgo")
                            }
                        }
                        val profileName = request.profileName
                        if (profileName != null) {
                            append(metadataSeparator)
                            append(profileName)
                        }
                    }
                }
                Text(
                    text = metadataText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isAdmin) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    when {
                        requestStatus == SeerrRequestStatus.FAILED -> {
                            FilledTonalButton(
                                onClick = onRetry,
                                enabled = !actionInProgress,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .focusIndicator(),
                            ) {
                                Text(stringResource(R.string.requests_action_retry), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        requestStatus == SeerrRequestStatus.PENDING -> {
                            FilledTonalButton(
                                onClick = { pendingApproval = SeerrRequestStatus.APPROVED },
                                enabled = !actionInProgress,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .focusIndicator(),
                            ) {
                                Text(stringResource(R.string.requests_action_approve), style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { pendingApproval = SeerrRequestStatus.DECLINED },
                                enabled = !actionInProgress,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .focusIndicator(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colorScheme.errorContainer,
                                    contentColor = colorScheme.onErrorContainer,
                                ),
                            ) {
                                Text(stringResource(R.string.requests_action_decline), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = {
                                    if (isConfirmingDelete) {
                                        onDelete()
                                        isConfirmingDelete = false
                                    } else {
                                        isConfirmingDelete = true
                                    }
                                },
                                enabled = !actionInProgress,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .focusIndicator(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colorScheme.errorContainer,
                                    contentColor = colorScheme.onErrorContainer,
                                ),
                            ) {
                                Text(
                                    stringResource(if (isConfirmingDelete) R.string.requests_action_delete_confirm else R.string.requests_action_delete),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingApproval?.let { approval ->
        val isApprove = approval == SeerrRequestStatus.APPROVED
        com.raulshma.jellyplay.core.ui.components.ConfirmDialog(
            title = stringResource(if (isApprove) R.string.requests_dialog_approve_title else R.string.requests_dialog_decline_title),
            message = stringResource(if (isApprove) R.string.requests_dialog_approve_message else R.string.requests_dialog_decline_message),
            confirmText = stringResource(if (isApprove) R.string.requests_action_approve else R.string.requests_action_decline),
            dismissText = stringResource(R.string.requests_action_cancel),
            tone = if (isApprove) ConfirmTone.PRIMARY else ConfirmTone.DESTRUCTIVE,
            onConfirm = {
                if (isApprove) onApprove() else onDecline()
            },
            onDismiss = { pendingApproval = null },
        )
    }
}

/**
 * Locale-aware relative-time formats, resolved once per recomposition via
 * [androidx.compose.ui.res.stringResource] and threaded into [formatRelativeTime].
 */
private class RelativeTimeFormats(
    val justNow: String,
    val minutesAgo: String,
    val hoursAgo: String,
    val daysAgo: String,
    val monthsAgo: String,
    val yearsAgo: String,
)

@Composable
private fun rememberRelativeTimeFormats(): RelativeTimeFormats {
    return RelativeTimeFormats(
        justNow = stringResource(R.string.requests_time_just_now),
        minutesAgo = stringResource(R.string.requests_time_minutes_ago),
        hoursAgo = stringResource(R.string.requests_time_hours_ago),
        daysAgo = stringResource(R.string.requests_time_days_ago),
        monthsAgo = stringResource(R.string.requests_time_months_ago),
        yearsAgo = stringResource(R.string.requests_time_years_ago),
    )
}

private fun formatRelativeTime(dateStr: String, formats: RelativeTimeFormats): String? {
    return try {
        val date = OffsetDateTime.parse(dateStr)
        val now = OffsetDateTime.now()
        val duration = Duration.between(date, now)
        when {
            duration.toMinutes() < 1 -> formats.justNow
            duration.toMinutes() < 60 -> formats.minutesAgo.format(duration.toMinutes())
            duration.toHours() < 24 -> formats.hoursAgo.format(duration.toHours())
            duration.toDays() < 30 -> formats.daysAgo.format(duration.toDays())
            duration.toDays() < 365 -> formats.monthsAgo.format(duration.toDays() / 30)
            else -> formats.yearsAgo.format(duration.toDays() / 365)
        }
    } catch (_: Exception) {
        null
    }
}
