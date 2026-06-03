package com.raulshma.jellyplay.feature.admin.statistics.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint

@Composable
fun ActivityBarChart(
    data: List<PlaybackActivityPoint>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    animateEntrance: Boolean = true,
    label: String = "Activity",
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No activity data available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val maxValue = data.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
    val animatable = remember { Animatable(if (animateEntrance) 0f else 1f) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val cornerRadius = 4.dp

    LaunchedEffect(data) {
        if (animateEntrance) {
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, delayMillis = 200),
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val barWidth = (size.width / data.size) * 0.7f
            val spacing = (size.width / data.size) * 0.3f
            val chartHeight = size.height - 24f
            val animProgress = animatable.value

            data.forEachIndexed { index, point ->
                val barHeight = if (maxValue > 0) {
                    (point.value.toFloat() / maxValue.toFloat()) * chartHeight * animProgress
                } else 0f

                val x = index * (barWidth + spacing) + spacing / 2
                val y = chartHeight - barHeight

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                )
            }
        }

        if (data.size <= 10) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val step = (data.size / 5).coerceAtLeast(1)
                data.forEachIndexed { index, point ->
                    if (index % step == 0 || index == data.lastIndex) {
                        Text(
                            text = point.date.takeLast(5),
                            style = labelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HorizontalBreakdownChart(
    data: List<com.raulshma.jellyplay.core.model.ContentBreakdown>,
    modifier: Modifier = Modifier,
    animateEntrance: Boolean = true,
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No breakdown data available",
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
    val maxValue = data.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        data.forEachIndexed { index, item ->
            val color = colors[item.colorIndex % colors.size]
            val fraction = if (maxValue > 0) item.value.toFloat() / maxValue.toFloat() else 0f

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = item.value.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(ShapeCache.smooth4),
                ) {
                    val barColor = color
                    Canvas(modifier = Modifier.fillMaxWidth()) {
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.15f),
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset.Zero,
                            size = Size(size.width * fraction, size.height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryStatCard(
    value: Long,
    label: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    val animatable = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(value) {
        animatable.snapTo(0f)
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = spec,
        )
    }
    val displayValue = (value * animatable.value).toLong()

    androidx.compose.material3.Card(
        shape = ShapeCache.smooth16,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = formatNumber(displayValue),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CompletionRing(
    percentage: Float,
    modifier: Modifier = Modifier,
    ringSize: androidx.compose.ui.unit.Dp = 120.dp,
) {
    val animatable = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(percentage) {
        animatable.animateTo(
            targetValue = percentage,
            animationSpec = spec,
        )
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val strokeWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
    val ringSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { ringSize.toPx() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ringSize)) {
            val radius = (ringSizePx - strokeWidthPx) / 2
            val center = Offset(ringSizePx / 2, ringSizePx / 2)

            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
            )

            val sweepAngle = animatable.value * 360f
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                size = Size(ringSizePx - strokeWidthPx, ringSizePx - strokeWidthPx),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx),
            )
        }
        Text(
            text = "${(percentage * 100).toInt()}%",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatNumber(value: Long): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
    else -> value.toString()
}
