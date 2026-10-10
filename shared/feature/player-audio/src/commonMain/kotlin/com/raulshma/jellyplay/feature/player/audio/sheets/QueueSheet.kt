package com.raulshma.jellyplay.feature.player.audio.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronUp
import com.composables.icons.tabler.outline.GripVertical
import com.composables.icons.tabler.outline.List
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Radio
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.pressScale
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.reorder.rememberReorderableOrderedList
import com.raulshma.jellyplay.feature.player.audio.generated.resources.Res
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_queue_move_down
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_queue_move_up
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_queue_position
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_queue_radio
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_queue_radio_stop
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_queue_remove
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_queue_reorder_handle
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_queue_title
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_topbar_now_playing

private const val QUEUE_ITEM_CONTENT_TYPE = "queueItem"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: List<AudioQueueItem>,
    currentIndex: Int,
    isRadioActive: Boolean = false,
    onStopRadio: () -> Unit = {},
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = stringResource(Res.string.audio_queue_title),
                icon = Tabler.Outline.List,
                // Queue position of the currently-playing track (1-based).
                trailing = {
                    if (isRadioActive) {
                        RadioChip(onStopRadio = onStopRadio)
                    } else if (currentIndex in queue.indices) {
                        Text(
                            stringResource(
                                Res.string.audio_queue_position,
                                currentIndex + 1,
                                queue.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            Spacer(Modifier.height(20.dp))
            QueueRows(
                queue = queue,
                currentIndex = currentIndex,
                onSelect = onSelect,
                onRemove = onRemove,
                onMove = onMove,
            )
        }
    }
}

/**
 * The drag-to-reorder row list. Rows render from the promoted settings
 * reorderable holder's mirror ([rememberReorderableOrderedList]): a drag
 * reorders the MIRROR locally (instant feedback, no engine round-trip per
 * crossing), and ONE [onMove] is emitted at drag end — the dragged row's
 * index at gesture start → its final index — so the engine records a single
 * undoable move per gesture. The engine's queue echo then resyncs the idle
 * mirror onto the same order (no visible change). While a drag is in flight,
 * store emissions are ignored (the holder's pinned semantic), and the
 * now-playing highlight follows the dragged row by ITEM ID — the engine's
 * index still refers to the pre-drag order until the echo lands.
 */
@Composable
private fun QueueRows(
    queue: List<AudioQueueItem>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    // The dragged row (item + its mirror index at gesture start). Read inside
    // the holder's persist callback at drag end; `rememberUpdatedState` keeps
    // the callback fresh across recompositions despite the holder's
    // remember-once semantics.
    var dragStart by remember { mutableStateOf<Pair<AudioQueueItem, Int>?>(null) }
    val latestOnMove by rememberUpdatedState(onMove)

    val reorder = rememberReorderableOrderedList(
        storedOrder = queue,
        onPersist = { finalOrder ->
            val start = dragStart
            if (start != null) {
                val (draggedItem, fromIndex) = start
                val toIndex = finalOrder.indexOf(draggedItem)
                if (toIndex >= 0 && fromIndex != toIndex) latestOnMove(fromIndex, toIndex)
            }
        },
    )

    // The highlight tracks the PLAYING ITEM, not the engine's index: during a
    // drag the mirror order runs ahead of the engine's.
    val playingItemId = queue.getOrNull(currentIndex)?.id
    val mirrorCurrentIndex = reorder.items.indexOfFirst { it.id == playingItemId }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(
            reorder.items,
            key = { _, item -> item.id },
            contentType = { _, _ -> QUEUE_ITEM_CONTENT_TYPE },
        ) { index, item ->
            AnimatedQueueItem(
                index = index,
                currentIndex = if (mirrorCurrentIndex >= 0) mirrorCurrentIndex else currentIndex,
                item = item,
                isDragging = dragStart?.first == item,
                canMoveUp = index > 0,
                canMoveDown = index < reorder.items.lastIndex,
                onSelect = { onSelect(index) },
                onRemove = { onRemove(index) },
                onMoveUp = { latestOnMove(index, index - 1) },
                onMoveDown = { latestOnMove(index, index + 1) },
                onMeasure = { reorder.recordHeight(item, it) },
                onDragStart = {
                    dragStart = item to reorder.items.indexOf(item)
                    reorder.onDragStart(item)
                },
                onDrag = { delta -> reorder.onDrag(item, delta) },
                onDragEnd = {
                    // The holder persists INSIDE onDragEnd (reading dragStart)…
                    reorder.onDragEnd()
                    // …so the context clears only after the move was emitted.
                    dragStart = null
                },
            )
        }
    }
}

/**
 * One queue row: swipe-to-dismiss removal around the item content, plus the
 * reorder affordances on the trailing edge — a long-press drag handle for
 * touch, and move-up/move-down buttons that appear while the row (or any of
 * its focusable children) holds keyboard/D-pad focus, so TV and desktop
 * keyboard users can reorder by focus + OK. The row also exposes the same
 * moves as custom accessibility actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnimatedQueueItem(
    index: Int,
    currentIndex: Int,
    item: AudioQueueItem,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMeasure: (heightPx: Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val moveUpLabel = stringResource(Res.string.audio_queue_move_up)
    val moveDownLabel = stringResource(Res.string.audio_queue_move_down)
    var rowHasFocus by remember { mutableStateOf(false) }

    // Swipe-to-dismiss removes the item from the queue. The currently-playing
    // track (index == currentIndex) is intentionally still swipeable — the
    // underlying player handles queue mutation for the active item safely.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        },
        positionalThreshold = { distance -> distance * 0.6f },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val isError = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart ||
                dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isError) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surface,
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Tabler.Outline.Trash,
                    contentDescription = stringResource(Res.string.audio_queue_remove),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onMeasure(it.height) }
                .graphicsLayer { alpha = if (isDragging) 0.85f else 1f }
                .semantics {
                    customActions = buildList {
                        if (canMoveUp) {
                            add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
                        }
                        if (canMoveDown) {
                            add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueueItemContent(
                modifier = Modifier.weight(1f),
                isCurrentItem = index == currentIndex,
                name = item.name,
                artist = item.artist,
                onSelect = onSelect,
                onRowFocusChange = { rowHasFocus = it },
            )
            // Keyboard/D-pad reorder: two compact focusable buttons, surfaced
            // only while the row holds focus (touch users have the drag
            // handle below — the buttons stay out of the way otherwise).
            AnimatedVisibility(
                visible = rowHasFocus,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Row {
                    QueueMoveButton(
                        icon = Tabler.Outline.ChevronUp,
                        label = moveUpLabel,
                        enabled = canMoveUp,
                        onClick = onMoveUp,
                    )
                    QueueMoveButton(
                        icon = Tabler.Outline.ChevronDown,
                        label = moveDownLabel,
                        enabled = canMoveDown,
                        onClick = onMoveDown,
                    )
                }
            }
            QueueDragHandle(
                isDragging = isDragging,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
            )
        }
    }
}

/** The long-press drag handle (touch reorder), reusing the settings gesture. */
@Composable
private fun QueueDragHandle(
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Tabler.Outline.GripVertical,
            contentDescription = stringResource(Res.string.audio_queue_reorder_handle),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
                .copy(alpha = if (isDragging) 0.9f else 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The focused-row move-up/move-down target (D-pad/keyboard reachable). */
@Composable
private fun QueueMoveButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(ShapeCache.smoothPill)
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun QueueItemContent(
    modifier: Modifier = Modifier,
    isCurrentItem: Boolean,
    name: String,
    artist: String,
    onSelect: () -> Unit,
    onRowFocusChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .pressScale(
                interactionSource = interactionSource,
                defaultScale = 0.97f,
                spec = MaterialTheme.motionScheme.fastSpatialSpec(),
                pressedAlpha = 0.7f,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
            // Focus of the row's own click target AND its focusable children
            // (the move buttons) — one flag drives the affordance visibility.
            .onFocusChanged { onRowFocusChange(it.hasFocus) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentItem) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrentItem) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrentItem) {
            Icon(
                imageVector = Tabler.Outline.PlayerPlay,
                contentDescription = stringResource(Res.string.audio_topbar_now_playing),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * The active-radio chip in the queue header: pulse icon + label, tappable to
 * stop the refill loop (the queue itself keeps playing as it is).
 */
@Composable
private fun RadioChip(onStopRadio: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(ShapeCache.smoothPill)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onStopRadio)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Tabler.Outline.Radio,
            contentDescription = stringResource(Res.string.audio_queue_radio_stop),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(14.dp),
        )
        Text(
            stringResource(Res.string.audio_queue_radio),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
