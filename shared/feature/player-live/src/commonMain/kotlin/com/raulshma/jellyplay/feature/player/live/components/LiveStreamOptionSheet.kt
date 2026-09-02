package com.raulshma.jellyplay.feature.player.live.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Server
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import com.raulshma.jellyplay.feature.player.live.generated.resources.Res
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_option_auto_desc
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_option_direct_desc
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_option_transcode_desc
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_stream_description
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_stream_title

/**
 * Bottom sheet for choosing the live TV stream delivery method (Auto /
 * Direct Stream / Transcode). Mirrors the VOD `PlaybackModeSheet`, but
 * offers a live-specific option set — live tuners cannot be served verbatim,
 * so "Force Direct Play" is intentionally absent.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LiveStreamOptionSheet(
    currentOption: LiveStreamOption,
    onSelect: (LiveStreamOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

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
            SheetHeader(
                title = stringResource(Res.string.live_stream_title),
                icon = Tabler.Outline.Server,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.live_stream_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(20.dp))

            if (isTv) {
                LazyColumn(modifier = Modifier.verticalWrapAround()) {
                    items(LiveStreamOption.entries, key = { it.name }) { option ->
                        val isSelected = option == currentOption
                        val isFirstOrSelected = isSelected ||
                            (currentOption !in LiveStreamOption.entries && option == LiveStreamOption.AUTO)
                        val optionFocusState = rememberTvFocusState(focusedScale = 1.02f)
                        val shape = ShapeCache.smooth8
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clip(shape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                )
                                .then(optionFocusState.focusModifier)
                                .ifElse(isFirstOrSelected, Modifier.focusRequester(focusRequester))
                                .tvFocusIndicator(optionFocusState, shape)
                                .clickable { onSelect(option); onDismiss() }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.displayName,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = option.description(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiveStreamOption.entries.forEach { option ->
                        val isSelected = option == currentOption
                        LiveOptionChip(
                            text = option.displayName,
                            selected = isSelected,
                            onClick = {
                                onSelect(option)
                                onDismiss()
                            },
                            style = LiveOptionChipStyle.SURFACE,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStreamOption.description(): String = when (this) {
    LiveStreamOption.AUTO -> stringResource(Res.string.live_option_auto_desc)
    LiveStreamOption.DIRECT_STREAM -> stringResource(Res.string.live_option_direct_desc)
    LiveStreamOption.TRANSCODE -> stringResource(Res.string.live_option_transcode_desc)
}
