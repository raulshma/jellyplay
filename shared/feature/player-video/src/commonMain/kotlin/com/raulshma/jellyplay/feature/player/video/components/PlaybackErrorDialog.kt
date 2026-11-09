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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_dismiss
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_error
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_error_fatal
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_error_retryable
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_retry_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_retry_with
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_try_another_engine








import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.RequestOrRestoreFocus
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.feature.player.video.PlatformTranscodeReason
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiR
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reasons_title
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
    transcodeReasons: List<PlatformTranscodeReason> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val alternativeEngines = PlayerType.entries.filter {
        it != PlayerType.EXTERNAL && it != currentPlayerType
    }
    val isTv = LocalTvMode.current
    // TV: land initial focus on the primary recovery action — Retry when the
    // error is recoverable, otherwise the first switch-engine action (the
    // overlay's whole point is to get the user unstuck). Without this the
    // player surface keeps focus and its D-pad arms eat every key press, so
    // the dialog is unreachable with a remote. Mirrors the live player's
    // error-banner retry grab.
    val primaryActionFocus = remember { FocusRequester() }
    // Last action in the dialog, for the Up-at-the-top wrap. A plain Column is
    // not a focus target, so a container-level exit handler never runs here —
    // the wrap lives on the first/last actions themselves.
    val lastActionFocus = remember { FocusRequester() }
    if (isTv) {
        RequestOrRestoreFocus(primaryActionFocus, debugKey = "tv_playback_error")
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
                    stringResource(Res.string.player_video_playback_error),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                // Friendly headline rather than the raw exception string, which may be a
                // stack-trace-like decoder/codec message that reads poorly to users.
                Text(
                    if (retryable) {
                        stringResource(Res.string.player_video_playback_error_retryable)
                    } else {
                        stringResource(Res.string.player_video_playback_error_fatal)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .playbackErrorActionFocus(
                                shape = ShapeCache.smooth12,
                                wrapUpTo = lastActionFocus,
                            )
                            .ifElse(isTv, Modifier.focusRequester(primaryActionFocus)),
                        shape = ShapeCache.smooth12,
                    ) {
                        Text(stringResource(Res.string.player_video_retry_on, currentPlayerType.displayName))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(Res.string.player_video_try_another_engine),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                alternativeEngines.forEachIndexed { index, engine ->
                    TextButton(
                        onClick = { onRetryWithEngine(engine) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .playbackErrorActionFocus(
                                shape = ShapeCache.smooth12,
                                // Fatal errors have no Retry row, so the
                                // first switch-engine action is the top edge.
                                wrapUpTo = if (!retryable && index == 0) lastActionFocus else null,
                            )
                            // Fatal (non-retryable) errors land on the first
                            // switch-engine action instead — there is no Retry.
                            .ifElse(
                                isTv && !retryable && index == 0,
                                Modifier.focusRequester(primaryActionFocus),
                            ),
                        shape = ShapeCache.smooth12,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    ) {
                        Text(stringResource(Res.string.player_video_retry_with, engine.displayName))
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .playbackErrorActionFocus(
                            shape = ShapeCache.smooth12,
                            // Dismiss is the bottom edge; Up only wraps when
                            // it is also the top edge (no Retry, no engines).
                            wrapUpTo = if (!retryable && alternativeEngines.isEmpty()) lastActionFocus else null,
                            wrapDownTo = primaryActionFocus,
                        )
                        .ifElse(
                            isTv && !retryable && alternativeEngines.isEmpty(),
                            Modifier.focusRequester(primaryActionFocus),
                        )
                        .ifElse(isTv, Modifier.focusRequester(lastActionFocus)),
                    shape = ShapeCache.smooth12,
                ) {
                    Text(stringResource(Res.string.player_video_dismiss))
                }
            }
        }
    }
}

/**
 * TV D-pad actions are full-width rows: sideways has nowhere to go, so cancel
 * the left/right focus search — otherwise it escapes to the player surface
 * behind the scrim. [focusIndicator] paints the same focus ring the rest of
 * the player chrome uses so the selected action is visible from the couch.
 *
 * [wrapUpTo]/[wrapDownTo] are the dialog's cyclic edges: the actions are laid
 * out directly in a plain Column (not a focusable group), so a container-level
 * exit handler never fires. Setting the directional properties on the
 * first/last action itself keeps Up/Down inside the dialog instead of
 * escaping to the player surface and its seek arms.
 */
@Composable
private fun Modifier.playbackErrorActionFocus(
    shape: Shape,
    wrapUpTo: FocusRequester? = null,
    wrapDownTo: FocusRequester? = null,
): Modifier {
    val isTv = LocalTvMode.current
    return this
        .then(
            if (isTv) {
                Modifier.focusProperties {
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                    if (wrapUpTo != null) up = wrapUpTo
                    if (wrapDownTo != null) down = wrapDownTo
                }
            } else {
                Modifier
            },
        )
        .focusIndicator(shape)
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
    transcodeReasons: List<PlatformTranscodeReason> = emptyList(),
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
