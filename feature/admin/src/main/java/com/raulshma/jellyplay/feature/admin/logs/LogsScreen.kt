package com.raulshma.jellyplay.feature.admin.logs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Activity
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.FileText
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.ActivityLogSeverity
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColor = rememberScreenBackgroundColor()
    val tabs = listOf("Log Files", "Activity Log")

    JellyPlayScreenScaffold(
        title = "Logs",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            if (state.selectedTabIndex == 1) {
                if (state.isLiveStreamActive) {
                    IconButton(onClick = { viewModel.stopLiveStream() }) {
                        Icon(
                            Tabler.Outline.PlayerPause,
                            contentDescription = "Stop Live",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    IconButton(onClick = { viewModel.startLiveStream() }) {
                        Icon(Tabler.Outline.Activity, contentDescription = "Start Live")
                    }
                }
            }
            IconButton(onClick = { viewModel.loadInitialData() }) {
                Icon(Tabler.Outline.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            PrimaryTabRow(
                selectedTabIndex = state.selectedTabIndex,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTabIndex == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title) },
                    )
                }
            }

            when (state.selectedTabIndex) {
                0 -> LogFilesTab(
                    logFiles = state.logFiles,
                    selectedLogFileName = state.selectedLogFileName,
                    selectedLogFileContent = state.selectedLogFileContent,
                    isLoadingContent = state.isLoadingLogContent,
                    onFileClick = { viewModel.loadLogFile(it) },
                    onBackToList = { viewModel.clearSelectedLogFile() },
                    bottomPadding = adaptiveInfo.bottomPadding(isTv),
                )
                1 -> ActivityLogTab(
                    entries = state.activityEntries,
                    isLiveActive = state.isLiveStreamActive,
                    isLoadingMore = false,
                    onLoadMore = { viewModel.loadMoreActivity() },
                    bottomPadding = adaptiveInfo.bottomPadding(isTv),
                )
            }
        }
    }
}

@Composable
private fun LogFilesTab(
    logFiles: List<LogFile>,
    selectedLogFileName: String?,
    selectedLogFileContent: String?,
    isLoadingContent: Boolean,
    onFileClick: (String) -> Unit,
    onBackToList: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    if (selectedLogFileContent != null || isLoadingContent) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBackToList) {
                    Icon(
                        Tabler.Outline.ArrowLeft,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Back")
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    selectedLogFileName ?: "Loading...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isLoadingContent) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                val lines = (selectedLogFileContent ?: "").lines()
                val listState = rememberLazyListState()
                LaunchedEffect(selectedLogFileContent) {
                    if (lines.isNotEmpty()) {
                        listState.scrollToItem(lines.lastIndex)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                ) {
                    items(
                        items = lines,
                        key = { index -> index },
                    ) { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    } else {
        if (logFiles.isEmpty()) {
            ScreenEmptyState(
                icon = Tabler.Outline.FileText,
                title = "No log files found",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items = logFiles, key = { it.name }) { file ->
                    LogFileItem(file = file, onClick = { onFileClick(file.name) })
                }
            }
        }
    }
}

@Composable
private fun LogFileItem(file: LogFile, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "logFileScale",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Tabler.Outline.FileText,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    if (file.dateModified.isNotBlank()) {
                        Text(
                            file.dateModified.take(19).replace("T", " "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (file.size > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatFileSize(file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityLogTab(
    entries: List<ActivityLogEntry>,
    isLiveActive: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= listState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingMore) {
            onLoadMore()
        }
    }

    if (isLiveActive) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Live streaming active",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }

    if (entries.isEmpty()) {
        ScreenEmptyState(
            icon = Tabler.Outline.Activity,
            title = "No activity log entries",
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items = entries, key = { it.id }) { entry ->
                ActivityEntryItem(entry = entry)
            }
            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityEntryItem(entry: ActivityLogEntry) {
    val severityColor = when (entry.severity) {
        ActivityLogSeverity.ERROR, ActivityLogSeverity.FATAL -> MaterialTheme.colorScheme.error
        ActivityLogSeverity.WARNING -> Color(0xFFFF9800)
        ActivityLogSeverity.TRACE, ActivityLogSeverity.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        ActivityLogSeverity.INFORMATION -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth12,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(severityColor),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.overview?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        entry.date.take(19).replace("T", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.type.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(ShapeCache.smooth4)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                entry.type,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}
