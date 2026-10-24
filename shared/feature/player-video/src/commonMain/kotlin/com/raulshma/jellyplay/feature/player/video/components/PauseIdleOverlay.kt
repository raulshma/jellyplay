package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.model.PauseScreensaverMode
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import com.raulshma.jellyplay.core.ui.components.rememberWallClockTimeString
import com.raulshma.jellyplay.feature.player.video.PauseIdleController
import kotlinx.coroutines.delay

/** The screensaver tick cadence — the controller folds the injected clock per tick. */
private const val PAUSE_IDLE_TICK_MS = 1_000L

/**
 * Creates the paused-idle screensaver's controller (#11) and runs its tick
 * loop. The controller is Compose-free ([PauseIdleController]); this wrapper
 * owns the composition wiring: the 1 Hz tick loop runs only while playback is
 * paused (a playing session has nothing armed, so the loop parks), and every
 * input rides the screen's reader-lambda rule — the gate/budget lambdas close
 * over delegated state (`uiState`, `currentSheet`, …) and read it LIVE per
 * tick, so a settings change or a sheet flip applies without a rebuild.
 */
@Composable
internal fun rememberPauseIdleController(
    isPlaying: Boolean,
    isArmingEnabled: () -> Boolean,
    idleTimeoutMs: () -> Long,
): PauseIdleController {
    val controller = remember {
        PauseIdleController(
            isArmingEnabled = isArmingEnabled,
            idleTimeoutMs = idleTimeoutMs,
            nowMs = System::currentTimeMillis,
        )
    }
    LaunchedEffect(controller, isPlaying) {
        controller.onPlaybackStateChanged(isPlaying)
        if (!isPlaying) {
            while (true) {
                delay(PAUSE_IDLE_TICK_MS)
                controller.tick()
            }
        }
    }
    return controller
}

/**
 * The paused-idle screensaver overlay (#11): covers the player content while
 * [isActive] with the configured [mode] surface:
 *  - [PauseScreensaverMode.CLOCK] — the live wall clock, large, on a dimmed
 *    black field.
 *  - [PauseScreensaverMode.LOGO] — the player mark centered with a slow
 *    gentle opacity pulse. The shared resources carry no app-logo drawable
 *    (the launcher icons live in the host apps' res), so the mark is the
 *    player glyph in a circular chrome — the identity the player module
 *    actually ships.
 *  - [PauseScreensaverMode.FADE_TO_BLACK] — a pure black overlay.
 *
 * The overlay consumes NO pointer input: taps fall through to the gesture
 * layer beneath, and the screen's interaction funnel dismisses the
 * controller (the overlay is a pure function of [isActive]).
 */
@Composable
internal fun PauseIdleOverlay(
    isActive: Boolean,
    mode: PauseScreensaverMode,
    modifier: Modifier = Modifier,
) {
    if (!isActive || mode == PauseScreensaverMode.OFF) return
    when (mode) {
        PauseScreensaverMode.CLOCK -> {
            Box(
                modifier = modifier.background(Color.Black.copy(alpha = 0.94f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rememberWallClockTimeString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = playerOnScrim().copy(alpha = 0.85f),
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        PauseScreensaverMode.LOGO -> {
            Box(
                modifier = modifier.background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                PulsingPlayerMark()
            }
        }

        PauseScreensaverMode.FADE_TO_BLACK -> {
            Box(modifier = modifier.background(Color.Black))
        }

        PauseScreensaverMode.OFF -> Unit
    }
}

/** The LOGO mode's mark: the player glyph in a circular chrome, opacity pulsing. */
@Composable
private fun BoxScope.PulsingPlayerMark() {
    val reducedMotion = LocalReducedMotion.current
    val pulse = rememberInfiniteTransition(label = "pauseIdleLogoPulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_800, easing = AlphaEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pauseIdleLogoAlpha",
    )
    Surface(
        shape = CircleShape,
        color = playerScrimColor().copy(alpha = 0.14f),
        modifier = Modifier
            .align(Alignment.Center)
            .size(128.dp)
            .graphicsLayer {
                // Reduced motion holds the mark at full presence instead of
                // pulsing (the animation itself stays composed but static).
                this.alpha = if (reducedMotion) 0.9f else alpha
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.PlayerPlay,
                contentDescription = null,
                tint = playerOnScrim().copy(alpha = 0.8f),
                modifier = Modifier.size(56.dp),
            )
        }
    }
}
