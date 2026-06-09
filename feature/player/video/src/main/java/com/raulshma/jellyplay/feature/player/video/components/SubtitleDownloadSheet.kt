package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubtitleDownloadSheet(
    subtitles: List<RemoteSubtitleInfo>,
    isLoading: Boolean,
    onDownload: (RemoteSubtitleInfo) -> Unit,
    onLoadLocalFile: () -> Unit,
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
                "Download Subtitles",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onLoadLocalFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Tabler.Outline.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Load from device")
            }
            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                JellyPlayLoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(24.dp),
                )
            } else if (subtitles.isEmpty()) {
                Text(
                    "No remote subtitles available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn {
                    itemsIndexed(subtitles, key = { _, sub -> sub.id }, contentType = { _, _ -> "subtitle" }) { index, sub ->
                        val shape = when {
                            subtitles.size == 1 -> ShapeCache.smooth16
                            index == 0 -> com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(0, subtitles.size)
                            index == subtitles.lastIndex -> com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(index, subtitles.size)
                            else -> ShapeCache.smooth8
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clip(shape)
                                .background(Color.White.copy(alpha = 0.04f))
                                .tvFocusable()
                                .clickable { onDownload(sub) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    sub.name ?: sub.language ?: "Unknown",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (sub.isHashMatch) {
                                        Text(
                                            "Perfect Match",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    sub.communityRating?.let {
                                        Text(
                                            "★ ${"%.1f".format(it)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    sub.language?.let {
                                        Text(
                                            it.uppercase(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    sub.format?.let {
                                        Text(
                                            it.uppercase(),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    sub.provider?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            sub.downloadCount?.let { count ->
                                Text(
                                    "$count",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
