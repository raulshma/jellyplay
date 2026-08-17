package com.raulshma.jellyplay.feature.admin.statistics.detail

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.isLightColor
import com.raulshma.jellyplay.core.model.MusicStatistics
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.AnimatedSectionEntrance
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.statistics.components.ActivityBarChart
import com.raulshma.jellyplay.feature.admin.statistics.components.ComparisonCard
import com.raulshma.jellyplay.feature.admin.statistics.components.CompletionRing
import com.raulshma.jellyplay.feature.admin.statistics.components.HorizontalBreakdownChart
import com.raulshma.jellyplay.feature.admin.statistics.components.PieChart
import com.raulshma.jellyplay.feature.admin.statistics.components.StreakCard
import com.raulshma.jellyplay.feature.admin.statistics.components.SummaryStatCard
import com.raulshma.jellyplay.feature.admin.statistics.components.TrendLineChart
import com.raulshma.jellyplay.feature.admin.statistics.components.WatchTimeCard
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Share
import com.raulshma.jellyplay.feature.admin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserStatisticsDetailScreen(
    userId: String,
    onBack: () -> Unit,
    viewModel: UserStatisticsDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(userId) { viewModel.loadUser(userId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // TV focus-on-launch: focus the first card once content arrives so D-pad input lands on
    // content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = if (state.isLoading || state.error != null) 0 else 1,
        tag = "user_stats_detail_init",
    )

    JellyPlayScreenScaffold(
        title = state.detail.user.name.ifBlank { stringResource(R.string.admin_user_statistics_title) },
        onBack = onBack,
        actions = {
            if (!state.isLoading && state.error == null) {
                val shareFocusState = rememberTvFocusState()
                IconButton(
                    onClick = {
                        scope.launch {
                            shareYearInJellyfin(
                                context = context,
                                detail = state.detail,
                            )
                        }
                    },
                    modifier = Modifier.then(shareFocusState.focusModifier).tvFocusIndicator(shareFocusState, CircleShape),
                ) {
                    Icon(
                        Tabler.Outline.Share,
                        contentDescription = stringResource(R.string.admin_share_cd),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { paddingValues ->
        when {
            state.isLoading -> ScreenLoadingState()
            state.error != null -> ErrorScreen(
                message = state.error ?: stringResource(R.string.admin_failed_load_user_details),
                onRetry = { viewModel.loadUser(userId) },
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .tvFocusRestorer()
                    .focusRequester(listFocusRequester)
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 8.dp,
                    bottom = adaptiveInfo.bottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    AnimatedSectionEntrance(visible = true) { UserHeaderCard(state) }
                }
                item {
                    AnimatedSectionEntrance(visible = true) {
                        StatsSummaryRow(state)
                    }
                }
                if (state.detail.weeklyWatchTimeSec > 0 || state.detail.monthlyWatchTimeSec > 0 || state.detail.statistics.totalWatchTimeSec > 0) {
                    item {
                        AnimatedSectionEntrance(visible = true) {
                            WatchTimeCard(
                                weeklySeconds = state.detail.weeklyWatchTimeSec,
                                monthlySeconds = state.detail.monthlyWatchTimeSec,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
                if (state.detail.viewingStreak.currentStreak > 0 || state.detail.viewingStreak.longestStreak > 0) {
                    item {
                        AnimatedSectionEntrance(visible = true) {
                            StreakCard(
                                currentStreak = state.detail.viewingStreak.currentStreak,
                                longestStreak = state.detail.viewingStreak.longestStreak,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
                if (state.detail.monthlyComparison.currentMonthMinutes > 0) {
                    item {
                        AnimatedSectionEntrance(visible = true) {
                            ComparisonCard(
                                percentageChange = state.detail.monthlyComparison.percentageChange,
                                currentMinutes = state.detail.monthlyComparison.currentMonthMinutes,
                                previousMinutes = state.detail.monthlyComparison.previousMonthMinutes,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
                if (state.detail.activityChart.isNotEmpty()) {
                    item {
                        AnimatedSectionEntrance(visible = true) {
                            ActivityChartCard(state)
                        }
                    }
                }
                if (state.detail.trendData.isNotEmpty()) {
                    item {
                        AnimatedSectionEntrance(visible = true) {
                            TrendLineChartCard(state)
                        }
                    }
                }
                if (state.detail.typeBreakdown.isNotEmpty()) {
                    item {
                        AnimatedSectionEntrance(visible = true) {
                            BreakdownCard(
                                title = stringResource(R.string.admin_content_breakdown),
                                data = state.detail.typeBreakdown,
                            )
                        }
                    }
                }
                if (state.detail.genreBreakdown.isNotEmpty()) {
                    item {
                        AnimatedSectionEntrance(visible = true) {
                            BreakdownCard(
                                title = stringResource(R.string.admin_by_genre),
                                data = state.detail.genreBreakdown,
                            )
                        }
                    }
                }
                if (state.detail.genrePieData.isNotEmpty()) {
                    item {
                        AnimatedSectionEntrance(visible = true) {
                            GenrePieChartCard(state)
                        }
                    }
                }
                if (state.pluginStatus == PlaybackReportingStatus.AVAILABLE && state.detail.musicStats.topTracks.isNotEmpty()) {
                    item {
                        AnimatedSectionEntrance(visible = true) {
                            MusicStatsSection(state.detail.musicStats)
                        }
                    }
                }
                if (state.detail.methodBreakdown.isNotEmpty()) {
                    item {
                        AnimatedSectionEntrance(visible = true) {
                            BreakdownCard(
                                title = stringResource(R.string.admin_playback_methods),
                                data = state.detail.methodBreakdown,
                            )
                        }
                    }
                }
                if (state.detail.deviceBreakdown.isNotEmpty()) {
                    item {
                        AnimatedSectionEntrance(visible = true) {
                            BreakdownCard(
                                title = stringResource(R.string.admin_clients_devices),
                                data = state.detail.deviceBreakdown,
                            )
                        }
                    }
                }
                if (state.detail.topItems.isNotEmpty()) {
                    item {
                        AnimatedSectionEntrance(visible = true) {
                            Top5Section(state)
                        }
                    }
                    if (state.detail.topItems.size > 5) {
                        items(
                            items = state.detail.topItems.drop(5),
                            key = { it.itemId },
                            contentType = { "topItem" },
                        ) { item ->
                            TopItemRow(item)
                        }
                    }
                    if (state.detail.hasMoreItems) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val loadMoreFocusState = rememberTvFocusState()
                                FilledTonalButton(
                                    onClick = { viewModel.loadMore() },
                                    enabled = !state.isLoadingMore,
                                    shape = ShapeCache.smooth16,
                                    modifier = Modifier.then(loadMoreFocusState.focusModifier).tvFocusIndicator(loadMoreFocusState, ShapeCache.smooth16),
                                ) {
                                    Text(if (state.isLoadingMore) stringResource(R.string.admin_loading_dots) else stringResource(R.string.admin_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserHeaderCard(state: UserDetailState) {
    Card(
        shape = ShapeCache.smooth28,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.detail.user.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.detail.user.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!state.detail.statistics.lastSeen.isNullOrBlank()) {
                    Text(
                        "Last seen: ${state.detail.statistics.lastSeen}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.detail.statistics.completionRate > 0f) {
                CompletionRing(
                    percentage = state.detail.statistics.completionRate,
                    ringSize = 80.dp,
                )
            }
        }
    }
}

@Composable
private fun StatsSummaryRow(state: UserDetailState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryStatCard(
            value = state.detail.statistics.totalPlayCount.toLong(),
            label = stringResource(R.string.admin_stat_total_plays),
            modifier = Modifier.weight(1f),
        )
        SummaryStatCard(
            value = state.detail.statistics.moviePlayCount.toLong(),
            label = stringResource(R.string.admin_stat_movies),
            modifier = Modifier.weight(1f),
        )
        SummaryStatCard(
            value = state.detail.statistics.episodePlayCount.toLong(),
            label = stringResource(R.string.admin_stat_episodes),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActivityChartCard(state: UserDetailState) {
    Card(
        shape = ShapeCache.smooth20,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.admin_activity_30_days),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            ActivityBarChart(data = state.detail.activityChart)
        }
    }
}

@Composable
private fun TrendLineChartCard(state: UserDetailState) {
    Card(
        shape = ShapeCache.smooth20,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.admin_watch_time_trend),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.detail.averageDailyMinutes > 0) {
                    Text(
                        "Avg ${state.detail.averageDailyMinutes}m/day",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            TrendLineChart(data = state.detail.trendData)
        }
    }
}

@Composable
private fun GenrePieChartCard(state: UserDetailState) {
    Card(
        shape = ShapeCache.smooth20,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.admin_most_watched_genres),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            PieChart(data = state.detail.genrePieData)
        }
    }
}

@Composable
private fun Top5Section(state: UserDetailState) {
    Column {
        Text(
            stringResource(R.string.admin_top_watched),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp),
        )
        Spacer(Modifier.height(8.dp))
        state.detail.topItems.take(5).forEachIndexed { index, item ->
            TopItemWithRank(item, rank = index + 1)
        }
    }
}

@Composable
private fun TopItemWithRank(item: com.raulshma.jellyplay.core.model.UserTopItem, rank: Int) {
    Card(
        shape = ShapeCache.smooth12,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Medal tint is drawn at low alpha over the card surface, so the digit text must
            // contrast with the *effective* composited color rather than the raw medal tone.
            // Gold/silver are bright and fail against the pale tint they produce on light surfaces.
            val cardSurface = MaterialTheme.colorScheme.surfaceContainerLow
            val medalColor = when (rank) {
                1 -> Color(0xFFFFD700)
                2 -> Color(0xFFC0C0C0)
                3 -> Color(0xFFCD7F32)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val medalBackground = medalColor.copy(alpha = 0.2f).compositeOver(cardSurface)
            val medalText = if (rank in 1..3) {
                if (isLightColor(medalBackground)) Color.Black else Color.White
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(medalBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$rank",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = medalText,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(ShapeCache.smooth8)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.type.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                val seriesName = item.seriesName
                if (!seriesName.isNullOrBlank()) {
                    Text(
                        seriesName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "${item.playCount}x",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MusicStatsSection(musicStats: MusicStatistics) {
    Card(
        shape = ShapeCache.smooth20,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.admin_music_listening),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            if (musicStats.totalListeningHours > 0f) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "${musicStats.totalListeningHours.toInt()}h",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.admin_listening_time),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (musicStats.topArtists.isNotEmpty()) {
                Text(
                    stringResource(R.string.admin_top_artists),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                musicStats.topArtists.forEachIndexed { index, artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            artist.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${artist.value} plays",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (musicStats.topGenres.isNotEmpty()) {
                Text(
                    stringResource(R.string.admin_top_genres),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                HorizontalBreakdownChart(data = musicStats.topGenres)
                Spacer(Modifier.height(12.dp))
            }
            if (musicStats.topTracks.isNotEmpty()) {
                Text(
                    stringResource(R.string.admin_top_tracks),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                musicStats.topTracks.forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            val albumName = track.seriesName
                            if (!albumName.isNullOrBlank()) {
                                Text(
                                    albumName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        Text(
                            "${track.playCount}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (musicStats.totalListeningHours == 0f && musicStats.topTracks.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.admin_no_music_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BreakdownCard(
    title: String,
    data: List<com.raulshma.jellyplay.core.model.ContentBreakdown>,
) {
    var showChart by remember { mutableStateOf(true) }

    Card(
        shape = ShapeCache.smooth20,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                SegmentedControl(
                    selected = showChart,
                    onSelectedChange = { showChart = it }
                )
            }
            Spacer(Modifier.height(16.dp))
            if (showChart) {
                HorizontalBreakdownChart(data = data)
            } else {
                BreakdownList(data = data)
            }
        }
    }
}

@Composable
private fun SegmentedControl(
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chartBgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "chartBgColor"
    )
    val chartTextColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "chartTextColor"
    )
    val listBgColor by animateColorAsState(
        targetValue = if (!selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "listBgColor"
    )
    val listTextColor by animateColorAsState(
        targetValue = if (!selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "listTextColor"
    )

    Row(
        modifier = modifier
            .clip(ShapeCache.smoothPill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(ShapeCache.smoothPill)
                .background(chartBgColor)
                .clickable { onSelectedChange(true) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.admin_chart),
                style = MaterialTheme.typography.labelMedium,
                color = chartTextColor,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(
            modifier = Modifier
                .clip(ShapeCache.smoothPill)
                .background(listBgColor)
                .clickable { onSelectedChange(false) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.admin_list),
                style = MaterialTheme.typography.labelMedium,
                color = listTextColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun BreakdownList(
    data: List<com.raulshma.jellyplay.core.model.ContentBreakdown>,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.admin_no_breakdown_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )
    val total = data.sumOf { it.value }.coerceAtLeast(1L)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        data.forEach { item ->
            val color = colors[item.colorIndex % colors.size]
            val percentage = (item.value.toFloat() / total.toFloat()) * 100

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = String.format(Locale.getDefault(), "%.1f%%", percentage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = item.value.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TopItemRow(item: com.raulshma.jellyplay.core.model.UserTopItem) {
    Card(
        shape = ShapeCache.smooth12,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ShapeCache.smooth8)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.type.take(1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                val seriesName = item.seriesName
                if (!seriesName.isNullOrBlank()) {
                    Text(
                        seriesName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "${item.playCount}x",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private suspend fun shareYearInJellyfin(
    context: Context,
    detail: UserDetailPage,
) = withContext(Dispatchers.IO) {
    try {
        val shareText = buildString {
            append("My Year in Jellyfin\n\n")
            val totalHours = detail.statistics.totalWatchTimeSec / 3600
            if (totalHours > 0) {
                append("${totalHours} hours watched\n")
            }
            if (detail.statistics.totalPlayCount > 0) {
                append("${detail.statistics.totalPlayCount} titles played\n")
            }
            if (detail.topItems.isNotEmpty()) {
                append("\nTop Watched:\n")
                detail.topItems.take(5).forEachIndexed { i, item ->
                    append("${i + 1}. ${item.name} (${item.playCount}x)\n")
                }
            }
            if (detail.genrePieData.isNotEmpty()) {
                append("\nFavorite Genres: ${detail.genrePieData.take(3).joinToString(", ") { it.label }}\n")
            }
            if (detail.viewingStreak.longestStreak > 0) {
                append("\nLongest Streak: ${detail.viewingStreak.longestStreak} days\n")
            }
            append("\n- JellyPlay")
        }

        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "My Year in Jellyfin")
            }
            context.startActivity(Intent.createChooser(intent, "Share Stats"))
        }
    } catch (_: Exception) {
    }
}
