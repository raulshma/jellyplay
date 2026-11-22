package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Cpu
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Wifi
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.player.video.engine.StallCause
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_recovery_boost_buffer
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_recovery_keep_waiting
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_recovery_switch_engine
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_recovery_switch_engine_generic
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_recovery_switch_quality
import com.raulshma.jellyplay.feature.player.video.state.ActionableRecovery
import com.raulshma.jellyplay.feature.player.video.state.RecoveryAction
import org.jetbrains.compose.resources.stringResource

/**
 * Actionable slow-playback recovery banner presented when playback stalls
 * chronically or bandwidth drops severely.
 *
 * Implements User Decision 2 (Zero silent quality drops): strictly user-confirmed
 * 1-click remediation.
 *
 * Dual-surface design:
 * - Mobile: Floating card anchored bottom-center above controls clearance.
 * - TV: D-pad focus auto-grab on the primary button, BACK key priority dismissal.
 */
@Composable
fun PlaybackRecoveryBanner(
    recovery: ActionableRecovery,
    onApplyAction: (RecoveryAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val isTv = LocalTvMode.current
    val localPrimaryFocusRequester = remember { FocusRequester() }
    val primaryFocusRequester = focusRequester ?: localPrimaryFocusRequester
    val dismissFocusRequester = remember { FocusRequester() }

    val primaryTvFocus = rememberTvFocusState()
    val dismissTvFocus = rememberTvFocusState()

    LaunchedEffect(recovery.id, isTv) {
        if (isTv) {
            primaryFocusRequester.tryRequestFocus("recovery_primary")
        }
    }

    Surface(
        shape = ShapeCache.smooth20,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        modifier = modifier
            .padding(horizontal = 24.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = ShapeCache.smooth20,
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val icon = when (recovery.cause) {
                    StallCause.NETWORK_STARVATION -> Tabler.Outline.Wifi
                    StallCause.DECODER_OVERLOAD -> Tabler.Outline.Cpu
                    StallCause.SERVER_TRANSCODE_BOTTLENECK -> Tabler.Outline.DeviceTv
                    else -> Tabler.Outline.AlertTriangle
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = recovery.title,
                    style = if (isTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = recovery.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Primary Action Button (1-Click Recovery)
                val primaryLabel = actionLabel(recovery.primaryAction)
                Surface(
                    shape = ShapeCache.smoothPill,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(ShapeCache.smoothPill)
                        .ifElse(
                            isTv,
                            Modifier
                                .focusRequester(primaryFocusRequester)
                                .then(primaryTvFocus.focusModifier)
                                .tvFocusIndicator(primaryTvFocus, shape = ShapeCache.smoothPill),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onApplyAction(recovery.primaryAction)
                        },
                ) {
                    Text(
                        text = primaryLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }

                // Secondary Action Button (Dismiss / Keep Waiting)
                Surface(
                    shape = ShapeCache.smoothPill,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .clip(ShapeCache.smoothPill)
                        .ifElse(
                            isTv,
                            Modifier
                                .focusRequester(dismissFocusRequester)
                                .then(dismissTvFocus.focusModifier)
                                .tvFocusIndicator(dismissTvFocus, shape = ShapeCache.smoothPill),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onDismiss()
                        },
                ) {
                    Text(
                        text = stringResource(Res.string.player_video_recovery_keep_waiting),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun actionLabel(action: RecoveryAction): String = when (action) {
    is RecoveryAction.SwitchQuality -> stringResource(Res.string.player_video_recovery_switch_quality, action.height)
    is RecoveryAction.SwitchEngine -> when (action.targetEngine) {
        PlayerType.MPV, PlayerType.EXO_PLAYER -> stringResource(Res.string.player_video_recovery_switch_engine, action.targetEngine.displayName)
        else -> stringResource(Res.string.player_video_recovery_switch_engine_generic)
    }
    is RecoveryAction.BoostBufferSize -> stringResource(Res.string.player_video_recovery_boost_buffer)
    RecoveryAction.Dismiss -> stringResource(Res.string.player_video_recovery_keep_waiting)
}
