package com.raulshma.jellyplay.feature.insights.heatmap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronLeft
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Share
import com.raulshma.jellyplay.core.data.repository.DailyWatchActivity
import com.raulshma.jellyplay.core.data.repository.HeatmapFilter
import com.raulshma.jellyplay.core.designsystem.theme.isLightColor
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchProgressHeatmapScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit = {},
    viewModel: WatchProgressHeatmapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    if (state.shareRequested) {
        LaunchedEffect(state.shareRequested) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = view.drawToBitmap()
                    shareHeatmapImage(context, bitmap)
                }
            }
            viewModel.onEvent(HeatmapEvent.ShareConsumed)
        }
    }

    JellyPlayScreenScaffold(
        title = "Watch Progress",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.onEvent(HeatmapEvent.RequestShare) }) {
                Icon(Tabler.Outline.Share, contentDescription = "Share")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Loading...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                if (!state.isPluginAvailable) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = "Install the Playback Reporting plugin on your Jellyfin server for detailed heatmap data. Showing basic activity from watch history.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                YearSelector(
                    year = state.year,
                    onYearChange = { viewModel.onEvent(HeatmapEvent.SetYear(it)) },
                )

                Spacer(Modifier.height(12.dp))

                StreakStats(streakInfo = state.streakInfo)

                Spacer(Modifier.height(16.dp))

                FilterChips(
                    currentFilter = state.filter,
                    onFilterChange = { viewModel.onEvent(HeatmapEvent.SetFilter(it)) },
                )

                Spacer(Modifier.height(12.dp))

                HeatmapGrid(
                    year = state.year,
                    dailyActivities = state.dailyActivities,
                    onDayClick = { viewModel.onEvent(HeatmapEvent.SelectDay(it)) },
                )
            }
        }
    }

    state.selectedDay?.let { dayInfo ->
        DayDetailSheet(
            dayInfo = dayInfo,
            onDismiss = { viewModel.onEvent(HeatmapEvent.DismissDayDetail) },
            onItemClick = onItemClick,
        )
    }
}

@Composable
private fun YearSelector(
    year: Int,
    onYearChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = { onYearChange(year - 1) }) {
            Icon(Tabler.Outline.ChevronLeft, contentDescription = "Previous year")
        }
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val currentYear = LocalDate.now().year
        IconButton(
            onClick = { onYearChange(minOf(year + 1, currentYear)) },
            enabled = year < currentYear,
        ) {
            Icon(Tabler.Outline.ChevronRight, contentDescription = "Next year")
        }
    }
}

@Composable
private fun StreakStats(streakInfo: com.raulshma.jellyplay.core.data.repository.StreakInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem("Current Streak", "${streakInfo.currentStreak} days")
        StatItem("Longest Streak", "${streakInfo.longestStreak} days")
        StatItem("Active Days", "${streakInfo.totalActiveDays}")
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterChips(
    currentFilter: HeatmapFilter,
    onFilterChange: (HeatmapFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        HeatmapFilter.entries.forEach { filter ->
            FilterChip(
                selected = currentFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(filter.label) },
                modifier = Modifier.padding(horizontal = 4.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Immutable
private data class HeatmapCell(
    val date: LocalDate,
    val level: Int,
    val value: Long,
)

private fun LocalDate.dayOfWeekIndex(): Int = when (dayOfWeek) {
    DayOfWeek.SUNDAY -> 0
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
}

private fun calculateGrid(
    year: Int,
    dailyActivities: List<DailyWatchActivity>,
): Pair<Array<HeatmapCell?>, Int> {
    val startDate = LocalDate.of(year, 1, 1)
    val endDate = LocalDate.of(year, 12, 31)
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    val valueByDate = mutableMapOf<LocalDate, Long>()
    for (activity in dailyActivities) {
        runCatching { LocalDate.parse(activity.date, formatter) }
            .getOrNull()
            ?.let { date -> valueByDate[date] = activity.value }
    }

    val maxValue = valueByDate.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L

    val numWeeks = ChronoUnit.WEEKS.between(
        startDate.with(DayOfWeek.SUNDAY),
        endDate.with(DayOfWeek.SATURDAY),
    ).toInt() + 1
    val grid = arrayOfNulls<HeatmapCell>(numWeeks * 7)

    var current = startDate
    while (!current.isAfter(endDate) && !current.isAfter(today)) {
        val weekIndex = ChronoUnit.WEEKS.between(
            startDate.with(DayOfWeek.SUNDAY),
            current.with(DayOfWeek.SUNDAY),
        ).toInt()
        val dayIndex = current.dayOfWeekIndex()
        val pos = weekIndex * 7 + dayIndex
        if (pos in grid.indices) {
            val value = valueByDate[current] ?: 0L
            val level = if (value > 0) {
                val ratio = (value.toDouble() / maxValue).coerceIn(0.0, 1.0)
                when {
                    ratio <= 0.25 -> 1
                    ratio <= 0.50 -> 2
                    ratio <= 0.75 -> 3
                    else -> 4
                }
            } else 0
            grid[pos] = HeatmapCell(date = current, level = level, value = value)
        }
        current = current.plusDays(1)
    }

    return grid to numWeeks
}

@Composable
private fun HeatmapGrid(
    year: Int,
    dailyActivities: List<DailyWatchActivity>,
    onDayClick: (LocalDate?) -> Unit,
) {
    val (grid, numWeeks) = remember(year, dailyActivities) {
        calculateGrid(year, dailyActivities)
    }

    val cellSize = 11.dp
    val cellGap = 2.dp
    val labelWidth = 28.dp
    val monthLabelHeight = 18.dp
    val cornerRadius = 2.dp

    val cellSizePx = with(LocalDensity.current) { cellSize.toPx() }
    val cellGapPx = with(LocalDensity.current) { cellGap.toPx() }
    val cornerRadiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    val monthLabelHeightPx = with(LocalDensity.current) { monthLabelHeight.toPx() }

    val isDark = !isLightColor(MaterialTheme.colorScheme.background)
    val levelColors = remember(isDark) {
        if (isDark) {
            arrayOf(
                Color(0xFF161B22),
                Color(0xFF0E4429),
                Color(0xFF006D32),
                Color(0xFF26A641),
                Color(0xFF39D353),
            )
        } else {
            arrayOf(
                Color(0xFFEBEDF0),
                Color(0xFF9BE9A8),
                Color(0xFF40C463),
                Color(0xFF30A14E),
                Color(0xFF216E39),
            )
        }
    }

    val startDate = LocalDate.of(year, 1, 1)
    val monthPositions = remember(year) {
        val months = mutableMapOf<Int, String>()
        for (month in 1..12) {
            val firstOfMonth = LocalDate.of(year, month, 1)
            if (firstOfMonth.isAfter(LocalDate.now())) break
            val weekIndex = ChronoUnit.WEEKS.between(
                startDate.with(DayOfWeek.SUNDAY),
                firstOfMonth.with(DayOfWeek.SUNDAY),
            ).toInt()
            months[weekIndex] = firstOfMonth.month.name.take(3)
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        }
        months
    }

    val dayLabels = remember {
        listOf("Sun", "", "Tue", "", "Thu", "", "Sat")
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        DayLabels(
            dayLabels = dayLabels,
            cellSizePx = cellSizePx,
            cellGapPx = cellGapPx,
            monthLabelHeightPx = monthLabelHeightPx,
        )

        Column(modifier = Modifier.padding(start = labelWidth)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(monthLabelHeight),
            ) {
                monthPositions.forEach { (weekIndex, monthLabel) ->
                    val cellStepDp = with(LocalDensity.current) { (cellSizePx + cellGapPx) }
                    Box(
                        modifier = Modifier
                            .width(with(LocalDensity.current) { cellStepDp.toDp() })
                            .height(monthLabelHeight),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(
                            text = monthLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        with(LocalDensity.current) {
                            ((cellSizePx + cellGapPx) * 7 + 4f).toDp()
                        }
                    )
                    .pointerInput(numWeeks) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tapX = down.position.x
                            val tapY = down.position.y
                            val weekIdx = (tapX / (cellSizePx + cellGapPx)).toInt()
                            val dayIdx = (tapY / (cellSizePx + cellGapPx)).toInt()
                            if (weekIdx in 0 until numWeeks && dayIdx in 0 until 7) {
                                val cell = grid.getOrNull(weekIdx * 7 + dayIdx)
                                onDayClick(cell?.date)
                            }
                        }
                    },
            ) {
                for (week in 0 until numWeeks) {
                    for (day in 0 until 7) {
                        val pos = week * 7 + day
                        val cell = grid.getOrNull(pos)
                        val level = cell?.level ?: 0
                        val color = levelColors[level]

                        val x = week * (cellSizePx + cellGapPx)
                        val y = day * (cellSizePx + cellGapPx)

                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, y),
                            size = Size(cellSizePx, cellSizePx),
                            cornerRadius = CornerRadius(cornerRadiusPx),
                        )
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Less",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        levelColors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(cellSize)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(color)
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            "More",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayLabels(
    dayLabels: List<String>,
    cellSizePx: Float,
    cellGapPx: Float,
    monthLabelHeightPx: Float,
) {
    Column(modifier = Modifier.width(28.dp)) {
        Spacer(
            Modifier.height(
                with(LocalDensity.current) { monthLabelHeightPx.toDp() }
            )
        )
        dayLabels.forEach { label ->
            if (label.isNotEmpty()) {
                Box(
                    modifier = Modifier.height(
                        with(LocalDensity.current) { (cellSizePx + cellGapPx).toDp() }
                    ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(
                    Modifier.height(
                        with(LocalDensity.current) { (cellSizePx + cellGapPx).toDp() }
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(
    dayInfo: SelectedDayInfo,
    onDismiss: () -> Unit,
    onItemClick: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = dayInfo.dateLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(4.dp))

            val totalMinutes = dayInfo.sessions.sumOf {
                kotlin.math.max(0L, it.duration / 60_000_000_000L)
            }
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            val durationText = when {
                hours > 0 -> "${hours}h ${mins}m"
                mins > 0 -> "${mins}m"
                dayInfo.sessions.isNotEmpty() -> "< 1m"
                else -> "No activity"
            }
            Text(
                text = "${dayInfo.sessions.size} session${if (dayInfo.sessions.size != 1) "s" else ""} \u00B7 $durationText watched",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            val uniqueItems = dayInfo.sessions
                .groupBy { it.itemId }
                .mapValues { (_, sessions) -> sessions.maxOf { it.duration } }

            uniqueItems.forEach { (itemId, durationTicks) ->
                val resolved = dayInfo.resolvedItems[itemId]
                val name = resolved?.name ?: dayInfo.sessions.first { it.itemId == itemId }.name
                val imageUrl = resolved?.imageUrl
                val posMinutes = durationTicks / 60_000_000_000L

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onItemClick(itemId) }
                        .padding(vertical = 6.dp),
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .build(),
                            contentDescription = name,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                    } else {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Tabler.Outline.PlayerPlay,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (posMinutes > 0) "${posMinutes}m watched" else "Played",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

private fun shareHeatmapImage(context: Context, bitmap: Bitmap) {
    val file = File(context.cacheDir, "watch_progress_heatmap.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Watch Progress"))
}
