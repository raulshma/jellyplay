package com.raulshma.jellyplay.feature.admin.statistics

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.InfoCircle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserStatistics
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.StaggeredSection
import com.raulshma.jellyplay.feature.admin.statistics.components.SummaryStatCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserStatisticsScreen(
    onBack: () -> Unit,
    onUserDetail: (userId: String) -> Unit,
    viewModel: UserStatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val adaptiveInfo = LocalAdaptiveInfo.current

    JellyPlayScreenScaffold(
        title = "User Statistics",
        onBack = onBack,
    ) { paddingValues ->
        when {
            state.isLoading -> ScreenLoadingState()
            state.error != null -> ErrorScreen(
                message = state.error ?: "Failed to load statistics",
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
                    modifier = Modifier.fillMaxSize(),
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
            label = "Total Users",
            modifier = Modifier.weight(1f),
        )
        SummaryStatCard(
            value = activeThisWeek,
            label = "Active Now",
            modifier = Modifier.weight(1f),
        )
        SummaryStatCard(
            value = totalPlays,
            label = "Total Plays",
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
                StatPill("${user.moviePlayCount} Movies")
                StatPill("${user.episodePlayCount} Episodes")
                if (user.songPlayCount > 0) StatPill("${user.songPlayCount} Songs")
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
                        "Completion",
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

private fun formatDuration(seconds: Long): String = when {
    seconds >= 3600 -> String.format("%.1fh", seconds / 3600.0)
    seconds >= 60 -> "${seconds / 60}m"
    else -> "${seconds}s"
}
