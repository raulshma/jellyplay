package com.raulshma.jellyplay.feature.admin.statistics

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowsUpDown
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.FileDownload
import com.composables.icons.tabler.outline.InfoCircle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserStatistics
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.StaggeredSection
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.R
import com.raulshma.jellyplay.feature.admin.statistics.components.SummaryStatCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserStatisticsScreen(
    onBack: () -> Unit,
    onUserDetail: (userId: String) -> Unit,
    viewModel: UserStatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val context = LocalContext.current
    val userMessageBus = com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus.current
    var showSortMenu by remember { mutableStateOf(false) }

    // TV focus-on-launch: focus the first user card once data arrives so D-pad input lands on
    // content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = if (state.isLoading || state.error != null) 0 else state.users.size.coerceAtLeast(1),
        tag = "user_stats_init",
    )

    if (state.shareRequested) {
        LaunchedEffect(state.shareRequested) {
            shareUserStatsCsv(context, state.users)
            userMessageBus.info(context.getString(R.string.user_stats_export_shared))
            viewModel.consumeExportRequest()
        }
    }

    JellyPlayScreenScaffold(
        title = stringResource(R.string.admin_user_statistics_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { showSortMenu = true }) {
                Icon(
                    Tabler.Outline.ArrowsUpDown,
                    contentDescription = stringResource(R.string.user_stats_sort_cd),
                )
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
            ) {
                UserStatisticsSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.labelRes)) },
                        onClick = {
                            viewModel.setSort(sort)
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (state.sort == sort) {
                                Icon(Tabler.Outline.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            } else {
                                Spacer(modifier = Modifier.size(18.dp))
                            }
                        },
                    )
                }
            }
            IconButton(onClick = { viewModel.requestExport() }) {
                Icon(
                    Tabler.Outline.FileDownload,
                    contentDescription = stringResource(R.string.user_stats_export_cd),
                )
            }
        },
    ) { paddingValues ->
        when {
            state.isLoading -> ScreenLoadingState()
            state.error != null -> ErrorScreen(
                message = state.error ?: stringResource(R.string.admin_failed_load_statistics),
                onRetry = { viewModel.loadStatistics() },
            )
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .tvFocusRestorer()
                        .focusRequester(listFocusRequester),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = adaptiveInfo.bottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        StaggeredSection(visible = true, index = 0) {
                            PluginStatusBanner(pluginStatus = state.pluginStatus)
                        }
                    }

                    item {
                        StaggeredSection(visible = true, index = 1) {
                            SummaryRow(
                                totalUsers = state.totalUsers.toLong(),
                                activeThisWeek = state.activeThisWeek.toLong(),
                                totalPlays = state.totalPlays.toLong(),
                            )
                        }
                    }

                    items(
                        items = state.users,
                        key = { it.userId },
                        contentType = { "userStat" },
                    ) { user ->
                        StaggeredSection(visible = true, index = state.users.indexOf(user) + 2) {
                            UserStatisticsCard(
                                user = user,
                                pluginStatus = state.pluginStatus,
                                onClick = { onUserDetail(user.userId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginStatusBanner(pluginStatus: PlaybackReportingStatus) {
    if (pluginStatus == PlaybackReportingStatus.UNAVAILABLE) {
        Card(
            shape = ShapeCache.smooth16,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Tabler.Outline.InfoCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Limited stats available. Install the Playback Reporting plugin for watch time data and detailed analytics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    totalUsers: Long,
    activeThisWeek: Long,
    totalPlays: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryStatCard(
            value = totalUsers,
            label = stringResource(R.string.admin_stat_total_users),
            modifier = Modifier.weight(1f),
        )
        SummaryStatCard(
            value = activeThisWeek,
            label = stringResource(R.string.admin_stat_active_now),
            modifier = Modifier.weight(1f),
        )
        SummaryStatCard(
            value = totalPlays,
            label = stringResource(R.string.admin_stat_total_plays),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UserStatisticsCard(
    user: UserStatistics,
    pluginStatus: PlaybackReportingStatus,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "userCardScale",
    )
    val containerColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )
    val avatarColor = containerColors[user.userId.hashCode().mod(containerColors.size)]

    Card(
        shape = ShapeCache.smooth20,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusIndicator(ShapeCache.smooth20)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        user.userName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            user.userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (user.isAdmin) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(ShapeCache.smoothPill)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "Admin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        if (user.isCurrentlyActive) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                    if (!user.lastSeen.isNullOrBlank()) {
                        Text(
                            "Last seen: ${user.lastSeen}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatPill("${user.moviePlayCount} " + stringResource(R.string.admin_stat_movies))
                StatPill("${user.episodePlayCount} " + stringResource(R.string.admin_stat_episodes))
                if (user.songPlayCount > 0) StatPill("${user.songPlayCount} " + stringResource(R.string.admin_stat_songs))
                if (pluginStatus == PlaybackReportingStatus.AVAILABLE && user.totalWatchTimeSec > 0) {
                    StatPill(formatDuration(user.totalWatchTimeSec))
                }
            }

            if (user.completionRate > 0f) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.admin_completion),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.2f),
                    )
                    JellyPlayLinearProgressIndicator(
                        progress = { user.completionRate },
                        modifier = Modifier
                            .weight(0.8f)
                            .height(4.dp)
                            .clip(ShapeCache.smooth4),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPill(text: String) {
    Box(
        modifier = Modifier
            .clip(ShapeCache.smoothPill)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatDuration(seconds: Long): String =
    com.raulshma.jellyplay.core.ui.components.formatDurationApproxSeconds(seconds)

private fun shareUserStatsCsv(context: Context, users: List<UserStatistics>) {
    val header = "User,Plays,Movies,Episodes,Songs,WatchTimeSec,CompletionRate"
    val rows = users.joinToString("\n") { u ->
        val name = "\"" + u.userName.replace("\"", "\"\"") + "\""
        listOf(
            name,
            u.totalPlayCount.toString(),
            u.moviePlayCount.toString(),
            u.episodePlayCount.toString(),
            u.songPlayCount.toString(),
            u.totalWatchTimeSec.toString(),
            u.completionRate.toString(),
        ).joinToString(",")
    }
    val csv = "$header\n$rows"
    val file = File(context.cacheDir, "user_stats_${System.currentTimeMillis()}.csv")
    file.writeText(csv)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Export CSV"))
}
