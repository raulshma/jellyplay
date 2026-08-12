package com.raulshma.jellyplay.feature.details

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.util.formatFileSize
import com.raulshma.jellyplay.feature.details.R

/**
 * Full-screen Sonarr-style series management: collapsible seasons, per-episode
 * monitor toggle / search / delete, season-level search monitored / monitor all,
 * and series-level refresh / refresh & scan / search.
 */
@Composable
fun ManageSeriesScreen(
    seriesId: String,
    onBack: () -> Unit,
    viewModel: ManageSeriesViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backgroundColor = rememberScreenBackgroundColor()
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val contentPad = adaptiveInfo.contentPadding(isTv)

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearUserMessage()
        }
    }

    val title = uiState.series?.title ?: stringResource(R.string.detail_manage_title)

    Box(Modifier.fillMaxSize()) {
        JellyPlayScreenScaffold(
            title = title,
            onBack = onBack,
            backgroundColor = backgroundColor,
            actions = {
                if (uiState.actionTarget is ActionTarget.Series) {
                    JellyPlayLoadingIndicator(
                        modifier = Modifier.size(20.dp).padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        ) { innerPadding ->
            when {
                uiState.isLoading -> LoadingScreen()
                uiState.error != null && uiState.series == null -> ErrorScreen(
                    message = uiState.error!!,
                    onRetry = { viewModel.load(seriesId) },
                )
                uiState.series == null -> ErrorScreen(
                    message = stringResource(R.string.detail_manage_not_tracked),
                )
                else -> ManageSeriesContent(
                    state = uiState,
                    contentPad = contentPad,
                    bottomPad = innerPadding.calculateBottomPadding(),
                    onRefreshSeries = viewModel::refreshSeries,
                    onRefreshAndScan = viewModel::refreshAndScan,
                    onSearchSeries = viewModel::searchSeries,
                    onToggleSeasonExpanded = viewModel::toggleSeasonExpanded,
                    onSearchSeason = viewModel::searchSeason,
                    onToggleSeasonMonitor = viewModel::toggleSeasonMonitor,
                    onToggleEpisodeMonitored = viewModel::toggleEpisodeMonitored,
                    onSearchEpisode = viewModel::searchEpisode,
                    onRequestDeleteEpisode = viewModel::requestDeleteEpisode,
                )
            }
        }

        // Snackbar host — sits above the scaffold content.
        com.raulshma.jellyplay.core.ui.components.JellyPlaySnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )
    }

    // Delete-file confirmation dialog.
    uiState.pendingDeleteEpisode?.let { episode ->
        ConfirmDialog(
            title = stringResource(R.string.detail_manage_delete_title),
            message = stringResource(
                R.string.detail_manage_delete_message,
                episode.title,
                "S${episode.seasonNumber}E${episode.episodeNumber}",
            ),
            confirmText = stringResource(R.string.detail_manage_delete_confirm),
            dismissText = stringResource(R.string.detail_cancel),
            icon = Tabler.Outline.Trash,
            tone = ConfirmTone.DESTRUCTIVE,
            onConfirm = { viewModel.confirmDeleteEpisode() },
            onDismiss = { viewModel.cancelDeleteEpisode() },
        )
    }
}

@Composable
private fun ManageSeriesContent(
    state: ManageSeriesUiState,
    contentPad: Dp,
    bottomPad: Dp,
    onRefreshSeries: () -> Unit,
    onRefreshAndScan: () -> Unit,
    onSearchSeries: () -> Unit,
    onToggleSeasonExpanded: (Int) -> Unit,
    onSearchSeason: (Int) -> Unit,
    onToggleSeasonMonitor: (Int) -> Unit,
    onToggleEpisodeMonitored: (ArrSeriesEpisode) -> Unit,
    onSearchEpisode: (ArrSeriesEpisode) -> Unit,
    onRequestDeleteEpisode: (ArrSeriesEpisode) -> Unit,
) {
    val listState = rememberLazyListState()
    val seriesInFlight = (state.actionTarget as? ActionTarget.Series)?.action

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .tvFocusRestorer(),
        contentPadding = PaddingValues(
            start = contentPad,
            end = contentPad,
            bottom = bottomPad + 80.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Series header: monitored indicator.
        item(key = "series_header") {
            SeriesHeaderRow(state = state)
        }

        // Series-level action buttons. Each shows its own spinner (keyed to a
        // specific SeriesAction) so a press is reflected only on its own button.
        item(key = "series_actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeriesActionButton(
                    label = stringResource(R.string.detail_manage_refresh_metadata),
                    icon = Tabler.Outline.Refresh,
                    onClick = onRefreshSeries,
                    modifier = Modifier.weight(1f),
                    loading = seriesInFlight == SeriesAction.REFRESH,
                )
                SeriesActionButton(
                    label = stringResource(R.string.detail_manage_refresh_scan),
                    icon = Tabler.Outline.Refresh,
                    onClick = onRefreshAndScan,
                    modifier = Modifier.weight(1f),
                    loading = seriesInFlight == SeriesAction.REFRESH_AND_SCAN,
                )
                SeriesActionButton(
                    label = stringResource(R.string.detail_manage_search_series),
                    icon = Tabler.Outline.Search,
                    onClick = onSearchSeries,
                    modifier = Modifier.weight(1f),
                    loading = seriesInFlight == SeriesAction.SEARCH,
                )
            }
        }

        // Season sections.
        state.episodesBySeason.forEach { (seasonNumber, episodes) ->
            val stats = state.seasonStats(seasonNumber)
            val expanded = seasonNumber in state.expandedSeasons

            item(key = "season_header_$seasonNumber") {
                SeasonHeader(
                    seasonNumber = seasonNumber,
                    stats = stats,
                    expanded = expanded,
                    onToggleExpanded = { onToggleSeasonExpanded(seasonNumber) },
                    onSearch = { onSearchSeason(seasonNumber) },
                    onToggleMonitor = { onToggleSeasonMonitor(seasonNumber) },
                    isSearching = (state.actionTarget as? ActionTarget.Season)?.seasonNumber == seasonNumber,
                )
            }

            if (expanded) {
                items(items = episodes, key = { it.id }) { episode ->
                    // Memoize per-episode so the row stays skippable: without
                    // this, three fresh lambdas are allocated per visible row
                    // per recomposition, forcing EpisodeRow to recompose even
                    // when its inputs are unchanged.
                    val onToggleMonitored = remember(episode.id) { { onToggleEpisodeMonitored(episode) } }
                    val onSearch = remember(episode.id) { { onSearchEpisode(episode) } }
                    val onDelete = remember(episode.id) { { onRequestDeleteEpisode(episode) } }
                    EpisodeRow(
                        episode = episode,
                        isLoading = (state.actionTarget as? ActionTarget.Episode)?.episodeId == episode.id,
                        onToggleMonitored = onToggleMonitored,
                        onSearch = onSearch,
                        onDelete = onDelete,
                    )
                }
            }
        }

        if (state.episodesBySeason.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.detail_manage_no_episodes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesHeaderRow(state: ManageSeriesUiState) {
    val series = state.series ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        // Monitored indicator.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (series.monitored) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                contentDescription = null,
                tint = if (series.monitored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (series.monitored) R.string.detail_manage_monitored else R.string.detail_manage_unmonitored,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (series.monitored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            // Total storage used by downloaded episode files.
            if (state.totalStorageBytes > 0) {
                Text(
                    text = stringResource(R.string.detail_manage_disk_used_format, formatFileSize(state.totalStorageBytes)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Series on-disk path (Sonarr exposes this on the series resource).
        series.path?.let { path ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SeriesActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier,
    ) {
        if (loading) {
            JellyPlayLoadingIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun SeasonHeader(
    seasonNumber: Int,
    stats: ManageSeriesUiState.SeasonStats,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSearch: () -> Unit,
    onToggleMonitor: () -> Unit,
    isSearching: Boolean,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val focusState = rememberTvFocusState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth14)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(focusState.focusModifier)
                .tvFocusIndicator(focusState, ShapeCache.smooth16)
                .clickable { onToggleExpanded() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Tabler.Outline.ChevronDown else Tabler.Outline.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (seasonNumber == 0) {
                    stringResource(R.string.detail_manage_season_specials)
                } else {
                    stringResource(R.string.detail_manage_season_format, seasonNumber)
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    R.string.detail_manage_season_count_format,
                    stats.downloaded,
                    stats.total,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (isSearching) {
                JellyPlayLoadingIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onSearch) {
                Icon(
                    Tabler.Outline.Search,
                    contentDescription = stringResource(R.string.detail_manage_cd_search_season),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onToggleMonitor) {
                Icon(
                    if (stats.monitored == stats.total) Tabler.Outline.EyeOff else Tabler.Outline.Eye,
                    contentDescription = stringResource(R.string.detail_manage_cd_toggle_monitor_season),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: ArrSeriesEpisode,
    isLoading: Boolean,
    onToggleMonitored: () -> Unit,
    onSearch: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth14)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Monitor toggle.
        IconButton(onClick = onToggleMonitored) {
            Icon(
                imageVector = if (episode.monitored) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                contentDescription = stringResource(R.string.detail_manage_cd_toggle_monitor_episode),
                tint = if (episode.monitored) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${episode.seasonNumber}×${episode.episodeNumber.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status badge — Sonarr-style: considers air date so an episode
                // that hasn't aired yet reads "Unaired", not "Missing".
                val (statusText, statusColor) = episodeStatus(episode)
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = CircleShape,
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                // Air date (Sonarr shows it on every episode row).
                episode.airDateUtc?.let { airDate ->
                    val formatted = formatAirDate(airDate) ?: return@let
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                episode.quality?.let { q ->
                    Text(
                        text = q,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                episode.fileSizeBytes?.let { bytes ->
                    Text(
                        text = formatFileSize(bytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isLoading) {
                    Spacer(Modifier.weight(1f))
                    JellyPlayLoadingIndicator(
                        modifier = Modifier.size(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Per-episode overflow menu.
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Tabler.Outline.DotsVertical,
                    contentDescription = stringResource(R.string.detail_manage_cd_episode_menu),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.detail_manage_search)) },
                    onClick = { menuExpanded = false; onSearch() },
                    leadingIcon = { Icon(Tabler.Outline.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (episode.monitored) R.string.detail_manage_unmonitor
                                else R.string.detail_manage_monitor,
                            ),
                        )
                    },
                    onClick = { menuExpanded = false; onToggleMonitored() },
                    leadingIcon = {
                        Icon(
                            if (episode.monitored) Tabler.Outline.EyeOff else Tabler.Outline.Eye,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                if (episode.hasDownload) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.detail_manage_delete_file),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = { menuExpanded = false; onDelete() },
                        leadingIcon = {
                            Icon(
                                Tabler.Outline.Trash,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Sonarr-style air-date formatting. Sonarr returns ISO-8601 UTC ("2025-03-04T00:00:00Z");
 * we display the date portion in a short locale-friendly form. Returns null on parse failure.
 */
private fun formatAirDate(isoUtc: String): String? {
    val datePart = isoUtc.substringBefore('T')
    val parsed = runCatching { java.time.LocalDate.parse(datePart) }.getOrNull() ?: return null
    val formatted = runCatching {
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
            .withLocale(java.util.Locale.getDefault())
            .format(parsed)
    }.getOrNull() ?: return datePart
    return formatted
}

/**
 * Sonarr-style episode status, considering air date. Mirrors the Sonarr web UI:
 * - Has a file → "Downloaded"
 * - No file, airs in the future → "Unaired"
 * - No file, already aired, monitored → "Missing"
 * - No file, not monitored → "Unmonitored"
 *
 * Returns the localized label and a [Color] for the badge. The [Color] capture is
 * fine here because the caller ([EpisodeRow]) is a @Composable and reads the
 * current theme snapshot at call time.
 */
@Composable
private fun episodeStatus(episode: ArrSeriesEpisode): Pair<String, androidx.compose.ui.graphics.Color> {
    val cs = MaterialTheme.colorScheme
    return when {
        episode.hasFile -> stringResource(R.string.detail_manage_status_downloaded) to cs.primary
        !episode.monitored -> stringResource(R.string.detail_manage_status_unmonitored) to cs.onSurfaceVariant
        isUnaired(episode) -> stringResource(R.string.detail_manage_status_unaired) to cs.tertiary
        else -> stringResource(R.string.detail_manage_status_missing) to cs.error
    }
}

/** True when the episode has no file and its air date is today or later. */
private fun isUnaired(episode: ArrSeriesEpisode): Boolean {
    val airDate = episode.airDateUtc?.substringBefore('T') ?: return false
    val parsed = runCatching { java.time.LocalDate.parse(airDate) }.getOrNull() ?: return false
    // Treat "airing today" as unaired until the day passes (matches Sonarr).
    return !parsed.isBefore(java.time.LocalDate.now())
}
