package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.player.video.formatDuration
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_chapters
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterPickerSheet(
    chapters: List<ChapterInfo>,
    currentPositionMs: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    // Parallel to [chapters]; entries are null when the chapter has no
    // server-extracted image (or the item id is unavailable).
    chapterImages: List<String?> = emptyList(),
) {
    PickerSheetScaffold(
        title = stringResource(Res.string.player_video_chapters),
        icon = Tabler.Outline.List,
        onDismiss = onDismiss,
        // Chapters load asynchronously — re-arm the focus grab once they land,
        // and never grab onto an empty list.
        focusEffectKeys = listOf(chapters),
        canGrabFocus = { chapters.isNotEmpty() },
        contentTopGap = 12.dp,
    ) { _, focusRequester ->
        // Resolve the current chapter once in an O(n) pass instead of an
        // O(n^2) re-scan (chapters.none { chapters.indexOf(it) }) per row,
        // per position tick — the sheet re-lambdas at ~4 Hz while open.
        val currentChapterIndex = remember(chapters, currentPositionMs) {
            var found = -1
            for (i in chapters.indices) {
                val chapterMs = chapters[i].startPositionTicks / 10_000
                val nextMs = if (i < chapters.lastIndex) {
                    chapters[i + 1].startPositionTicks / 10_000
                } else {
                    Long.MAX_VALUE
                }
                if (currentPositionMs >= chapterMs && currentPositionMs < nextMs) {
                    found = i
                    break
                }
            }
            found
        }
        // The focus target is the current chapter, or the first row when no
        // chapter matches the position (mirrors the prior .none{} fallback).
        val focusTargetIndex = if (currentChapterIndex >= 0) currentChapterIndex else 0
        LazyColumn(modifier = Modifier.verticalWrapAround()) {
            itemsIndexed(chapters, key = { index, chapter -> "${index}_${chapter.startPositionTicks}" }, contentType = { _, _ -> "chapter" }) { index, chapter ->
                val isCurrentChapter = index == currentChapterIndex
                val isTarget = index == focusTargetIndex

                ChapterItem(
                    chapter = chapter,
                    imageUrl = chapterImages.getOrNull(index),
                    isCurrentChapter = isCurrentChapter,
                    isLast = index == chapters.lastIndex,
                    itemCount = chapters.size,
                    onSelect = { onSelect(chapter.startPositionTicks) },
                    modifier = Modifier.ifElse(isTarget, Modifier.focusRequester(focusRequester)),
                )
            }
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: ChapterInfo,
    imageUrl: String?,
    isCurrentChapter: Boolean,
    isLast: Boolean,
    itemCount: Int,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapterMs = chapter.startPositionTicks / 10_000

    TvFocusableOptionRow(
        selected = isCurrentChapter,
        onClick = onSelect,
        shape = pickerRowShape(itemCount = itemCount, isLast = isLast),
        modifier = modifier,
    ) {
        ChapterThumbnail(
            url = imageUrl,
            name = chapter.name,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            TvOptionRowLabel(text = chapter.name, selected = isCurrentChapter)
            Text(
                formatDuration(chapterMs),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isCurrentChapter) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.PlayerPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * 16:9 chapter thumbnail, same source as the detail screen's chapter rows
 * (`/Items/{id}/Images/Chapter/{index}` via [MediaImage]). Chapters without a
 * server-extracted image (`imageTag == null`) render the text-only row layout.
 */
@Composable
private fun ChapterThumbnail(
    url: String?,
    name: String,
) {
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 54.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            MediaImage(
                url = url,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                size = CoilSize(192, 108),
            )
        } else {
            Icon(
                Tabler.Outline.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
