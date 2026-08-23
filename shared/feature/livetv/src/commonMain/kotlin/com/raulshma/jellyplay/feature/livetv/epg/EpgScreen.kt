package com.raulshma.jellyplay.feature.livetv.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.livetv.generated.resources.Res
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_action_cancel
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_action_done
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_action_ok
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_channel
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_epg_title
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_live
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_no_guide_available
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_record_once
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_record_program_prompt
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_record_schedule_failed
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_record_single_timer_note
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_record_success
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_recording_in_progress
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_scheduling_timer
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_will_be_recorded
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Timeline nudge for D-pad left/right presses that find no focusable program while the guide can still scroll (60 min). */
private const val TIMELINE_SHIFT_DP = 240

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgScreen(
    onProgramClick: (LiveTvProgram) -> Unit,
    onBack: () -> Unit,
    onRecordClick: ((LiveTvProgram) -> Unit)? = null,
    viewModel: EpgViewModel = koinViewModel(),
) {
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = viewModel.isLoading,
        hasError = viewModel.error != null,
        networkStatus = networkStatus,
    )

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val bottomPad = adaptiveInfo.bottomPadding(isTv)

    val backgroundColor = rememberScreenBackgroundColor()
    val focusRequester = remember { FocusRequester() }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.livetv_epg_title),
        onBack = onBack,
        backgroundColor = backgroundColor,
        topBarStyle = com.raulshma.jellyplay.core.ui.components.TopBarStyle.None,
        actions = {
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(start = 12.dp),
            )
        },
    ) {
        when {
            viewModel.error != null && viewModel.channels.isEmpty() -> {
                ErrorScreen(
                    message = viewModel.error!!,
                    onRetry = { viewModel.loadGuide() },
                )
            }
            viewModel.channels.isEmpty() && !viewModel.isLoading -> {
                ScreenEmptyState(
                    icon = Tabler.Outline.Calendar,
                    title = stringResource(Res.string.livetv_no_guide_available),
                )
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = viewModel.isLoading,
                    onRefresh = { viewModel.loadGuide() },
                ) {
                    EpgGrid(
                        gridData = viewModel.gridData,
                        now = viewModel.now,
                        contentPadding = contentPad,
                        bottomPadding = bottomPad,
                        onProgramClick = onProgramClick,
                        onRecordClick = onRecordClick ?: { program -> viewModel.requestRecord(program) },
                        focusRequester = focusRequester,
                    )
                }
            }
        }
    }

    viewModel.recordDialog?.let { state ->
        RecordDialog(
            state = state,
            onConfirm = { viewModel.confirmRecord() },
            onDismiss = { viewModel.dismissRecordDialog() },
        )
    }
}

@Composable
private fun RecordDialog(
    state: RecordDialogState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is RecordDialogState.Confirm -> ConfirmDialog(
            title = stringResource(Res.string.livetv_record_program_prompt),
            message = state.program.name,
            confirmText = stringResource(Res.string.livetv_record_once),
            dismissText = stringResource(Res.string.livetv_action_cancel),
            tone = ConfirmTone.NEUTRAL,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            content = {
                state.program.episodeTitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(Res.string.livetv_record_single_timer_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        is RecordDialogState.Requesting -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.livetv_recording_in_progress)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JellyPlayLoadingIndicator()
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.livetv_scheduling_timer))
                }
            },
            confirmButton = {},
            dismissButton = {},
        )
        is RecordDialogState.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.livetv_record_success)) },
            text = { Text(stringResource(Res.string.livetv_will_be_recorded, state.programName)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.livetv_action_done)) } },
        )
        is RecordDialogState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.livetv_record_schedule_failed)) },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.livetv_action_ok)) } },
        )
    }
}

@Composable
private fun EpgGrid(
    gridData: EpgGridData,
    now: java.time.Instant,
    contentPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onProgramClick: (LiveTvProgram) -> Unit,
    onRecordClick: ((LiveTvProgram) -> Unit)?,
    focusRequester: FocusRequester,
) {
    val density = LocalDensity.current
    val horizontalScrollState = rememberScrollState()
    val lazyListState = rememberLazyListState()
    val nowOffsetDp = remember(now, gridData.windowStart) {
        if (now < gridData.windowStart) 0f
        else if (now > gridData.windowEnd) gridData.totalWidthDp
        else now.offsetDp(gridData.windowStart)
    }
    val channelColumnWidth = EpgGridLayout.CHANNEL_COLUMN_WIDTH
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // D-pad right/left first runs the normal focus search; when there is no
    // focusable program in that direction but the shared timeline window can
    // still scroll, nudge it so the revealed programs become the next focus
    // targets (all cells stay composed, so focus search picks them up once
    // they're scrolled into view).
    fun shiftTimeline(forward: Boolean): Boolean {
        val delta = with(density) { TIMELINE_SHIFT_DP.dp.roundToPx() } * if (forward) 1 else -1
        val target = (horizontalScrollState.value + delta).coerceIn(0, horizontalScrollState.maxValue)
        if (target == horizontalScrollState.value) return false
        scope.launch { horizontalScrollState.animateScrollTo(target) }
        return true
    }

    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = gridData.rows.size,
        tag = "epg_init",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onDpadKey(
                onRight = { focusManager.moveFocus(FocusDirection.Right) || shiftTimeline(forward = true) },
                onLeft = { focusManager.moveFocus(FocusDirection.Left) || shiftTimeline(forward = false) },
            ),
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            // ── Sticky time-header row ──
            item(key = "epg_time_header", contentType = "time_header") {
                TimeHeaderRow(
                    windowStart = gridData.windowStart,
                    windowEnd = gridData.windowEnd,
                    totalWidthDp = gridData.totalWidthDp,
                    channelColumnWidth = channelColumnWidth,
                    horizontalScrollState = horizontalScrollState,
                    nowOffsetDp = nowOffsetDp,
                )
            }

            // ── Channel rows ──
            itemsIndexed(
                items = gridData.rows,
                key = { _, row -> "channel_${row.channel.id}" },
                contentType = { _, _ -> "channel_row" },
            ) { index, row ->
                // Layout is purely geometric: depends only on the (stable)
                // row + grid window, NOT on `now`. Re-laid out only when the
                // data or fetch window changes, so the 30s now-tick never
                // invalidates it.
                val rowLayout = remember(row, gridData) {
                    layoutChannelRow(row, gridData)
                }
                ChannelProgramsRow(
                    rowLayout = rowLayout,
                    windowStart = gridData.windowStart,
                    now = now,
                    channelColumnWidth = channelColumnWidth,
                    totalWidthDp = gridData.totalWidthDp,
                    horizontalScrollState = horizontalScrollState,
                    onProgramClick = onProgramClick,
                    onRecordClick = onRecordClick,
                    modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                )
            }
        }

        // ── "Now" vertical indicator line ──
        // Rendered as an overlay across the full grid height, offset by the
        // current horizontal scroll so it tracks the current time accurately.
        // The scroll value is read only inside the offset/graphicsLayer
        // lambdas (layout/draw phase) so EPG scrolling never recomposes the
        // grid; visibility is gated by a draw-phase alpha instead of the
        // former composition-time range check.
        if (now >= gridData.windowStart && now <= gridData.windowEnd) {
            val channelColPx = with(density) { channelColumnWidth.toPx() }
            val nowOffsetPx = with(density) { nowOffsetDp.dp.toPx() }
            val totalWidthPx = with(density) { gridData.totalWidthDp.dp.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        val scrollPx = horizontalScrollState.value
                        IntOffset(
                            x = (channelColPx + nowOffsetPx - scrollPx).roundToInt(),
                            y = 0,
                        )
                    }
                    .graphicsLayer {
                        val scrollPx = horizontalScrollState.value
                        val xPx = channelColPx + nowOffsetPx - scrollPx
                        alpha = if (xPx >= channelColPx && xPx <= channelColPx + totalWidthPx) 1f else 0f
                    }
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
            )
        }
    }
}

@Composable
private fun TimeHeaderRow(
    windowStart: java.time.Instant,
    windowEnd: java.time.Instant,
    totalWidthDp: Float,
    channelColumnWidth: androidx.compose.ui.unit.Dp,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    nowOffsetDp: Float,
) {
    val markers = remember(windowStart, windowEnd) { buildTimeMarkers(windowStart, windowEnd) }
    Row(modifier = Modifier.fillMaxWidth()) {
        // Sticky channel-column header
        Box(
            modifier = Modifier
                .width(channelColumnWidth)
                .height(EpgGridLayout.TIME_HEADER_HEIGHT)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(Res.string.livetv_channel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Horizontally-scrolling time ruler
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EpgGridLayout.TIME_HEADER_HEIGHT)
                .horizontalScroll(horizontalScrollState),
        ) {
            Box(modifier = Modifier.width(totalWidthDp.dp)) {
                markers.forEach { marker ->
                    val x = marker.offsetDp(windowStart)
                    Text(
                        text = marker.formatTimeHeader(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .offset(x = x.dp)
                            .padding(start = 4.dp, top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelProgramsRow(
    rowLayout: ChannelRowLayout,
    windowStart: java.time.Instant,
    now: java.time.Instant,
    channelColumnWidth: androidx.compose.ui.unit.Dp,
    totalWidthDp: Float,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    onProgramClick: (LiveTvProgram) -> Unit,
    onRecordClick: ((LiveTvProgram) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Derive the single currently-live program id for this row from `now`.
    // The whole row recomposes on a 30s tick, but [ProgramCell] only re-reads
    // this State via a derived check, so only the one cell whose live status
    // actually flips is invalidated — not the entire program strip layout.
    val liveProgramId by remember(rowLayout, windowStart) {
        derivedStateOf {
            val live = rowLayout.programLayouts.firstOrNull { layout ->
                val s = layout.program.startInstant()
                val e = layout.program.endInstant() ?: s
                s != null && now >= s && now < (e ?: s)
            }
            live?.program?.id
        }
    }
    Row(modifier = modifier.fillMaxWidth().height(EpgGridLayout.CHANNEL_ROW_HEIGHT)) {
        // Sticky channel-name cell
        ChannelNameCell(
            name = rowLayout.channel.name,
            number = rowLayout.channel.number,
            width = channelColumnWidth,
        )
        // Programs strip — horizontal scroll shared with header row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState),
        ) {
            Box(modifier = Modifier.width(totalWidthDp.dp)) {
                rowLayout.programLayouts.forEach { layout ->
                    // Memoize per-cell click lambdas so they don't reallocate
                    // on every recomposition of the strip.
                    val clickLambda = remember(layout.program.id, onProgramClick) {
                        { onProgramClick(layout.program) }
                    }
                    val recordLambda = remember(layout.program.id, onRecordClick) {
                        onRecordClick?.let { cb -> { cb(layout.program) } }
                    }
                    ProgramCell(
                        layout = layout,
                        isCurrent = liveProgramId == layout.program.id,
                        onClick = clickLambda,
                        onRecordClick = recordLambda,
                        modifier = Modifier.offset(x = layout.startOffsetDp.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelNameCell(
    name: String,
    number: String?,
    width: androidx.compose.ui.unit.Dp,
) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (!number.isNullOrBlank()) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgramCell(
    layout: ProgramLayout,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRecordClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState()
    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    }
    val borderColor = if (isCurrent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }
    Box(
        modifier = modifier
            .width(layout.widthDp.dp)
            .fillMaxHeight()
            .padding(end = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(focusState.focusModifier)
                .tvFocusIndicator(focusState, ShapeCache.smooth8)
                .clip(ShapeCache.smooth8)
                .background(containerColor)
                .border(width = 1.dp, color = borderColor, shape = ShapeCache.smooth8)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = layout.program.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isCurrent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = stringResource(Res.string.livetv_live),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (onRecordClick != null) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .focusIndicator(CircleShape)
                        .clickable(onClick = onRecordClick)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Tabler.Outline.Circle,
                        contentDescription = stringResource(Res.string.livetv_record_once),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}
