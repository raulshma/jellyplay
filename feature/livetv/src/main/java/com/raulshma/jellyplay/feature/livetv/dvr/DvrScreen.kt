package com.raulshma.jellyplay.feature.livetv.dvr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.DvrTimerStatus
import com.raulshma.jellyplay.core.ui.components.ErrorScreen

@OptIn(ExperimentalMaterial3Api::class)
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

    val backgroundColor = lerp(
        MaterialTheme.colorScheme.background,
        Color.Black,
        0.70f,
    )

    var headerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { headerVisible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        if (viewModel.error != null && viewModel.timers.isEmpty() && viewModel.seriesTimers.isEmpty()) {
            ErrorScreen(
                message = viewModel.error!!,
                onRetry = { viewModel.load() },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // ═══════════════════════════════════════════════════════════════
                // ── Header Section (cinematic dark, white-on-dark text)
                // ═══════════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    backgroundColor.copy(alpha = 0.95f),
                                    backgroundColor,
                                ),
                            )
                        )
                        .statusBarsPadding()
                        .padding(top = 16.dp),
                ) {
                    // ── Title + action row ──
                    AnimatedVisibility(
                        visible = headerVisible,
                        enter = fadeIn(tween(500)) + slideInVertically(
                            tween(500),
                            initialOffsetY = { -40 },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable(onClick = onBack),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Recordings",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = Color.White,
                                )
                                com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                                    status = headerStatus,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }

                // ═══════════════════════════════════════════════════════════════
                // ── Recordings List
                // ═══════════════════════════════════════════════════════════════
                if (viewModel.timers.isEmpty() && viewModel.seriesTimers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.White.copy(alpha = 0.3f),
                            )
                            Text(
                                text = "No scheduled recordings",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 100.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (viewModel.seriesTimers.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Series Recordings",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                )
                            }
                            items(viewModel.seriesTimers.size, key = { viewModel.seriesTimers[it].id }) { index ->
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
                                        .background(Color.White.copy(alpha = 0.08f))
                                )
                            }
                        }

                        if (viewModel.timers.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Scheduled Recordings",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                )
                            }
                            items(viewModel.timers.size, key = { viewModel.timers[it].id }) { index ->
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
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable { }
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
                        else -> Color.White.copy(alpha = 0.08f)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (timer.status) {
                    DvrTimerStatus.RECORDING -> Icons.Default.FiberManualRecord
                    else -> Icons.Default.Schedule
                },
                contentDescription = null,
                tint = when (timer.status) {
                    DvrTimerStatus.RECORDING -> MaterialTheme.colorScheme.error
                    DvrTimerStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
                    else -> Color.White.copy(alpha = 0.5f)
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
                color = Color.White,
            )
            Text(
                text = timer.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
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
                    color = Color.White.copy(alpha = 0.45f),
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
                .background(Color.White.copy(alpha = 0.08f))
                .clickable { onCancel(timer.id) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Cancel recording",
                tint = Color.White.copy(alpha = 0.6f),
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
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable { }
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
                Icons.Default.FiberManualRecord,
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
                color = Color.White,
            )
            val channelName = timer.channelName
            if (channelName != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (timer.recordAnyChannel) "Any channel" else channelName ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.45f),
            )
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable { onCancel(timer.id) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Cancel series recording",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
