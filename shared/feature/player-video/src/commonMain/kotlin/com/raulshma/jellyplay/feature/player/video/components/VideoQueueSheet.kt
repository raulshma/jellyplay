package com.raulshma.jellyplay.feature.player.video.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.size.Size as CoilSize
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.data.playback.VideoQueueItem
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_queue_clear
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_queue_move_down
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_queue_move_up
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_queue_remove
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_queue_subtitle
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_queue_title
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_queue_up_next
import org.jetbrains.compose.resources.stringResource

/**
 * The manual up-next queue for video (the overflow menu's "Up next" entry).
 * Rows play on click; the trailing affordances move/remove without playing.
 * Reorder is button-driven (move up/down) rather than drag — the same TV +
 * touch shape the audio queue's non-drag affordances use, and it keeps every
 * action D-pad reachable inside one focus row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoQueueSheet(
    queue: List<VideoQueueItem>,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.player_video_queue_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(Res.string.player_video_queue_subtitle, queue.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(
                        Tabler.Outline.Trash,
                        contentDescription = stringResource(Res.string.player_video_queue_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                modifier = Modifier.heightIn(max = 440.dp),
            ) {
                itemsIndexed(
                    queue,
                    key = { _, item -> item.itemId },
                    contentType = { _, _ -> "videoQueueItem" },
                ) { index, item ->
                    VideoQueueRow(
                        item = item,
                        index = index,
                        itemCount = queue.size,
                        isHead = index == 0,
                        onSelect = { onSelect(index) },
                        onRemove = { onRemove(index) },
                        onMoveUp = { onMove(index, index - 1) },
                        onMoveDown = { onMove(index, index + 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoQueueRow(
    item: VideoQueueItem,
    index: Int,
    itemCount: Int,
    isHead: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSelect)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QueueThumbnail(item.imageUrl, item.title)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isHead) {
                    Text(
                        stringResource(Res.string.player_video_queue_up_next),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (index > 0) {
            IconButton(onClick = onMoveUp) {
                Icon(
                    Tabler.Outline.ChevronUp,
                    contentDescription = stringResource(Res.string.player_video_queue_move_up),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (index < itemCount - 1) {
            IconButton(onClick = onMoveDown) {
                Icon(
                    Tabler.Outline.ChevronDown,
                    contentDescription = stringResource(Res.string.player_video_queue_move_down),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                Tabler.Outline.X,
                contentDescription = stringResource(Res.string.player_video_queue_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueThumbnail(url: String?, title: String) {
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            MediaImage(
                url = url,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                size = CoilSize(144, 84),
            )
        } else {
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
