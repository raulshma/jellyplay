package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.clickable
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.feature.player.video.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterPickerSheet(
    chapters: List<ChapterInfo>,
    currentPositionMs: Long,
    onSelect: (Long) -> Unit,
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
            Text(
                "Chapters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                itemsIndexed(chapters, contentType = { _, _ -> "chapter" }) { index, chapter ->
                    val chapterMs = chapter.startPositionTicks / 10_000
                    val isCurrentChapter = if (index < chapters.lastIndex) {
                        val nextChapterMs = chapters[index + 1].startPositionTicks / 10_000
                        currentPositionMs in chapterMs until nextChapterMs
                    } else {
                        currentPositionMs >= chapterMs
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusable().clickable { onSelect(chapter.startPositionTicks) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                chapter.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isCurrentChapter) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isCurrentChapter) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                formatDuration(chapterMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isCurrentChapter) {
                            Text("\u25B6", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
