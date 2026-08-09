package com.raulshma.jellyplay.feature.player.audio.sheets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.List
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.feature.player.audio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: List<AudioQueueItem>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
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
                title = stringResource(R.string.audio_queue_title),
                icon = Tabler.Outline.List,
                // Queue position of the currently-playing track (1-based).
                trailing = {
                    if (currentIndex in queue.indices) {
                        Text(
                            stringResource(
                                R.string.audio_queue_position,
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
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(
                    queue,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> "queueItem" },
                ) { index, item ->
                    AnimatedQueueItem(
                        index = index,
                        currentIndex = currentIndex,
                        item = item,
                        onSelect = { onSelect(index) },
                        onRemove = { onRemove(index) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnimatedQueueItem(
    index: Int,
    currentIndex: Int,
    item: AudioQueueItem,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
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
                    contentDescription = stringResource(R.string.audio_queue_remove),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        QueueItemContent(
            isCurrentItem = index == currentIndex,
            name = item.name,
            artist = item.artist,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun QueueItemContent(
    isCurrentItem: Boolean,
    name: String,
    artist: String,
    onSelect: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "queueItemScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "queueItemAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
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
                contentDescription = stringResource(R.string.audio_topbar_now_playing),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
