package com.raulshma.jellyplay.feature.admin.statistics.detail

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.StaggeredSection
import com.raulshma.jellyplay.feature.admin.statistics.components.ActivityBarChart
import com.raulshma.jellyplay.feature.admin.statistics.components.CompletionRing
import com.raulshma.jellyplay.feature.admin.statistics.components.HorizontalBreakdownChart
import com.raulshma.jellyplay.feature.admin.statistics.components.SummaryStatCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserStatisticsDetailScreen(
    userId: String,
    onBack: () -> Unit,
    viewModel: UserStatisticsDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(userId) { viewModel.loadUser(userId) }
    val state by viewModel.state.collectAsState()
    val adaptiveInfo = LocalAdaptiveInfo.current

    JellyPlayScreenScaffold(
        title = state.detail.user.name.ifBlank { "User Statistics" },
        onBack = onBack,
    ) { paddingValues ->
        when {
            state.isLoading -> ScreenLoadingState()
            state.error != null -> ErrorScreen(
                message = state.error ?: "Failed to load user details",
                onRetry = { viewModel.loadUser(userId) },
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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
                    StaggeredSection(visible = true, index = 0) { UserHeaderCard(state) }
                }
                item {
                    StaggeredSection(visible = true, index = 1) {
                        StatsSummaryRow(state)
                    }
                }
                if (state.detail.activityChart.isNotEmpty()) {
                    item {
                        StaggeredSection(visible = true, index = 2) {
                            ActivityChartCard(state)
                        }
                    }
                }
                if (state.detail.typeBreakdown.isNotEmpty()) {
                    item {
                        StaggeredSection(visible = true, index = 3) {
                            BreakdownCard(
                                title = "Content Breakdown",
                                data = state.detail.typeBreakdown,
                            )
                        }
                    }
                }
                if (state.detail.genreBreakdown.isNotEmpty()) {
                    item {
                        StaggeredSection(visible = true, index = 4) {
                            BreakdownCard(
                                title = "By Genre",
                                data = state.detail.genreBreakdown,
                            )
                        }
                    }
                }
                if (state.detail.topItems.isNotEmpty()) {
                    item {
                        StaggeredSection(visible = true, index = 5) {
                            Text(
                                "Top Watched Items",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    items(
                        items = state.detail.topItems,
                        key = { it.itemId },
                    ) { item ->
                        TopItemRow(item)
                    }
                    if (state.detail.hasMoreItems) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                FilledTonalButton(
                                    onClick = { viewModel.loadMore() },
                                    enabled = !state.isLoadingMore,
                                    shape = ShapeCache.smooth16,
                                ) {
                                    Text(if (state.isLoadingMore) "Loading..." else "Load More")
                                }
                            }
                        }
                    }
                }
                if (state.detail.methodBreakdown.isNotEmpty()) {
                    item {
                        StaggeredSection(visible = true, index = 6) {
                            BreakdownCard(
                                title = "Playback Methods",
                                data = state.detail.methodBreakdown,
                            )
                        }
                    }
                }
                if (state.detail.deviceBreakdown.isNotEmpty()) {
                    item {
                        StaggeredSection(visible = true, index = 7) {
                            BreakdownCard(
                                title = "Clients & Devices",
                                data = state.detail.deviceBreakdown,
                            )
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
            label = "Total Plays",
            modifier = Modifier.weight(1f),
        )
        SummaryStatCard(
            value = state.detail.statistics.moviePlayCount.toLong(),
            label = "Movies",
            modifier = Modifier.weight(1f),
        )
        SummaryStatCard(
            value = state.detail.statistics.episodePlayCount.toLong(),
            label = "Episodes",
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
                "Activity (30 days)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            ActivityBarChart(data = state.detail.activityChart)
        }
    }
}

@Composable
private fun BreakdownCard(
    title: String,
    data: List<com.raulshma.jellyplay.core.model.ContentBreakdown>,
) {
    Card(
        shape = ShapeCache.smooth20,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalBreakdownChart(data = data)
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
