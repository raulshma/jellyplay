package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.feature.player.video.engine.EngineBufferHealth
import com.raulshma.jellyplay.feature.player.video.engine.NetworkThroughputSnapshot
import com.raulshma.jellyplay.feature.player.video.engine.StallCause
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_buffering
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_pill_estimate
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_pill_stream
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_pill_transcode
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stall_decoder_bottleneck
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stall_network_starvation
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stall_transcode_lag
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Centered floating diagnostics pill surfacing live buffer progress, transfer
 * metrics, transcode speed, and stall classification during playback stalls.
 *
 * Implements the 1.0s grace window (User Decision 3): transient micro-stalls
 * (<1.0s) show only the minimal spinner. The diagnostics pill appears smoothly
 * only if buffering persists past 1.0s.
 *
 * Deliberately PASSIVE — no pointer input of its own, so taps within its
 * bounds reach the playback surface underneath.
 */
@Composable
fun BufferingDiagnosticsPill(
    isBuffering: Boolean,
    bufferHealth: EngineBufferHealth?,
    throughput: NetworkThroughputSnapshot?,
    stallCause: StallCause = StallCause.UNKNOWN,
    transcodeSpeedRatio: Double? = null,
    isTranscoding: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showPill by remember { mutableStateOf(false) }

    LaunchedEffect(isBuffering) {
        if (isBuffering) {
            // 1.0s grace window before revealing telemetry
            delay(1000L)
            showPill = true
        } else {
            showPill = false
        }
    }

    AnimatedVisibility(
        visible = isBuffering && showPill,
        enter = fadeIn(tween(180, easing = AlphaEasing)) + slideInVertically(
            initialOffsetY = { 20 },
            animationSpec = tween(180, easing = AlphaEasing),
        ),
        exit = fadeOut(tween(150, easing = AlphaEasing)) + slideOutVertically(
            targetOffsetY = { 10 },
            animationSpec = tween(150, easing = AlphaEasing),
        ),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(ShapeCache.smoothPill)
                .background(playerScrimColor().copy(alpha = 0.85f))
                .border(
                    width = 1.dp,
                    color = playerOnScrim().copy(alpha = 0.15f),
                    shape = ShapeCache.smoothPill,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Top row: Cause badge + Buffer Progress
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val causeBadge = when (stallCause) {
                    StallCause.NETWORK_STARVATION -> stringResource(Res.string.player_video_stall_network_starvation)
                    StallCause.SERVER_TRANSCODE_BOTTLENECK -> stringResource(Res.string.player_video_stall_transcode_lag)
                    StallCause.DECODER_OVERLOAD -> stringResource(Res.string.player_video_stall_decoder_bottleneck)
                    StallCause.BUFFER_UNDERRUN -> stringResource(Res.string.player_video_buffering)
                    StallCause.UNKNOWN -> null
                }
                if (causeBadge != null) {
                    Text(
                        text = causeBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when (stallCause) {
                            StallCause.DECODER_OVERLOAD -> MaterialTheme.colorScheme.error
                            StallCause.SERVER_TRANSCODE_BOTTLENECK -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = playerOnScrim().copy(alpha = 0.5f),
                    )
                }

                val bufferedSec = bufferHealth?.let { (it.bufferedDurationMs / 100.0).roundToInt() / 10.0 } ?: 0.0
                val targetSec = bufferHealth?.let { (it.rebufferThresholdMs / 100.0).roundToInt() / 10.0 } ?: 3.0
                val progressFraction = bufferHealth?.bufferProgressFraction ?: 0f

                Text(
                    text = "${bufferedSec}s / ${targetSec}s",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    color = playerOnScrim(),
                )

                JellyPlayLinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .width(72.dp)
                        .height(3.dp)
                        .clip(ShapeCache.smoothPill),
                )
            }

            // Bottom row: Telemetry details, folded once by
            // [bufferingPillTelemetry]: the measured download rate (only when
            // the engine actually exposes one), the EWMA bandwidth estimate on
            // its own LABELED row (≈ … (est.) — never presented as a download
            // rate), the stream bitrate, and the transcode speed.
            val telemetry = bufferingPillTelemetry(throughput, transcodeSpeedRatio, isTranscoding)
            val detailsText = buildString {
                telemetry.measuredThroughputBps?.let {
                    append("↓ ")
                    append(formatBandwidthLocal(it))
                }
                telemetry.estimatedBandwidthBps?.let {
                    if (isNotEmpty()) append(" • ")
                    append(stringResource(Res.string.player_video_pill_estimate, formatBandwidthLocal(it)))
                }
                telemetry.streamBitrateBps?.let {
                    if (isNotEmpty()) append(" • ")
                    append(stringResource(Res.string.player_video_pill_stream, formatBandwidthLocal(it)))
                }
                telemetry.transcodeSpeedRatio?.let {
                    if (isNotEmpty()) append(" • ")
                    append(stringResource(Res.string.player_video_pill_transcode, it))
                }
            }

            if (detailsText.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = detailsText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    ),
                    color = playerOnScrim().copy(alpha = 0.75f),
                )
            }
        }
    }
}

private fun formatBandwidthLocal(bps: Long): String = when {
    bps >= 1_000_000 -> {
        val mbps = (bps / 100_000.0).roundToInt() / 10.0
        "${mbps} Mbps"
    }
    bps >= 1_000 -> "${bps / 1_000} kbps"
    else -> "$bps bps"
}

/**
 * The pill's telemetry-line rows as pure data — the folds (measured-rate /
 * estimate / bitrate / transcode gates) the composable maps onto strings.
 *
 * The bandwidth estimate rides its OWN row: the engines expose no measured
 * instantaneous throughput, so `currentThroughputBps` stays 0 (the `↓` row
 * appears only if a real measurement ever lands) and the estimate renders
 * labeled as an estimate (`player_video_pill_estimate`) — never as a download
 * rate. Pure over plain inputs (the SeekChipCommitClock precedent) so the
 * gates stay JVM-testable without composition.
 */
internal data class BufferingPillTelemetry(
    /** Measured download rate (bps); null when none is measured. */
    val measuredThroughputBps: Long?,
    /** EWMA bandwidth estimate (bps); null when the engine exposes none. */
    val estimatedBandwidthBps: Long?,
    /** Active stream bitrate (bps); null below the >0 render gate. */
    val streamBitrateBps: Long?,
    /** Transcode speed ratio rounded to 0.1 steps; null when not transcoding. */
    val transcodeSpeedRatio: Double?,
)

/** The telemetry fold behind [BufferingDiagnosticsPill]'s details line. */
internal fun bufferingPillTelemetry(
    throughput: NetworkThroughputSnapshot?,
    transcodeSpeedRatio: Double?,
    isTranscoding: Boolean,
): BufferingPillTelemetry = BufferingPillTelemetry(
    measuredThroughputBps = throughput?.currentThroughputBps?.takeIf { it > 0 },
    estimatedBandwidthBps = throughput?.estimatedBandwidthBps?.takeIf { it > 0 },
    streamBitrateBps = throughput?.activeBitrateBps?.takeIf { it > 0 }?.toLong(),
    transcodeSpeedRatio = transcodeSpeedRatio
        ?.takeIf { isTranscoding }
        ?.let { (it * 10.0).roundToInt() / 10.0 },
)
