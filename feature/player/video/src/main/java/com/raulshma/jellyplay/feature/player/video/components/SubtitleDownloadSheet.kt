package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
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
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
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
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }
    val loadBtnFocus = rememberTvFocusState()

    LaunchedEffect(isTv) {
        if (isTv) {
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
                    .padding(horizontal = 16.dp)
                    .ifElse(isTv, Modifier.focusRequester(focusRequester))
                    .then(loadBtnFocus.focusModifier)
                    .tvFocusIndicator(loadBtnFocus, ShapeCache.smoothPill),
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
                LazyColumn(modifier = Modifier.verticalWrapAround()) {
                    // Composite key: providers (OpenSubtitles, etc.) occasionally
                    // return duplicate `Id`s across sources, which previously
                    // crashed with `IllegalArgumentException: Key "x" was already
                    // used`. Combining the (stable where unique) id with the
                    // index guarantees uniqueness without losing identity on
                    // ordinary list updates.
                    itemsIndexed(
                        subtitles,
                        key = { index, sub -> "${sub.id}_$index" },
                        contentType = { _, _ -> "subtitle" },
                    ) { index, sub ->
                        SubtitleDownloadItem(
                            subtitle = sub,
                            isLast = index == subtitles.lastIndex,
                            itemCount = subtitles.size,
                            onDownload = { onDownload(sub) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleDownloadItem(
    subtitle: RemoteSubtitleInfo,
    isLast: Boolean,
    itemCount: Int,
    onDownload: () -> Unit,
) {
    val shape = when {
        itemCount == 1 -> ShapeCache.smooth16
        isLast -> com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(if (isLast) itemCount - 1 else 0, itemCount)
        else -> ShapeCache.smooth8
    }
    val focusState = rememberTvFocusState(focusedScale = 1.02f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.04f))
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape)
            .clickable { onDownload() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                subtitle.name ?: subtitle.language ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (subtitle.isHashMatch) {
                    Text(
                        "Perfect Match",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                subtitle.communityRating?.let {
                    Text(
                        "\u2605 ${"%.1f".format(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                subtitle.language?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                subtitle.format?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                subtitle.provider?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        subtitle.downloadCount?.let { count ->
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
