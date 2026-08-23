package com.raulshma.jellyplay.feature.livetv.programs

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberStableCallback
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.livetv.R
import com.raulshma.jellyplay.feature.livetv.components.RecordDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(
    onProgramClick: (LiveTvProgram) -> Unit,
    viewModel: ProgramsViewModel = hiltViewModel(),
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
        itemCount = uiState.rows.size,
        tag = "programs_init",
    )

    val onItemLongPress = remember(viewModel) { { program: LiveTvProgram -> viewModel.requestRecord(program) } }
    val getImageUrl = remember(viewModel) { { id: String, tag: String? -> viewModel.getImageUrl(id, tag) } }

    when {
        uiState.isLoading && uiState.rows.isEmpty() -> {
            ScreenLoadingState(modifier = Modifier.fillMaxSize())
        }
        uiState.error != null && uiState.rows.isEmpty() -> {
            ErrorScreen(message = uiState.error!!, onRetry = { viewModel.load() })
        }
        uiState.rows.isEmpty() && !uiState.isLoading -> {
            ScreenEmptyState(
                icon = Tabler.Outline.DeviceTv,
                title = stringResource(R.string.livetv_no_programs_available),
            )
        }
        else -> {
            PullToRefreshBox(
                isRefreshing = uiState.refreshing,
                onRefresh = { viewModel.load() },
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup()
                        .tvFocusRestorer()
                        .focusRequester(focusRequester),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomPad),
                    verticalArrangement = Arrangement.spacedBy(spacing + 8.dp),
                ) {
                    items(
                        items = uiState.rows,
                        // Section ids are fixed-distinct per ProgramsViewModel,
                        // so the id alone is a stable collision-free key (a
                        // title key would crash on duplicate titles) that
                        // survives "On Now" refreshes (a program-id-keyed row
                        // would lose scroll position whenever the top program
                        // rotates).
                        key = { row -> row.id },
                        contentType = { "program_row" },
                    ) { row ->
                        ProgramRowSection(
                            row = row,
                            contentPad = contentPad,
                            spacing = spacing,
                            onItemClick = onProgramClick,
                            onItemLongPress = onItemLongPress,
                            getImageUrl = getImageUrl,
                        )
                    }
                }
            }
        }
    }

    val dialog = uiState.recordDialog
    if (dialog !is com.raulshma.jellyplay.feature.livetv.components.RecordDialogState.Idle) {
        RecordDialog(
            state = dialog,
            onRecordOnce = { viewModel.recordOnce(it) },
            onRecordSeries = { viewModel.recordSeries(it) },
            onCancelTimer = { viewModel.cancelTimer(it) },
            onCancelSeries = { viewModel.cancelSeries(it) },
            onDismiss = { viewModel.dismissRecordDialog() },
        )
    }
}

@Composable
private fun ProgramRowSection(
    row: ProgramRow,
    contentPad: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    onItemClick: (LiveTvProgram) -> Unit,
    onItemLongPress: (LiveTvProgram) -> Unit,
    getImageUrl: (String, String?) -> String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = contentPad, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.tvFocusRestorer(),
        ) {
            items(items = row.programs, key = { it.id }) { program ->
                // Memoized per-item derivations + click lambdas keep ProgramCard
                // skippable across uiState emissions — the LibraryScreen grid
                // pattern.
                val imageUrl = remember(program.id, program.imageTag) {
                    getImageUrl(program.id, program.imageTag)
                }
                val memoizedClick = rememberStableCallback { onItemClick(program) }
                val memoizedLongPress = rememberStableCallback { onItemLongPress(program) }
                ProgramCard(
                    program = program,
                    imageUrl = imageUrl,
                    onClick = memoizedClick,
                    onLongClick = memoizedLongPress,
                )
            }
        }
    }
}

@Composable
private fun ProgramCard(
    program: LiveTvProgram,
    imageUrl: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(ShapeCache.smooth12)
            .focusIndicator(ShapeCache.smooth12)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 212.dp, height = 120.dp)
                .clip(ShapeCache.smooth10)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl.isNotBlank()) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = program.name,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Tabler.Outline.DeviceTv,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // "LIVE" pill for programs currently airing.
            if (!program.hasAired) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(ShapeCache.smooth8)
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.onError),
                    )
                    Text(
                        text = stringResource(R.string.livetv_live),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            }
            // Recording indicator
            if (!program.timerId.isNullOrEmpty() || !program.seriesTimerId.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(ShapeCache.smooth16)
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Tabler.Outline.RecordMail,
                        contentDescription = stringResource(R.string.livetv_recording_status),
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = program.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        program.channelName?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
