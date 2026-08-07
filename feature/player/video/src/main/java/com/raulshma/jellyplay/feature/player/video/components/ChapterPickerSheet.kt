package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.feature.player.video.formatDuration
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterPickerSheet(
    chapters: List<ChapterInfo>,
    currentPositionMs: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv, chapters) {
        if (isTv && chapters.isNotEmpty()) {
            focusRequester.tryRequestFocus("sheet")
        }
    }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Chapters",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
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
}

@Composable
private fun ChapterItem(
    chapter: ChapterInfo,
    isCurrentChapter: Boolean,
    isLast: Boolean,
    itemCount: Int,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapterMs = chapter.startPositionTicks / 10_000
    val shape = when {
        itemCount == 1 -> ShapeCache.smooth16
        isLast -> com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(if (isLast) itemCount - 1 else 0, itemCount)
        else -> ShapeCache.smooth8
    }
    val focusState = rememberTvFocusState(focusedScale = 1.02f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(shape)
            .background(
                if (isCurrentChapter) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
            )
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape)
            .clickable { onSelect() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                chapter.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isCurrentChapter) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isCurrentChapter) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
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
