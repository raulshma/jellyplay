package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Moon
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.player.video.StillWatchingPromptState
import com.raulshma.jellyplay.feature.player.video.StillWatchingReason
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_still_watching_continue
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_still_watching_episodes_subtitle
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_still_watching_hours_subtitle
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_still_watching_stop
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_still_watching_title
import kotlinx.coroutines.delay

/**
 * The "Still watching?" confirm overlay (feature 1.3): raised instead of a
 * silent auto-advance when the still-watching gate fires — the episode arm at
 * natural playback end, the hours arm when the pass-out protection trips and
 * the mode includes HOURS. **Continue** (counter reset → play next / resume)
 * keeps going; **Stop** (or the countdown expiring) pauses and stops
 * autoplaying. A thin renderer: the countdown state machine lives on
 * [StillWatchingPromptState] (the VM ticks it once per second through
 * [onTick]); [pauseCountdown] reuses the Up Next card's hook — a settings
 * sheet or the screen lock must not rush the answer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StillWatchingOverlay(
    state: StillWatchingPromptState?,
    onContinue: () -> Unit,
    onStop: () -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier,
    // Pauses the auto-dismiss countdown while a settings sheet is open or the
    // player is locked (the NextEpisodeOverlay pauseCountdown hook, reused).
    pauseCountdown: Boolean = false,
) {
    val isTv = LocalTvMode.current
    val tvContinueFocusRequester = remember { FocusRequester() }

    // TV: put focus on Continue as the card appears, so D-pad users answer
    // with one press (the NextEpisodeOverlay's 300 ms settle delay).
    LaunchedEffect(state?.reason) {
        if (state != null && isTv) {
            delay(300)
            tvContinueFocusRequester.tryRequestFocus("tv_still_watching")
        }
    }

    // The auto-dismiss driver: one tick per second while visible and not
    // paused. The VM owns the countdown math — expiry lands as Stop there.
    LaunchedEffect(state, pauseCountdown) {
        if (state != null && !pauseCountdown) {
            delay(1000)
            onTick()
        }
    }

    val reducedMotion = LocalReducedMotion.current
    val enter: EnterTransition =
        if (reducedMotion) EnterTransition.None
        else fadeIn(tween(150)) + scaleIn(initialScale = 0.92f, animationSpec = tween(150))
    val exit: ExitTransition =
        if (reducedMotion) ExitTransition.None
        else fadeOut(tween(200)) + scaleOut(targetScale = 0.92f, animationSpec = tween(200))

    AnimatedVisibility(
        visible = state != null,
        enter = enter,
        exit = exit,
        modifier = modifier,
    ) {
        Surface(
            shape = ShapeCache.smooth24,
            color = playerScrimColor().copy(alpha = 0.92f),
            border = BorderStroke(1.dp, playerOnScrim().copy(alpha = 0.1f)),
            modifier = Modifier.width(320.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    imageVector = if (state?.reason == StillWatchingReason.HOURS_IDLE) {
                        Tabler.Outline.Moon
                    } else {
                        Tabler.Outline.PlayerPlay
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = org.jetbrains.compose.resources.stringResource(Res.string.player_video_still_watching_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = playerOnScrim(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (state?.reason) {
                        StillWatchingReason.HOURS_IDLE ->
                            org.jetbrains.compose.resources.stringResource(
                                Res.string.player_video_still_watching_hours_subtitle
                            )
                        else ->
                            org.jetbrains.compose.resources.stringResource(
                                Res.string.player_video_still_watching_episodes_subtitle
                            )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = playerOnScrim().copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )
                val countdown = state?.countdownSeconds ?: 0
                if (state != null && countdown > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${countdown}s",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = playerOnScrim().copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val stopFocusState = rememberTvFocusState(focusedScale = 1.06f)
                    Surface(
                        shape = ShapeCache.smoothPill,
                        color = playerOnScrim().copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, playerOnScrim().copy(alpha = 0.2f)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(ShapeCache.smoothPill)
                            .then(stopFocusState.focusModifier)
                            .tvFocusIndicator(stopFocusState, ShapeCache.smoothPill)
                            .clickable(onClick = onStop),
                    ) {
                        Text(
                            text = org.jetbrains.compose.resources.stringResource(Res.string.player_video_still_watching_stop),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = playerOnScrim(),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    val continueFocusState = rememberTvFocusState(focusedScale = 1.06f)
                    Surface(
                        shape = ShapeCache.smoothPill,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .clip(ShapeCache.smoothPill)
                            .ifElse(isTv, Modifier.focusRequester(tvContinueFocusRequester))
                            .then(continueFocusState.focusModifier)
                            .tvFocusIndicator(continueFocusState, ShapeCache.smoothPill)
                            .clickable(onClick = onContinue),
                    ) {
                        Text(
                            text = org.jetbrains.compose.resources.stringResource(Res.string.player_video_still_watching_continue),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
