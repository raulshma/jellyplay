package com.raulshma.jellyplay.feature.livetv.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import com.raulshma.jellyplay.feature.livetv.generated.resources.Res
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_delete
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_delete_recording_body
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_delete_recording_cd
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_delete_recording_title
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_no_recordings_available
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_section_latest_recordings

/** Recordings grid: 2 columns per row, vertical scroll. */
private const val GRID_COLUMNS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onRecordingClick: (String) -> Unit,
    viewModel: RecordingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val bottomPad = adaptiveInfo.bottomPadding(isTv)

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = uiState.recordings.size,
        tag = "recordings_init",
    )

    // Delete confirm dialog. Long-press a recording to open it.
    uiState.pendingDelete?.let { recording ->
        ConfirmDialog(
            title = stringResource(Res.string.livetv_delete_recording_title),
            message = stringResource(Res.string.livetv_delete_recording_body, recording.name),
            confirmText = stringResource(Res.string.livetv_delete),
            dismissText = stringResource(CoreUiRes.string.core_cancel),
            tone = ConfirmTone.DESTRUCTIVE,
            confirmLoading = uiState.isDeleting,
            onConfirm = { viewModel.deleteRecording() },
            onDismiss = { viewModel.dismissDeleteDialog() },
        )
    }

    when {
        uiState.isLoading && uiState.recordings.isEmpty() -> {
            ScreenLoadingState(modifier = Modifier.fillMaxSize())
        }
        uiState.error != null && uiState.recordings.isEmpty() -> {
            ErrorScreen(message = uiState.error!!, onRetry = { viewModel.load() })
        }
        uiState.recordings.isEmpty() -> {
            ScreenEmptyState(
                icon = Tabler.Outline.RecordMail,
                title = stringResource(Res.string.livetv_no_recordings_available),
            )
        }
        else -> {
            val rows = remember(uiState.recordings) { uiState.recordings.chunked(GRID_COLUMNS) }
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.load() },
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup()
                        .tvFocusRestorer()
                        .focusRequester(focusRequester),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp),
                ) {
                    if (uiState.recordings.isNotEmpty()) {
                        item {
                            SectionTitle(stringResource(Res.string.livetv_section_latest_recordings), contentPad)
                        }
                        // 2-column grid rendered as one lazy row per pair so the
                        // whole tab keeps a single vertical scroll (no nested
                        // scrollers to fight for height).
                        items(
                            items = rows,
                            key = { row -> "${row.first().id}:${row.last().id}" },
                            contentType = { "recording_row" },
                        ) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = contentPad),
                                horizontalArrangement = Arrangement.spacedBy(spacing),
                            ) {
                                row.forEach { recording ->
                                    val imageUrl = remember(recording.id, recording.imageTag) {
                                        viewModel.getImageUrl(recording.id, recording.imageTag)
                                    }
                                    RecordingCard(
                                        recording = recording,
                                        imageUrl = imageUrl,
                                        onClick = { onRecordingClick(recording.id) },
                                        onLongClick = { viewModel.showDeleteDialog(recording) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                // Trailing single card in an odd-length list spans
                                // only its own column (no artificial stretch).
                                if (row.size < GRID_COLUMNS) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                    item { Spacer(Modifier.height(bottomPad)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, contentPad: androidx.compose.ui.unit.Dp) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = contentPad, vertical = 6.dp),
    )
}

@Composable
private fun RecordingCard(
    recording: LiveTvRecording,
    imageUrl: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(ShapeCache.smooth12)
            .focusIndicator(ShapeCache.smooth12)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(Res.string.livetv_delete_recording_cd),
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(ShapeCache.smooth10)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl.isNotBlank()) {
                MediaImage(url = imageUrl, contentDescription = recording.name, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Tabler.Outline.RecordMail, contentDescription = null, modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = recording.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        recording.channelName?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
