package com.raulshma.jellyplay.feature.livetv.epg

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.FiberManualRecord
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
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgScreen(
    onProgramClick: (LiveTvProgram) -> Unit,
    onBack: () -> Unit,
    onRecordClick: ((LiveTvProgram) -> Unit)? = null,
    viewModel: EpgViewModel = hiltViewModel(),
) {
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current
        .collectAsStateWithLifecycle()
    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = viewModel.isLoading,
        hasError = false,
        networkStatus = networkStatus,
    )

    val isLightTheme = MaterialTheme.colorScheme.background.let { bg -> (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) > 0.5f }
    val backgroundColor = if (isLightTheme) {
        MaterialTheme.colorScheme.background
    } else {
        lerp(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            0.70f,
        )
    }

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val bottomPad = adaptiveInfo.bottomPadding(isTv)

    var headerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { headerVisible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        if (viewModel.error != null && viewModel.programs.isEmpty()) {
            ErrorScreen(
                message = viewModel.error!!,
                onRetry = { viewModel.loadGuide() },
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
                        enter = fadeIn(tween(AnimationTokens.SlowDuration, easing = AlphaEasing)) + slideInVertically(
                            tween(AnimationTokens.SlowDuration, easing = FancyTransitionEasing),
                            initialOffsetY = { -40 },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = contentPad, end = contentPad),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(ShapeCache.smooth10)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                        .tvFocusable().clickable(onClick = onBack),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Program Guide",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                                    status = headerStatus,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }

                    // ── Program count ──
                    AnimatedVisibility(
                        visible = headerVisible && viewModel.programs.isNotEmpty(),
                        enter = fadeIn(tween(AnimationTokens.StandardDuration, delayMillis = 200, easing = AlphaEasing)),
                    ) {
                        Text(
                            text = "${viewModel.programs.size} programs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(
                                start = contentPad,
                                end = contentPad,
                                top = 8.dp,
                                bottom = 8.dp,
                            ),
                        )
                    }
                }

                // ═══════════════════════════════════════════════════════════════
                // ── Program List
                // ═══════════════════════════════════════════════════════════════
                if (viewModel.programs.isEmpty() && !viewModel.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.CalendarViewWeek,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                            Text(
                                text = "No program guide available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
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
                        items(
                            items = viewModel.programs,
                            key = { "${it.channelId}_${it.id}" },
                            contentType = { "program" },
                        ) { program ->
                            ProgramCard(
                                program = program,
                                onClick = { onProgramClick(program) },
                                onRecordClick = onRecordClick?.let { { it(program) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramCard(
    program: LiveTvProgram,
    onClick: () -> Unit,
    onRecordClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .tvFocusable().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = program.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (program.startDate != null && program.endDate != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${program.startDate} - ${program.endDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            val overview = program.overview
            if (overview != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val officialRating = program.officialRating
            if (officialRating != null) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(ShapeCache.smooth4)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = officialRating,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (onRecordClick != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .tvFocusable().clickable(onClick = onRecordClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = "Record",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
