package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.ui.player.FormattedTranscodeReason
import com.raulshma.jellyplay.core.ui.R as CoreUiR
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

/**
 * In-window playback error overlay.
 *
 * Rendered as a plain Compose layer (NOT a Material `AlertDialog`/popup) so it inherits the
 * player's immersive window flags. A popup opens a new top-level window that does not carry
 * the immersive flag, so the status/navigation bars would flash on every error — the same
 * problem solved for the bottom sheets (see PlayerModalBottomSheet) and overflow menu.
 */
@Composable
fun PlaybackErrorOverlay(
    errorMessage: String,
    currentPlayerType: PlayerType,
    /**
     * Whether the underlying [EngineError] is recoverable on the same engine
     * (Network, Render, watchdog timeout). When `true`, a "Retry" button is
     * shown ahead of the switch-engine buttons. Fatal errors (Decoder, Drm)
     * pass `false` → only switch-engine buttons + Dismiss.
     */
    retryable: Boolean,
    onRetry: () -> Unit,
    onRetryWithEngine: (PlayerType) -> Unit,
    onDismiss: () -> Unit,
    /** Server-reported transcode reasons when the failed stream was a
     *  transcode; naturally empty for direct-play errors — the session-side
     *  fetch only arms on transcode resolves, so the engine error stands
     *  alone there. */
    transcodeReasons: List<FormattedTranscodeReason> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val alternativeEngines = PlayerType.entries.filter {
        it != PlayerType.EXTERNAL && it != currentPlayerType
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = ShapeCache.smooth20,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Tabler.Outline.AlertCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.player_video_playback_error),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                // Friendly headline rather than the raw exception string, which may be a
                // stack-trace-like decoder/codec message that reads poorly to users.
                Text(
                    if (retryable) {
                        stringResource(R.string.player_video_playback_error_retryable)
                    } else {
                        stringResource(R.string.player_video_playback_error_fatal)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (errorMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                if (transcodeReasons.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = ShapeCache.smooth12,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                stringResource(CoreUiR.string.transcode_reasons_title),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            transcodeReasons.forEach { reason ->
                                Text(
                                    text = reason.renderedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (retryable) {
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ShapeCache.smooth12,
                    ) {
                        Text(stringResource(R.string.player_video_retry_on, currentPlayerType.displayName))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.player_video_try_another_engine),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                alternativeEngines.forEach { engine ->
                    TextButton(
                        onClick = { onRetryWithEngine(engine) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ShapeCache.smooth12,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    ) {
                        Text(stringResource(R.string.player_video_retry_with, engine.displayName))
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCache.smooth12,
                ) {
                    Text(stringResource(R.string.player_video_dismiss))
                }
            }
        }
    }
}

/** Backwards-compatible alias for the previous [PlaybackErrorDialog] name. */
@Composable
fun PlaybackErrorDialog(
    errorMessage: String,
    currentPlayerType: PlayerType,
    retryable: Boolean,
    onRetry: () -> Unit,
    onRetryWithEngine: (PlayerType) -> Unit,
    onDismiss: () -> Unit,
    transcodeReasons: List<FormattedTranscodeReason> = emptyList(),
) {
    PlaybackErrorOverlay(
        errorMessage = errorMessage,
        currentPlayerType = currentPlayerType,
        retryable = retryable,
        onRetry = onRetry,
        onRetryWithEngine = onRetryWithEngine,
        onDismiss = onDismiss,
        transcodeReasons = transcodeReasons,
    )
}
