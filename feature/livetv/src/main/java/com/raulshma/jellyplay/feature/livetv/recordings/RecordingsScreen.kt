package com.raulshma.jellyplay.feature.livetv.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.model.RecordingFolder
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.livetv.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onRecordingClick: (String) -> Unit,
    onFolderClick: (RecordingFolder) -> Unit,
    viewModel: RecordingsViewModel = hiltViewModel(),
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
        itemCount = uiState.recordings.size + uiState.folders.size,
        tag = "recordings_init",
    )

    when {
        uiState.isLoading && uiState.recordings.isEmpty() && uiState.folders.isEmpty() -> {
            ScreenLoadingState(modifier = Modifier.fillMaxSize())
        }
        uiState.error != null && uiState.recordings.isEmpty() && uiState.folders.isEmpty() -> {
            ErrorScreen(message = uiState.error!!, onRetry = { viewModel.load() })
        }
        uiState.recordings.isEmpty() && uiState.folders.isEmpty() -> {
            ScreenEmptyState(
                icon = Tabler.Outline.RecordMail,
                title = stringResource(R.string.livetv_no_recordings_available),
            )
        }
        else -> {
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
                            SectionTitle(stringResource(R.string.livetv_section_latest_recordings), contentPad)
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = contentPad),
                                horizontalArrangement = Arrangement.spacedBy(spacing),
                                modifier = Modifier.tvFocusRestorer(),
                            ) {
                                items(items = uiState.recordings, key = { it.id }) { recording ->
                                    RecordingCard(
                                        recording = recording,
                                        imageUrl = viewModel.getImageUrl(recording.id, recording.imageTag),
                                        onClick = { onRecordingClick(recording.id) },
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                    if (uiState.folders.isNotEmpty()) {
                        item {
                            SectionTitle(stringResource(R.string.livetv_section_recording_folders), contentPad)
                        }
                        items(items = uiState.folders, key = { it.id }) { folder ->
                            FolderRow(
                                folder = folder,
                                contentPad = contentPad,
                                spacing = spacing,
                                onClick = { onFolderClick(folder) },
                            )
                        }
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
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(ShapeCache.smooth12)
            .focusIndicator(ShapeCache.smooth12)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 192.dp, height = 110.dp)
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

@Composable
private fun FolderRow(
    folder: RecordingFolder,
    contentPad: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = contentPad, vertical = 6.dp)
            .clip(ShapeCache.smooth12)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .focusIndicator(ShapeCache.smooth12)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Tabler.Outline.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Tabler.Outline.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}
