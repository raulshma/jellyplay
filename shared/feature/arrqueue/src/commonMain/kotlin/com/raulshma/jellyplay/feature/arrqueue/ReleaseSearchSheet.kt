package com.raulshma.jellyplay.feature.arrqueue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Magnet
import com.composables.icons.tabler.outline.Search
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrRelease
import com.raulshma.jellyplay.core.model.arr.ArrReleaseHistoryStatus
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.Res
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_cancel
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_grab
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_unknown_error
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_approved
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_cache_miss_body
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_empty
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_full_season
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_grab_anyway
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_grab_message
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_grab_rejected_message
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_grab_title
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_history_failed
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_history_grabbed
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_loading
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_rejected
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_retry
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_search_again
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_seeders_leechers
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_sort_age
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_sort_score
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_sort_seeders
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.releaseSearch_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The interactive release-search sheet (the DownloadDetailsSheet TvSafeSheet
 * pattern): opens from a queue row's overflow action, lists the owning
 * server's candidate releases with seeders/quality/score/approval state, and
 * grabs one — plain, or with `shouldOverride` ("Grab anyway") for a rejected
 * row. All state comes from [ReleaseSearchState] (the pure machine); this file
 * keeps only the drawing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReleaseSearchSheet(
    item: ArrQueueItem,
    sheet: ReleaseSearchState,
    onDismiss: () -> Unit,
    onSearchAgain: () -> Unit,
    onSortSelected: (ReleaseSort) -> Unit,
    onToggleInfo: (String) -> Unit,
    onRequestGrab: (ArrRelease) -> Unit,
    onDismissGrabDialog: () -> Unit,
    onConfirmGrab: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TvSafeSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            SheetHeader(
                title = stringResource(Res.string.releaseSearch_title),
                icon = Tabler.Outline.Search,
                subtitle = item.title,
                onClose = onDismiss,
            )

            when (val phase = sheet.phase) {
                is ReleaseSearchPhase.Loading -> ReleaseSearchLoadingState()

                is ReleaseSearchPhase.Empty -> ReleaseSearchMessageState(
                    text = stringResource(Res.string.releaseSearch_empty),
                )

                is ReleaseSearchPhase.CacheMiss -> ReleaseSearchRecoverableErrorState(
                    text = stringResource(Res.string.releaseSearch_cache_miss_body),
                    actionLabel = stringResource(Res.string.releaseSearch_search_again),
                    onAction = onSearchAgain,
                )

                is ReleaseSearchPhase.Error -> ReleaseSearchRecoverableErrorState(
                    text = phase.message ?: stringResource(Res.string.arrqueue_unknown_error),
                    actionLabel = stringResource(Res.string.releaseSearch_retry),
                    onAction = onSearchAgain,
                )

                is ReleaseSearchPhase.Results -> ReleaseSearchResults(
                    sheet = sheet,
                    onSortSelected = onSortSelected,
                    onToggleInfo = onToggleInfo,
                    onRequestGrab = onRequestGrab,
                )
            }
        }
    }

    // Grab confirmation — a window-level dialog layered over the sheet, the
    // queue screen's inline action dialogs' pattern. Rejected rows spell out
    // the reasons and their confirm button is the explicit "Grab anyway".
    sheet.confirmGrab?.let { release ->
        val rejected = release.needsOverride
        ConfirmDialog(
            title = stringResource(Res.string.releaseSearch_grab_title),
            message = if (rejected) {
                stringResource(
                    Res.string.releaseSearch_grab_rejected_message,
                    release.title,
                    release.rejections.joinToString("; ").ifBlank { stringResource(Res.string.releaseSearch_rejected) },
                )
            } else {
                stringResource(Res.string.releaseSearch_grab_message, release.title)
            },
            confirmText = stringResource(
                if (rejected) Res.string.releaseSearch_grab_anyway else Res.string.arrqueue_grab,
            ),
            onConfirm = onConfirmGrab,
            confirmLoading = sheet.grabbing,
            onDismiss = onDismissGrabDialog,
            dismissText = stringResource(Res.string.arrqueue_cancel),
            tone = ConfirmTone.NEUTRAL,
            icon = Tabler.Outline.Magnet,
        )
    }
}

// ── Phase bodies ──────────────────────────────────────────────────────────

@Composable
private fun ReleaseSearchLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JellyPlayCircularProgressIndicator(modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.releaseSearch_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReleaseSearchMessageState(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ReleaseSearchRecoverableErrorState(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(
            onClick = onAction,
            shape = ShapeCache.smooth12,
        ) {
            Text(actionLabel)
        }
    }
}

// ── Results ───────────────────────────────────────────────────────────────

@Composable
private fun ReleaseSearchResults(
    sheet: ReleaseSearchState,
    onSortSelected: (ReleaseSort) -> Unit,
    onToggleInfo: (String) -> Unit,
    onRequestGrab: (ArrRelease) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // Sort switch — Score / Seeders / Age.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortChip(ReleaseSort.SCORE, Res.string.releaseSearch_sort_score, sheet.sort, onSortSelected)
            SortChip(ReleaseSort.SEEDERS, Res.string.releaseSearch_sort_seeders, sheet.sort, onSortSelected)
            SortChip(ReleaseSort.AGE, Res.string.releaseSearch_sort_age, sheet.sort, onSortSelected)
        }

        // The last failed grab's message — the rows stay (unlike a search
        // failure, the list is still valid).
        sheet.grabError?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.labelMedium,
                color = StatusColors.error,
                modifier = Modifier.padding(horizontal = 16.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sheet.sortedReleases.forEach { release ->
                ReleaseRow(
                    release = release,
                    historyStatus = sheet.historyStatuses[release.guid],
                    grabbing = sheet.grabbing,
                    infoExpanded = sheet.infoExpandedGuid == release.guid,
                    onToggleInfo = { onToggleInfo(release.guid) },
                    onGrab = { onRequestGrab(release) },
                )
            }
        }
    }
}

@Composable
private fun SortChip(
    sort: ReleaseSort,
    label: StringResource,
    selected: ReleaseSort,
    onSelect: (ReleaseSort) -> Unit,
) {
    FilterChip(
        selected = selected == sort,
        onClick = { onSelect(sort) },
        label = { Text(stringResource(label), style = MaterialTheme.typography.labelMedium) },
    )
}

/** The row's status chip shape: a 15 %-tinted pill with label-small text. */
@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ── Release row ───────────────────────────────────────────────────────────

@Composable
private fun ReleaseRow(
    release: ArrRelease,
    historyStatus: ArrReleaseHistoryStatus?,
    grabbing: Boolean,
    infoExpanded: Boolean,
    onToggleInfo: () -> Unit,
    onGrab: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth12,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = releaseMetaLine(release),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                historyStatus?.let { status ->
                    Spacer(Modifier.width(8.dp))
                    val (label, color) = when (status) {
                        ArrReleaseHistoryStatus.GRABBED ->
                            Res.string.releaseSearch_history_grabbed to StatusColors.info
                        ArrReleaseHistoryStatus.FAILED ->
                            Res.string.releaseSearch_history_failed to StatusColors.error
                    }
                    StatusChip(text = stringResource(label), color = color)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Custom-format score chip (signed, score-style).
                val score = release.customFormatScore
                val scoreColor = if (score >= 0) StatusColors.success else StatusColors.error
                StatusChip(text = if (score > 0) "+$score" else "$score", color = scoreColor)
                Spacer(Modifier.width(8.dp))
                // Approval state — the rejected chip pairs with the info
                // toggle that expands the server's rejection reasons.
                val rejected = release.needsOverride
                val approvalColor = if (rejected) StatusColors.error else StatusColors.success
                StatusChip(
                    text = stringResource(
                        if (rejected) Res.string.releaseSearch_rejected else Res.string.releaseSearch_approved,
                    ),
                    color = approvalColor,
                )
                if (release.fullSeason) {
                    Spacer(Modifier.width(8.dp))
                    StatusChip(text = stringResource(Res.string.releaseSearch_full_season), color = StatusColors.pending)
                }
                Spacer(Modifier.width(8.dp))
                if (release.seeders != null || release.leechers != null) {
                    Text(
                        text = stringResource(
                            Res.string.releaseSearch_seeders_leechers,
                            release.seeders ?: 0,
                            release.leechers ?: 0,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (release.rejections.isNotEmpty()) {
                    IconButton(onClick = onToggleInfo, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Tabler.Outline.InfoCircle,
                            contentDescription = null,
                            tint = if (rejected) StatusColors.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = onGrab,
                    enabled = !grabbing,
                    shape = ShapeCache.smooth12,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(stringResource(Res.string.arrqueue_grab), style = MaterialTheme.typography.labelMedium)
                }
            }

            // The rejection reasons, behind the info expand.
            if (infoExpanded && release.rejections.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                release.rejections.forEach { reason ->
                    Text(
                        text = "• $reason",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** `indexer · quality · size · releaseGroup` — blank segments dropped. */
private fun releaseMetaLine(release: ArrRelease): String = listOf(
    release.indexer,
    release.quality,
    release.size?.formatBytes(),
    release.releaseGroup,
).filterNotNull()
    .filter { it.isNotBlank() }
    .joinToString(" · ")
