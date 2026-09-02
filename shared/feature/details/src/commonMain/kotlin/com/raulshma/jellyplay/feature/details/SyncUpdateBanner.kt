package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertCircle
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.OfflineSyncState
import com.raulshma.jellyplay.core.model.SyncStatus
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_close
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_action
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_change_images
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_change_media
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_change_metadata
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_checking
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_complete
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_description
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_in_progress
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_media_changed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_media_explanation
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_redownload
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_up_to_date
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resync_update_available
import org.jetbrains.compose.resources.stringResource

/**
 * Inline freshness banner shown above the [DownloadInfoCard]. Ports
 * `OfflineDetailScreen.SyncUpdateBanner` verbatim — the full 6-state branching
 * (resync Working/Done/Error + sync CHECKING/UPDATE_AVAILABLE/ERROR) with the
 * distinct error tint when [OfflineSyncState.mediaFileChanged].
 *
 * Tappable -> opens the [ResyncSheet]. Hidden (renders a zero-height spacer)
 * unless there's something to act on.
 */
@Composable
internal fun SyncUpdateBanner(
    syncState: OfflineSyncState?,
    resyncState: ResyncUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resync = resyncBannerContent(resyncState)
    when {
        resync != null -> ResyncBannerRow(
            icon = resync.icon,
            tint = resync.tint,
            text = resync.text,
            progress = resync.progress,
            onClick = onClick,
            modifier = modifier,
        )
        syncState?.status == SyncStatus.ERROR -> ResyncBannerRow(
            icon = Tabler.Outline.AlertTriangle,
            tint = MaterialTheme.colorScheme.error,
            text = stringResource(Res.string.detail_resync_failed),
            onClick = onClick,
            modifier = modifier,
        )
        syncState?.status == SyncStatus.CHECKING -> ResyncBannerRow(
            icon = Tabler.Outline.Refresh,
            tint = MaterialTheme.colorScheme.primary,
            text = stringResource(Res.string.detail_resync_checking),
            progress = true,
            onClick = onClick,
            modifier = modifier,
        )
        syncState?.status == SyncStatus.UPDATE_AVAILABLE -> ResyncBannerRow(
            icon = Tabler.Outline.AlertCircle,
            tint = if (syncState.mediaFileChanged) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.tertiary,
            text = if (syncState.mediaFileChanged) {
                stringResource(Res.string.detail_resync_media_changed)
            } else {
                stringResource(Res.string.detail_resync_update_available)
            },
            onClick = onClick,
            modifier = modifier,
        )
        else -> Spacer(Modifier.height(0.dp))
    }
}

/** Resolved (icon / tint / text / spinner) for a resync/re-download action state. */
private data class ResyncBannerContent(
    val icon: ImageVector,
    val tint: Color,
    val text: String,
    val progress: Boolean,
)

/**
 * Shared inline status text for a resync/re-download action state, or null when
 * idle. Single source for the resync strings so the banner and sheet no longer
 * resolve them independently. A completed resync whose result still flags a
 * changed media file surfaces the re-download prompt instead of "complete" —
 * that payload was previously discarded.
 */
@Composable
private fun resyncStatusText(state: ResyncUiState): String? = when (state) {
    ResyncUiState.Working -> stringResource(Res.string.detail_resync_in_progress)
    is ResyncUiState.Done -> if (state.result.mediaFileChanged) {
        stringResource(Res.string.detail_resync_media_changed)
    } else {
        stringResource(Res.string.detail_resync_complete)
    }
    is ResyncUiState.Error -> stringResource(Res.string.detail_resync_failed)
    ResyncUiState.Idle -> null
}

/** Banner row content for a resync state, or null when idle. */
@Composable
private fun resyncBannerContent(state: ResyncUiState): ResyncBannerContent? {
    val text = resyncStatusText(state) ?: return null
    return when (state) {
        ResyncUiState.Working -> ResyncBannerContent(
            icon = Tabler.Outline.Refresh,
            tint = MaterialTheme.colorScheme.primary,
            text = text,
            progress = true,
        )
        is ResyncUiState.Done -> if (state.result.mediaFileChanged) {
            ResyncBannerContent(
                icon = Tabler.Outline.AlertCircle,
                tint = MaterialTheme.colorScheme.error,
                text = text,
                progress = false,
            )
        } else {
            ResyncBannerContent(
                icon = Tabler.Outline.Check,
                tint = MaterialTheme.colorScheme.primary,
                text = text,
                progress = false,
            )
        }
        is ResyncUiState.Error -> ResyncBannerContent(
            icon = Tabler.Outline.AlertTriangle,
            tint = MaterialTheme.colorScheme.error,
            text = text,
            progress = false,
        )
        ResyncUiState.Idle -> null
    }
}

@Composable
internal fun ResyncBannerRow(
    icon: ImageVector,
    tint: Color,
    text: String,
    progress: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (progress) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = tint,
            )
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Tabler.Outline.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Resync detail bottom sheet. Ports `OfflineDetailScreen.OfflineResyncSheet`:
 * lists what changed (metadata/images/media) and offers a resync action with
 * live status. For a media-file change it offers the full re-download path
 * (a metadata/images resync can't fix it).
 *
 * Uses [TvSafeSheet] so D-pad focus works on TV (matching the rest of the
 * detail screen's sheet handling).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResyncSheet(
    syncState: OfflineSyncState?,
    resyncState: ResyncUiState,
    onResync: () -> Unit,
    onRedownloadMedia: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    TvSafeSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Tabler.Outline.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(Res.string.detail_resync_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            val status = syncState?.status
            if (status == SyncStatus.UPDATE_AVAILABLE || status == SyncStatus.CHECKING) {
                Text(
                    stringResource(Res.string.detail_resync_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // syncState is non-null here (status was read off it) — but keep the
                // safe-call for defensive null-safety if the upstream contract widens.
                val state = syncState
                if (state != null && state.metadataChanged) {
                    ResyncChangeChip(stringResource(Res.string.detail_resync_change_metadata))
                }
                if (state != null && state.imagesChanged) {
                    ResyncChangeChip(stringResource(Res.string.detail_resync_change_images))
                }
                if (state != null && state.mediaFileChanged) {
                    ResyncChangeChip(stringResource(Res.string.detail_resync_change_media), error = true)
                    Text(
                        stringResource(Res.string.detail_resync_media_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    stringResource(Res.string.detail_resync_up_to_date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (resyncState) {
                is ResyncUiState.Working -> resyncStatusText(resyncState)?.let { text ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is ResyncUiState.Error -> resyncStatusText(resyncState)?.let { base ->
                    Text(
                        "$base: ${resyncState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {}
            }

            if (syncState?.needsResync == true && resyncState !is ResyncUiState.Working) {
                Button(
                    onClick = onResync,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Tabler.Outline.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.detail_resync_action))
                }
            }
            // When only the media file changed, offer the full re-download path —
            // a metadata/images resync can't fix it. Error-toned so it reads as
            // destructive (it deletes the existing file first).
            if (syncState?.mediaFileChanged == true && resyncState !is ResyncUiState.Working) {
                Button(
                    onClick = onRedownloadMedia,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Tabler.Outline.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.detail_resync_redownload))
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.detail_close))
            }
        }
    }
}

@Composable
private fun ResyncChangeChip(text: String, error: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (error) Tabler.Outline.AlertTriangle else Tabler.Outline.Check,
            contentDescription = null,
            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
