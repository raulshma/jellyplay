package com.raulshma.jellyplay.feature.downloads.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.episodeContextLine
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_close
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_action
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_action
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_batch_checking
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_batch_empty
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_batch_title
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_media_changed
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_progress
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_resync_all
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet listing every download flagged for an update, with a per-item
 * resync action and a batch "sync all". Renders live progress from the sync
 * manager: each row shows its current phase (pending/working/done/error) and an
 * aggregate progress line runs while the batch is active.
 *
 * "Force resync" lives in the bottom action row beside Close so the resync icon
 * stays the single freshness hub while still offering the granular,
 * user-directed flow via progressive disclosure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsResyncSheet(
    updateRows: List<com.raulshma.jellyplay.core.model.OfflineSyncUpdate>,
    checking: Boolean,
    progress: com.raulshma.jellyplay.core.model.ResyncBatchProgress,
    onResyncAll: (List<String>) -> Unit,
    onResyncOne: (String) -> Unit,
    onForceResync: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            // Header: just the title + icon. The "Force resync" action moved to
            // the bottom action row beside Close so the two terminal controls
            // live together (matches the force-resync sheet's footer).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Tabler.Outline.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(Res.string.downloads_resync_batch_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            when {
                checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    JellyPlayCircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(Res.string.downloads_resync_batch_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                updateRows.isEmpty() -> Text(
                    stringResource(Res.string.downloads_resync_batch_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    if (progress.active) {
                        val done = progress.completed
                        Text(
                            stringResource(Res.string.downloads_resync_progress, done, progress.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(updateRows, key = { _, row -> row.id }) { _, row ->
                            val itemProgress = progress.items[row.id]
                            ResyncSheetRow(
                                update = row,
                                phase = itemProgress?.phase,
                                onResync = { onResyncOne(row.id) },
                            )
                        }
                    }
                    androidx.compose.material3.Button(
                        onClick = { onResyncAll(updateRows.map { it.id }) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !progress.active,
                    ) {
                        Icon(Tabler.Outline.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.downloads_resync_resync_all))
                    }
                }
            }
            // Bottom action row: Force resync (progressive disclosure of the
            // granular flow) sits beside Close so the two terminal controls
            // share a row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.TextButton(
                    onClick = onForceResync,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Tabler.Outline.RefreshAlert, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.downloads_force_resync_action))
                }
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.downloads_close))
                }
            }
        }
    }
}

@Composable
private fun ResyncSheetRow(
    update: com.raulshma.jellyplay.core.model.OfflineSyncUpdate,
    phase: com.raulshma.jellyplay.core.model.ResyncPhase?,
    onResync: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (phase) {
            com.raulshma.jellyplay.core.model.ResyncPhase.WORKING ->
                JellyPlayCircularProgressIndicator(modifier = Modifier.size(18.dp))
            com.raulshma.jellyplay.core.model.ResyncPhase.DONE ->
                Icon(Tabler.Outline.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            com.raulshma.jellyplay.core.model.ResyncPhase.ERROR ->
                Icon(Tabler.Outline.AlertTriangle, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            else ->
                Icon(Tabler.Outline.AlertCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                update.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Episode context line (SXXEXX · series) so episodes are identifiable
            // in the flat sheet list — same shape and styling as the downloads
            // list row (bold tag + plain series), via the shared helper.
            episodeContextLine(
                mediaType = update.mediaType,
                seriesName = update.seriesName,
                seasonNumber = update.seasonNumber,
                episodeNumber = update.episodeNumber,
            )?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (update.mediaFileChanged) {
                Text(
                    stringResource(Res.string.downloads_resync_media_changed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (phase == null || phase == com.raulshma.jellyplay.core.model.ResyncPhase.PENDING ||
            phase == com.raulshma.jellyplay.core.model.ResyncPhase.ERROR
        ) {
            androidx.compose.material3.TextButton(onClick = onResync) {
                Text(stringResource(Res.string.downloads_resync_action))
            }
        }
    }
}
