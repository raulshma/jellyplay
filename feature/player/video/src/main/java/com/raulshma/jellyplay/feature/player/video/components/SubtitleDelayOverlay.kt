package com.raulshma.jellyplay.feature.player.video.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.player.video.R
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Compact, transparent subtitle-delay setter overlaying the video so the user
 * can watch the live subtitles shift while nudging the offset.
 *
 * The outer box is `fillMaxSize` with **no** `pointerInput`/`clickable`, so taps
 * on empty space fall through to the host gesture layer — only the inner card
 * consumes input (mirrors [MpvSubtitleOverlay]'s pass-through pattern).
 *
 * Four buttons: −1s, −0.1s, +0.1s, +1s. Rapid taps accumulate locally and flush
 * to [onChange] via a short debounce so each tap shifts the engine once the
 * burst settles (avoids queuing a config update per tap). A reset chip zeroes
 * the offset immediately. The overlay stays open until the user closes it (✕ or
 * back) — there is no auto-hide.
 */
@Composable
fun SubtitleDelayOverlay(
    currentDelayMs: Long,
    onChange: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }

    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    // Local accumulator, re-seeded from the persisted value whenever the engine
    // or another surface mutates it out from under us (e.g. reset from the hub).
    var pendingMs by remember(currentDelayMs) { mutableLongStateOf(currentDelayMs) }
    val scope = rememberCoroutineScope()
    var flushJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleFlush(newMs: Long) {
        pendingMs = newMs
        // Cancel any pending flush and start a fresh debounce window; the final
        // value is pushed to the engine once the burst settles.
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(FLUSH_DEBOUNCE_MS)
            onChange(pendingMs)
        }
    }

    fun adjust(deltaMs: Long) {
        scheduleFlush((pendingMs + deltaMs).coerceIn(MIN_MS, MAX_MS))
    }

    fun reset() {
        flushJob?.cancel()
        pendingMs = 0L
        onChange(0L)
    }

    androidx.compose.runtime.LaunchedEffect(currentDelayMs) {
        // An external change (reset button elsewhere, engine hydration) wins:
        // re-seed the local accumulator and drop any pending flush.
        if (currentDelayMs != pendingMs) {
            flushJob?.cancel()
            pendingMs = currentDelayMs
        }
    }
    androidx.compose.runtime.LaunchedEffect(isTv) {
        if (isTv) focusRequester.tryRequestFocus("subtitle-delay-overlay")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = OVERLAY_END_MARGIN_DP.dp, bottom = OVERLAY_BOTTOM_CLEARANCE_DP.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .clip(RoundedCornerShape(16.dp))
                .background(playerScrimColor().copy(alpha = 0.85f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // Header: title + close.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.player_video_subtitle_delay),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Tabler.Outline.X,
                        contentDescription = stringResource(R.string.player_video_subtitle_delay_close),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Monospace readout — the one big affordance.
            Text(
                formatDelayLabel(pendingMs),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("subtitle_delay_value"),
            )

            Spacer(Modifier.height(12.dp))

            // −1s  −0.1s  +0.1s  +1s
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DelayButton(
                    icon = Tabler.Outline.Minus,
                    label = "1.0s",
                    description = stringResource(
                        R.string.player_video_subtitle_delay_decrease_coarse,
                    ),
                    focusRequester = focusRequester,
                    onClick = { adjust(-COARSE_STEP_MS) },
                    modifier = Modifier.testTag("subtitle_delay_minus_coarse"),
                )
                DelayButton(
                    icon = Tabler.Outline.Minus,
                    label = "0.1s",
                    description = stringResource(
                        R.string.player_video_subtitle_delay_decrease_fine,
                    ),
                    onClick = { adjust(-FINE_STEP_MS) },
                    modifier = Modifier.testTag("subtitle_delay_minus_fine"),
                )
                DelayButton(
                    icon = Tabler.Outline.Plus,
                    label = "0.1s",
                    description = stringResource(
                        R.string.player_video_subtitle_delay_increase_fine,
                    ),
                    onClick = { adjust(FINE_STEP_MS) },
                    modifier = Modifier.testTag("subtitle_delay_plus_fine"),
                )
                DelayButton(
                    icon = Tabler.Outline.Plus,
                    label = "1.0s",
                    description = stringResource(
                        R.string.player_video_subtitle_delay_increase_coarse,
                    ),
                    onClick = { adjust(COARSE_STEP_MS) },
                    modifier = Modifier.testTag("subtitle_delay_plus_coarse"),
                )
            }

            // Reset chip (only when non-zero).
            AnimatedVisibility(visible = pendingMs != 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .clickable { reset() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("subtitle_delay_reset"),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(R.string.player_video_subtitle_delay_reset),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact square delay button: icon on top, label beneath. TV-focusable; the
 * first one claims the overlay's initial focus.
 */
@Composable
private fun DelayButton(
    icon: ImageVector,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val focus = rememberTvFocusState()
    Box(
        modifier = modifier
            .size(width = 64.dp, height = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .then(focus.focusModifier)
            .tvFocusIndicator(focus, RoundedCornerShape(12.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
            )
        }
    }
}

private const val FINE_STEP_MS = 100L
private const val COARSE_STEP_MS = 1000L
private const val MIN_MS = -30000L
private const val MAX_MS = 30000L
private const val FLUSH_DEBOUNCE_MS = 250L

/**
 * Anchors the compact overlay against the right edge, clearing the bottom
 * control row.
 */
private const val OVERLAY_END_MARGIN_DP = 16
internal const val OVERLAY_BOTTOM_CLEARANCE_DP = 180
