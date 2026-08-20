package com.raulshma.jellyplay.feature.player.live.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronUp
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.feature.player.live.R

/**
 * Full-screen overlay shown while a live stream buffers or after it fails.
 *
 * On error it offers a "Retry" (same delivery method) plus explicit delivery-
 * method buttons (Auto / Direct Stream / Transcode) so the user can recover
 * from a transcode-path failure (e.g. server 500) or a direct-stream failure
 * (codec / tuner probe) without leaving the player. The raw technical error
 * is available behind an expandable "Show details" toggle. Mirrors the VOD
 * [com.raulshma.jellyplay.feature.player.video.components.PlaybackErrorOverlay]
 * recovery affordances, adapted to live delivery options.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveErrorBanner(
    isBuffering: Boolean,
    errorMessage: String?,
    errorDetail: String?,
    currentOption: LiveStreamOption,
    onRetry: () -> Unit,
    onRetryWithOption: (LiveStreamOption) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    retryFocusRequester: FocusRequester? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            isBuffering -> JellyPlayLoadingIndicator(color = Color.White)
            errorMessage != null -> Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.live_error_playback),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    errorMessage,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                // Expandable raw error details (stacktrace-grade), so the user
                // (or a bug report) can see the underlying cause without it
                // dominating the recovery UI.
                if (!errorDetail.isNullOrBlank()) {
                    var expanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(
                                if (expanded) R.string.live_error_hide_details
                                else R.string.live_error_show_details
                            ),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Icon(
                            imageVector = if (expanded) Tabler.Outline.ChevronUp else Tabler.Outline.ChevronDown,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.height(16.dp),
                        )
                    }
                    AnimatedVisibility(visible = expanded) {
                        Text(
                            errorDetail,
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(12.dp),
                        )
                    }
                }

                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (retryFocusRequester != null) Modifier.focusRequester(retryFocusRequester)
                            else Modifier,
                        ),
                ) {
                    Text(stringResource(R.string.live_retry))
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.live_error_try_different_method),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiveStreamOption.entries.forEach { option ->
                        val selected = option == currentOption
                        LiveOptionChip(
                            text = option.displayName,
                            selected = selected,
                            onClick = { onRetryWithOption(option) },
                            style = LiveOptionChipStyle.OVERLAY,
                        )
                    }
                }

                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.live_back))
                }
            }
        }
    }
}
