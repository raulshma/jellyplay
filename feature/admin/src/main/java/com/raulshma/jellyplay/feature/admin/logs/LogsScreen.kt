package com.raulshma.jellyplay.feature.admin.logs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
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
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.ActivityLogSeverity
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.SearchField
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()
    val tabs = listOf(R.string.admin_tab_log_files, R.string.admin_tab_activity_log)

    // TV focus-on-launch: focus the active tab's list once data arrives so D-pad input lands on
    // content, not the navigation drawer. Resets when the user switches tabs.
    val listFocusRequester = remember { FocusRequester() }
    val activeItemCount = when (state.selectedTabIndex) {
        0 -> if (state.selectedLogFileName == null) state.logFiles.size else state.selectedLogFileLines.size
        else -> state.activityEntries.size
    }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = activeItemCount,
        tag = "logs_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(R.string.admin_logs_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            if (state.selectedTabIndex == 1) {
                val liveFocusState = rememberTvFocusState()
                if (state.isLiveStreamActive) {
                    IconButton(
                        onClick = { viewModel.stopLiveStream() },
                        modifier = Modifier.then(liveFocusState.focusModifier).tvFocusIndicator(liveFocusState, CircleShape),
                    ) {
                        Icon(
                            Tabler.Outline.PlayerPause,
                            contentDescription = stringResource(R.string.admin_stop_live_cd),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.startLiveStream() },
                        modifier = Modifier.then(liveFocusState.focusModifier).tvFocusIndicator(liveFocusState, CircleShape),
                    ) {
                        Icon(Tabler.Outline.Activity, contentDescription = stringResource(R.string.admin_start_live_cd))
                    }
                }
            }
            val refreshFocusState = rememberTvFocusState()
            IconButton(
                onClick = { viewModel.loadInitialData() },
                modifier = Modifier.then(refreshFocusState.focusModifier).tvFocusIndicator(refreshFocusState, CircleShape),
            ) {
                Icon(Tabler.Outline.Refresh, contentDescription = stringResource(R.string.admin_refresh))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            PrimaryTabRow(
                selectedTabIndex = state.selectedTabIndex,
            ) {
                tabs.forEachIndexed { index, tabRes ->
                    Tab(
                        selected = state.selectedTabIndex == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(stringResource(tabRes)) },
                    )
                }
            }

            // Message-text filter. Applies to whichever tab is active.
            var searchQuery by remember { mutableStateOf("") }
            SearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.admin_logs_search_placeholder),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            // Resolve the filtered list once per recomposition.
            val query = searchQuery.trim()
            when (state.selectedTabIndex) {
                0 -> {
                    val filteredLines = remember(state.selectedLogFileLines, query) {
                        if (query.isEmpty()) state.selectedLogFileLines
                        else state.selectedLogFileLines.filter { query in it.text }
                    }
                    val filteredFiles = remember(state.logFiles, query) {
                        if (query.isEmpty()) state.logFiles
                        else state.logFiles.filter { query in it.name }
                    }
                    LogFilesTab(
                        logFiles = filteredFiles,
                        selectedLogFileName = state.selectedLogFileName,
                        selectedLogFileLines = filteredLines,
                        isLoadingContent = state.isLoadingLogContent,
                        isPollingActive = state.isLogPollingActive,
                        onTogglePolling = { viewModel.toggleLogPolling() },
                        onFileClick = { viewModel.loadLogFile(it) },
                        onBackToList = { viewModel.clearSelectedLogFile() },
                        bottomPadding = adaptiveInfo.bottomPadding(isTv),
                        listFocusRequester = listFocusRequester,
                    )
                }
                1 -> {
                    val filteredEntries = remember(state.activityEntries, query) {
                        if (query.isEmpty()) state.activityEntries
                        else state.activityEntries.filter { entry ->
                            query in entry.name || (entry.overview?.contains(query) == true) || query in entry.type
                        }
                    }
                    ActivityLogTab(
                        entries = filteredEntries,
                        isLiveActive = state.isLiveStreamActive,
                        liveEntryIds = state.liveEntryIds,
                        isLoadingMore = false,
                        onLoadMore = { viewModel.loadMoreActivity() },
                        bottomPadding = adaptiveInfo.bottomPadding(isTv),
                        listFocusRequester = listFocusRequester,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogFilesTab(
    logFiles: List<LogFile>,
    selectedLogFileName: String?,
    selectedLogFileLines: List<LogLine>,
    isLoadingContent: Boolean,
    isPollingActive: Boolean,
    onTogglePolling: () -> Unit,
    onFileClick: (String) -> Unit,
    onBackToList: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    listFocusRequester: FocusRequester,
) {
    if (selectedLogFileName != null || isLoadingContent) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val backFocusState = rememberTvFocusState()
                OutlinedButton(
                    onClick = onBackToList,
                    modifier = Modifier.then(backFocusState.focusModifier).tvFocusIndicator(backFocusState, ShapeCache.smooth12),
                ) {
                    Icon(
                        Tabler.Outline.ArrowLeft,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.admin_back))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    selectedLogFileName ?: stringResource(R.string.admin_loading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!isLoadingContent && selectedLogFileLines.isNotEmpty()) {
                    val pollingFocusState = rememberTvFocusState()
                    IconButton(
                        onClick = onTogglePolling,
                        modifier = Modifier.then(pollingFocusState.focusModifier).tvFocusIndicator(pollingFocusState, CircleShape),
                    ) {
                        Icon(
                            imageVector = if (isPollingActive) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                            contentDescription = stringResource(if (isPollingActive) R.string.admin_pause_live_logs else R.string.admin_resume_live_logs),
                            tint = if (isPollingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val lineCount = selectedLogFileLines.size
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.admin_n_lines, lineCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (isLoadingContent) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                JellyPlayLinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val listState = rememberLazyListState()
                
                var hasInitialScrolled by remember(selectedLogFileName) { mutableStateOf(false) }

                LaunchedEffect(selectedLogFileLines.size) {
                    if (selectedLogFileLines.isNotEmpty()) {
                        if (!hasInitialScrolled) {
                            listState.scrollToItem(selectedLogFileLines.lastIndex)
                            hasInitialScrolled = true
                        } else if (isPollingActive) {
                            listState.animateScrollToItem(selectedLogFileLines.lastIndex)
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .tvFocusRestorer()
                        .focusRequester(listFocusRequester)
                        .padding(horizontal = 8.dp),
                ) {
                    items(
                        items = selectedLogFileLines,
                        key = { line -> line.index },
                        contentType = { "logLine" },
                    ) { line ->
                        val annotatedLine = remember(line.text) { parseLogLine(line.text) }
                        
                        var isHighlighted by remember(line.index, line.addedTime) {
                            mutableStateOf(line.isNew)
                        }
                        LaunchedEffect(line.index, line.addedTime) {
                            if (line.isNew) {
                                delay(4000)
                                isHighlighted = false
                            }
                        }
                        val highlightAlpha by animateFloatAsState(
                            targetValue = if (isHighlighted) 0.15f else 0.0f,
                            animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                            label = "lineHighlightAlpha"
                        )
                        
                        Text(
                            text = annotatedLine,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .animateItem(placementSpec = lazyItemPlacementSpec())
                                .fillMaxWidth()
                                .background(
                                    color = if (highlightAlpha > 0f) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    } else {
        if (logFiles.isEmpty()) {
            ScreenEmptyState(
                icon = Tabler.Outline.FileText,
                title = stringResource(R.string.admin_no_log_files),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .tvFocusRestorer()
                    .focusRequester(listFocusRequester),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items = logFiles, key = { it.name }, contentType = { "logFile" }) { file ->
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
            .focusIndicator(ShapeCache.smooth16)
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
    liveEntryIds: Set<Long>,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    listFocusRequester: FocusRequester,
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
                stringResource(R.string.admin_live_streaming_active),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }

    if (entries.isEmpty()) {
        ScreenEmptyState(
            icon = Tabler.Outline.Activity,
            title = stringResource(R.string.admin_no_activity_log),
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .tvFocusRestorer()
                .focusRequester(listFocusRequester),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items = entries, key = { it.id }, contentType = { "activityEntry" }) { entry ->
                ActivityEntryItem(
                    entry = entry,
                    isNew = entry.id in liveEntryIds,
                )
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
private fun ActivityEntryItem(entry: ActivityLogEntry, isNew: Boolean = false) {
    val severityColor = when (entry.severity) {
        ActivityLogSeverity.ERROR, ActivityLogSeverity.FATAL -> MaterialTheme.colorScheme.error
        ActivityLogSeverity.WARNING -> StatusColors.warning
        ActivityLogSeverity.TRACE, ActivityLogSeverity.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        ActivityLogSeverity.INFORMATION -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth12,
        colors = CardDefaults.cardColors(
            containerColor = if (isNew) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (isNew) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null,
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

private val LOG_LEVEL_REGEX = Regex("\\b(ERROR|FATAL|WARN|WARNING|INFO|DEBUG|TRACE)\\b", RegexOption.IGNORE_CASE)

private fun parseLogLine(line: String): AnnotatedString = buildAnnotatedString {
    val match = LOG_LEVEL_REGEX.find(line)
    if (match != null) {
        append(line.substring(0, match.range.first))
        val level = match.value.uppercase()
        val color = when (level) {
            "ERROR", "FATAL" -> StatusColors.error
            "WARN", "WARNING" -> StatusColors.warning
            "INFO" -> StatusColors.info
            "DEBUG", "TRACE" -> StatusColors.debug
            else -> Color.Unspecified
        }
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
            append(match.value)
        }
        append(line.substring(match.range.last + 1))
    } else {
        append(line)
    }
}
