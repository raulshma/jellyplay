package com.raulshma.jellyplay.feature.calendar

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.ChevronLeft
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Settings
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import com.raulshma.jellyplay.core.ui.generated.resources.core_ok
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import com.raulshma.jellyplay.feature.calendar.generated.resources.Res
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_empty_desc
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_empty_title
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_error_unknown
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_feature_disabled_desc
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_feature_disabled_title
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_filter_all
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_filter_movies
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_filter_series
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_go_today
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_next_month_cd
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_no_detail
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_open_settings
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_pick_date_cd
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_prev_month_cd
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_refresh_cd
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_releases_count
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_title
import com.raulshma.jellyplay.feature.calendar.generated.resources.calendar_today
import java.time.LocalDate
import java.time.ZoneId
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpcomingCalendarScreen(
    onBack: () -> Unit,
    onOpenArrSettings: () -> Unit = {},
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    viewModel: UpcomingCalendarViewModel = koinViewModel(),
) {
    val state by viewModel.state
    val featureEnabled by viewModel.featureEnabled.collectAsStateWithLifecycle()
    // No-detail tap feedback (screen-forward seam): resolve the string here and
    // forward through the messenger actual — the legacy UserMessageBus /
    // UiText(R.id) machinery stays behind the Android actual (livetv
    // LiveTvMessenger pattern; desktop drops the message).
    val messenger = rememberCalendarMessenger()
    val noDetailText = stringResource(Res.string.calendar_no_detail)
    val listState = rememberLazyListState()
    val today = remember { today() }
    val isTv = LocalTvMode.current

    // TV focus-on-launch: focus the first card once the calendar loads so D-pad
    // input lands on content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = if (!featureEnabled || state.isLoading || state.error != null || state.items.isEmpty()) 0 else 1,
        tag = "calendar_init",
    )

    // Tap-month → date-picker affordance. After a date is picked the
    // visible month swaps (if needed) and the list scrolls to that day's row.
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingScrollDate by remember { mutableStateOf<LocalDate?>(null) }

    // Scroll the day-header for [pendingScrollDate] into view once the items
    // for its month have loaded. Re-runs whenever the items snapshot changes.
    LaunchedEffect(pendingScrollDate, state.items) {
        val target = pendingScrollDate ?: return@LaunchedEffect
        val dayGroups = groupByDay(state.items, state.filter)
        val index = dayGroups.indexOfFirst { it.date == target }
        if (index >= 0) {
            // +2 offsets for the two leading header items (monthNav, filterRow).
            listState.animateScrollToItem(index + 2)
            pendingScrollDate = null
        }
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.calendar_title),
        onBack = onBack,
        topBarStyle = TopBarStyle.Collapsing,
        actions = {
            IconButton(onClick = { viewModel.refresh() }, enabled = !state.isLoading) {
                Icon(Tabler.Outline.Refresh, contentDescription = stringResource(Res.string.calendar_refresh_cd))
            }
        },
    ) { paddingValues ->
        val bottomPadding = paddingValues.calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !featureEnabled -> FeatureDisabledState(
                    onOpenSettings = onOpenArrSettings,
                    modifier = Modifier.fillMaxSize(),
                )

                state.isLoading && state.items.isEmpty() -> DelayedLoadingScreen(
                    modifier = Modifier.fillMaxSize(),
                )

                state.error != null && state.items.isEmpty() -> ErrorScreen(
                    message = state.error ?: stringResource(Res.string.calendar_error_unknown),
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> {
                    val days = remember(state.items, state.filter) {
                        groupByDay(state.items, state.filter)
                    }
                    if (days.isEmpty()) {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Calendar,
                            title = stringResource(Res.string.calendar_empty_title),
                            description = stringResource(Res.string.calendar_empty_desc),
                            actionLabel = stringResource(Res.string.calendar_go_today),
                            onAction = { viewModel.goToToday() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        PullToRefreshBox(
                            isRefreshing = state.isLoading && state.items.isNotEmpty(),
                            onRefresh = { viewModel.refresh() },
                            enabled = !isTv,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .tvFocusRestorer()
                                    .focusRequester(listFocusRequester),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    top = 8.dp,
                                    end = 16.dp,
                                    bottom = 16.dp + bottomPadding,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item(key = "monthNav") {
                                    MonthNavHeader(
                                        label = state.visibleMonth.toMonthYearLabel(),
                                        canGoBack = true,
                                        onPrevious = { viewModel.changeMonth(-1) },
                                        onNext = { viewModel.changeMonth(1) },
                                        onToday = { viewModel.goToToday() },
                                        onLabelClick = { showDatePicker = true },
                                    )
                                }
                                item(key = "filterRow") {
                                    FilterRow(
                                        selected = state.filter,
                                        onSelect = viewModel::setFilter,
                                    )
                                }
                                days.forEach { day ->
                                    item(key = "dayHeader_${day.date}") {
                                        DayHeader(
                                            date = day.date,
                                            today = today,
                                            count = day.items.size,
                                        )
                                    }
                                    items(
                                        items = day.items,
                                        key = { viewModel.stableRowId(it) },
                                        contentType = { "calendarCard" },
                                    ) { item ->
                                        CalendarCard(
                                            item = item,
                                            enrichedPosterUrl = item.tmdbId?.let { state.enrichedPosters[it] },
                                            enabled = item.tmdbId != null,
                                            onClick = {
                                                val tmdbId = item.tmdbId
                                                if (tmdbId != null) {
                                                    val mediaType = if (item.mediaType == ArrMediaType.MOVIE) "movie" else "tv"
                                                    onItemClick(tmdbId, mediaType)
                                                } else {
                                                    messenger?.info(noDetailText)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = state.visibleMonth
                    .atDay(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val picked = LocalDate.ofEpochDay(millis / 86_400_000L)
                                viewModel.goToDate(picked)
                                pendingScrollDate = picked
                            }
                            showDatePicker = false
                        },
                    ) {
                        Text(stringResource(CoreUiRes.string.core_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(CoreUiRes.string.core_cancel))
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

// ── Header rows ────────────────────────────────────────────────────────────

@Composable
private fun MonthNavHeader(
    label: String,
    canGoBack: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onLabelClick: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth12,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = canGoBack) {
                Icon(Tabler.Outline.ChevronLeft, contentDescription = stringResource(Res.string.calendar_prev_month_cd))
            }
            // Tapping the month label opens a date picker so the user can
            // jump straight to a day instead of paging month by month.
            val pickDateLabel = stringResource(Res.string.calendar_pick_date_cd)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClickLabel = pickDateLabel,
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = onLabelClick,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(6.dp))
                Icon(
                    Tabler.Outline.Calendar,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNext) {
                Icon(Tabler.Outline.ChevronRight, contentDescription = stringResource(Res.string.calendar_next_month_cd))
            }
            TextButton(onClick = onToday) {
                Text(stringResource(Res.string.calendar_today))
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: CalendarFilter,
    onSelect: (CalendarFilter) -> Unit,
) {
    val options = listOf(
        CalendarFilter.ALL to stringResource(Res.string.calendar_filter_all),
        CalendarFilter.MOVIES to stringResource(Res.string.calendar_filter_movies),
        CalendarFilter.SERIES to stringResource(Res.string.calendar_filter_series),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (filter, label) ->
            SegmentedButton(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, count: Int) {
    val relative = date.toRelativeLabel(today)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = ShapeCache.smoothPill,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = date.toDayHeaderLabel(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (relative != null) {
            Spacer(Modifier.size(8.dp))
            Surface(
                shape = ShapeCache.smoothPill,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = relative,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(Res.string.calendar_releases_count, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── States ─────────────────────────────────────────────────────────────────

@Composable
private fun FeatureDisabledState(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Tabler.Outline.Database,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.calendar_feature_disabled_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.calendar_feature_disabled_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenSettings, shape = ShapeCache.smooth12) {
                Icon(Tabler.Outline.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.calendar_open_settings))
            }
        }
    }
}
