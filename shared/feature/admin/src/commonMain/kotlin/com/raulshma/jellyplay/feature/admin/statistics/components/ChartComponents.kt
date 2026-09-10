package com.raulshma.jellyplay.feature.admin.statistics.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_chart_monthly
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_chart_weekly
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_duration_vs_last
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_activity_data
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_breakdown_data
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_data
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_trend_data
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_watch_time_data
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_unit_hours
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_unit_minutes
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_watched_less
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_watched_more

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
                stringResource(Res.string.admin_no_activity_data),
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
    val entranceSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(data) {
        if (animateEntrance) {
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = entranceSpec,
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

        val labelStep = when {
            data.size <= 7 -> 1
            data.size <= 15 -> 2
            data.size <= 31 -> 5
            else -> data.size / 6
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            data.forEachIndexed { index, point ->
                if (index == 0 || index % labelStep == 0 || index == data.lastIndex) {
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
                stringResource(Res.string.admin_no_breakdown_data),
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
                    Canvas(modifier = Modifier.fillMaxSize()) {
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

@Composable
fun PieChart(
    data: List<com.raulshma.jellyplay.core.model.ContentBreakdown>,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.admin_no_data),
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
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.surfaceVariant,
    )
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f)
    val total = data.sumOf { it.value }.coerceAtLeast(1L)
    val animatedProgress = remember { Animatable(0f) }
    val entranceSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(data) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = entranceSpec,
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 32.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)

                drawCircle(
                    color = trackColor,
                    radius = radius,
                    center = center,
                )

                var startAngle = -90f
                data.forEachIndexed { index, item ->
                    val sweepAngle = (item.value.toFloat() / total.toFloat()) * 360f * animatedProgress.value
                    val color = colors[item.colorIndex % colors.size]
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
                    )
                    startAngle += sweepAngle
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            data.take(5).forEachIndexed { index, item ->
                val color = colors[item.colorIndex % colors.size]
                val percentage = (item.value.toFloat() / total.toFloat()) * 100
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = String.format(java.util.Locale.getDefault(), "%.0f%%", percentage),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun TrendLineChart(
    data: List<PlaybackActivityPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.admin_no_trend_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val maxValue = data.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
    val animatedProgress = remember { Animatable(0f) }
    val entranceSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fillPath = remember { androidx.compose.ui.graphics.Path() }
    val linePath = remember { androidx.compose.ui.graphics.Path() }

    LaunchedEffect(data) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = entranceSpec,
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val chartWidth = size.width
            val chartHeight = size.height - 24f
            val progress = animatedProgress.value

            fun xAt(index: Int): Float =
                if (data.size > 1) {
                    (index.toFloat() / (data.size - 1)) * chartWidth
                } else chartWidth / 2

            fun yAt(index: Int): Float =
                chartHeight - (data[index].value.toFloat() / maxValue.toFloat()) * chartHeight * progress

            if (data.size > 1) {
                val fillColor = lineColor.copy(alpha = 0.1f)
                fillPath.rewind()
                fillPath.apply {
                    moveTo(xAt(0), yAt(0))
                    for (i in 1 until data.size) {
                        val cpx = (xAt(i - 1) + xAt(i)) / 2
                        cubicTo(cpx, yAt(i - 1), cpx, yAt(i), xAt(i), yAt(i))
                    }
                    lineTo(xAt(data.lastIndex), chartHeight)
                    lineTo(xAt(0), chartHeight)
                    close()
                }
                drawPath(path = fillPath, color = fillColor)

                linePath.rewind()
                linePath.apply {
                    moveTo(xAt(0), yAt(0))
                    for (i in 1 until data.size) {
                        val cpx = (xAt(i - 1) + xAt(i)) / 2
                        cubicTo(cpx, yAt(i - 1), cpx, yAt(i), xAt(i), yAt(i))
                    }
                }
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()),
                )
            }

            data.forEachIndexed { index, _ ->
                drawCircle(
                    color = lineColor,
                    radius = 3.dp.toPx(),
                    center = Offset(xAt(index), yAt(index)),
                )
            }
        }

        val labelStep = when {
            data.size <= 7 -> 1
            data.size <= 15 -> 3
            data.size <= 31 -> 7
            else -> data.size / 5
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            data.forEachIndexed { index, point ->
                if (index == 0 || index % labelStep == 0 || index == data.lastIndex) {
                    Text(
                        text = point.date.takeLast(5),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun WatchTimeCard(
    weeklySeconds: Long,
    monthlySeconds: Long,
    modifier: Modifier = Modifier,
) {
    var showWeekly by remember { mutableStateOf(true) }
    val seconds = if (showWeekly) weeklySeconds else monthlySeconds
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60

    Card(
        shape = ShapeCache.smooth20,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Total Watch Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier
                        .clip(ShapeCache.smoothPill)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(if (showWeekly) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { showWeekly = true }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(Res.string.admin_chart_weekly),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showWeekly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(if (!showWeekly) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { showWeekly = false }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(Res.string.admin_chart_monthly),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!showWeekly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "$hours",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.admin_unit_hours),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    text = "$minutes",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.admin_unit_minutes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (seconds == 0L) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.admin_no_watch_time_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun StreakCard(
    currentStreak: Int,
    longestStreak: Int,
    modifier: Modifier = Modifier,
) {
    val animatedStreak = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(currentStreak) {
        animatedStreak.snapTo(0f)
        animatedStreak.animateTo(
            targetValue = 1f,
            animationSpec = spec,
        )
    }
    val displayStreak = (currentStreak * animatedStreak.value).toInt()

    Card(
        shape = ShapeCache.smooth20,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (currentStreak > 0) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (currentStreak > 0) "\uD83D\uDD25" else "\uD83D\uDCA4",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$displayStreak day${if (displayStreak != 1) "s" else ""}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Current viewing streak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (longestStreak > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$longestStreak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        "Longest streak",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ComparisonCard(
    percentageChange: Float,
    currentMinutes: Long,
    previousMinutes: Long,
    modifier: Modifier = Modifier,
) {
    if (currentMinutes == 0L && previousMinutes == 0L) return

    val isPositive = percentageChange >= 0
    val changeColor = if (isPositive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val arrow = if (isPositive) "\u2191" else "\u2193"

    Card(
        shape = ShapeCache.smooth20,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(changeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    arrow,
                    style = MaterialTheme.typography.titleLarge,
                    color = changeColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isPositive) Res.string.admin_watched_more else Res.string.admin_watched_less,
                        String.format(java.util.Locale.getDefault(), "%.0f", kotlin.math.abs(percentageChange))
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(Res.string.admin_duration_vs_last, formatDuration(currentMinutes), formatDuration(previousMinutes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDuration(totalMinutes: Long): String =
    com.raulshma.jellyplay.core.ui.components.formatDurationFromMinutes(totalMinutes)
