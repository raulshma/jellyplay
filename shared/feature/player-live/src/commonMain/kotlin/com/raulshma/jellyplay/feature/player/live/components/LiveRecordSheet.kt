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
import androidx.compose.foundation.lazy.itemsIndexed
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.RecordMail
import com.composables.icons.tabler.outline.Video
import com.composables.icons.tabler.outline.VideoPlus
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import com.raulshma.jellyplay.feature.player.live.generated.resources.Res
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_cancel_recording
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_cancel_series
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_record_no_program
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_record_once
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_record_series
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_record_title

/**
 * One record action surfaced by [LiveRecordSheet]. Built once per composition
 * so the phone (FlowRow) and TV (LazyColumn) renderers iterate the same list —
 * the project's canonical dual-renderer sheet idiom (see LiveStreamOptionSheet).
 */
private data class RecordAction(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val destructive: Boolean,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LiveRecordSheet(
    program: LiveTvProgram?,
    onRecordOnce: () -> Unit,
    onRecordSeries: () -> Unit,
    onCancelTimer: () -> Unit,
    onCancelSeries: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            focusRequester.tryRequestFocus("record-sheet")
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
                title = stringResource(Res.string.live_record_title),
                icon = Tabler.Outline.RecordMail,
            )
            if (program == null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.live_record_no_program),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                return@Column
            }

            val programName = program.name
            val episode = program.episodeTitle?.takeIf { it.isNotBlank() }
            Spacer(Modifier.height(4.dp))
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    programName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (episode != null) {
                    Text(
                        episode,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // Build the action set once from the program's timer state — both
            // renderers iterate this list. Mirrors jellyfin-web's
            // recordinghelper.toggleRecording decision tree.
            val hasTimer = !program.timerId.isNullOrEmpty()
            val hasSeriesTimer = !program.seriesTimerId.isNullOrEmpty()
            // Resolve strings in the composable scope; the remember lambda below
            // is NOT a @Composable scope, so stringResource() cannot live there.
            val cancelRecordingLabel = stringResource(Res.string.live_cancel_recording)
            val recordOnceLabel = stringResource(Res.string.live_record_once)
            val cancelSeriesLabel = stringResource(Res.string.live_cancel_series)
            val recordSeriesLabel = stringResource(Res.string.live_record_series)
            val actions = remember(program.id, hasTimer, hasSeriesTimer) {
                buildList {
                    if (hasTimer) {
                        add(
                            RecordAction(
                                key = "cancel_once",
                                label = cancelRecordingLabel,
                                icon = Tabler.Outline.X,
                                destructive = true,
                                onClick = onCancelTimer,
                            )
                        )
                    } else {
                        add(
                            RecordAction(
                                key = "record_once",
                                label = recordOnceLabel,
                                icon = Tabler.Outline.Video,
                                destructive = false,
                                onClick = onRecordOnce,
                            )
                        )
                    }
                    if (hasSeriesTimer) {
                        add(
                            RecordAction(
                                key = "cancel_series",
                                label = cancelSeriesLabel,
                                icon = Tabler.Outline.X,
                                destructive = true,
                                onClick = onCancelSeries,
                            )
                        )
                    } else {
                        add(
                            RecordAction(
                                key = "record_series",
                                label = recordSeriesLabel,
                                icon = Tabler.Outline.VideoPlus,
                                destructive = false,
                                onClick = onRecordSeries,
                            )
                        )
                    }
                }
            }

            if (isTv) {
                LazyColumn(modifier = Modifier.verticalWrapAround()) {
                    itemsIndexed(actions, key = { _, action -> action.key }) { index, action ->
                        val actionFocusState = rememberTvFocusState(focusedScale = 1.02f)
                        val shape = ShapeCache.smooth8
                        val isFirst = index == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clip(shape)
                                .background(
                                    if (action.destructive) MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                )
                                .then(actionFocusState.focusModifier)
                                .ifElse(isFirst, Modifier.focusRequester(focusRequester))
                                .tvFocusIndicator(actionFocusState, shape)
                                .clickable {
                                    action.onClick()
                                    onDismiss()
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = if (action.destructive) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (action.destructive) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                            )
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
                    actions.forEach { action ->
                        RecordActionChip(
                            text = action.label,
                            destructive = action.destructive,
                            onClick = {
                                action.onClick()
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Surface-styled action chip for the phone sheet. Destructive actions
 * (cancel) use the error palette so a cancel stands apart from a record.
 */
@Composable
private fun RecordActionChip(
    text: String,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(ShapeCache.smoothPill)
            .background(
                if (destructive) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
