package com.raulshma.jellyplay.feature.insights.heatmap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.raulshma.jellyplay.feature.insights.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.ContentScale
import coil3.size.Size as CoilSize
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ChevronLeft
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Filter
import com.composables.icons.tabler.outline.Photo
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Share
import com.raulshma.jellyplay.core.data.repository.DailyWatchActivity
import com.raulshma.jellyplay.core.data.repository.HeatmapFilter
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.isLightColor
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.ScreenErrorState
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.TvFocusDefaults
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
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
    val adaptiveInfo = LocalAdaptiveInfo.current

    // TV focus-on-launch: focus the heatmap content once it arrives so D-pad input lands on
    // content, not the navigation drawer.
    val contentFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = contentFocusRequester,
        itemCount = if (state.isLoading) 0 else 1,
        tag = "heatmap_init",
    )

    if (state.shareRequested) {
        LaunchedEffect(state.shareRequested) {
            withContext(Dispatchers.IO) {
                runCatching {
                    // TODO(F-23): capture only the HeatmapGrid subtree once
                    // rememberGraphicsLayer is available in the Compose BOM.
                    val bitmap = view.drawToBitmap()
                    shareHeatmapImage(context, bitmap)
                }
            }
            viewModel.onEvent(HeatmapEvent.ShareConsumed)
        }
    }

    JellyPlayScreenScaffold(
        title = stringResource(R.string.insights_title_watch_progress),
        onBack = onBack,
        actions = {
            val shareFocusState = rememberTvFocusState()
            IconButton(
                onClick = { viewModel.onEvent(HeatmapEvent.RequestShare) },
                modifier = Modifier.then(shareFocusState.focusModifier).tvFocusIndicator(shareFocusState, CircleShape),
            ) {
                Icon(Tabler.Outline.Share, contentDescription = stringResource(R.string.insights_cd_share))
            }
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                val filterFocusState = rememberTvFocusState()
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.then(filterFocusState.focusModifier).tvFocusIndicator(filterFocusState, CircleShape),
                ) {
                    Icon(Tabler.Outline.Filter, contentDescription = stringResource(R.string.insights_cd_configure_heatmap))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    Text(
                        text = stringResource(R.string.insights_filter_type),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    HeatmapFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.label) },
                            onClick = {
                                viewModel.onEvent(HeatmapEvent.SetFilter(filter))
                                menuExpanded = false
                            },
                            leadingIcon = {
                                if (state.filter == filter) {
                                    Icon(Tabler.Outline.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                } else {
                                    Spacer(modifier = Modifier.size(18.dp))
                                }
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.insights_select_year),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    val currentYear = java.time.LocalDate.now().year
                    for (y in currentYear downTo (currentYear - 4)) {
                        DropdownMenuItem(
                            text = { Text(y.toString()) },
                            onClick = {
                                viewModel.onEvent(HeatmapEvent.SetYear(y))
                                menuExpanded = false
                            },
                            leadingIcon = {
                                if (state.year == y) {
                                    Icon(Tabler.Outline.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                } else {
                                    Spacer(modifier = Modifier.size(18.dp))
                                }
                            }
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val isTv = LocalTvMode.current
        if (state.isLoading) {
            ScreenLoadingState(
                message = stringResource(R.string.insights_loading),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else if (state.error != null && state.dailyActivities.isEmpty()) {
            val content: @Composable (Modifier) -> Unit = { mod ->
                ScreenErrorState(
                    message = state.error!!,
                    onRetry = { viewModel.refresh() },
                    modifier = mod.fillMaxSize(),
                )
            }
            if (isTv) {
                content(Modifier.padding(innerPadding))
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    content(Modifier)
                }
            }
        } else {
            val heatmapContent: @Composable () -> Unit = {
                if (adaptiveInfo.windowSizeClass == WindowSizeClass.Compact) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .tvFocusRestorer()
                            .focusGroup()
                            .focusRequester(contentFocusRequester)
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                    ) {
                        if (!state.isPluginAvailable) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ShapeCache.smooth8,
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Text(
                                    text = stringResource(R.string.insights_plugin_missing_message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (state.dailyActivities.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ShapeCache.smooth8,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Text(
                                    text = stringResource(R.string.insights_no_activity_for_year, state.year),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            minActivityDate = state.minActivityDate,
                            onDayClick = { viewModel.onEvent(HeatmapEvent.SelectDay(it)) },
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .tvFocusRestorer()
                            .focusGroup()
                            .focusRequester(contentFocusRequester)
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (!state.isPluginAvailable) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ShapeCache.smooth8,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                ) {
                                    Text(
                                        text = stringResource(R.string.insights_plugin_missing_message),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }

                            if (state.dailyActivities.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ShapeCache.smooth8,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Text(
                                        text = stringResource(R.string.insights_no_activity_for_year, state.year),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }

                            YearSelector(
                                year = state.year,
                                onYearChange = { viewModel.onEvent(HeatmapEvent.SetYear(it)) },
                            )

                            StreakStats(streakInfo = state.streakInfo)

                            FilterChips(
                                currentFilter = state.filter,
                                onFilterChange = { viewModel.onEvent(HeatmapEvent.SetFilter(it)) },
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1.5f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.insights_activity_grid),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )

                            HeatmapGrid(
                                year = state.year,
                                dailyActivities = state.dailyActivities,
                                minActivityDate = state.minActivityDate,
                                onDayClick = { viewModel.onEvent(HeatmapEvent.SelectDay(it)) },
                            )
                        }
                    }
                }
            }
            if (isTv) {
                heatmapContent()
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    heatmapContent()
                }
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
    val prevYearFocusState = rememberTvFocusState()
    val nextYearFocusState = rememberTvFocusState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(
            onClick = { onYearChange(year - 1) },
            modifier = Modifier.then(prevYearFocusState.focusModifier).tvFocusIndicator(prevYearFocusState, CircleShape),
        ) {
            Icon(Tabler.Outline.ChevronLeft, contentDescription = stringResource(R.string.insights_cd_previous_year))
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
            modifier = Modifier.then(nextYearFocusState.focusModifier).tvFocusIndicator(nextYearFocusState, CircleShape),
        ) {
            Icon(Tabler.Outline.ChevronRight, contentDescription = stringResource(R.string.insights_cd_next_year))
        }
    }
}

@Composable
private fun StreakStats(
    streakInfo: com.raulshma.jellyplay.core.data.repository.StreakInfo,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth12,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatItem(stringResource(R.string.insights_stat_current_streak), stringResource(R.string.insights_days_format, streakInfo.currentStreak))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
            StatItem(stringResource(R.string.insights_stat_longest_streak), stringResource(R.string.insights_days_format, streakInfo.longestStreak))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
            StatItem(stringResource(R.string.insights_stat_active_days), streakInfo.totalActiveDays.toString())
        }
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
    minActivityDate: LocalDate?,
): Pair<Array<HeatmapCell?>, Int> {
    val startDate = if (minActivityDate != null && minActivityDate.year == year) {
        minActivityDate.minusDays(minActivityDate.dayOfWeekIndex().toLong())
    } else {
        LocalDate.of(year, 1, 1)
    }
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

// Start the TV cursor on today when the year covers it, otherwise on the first populated cell.
private fun initialFocusedCellIndex(grid: Array<HeatmapCell?>): Int {
    val todayIndex = grid.indexOfFirst { it?.date == LocalDate.now() }
    if (todayIndex >= 0) return todayIndex
    return grid.indexOfFirst { it != null }.coerceAtLeast(0)
}

@Composable
private fun HeatmapGrid(
    year: Int,
    dailyActivities: List<DailyWatchActivity>,
    minActivityDate: LocalDate?,
    onDayClick: (LocalDate?) -> Unit,
) {
    val (grid, numWeeks) = remember(year, dailyActivities, minActivityDate) {
        calculateGrid(year, dailyActivities, minActivityDate)
    }
    val activeDays = remember(grid) { grid.count { it != null && it.level > 0 } }
    val heatmapSummary = pluralStringResource(R.plurals.insights_heatmap_summary, activeDays, year, activeDays)

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
            com.raulshma.jellyplay.core.designsystem.theme.HeatmapPalette.dark
        } else {
            com.raulshma.jellyplay.core.designsystem.theme.HeatmapPalette.light
        }
    }

    val gridStartDate = remember(year, minActivityDate) {
        if (minActivityDate != null && minActivityDate.year == year) {
            minActivityDate.minusDays(minActivityDate.dayOfWeekIndex().toLong())
        } else {
            LocalDate.of(year, 1, 1)
        }
    }

    val monthPositions = remember(year, gridStartDate) {
        val months = mutableMapOf<Int, String>()
        val startMonth = gridStartDate.monthValue
        for (month in startMonth..12) {
            val firstOfMonth = LocalDate.of(year, month, 1)
            if (firstOfMonth.isAfter(LocalDate.now())) break
            val targetDate = if (firstOfMonth.isBefore(gridStartDate)) gridStartDate else firstOfMonth
            val weekIndex = ChronoUnit.WEEKS.between(
                gridStartDate.with(DayOfWeek.SUNDAY),
                targetDate.with(DayOfWeek.SUNDAY),
            ).toInt()
            months[weekIndex] = firstOfMonth.month.name.take(3)
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        }
        months
    }

    val sun = stringResource(R.string.insights_day_sun)
    val tue = stringResource(R.string.insights_day_tue)
    val thu = stringResource(R.string.insights_day_thu)
    val sat = stringResource(R.string.insights_day_sat)
    val dayLabels = remember(sun, tue, thu, sat) {
        listOf(sun, "", tue, "", thu, "", sat)
    }

    val gridWidthDp = with(LocalDensity.current) { (numWeeks * (cellSizePx + cellGapPx)).toDp() }

    val isTv = LocalTvMode.current

    // TV D-pad cursor over the grid as a flat cell index (week * 7 + day), matching the canvas
    // layout. Populated cells form one contiguous run (every day from the grid start up to today
    // or year end), so clamping movement to that range clamps at the data edges without wrapping.
    val minCellIndex = remember(grid) { grid.indexOfFirst { it != null } }
    val maxCellIndex = remember(grid) { grid.indexOfLast { it != null } }
    var focusedCellIndex by remember(grid) { mutableIntStateOf(initialFocusedCellIndex(grid)) }
    var heatmapHasFocus by remember { mutableStateOf(false) }
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }

    fun moveFocusedCell(delta: Int): Boolean {
        if (minCellIndex < 0) return false
        val next = (focusedCellIndex + delta).coerceIn(minCellIndex, maxCellIndex)
        if (next == focusedCellIndex) return false
        focusedCellIndex = next
        return true
    }

    val focusRingColor = MaterialTheme.colorScheme.primary
    val focusRingWidthPx = with(LocalDensity.current) { TvFocusDefaults.BorderWidth.toPx() }

    Box(modifier = Modifier
        .fillMaxWidth()) {
        DayLabels(
            dayLabels = dayLabels,
            cellSizePx = cellSizePx,
            cellGapPx = cellGapPx,
            monthLabelHeightPx = monthLabelHeightPx,
        )

        val scrollState = rememberScrollState()
        LaunchedEffect(numWeeks, year) {
            scrollState.scrollTo(scrollState.maxValue)
        }

        // Keep the focused week inside the viewport while the grid holds focus (TV D-pad).
        LaunchedEffect(focusedCellIndex, heatmapHasFocus, viewportWidthPx) {
            if (!heatmapHasFocus || focusedCellIndex < 0) return@LaunchedEffect
            if (viewportWidthPx <= 0f) return@LaunchedEffect
            val cellLeft = (focusedCellIndex / 7) * (cellSizePx + cellGapPx)
            val target = when {
                cellLeft < scrollState.value -> cellLeft
                cellLeft + cellSizePx > scrollState.value + viewportWidthPx -> cellLeft + cellSizePx - viewportWidthPx
                else -> return@LaunchedEffect
            }
            scrollState.animateScrollTo(target.roundToInt().coerceIn(0, scrollState.maxValue))
        }

        Column(
            modifier = Modifier
                .padding(start = labelWidth)
                .horizontalScroll(scrollState)
                .then(
                    if (isTv) {
                        Modifier.onSizeChanged { viewportWidthPx = it.width.toFloat() }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Box(
                modifier = Modifier
                    .width(gridWidthDp)
                    .height(monthLabelHeight),
            ) {
                monthPositions.forEach { (weekIndex, monthLabel) ->
                    val xOffset = with(LocalDensity.current) { (weekIndex * (cellSizePx + cellGapPx)).toDp() }
                    Text(
                        text = monthLabel,
                        modifier = Modifier.offset(x = xOffset),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Canvas(
                modifier = Modifier
                    .width(gridWidthDp)
                    .height(
                        with(LocalDensity.current) {
                            ((cellSizePx + cellGapPx) * 7 + 4f).toDp()
                        }
                    )
                    .semantics { contentDescription = heatmapSummary }
                    .pointerInput(numWeeks) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tapX = down.position.x
                            val tapY = down.position.y
                            val weekIdx = (tapX / (cellSizePx + cellGapPx)).roundToInt()
                            val dayIdx = (tapY / (cellSizePx + cellGapPx)).roundToInt()
                            if (weekIdx in 0 until numWeeks && dayIdx in 0 until 7) {
                                val cell = grid.getOrNull(weekIdx * 7 + dayIdx)
                                onDayClick(cell?.date)
                            }
                        }
                    }
                    .then(
                        if (isTv) {
                            Modifier
                                .onFocusChanged { heatmapHasFocus = it.isFocused }
                                .focusable()
                                .onDpadKey(
                                    // Direction keys that hit the data edge are left unconsumed so
                                    // focus traversal can move focus out of the grid; the cursor
                                    // itself never wraps.
                                    onLeft = { moveFocusedCell(-1) },
                                    onRight = { moveFocusedCell(1) },
                                    onUp = { moveFocusedCell(-7) },
                                    onDown = { moveFocusedCell(7) },
                                    onSelect = {
                                        val cell = grid.getOrNull(focusedCellIndex)
                                        if (cell != null) {
                                            onDayClick(cell.date)
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                )
                        } else {
                            Modifier
                        },
                    ),
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

                if (isTv && heatmapHasFocus && grid.getOrNull(focusedCellIndex) != null) {
                    val x = (focusedCellIndex / 7) * (cellSizePx + cellGapPx)
                    val y = (focusedCellIndex % 7) * (cellSizePx + cellGapPx)
                    val halfRing = focusRingWidthPx / 2f
                    drawRoundRect(
                        color = focusRingColor,
                        topLeft = Offset(x - halfRing, y - halfRing),
                        size = Size(cellSizePx + focusRingWidthPx, cellSizePx + focusRingWidthPx),
                        cornerRadius = CornerRadius(cornerRadiusPx + halfRing),
                        style = Stroke(width = focusRingWidthPx),
                    )
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
            stringResource(R.string.insights_legend_less),
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
            stringResource(R.string.insights_legend_more),
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

    val isTv = com.raulshma.jellyplay.core.ui.tv.LocalTvMode.current
    if (isTv) {
        com.raulshma.jellyplay.core.ui.components.TvSafeSheet(
            onDismissRequest = onDismiss,
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
                    hours > 0 -> stringResource(R.string.insights_duration_hm, hours, mins)
                    mins > 0 -> stringResource(R.string.insights_duration_m, mins)
                    dayInfo.sessions.isNotEmpty() -> stringResource(R.string.insights_duration_under_minute)
                    else -> stringResource(R.string.insights_no_activity)
                }
                Text(
                    text = pluralStringResource(R.plurals.insights_sessions_watched, dayInfo.sessions.size, dayInfo.sessions.size, durationText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                val uniqueItems = remember(dayInfo) {
                    dayInfo.sessions
                        .groupBy { it.itemId }
                        .mapValues { (_, sessions) -> sessions.maxOf { it.duration } }
                }

                uniqueItems.forEach { (itemId, durationTicks) ->
                    val resolved = dayInfo.resolvedItems[itemId]
                    val name = resolved?.name ?: dayInfo.sessions.first { it.itemId == itemId }.name
                    val imageUrl = resolved?.imageUrl
                    val posMinutes = durationTicks / 60_000_000_000L

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCache.smooth8)
                            .focusIndicator(ShapeCache.smooth12)
                            .clickable { onItemClick(itemId) }
                            .padding(vertical = 6.dp),
                    ) {
                        if (imageUrl != null) {
                            MediaImage(
                                url = imageUrl,
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                size = CoilSize(128, 128),
                                placeholderIcon = Tabler.Outline.Photo,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(ShapeCache.smooth4),
                            )
                            Spacer(Modifier.width(12.dp))
                        } else {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = ShapeCache.smooth4,
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
                                text = if (posMinutes > 0) stringResource(R.string.insights_minutes_watched, posMinutes) else stringResource(R.string.insights_played),
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
    } else {
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
                    hours > 0 -> stringResource(R.string.insights_duration_hm, hours, mins)
                    mins > 0 -> stringResource(R.string.insights_duration_m, mins)
                    dayInfo.sessions.isNotEmpty() -> stringResource(R.string.insights_duration_under_minute)
                    else -> stringResource(R.string.insights_no_activity)
                }
                Text(
                    text = pluralStringResource(R.plurals.insights_sessions_watched, dayInfo.sessions.size, dayInfo.sessions.size, durationText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                val uniqueItems = remember(dayInfo) {
                    dayInfo.sessions
                        .groupBy { it.itemId }
                        .mapValues { (_, sessions) -> sessions.maxOf { it.duration } }
                }

                uniqueItems.forEach { (itemId, durationTicks) ->
                    val resolved = dayInfo.resolvedItems[itemId]
                    val name = resolved?.name ?: dayInfo.sessions.first { it.itemId == itemId }.name
                    val imageUrl = resolved?.imageUrl
                    val posMinutes = durationTicks / 60_000_000_000L

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCache.smooth8)
                            .focusIndicator(ShapeCache.smooth12)
                            .clickable { onItemClick(itemId) }
                            .padding(vertical = 6.dp),
                    ) {
                        if (imageUrl != null) {
                            MediaImage(
                                url = imageUrl,
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                size = CoilSize(128, 128),
                                placeholderIcon = Tabler.Outline.Photo,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(ShapeCache.smooth4),
                            )
                            Spacer(Modifier.width(12.dp))
                        } else {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = ShapeCache.smooth4,
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
                                text = if (posMinutes > 0) stringResource(R.string.insights_minutes_watched, posMinutes) else stringResource(R.string.insights_played),
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
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.insights_share_chooser_title)))
}
