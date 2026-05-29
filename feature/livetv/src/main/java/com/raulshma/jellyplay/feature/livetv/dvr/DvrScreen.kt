package com.raulshma.jellyplay.feature.livetv.dvr

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.DvrTimerStatus
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.foundation.background
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun DvrScreen(
    onBack: () -> Unit,
    viewModel: DvrViewModel = hiltViewModel(),
) {
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current
        .collectAsStateWithLifecycle()
    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = viewModel.isLoading,
        hasError = false,
        networkStatus = networkStatus,
    )

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val bottomPad = adaptiveInfo.bottomPadding(isTv)

    val backgroundColor = rememberScreenBackgroundColor()

    JellyPlayScreenScaffold(
        title = "Recordings",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(start = 12.dp),
            )
        },
    ) {
        if (viewModel.error != null && viewModel.timers.isEmpty() && viewModel.seriesTimers.isEmpty()) {
            ErrorScreen(
                message = viewModel.error!!,
                onRetry = { viewModel.load() },
            )
        } else if (viewModel.timers.isEmpty() && viewModel.seriesTimers.isEmpty()) {
            ScreenEmptyState(
                icon = Tabler.Outline.Circle,
                title = "No scheduled recordings",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = contentPad,
                    end = contentPad,
                    top = 8.dp,
                    bottom = bottomPad,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                if (viewModel.seriesTimers.isNotEmpty()) {
                    item {
                        Text(
                            text = "Series Recordings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                    items(viewModel.seriesTimers.size, key = { viewModel.seriesTimers[it].id }, contentType = { "seriesTimer" }) { index ->
                        val timer = viewModel.seriesTimers[index]
                        SeriesTimerCard(
                            timer = timer,
                            onCancel = { viewModel.cancelSeriesTimer(it) },
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        )
                    }
                }

                if (viewModel.timers.isNotEmpty()) {
                    item {
                        Text(
                            text = "Scheduled Recordings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                    items(viewModel.timers.size, key = { viewModel.timers[it].id }, contentType = { "timer" }) { index ->
                        val timer = viewModel.timers[index]
                        TimerCard(
                            timer = timer,
                            onCancel = { viewModel.cancelTimer(it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerCard(
    timer: DvrTimer,
    onCancel: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .tvFocusable().clickable { }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when (timer.status) {
                        DvrTimerStatus.RECORDING -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        DvrTimerStatus.SCHEDULED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (timer.status) {
                    DvrTimerStatus.RECORDING -> Tabler.Outline.Circle
                    else -> Tabler.Outline.Clock
                },
                contentDescription = null,
                tint = when (timer.status) {
                    DvrTimerStatus.RECORDING -> MaterialTheme.colorScheme.error
                    DvrTimerStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timer.programName,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = timer.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            val timeText = buildString {
                timer.startDate?.let { append(it.substringBefore('T')) }
                timer.startDate?.let { append(" ") }
                timer.startDate?.let { append(it.substringAfter('T', "").substringBefore('+').substringBefore('Z')) }
                append(" - ")
                timer.endDate?.let { append(it.substringAfter('T', "").substringBefore('+').substringBefore('Z')) }
            }
            if (timeText.length > 3) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = timer.status.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .tvFocusable().clickable { onCancel(timer.id) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Tabler.Outline.Trash,
                contentDescription = "Cancel recording",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SeriesTimerCard(
    timer: DvrSeriesTimer,
    onCancel: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .tvFocusable().clickable { }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Tabler.Outline.Circle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timer.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val channelName = timer.channelName
            if (channelName != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (timer.recordAnyChannel) "Any channel" else channelName ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .tvFocusable().clickable { onCancel(timer.id) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Tabler.Outline.Trash,
                contentDescription = "Cancel series recording",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
