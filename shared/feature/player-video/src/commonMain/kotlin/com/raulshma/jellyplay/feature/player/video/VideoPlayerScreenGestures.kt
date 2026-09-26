package com.raulshma.jellyplay.feature.player.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.ui.tv.components.DpadSeekState
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.feature.player.video.components.GestureOverlay
import com.raulshma.jellyplay.feature.player.video.state.GestureSeekController

// ── Section host: input / gesture tier ───────────────────────────────────
// Owns everything that turns raw input into player intent at the surface Box:
// the TV D-pad + hardware-key key-input modifier, the tap/double-tap/pinch-
// zoom modifier, and the gesture indicator tier (swipe seek/volume/brightness
// overlay + hold-speed pill). Moved verbatim from VideoPlayerScreen.kt as a
// composition-only section-host split: declarations are unchanged except
// `private` → `internal` where the root file calls the symbol. See the
// section-host map on VideoPlayerScreen.kt.

/** Hold-speed pill offset above the bottom controls. */
private const val HOLD_SPEED_PILL_BOTTOM_CLEARANCE_DP = 180

/** The surface Box's TV D-pad / hardware-key focus + input tier (the inline if/else modifier chain verbatim). */
internal fun playerBoxKeyInputModifier(
    isTv: Boolean,
    hasHardwareKeyboard: Boolean,
    isSheetOpen: Boolean,
    showControls: Boolean,
    onShowControlsChange: (Boolean) -> Unit,
    tvPlayerFocusRequester: FocusRequester,
    keyboardFocusRequester: FocusRequester,
    onUserInteraction: () -> Unit,
    onKeyboardLayerFocusChange: (Boolean) -> Unit,
    seekState: DpadSeekState,
    doTogglePlayPause: () -> Unit,
    doSeekBack: () -> Unit,
    doSeekForward: () -> Unit,
    performConfirmHaptic: () -> Unit,
    handleMediaKeyDown: (KeyEvent) -> Boolean,
): Modifier =
    if (isTv && !isSheetOpen) {
        Modifier
            .focusRequester(tvPlayerFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                onUserInteraction()
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.playerKeyCode == PlayerKeyCodes.KEYCODE_SPACE
                ) {
                    doTogglePlayPause()
                    performConfirmHaptic()
                    onShowControlsChange(true)
                    true
                } else {
                    false
                }
            }
            .onDpadKeyEvent(
                onRight = { dpadKey ->
                    if (!showControls) {
                        if (dpadKey.isKeyDown) {
                            seekState.seekForward(dpadKey.repeatCount)
                        } else if (dpadKey.isKeyUp) {
                            seekState.commitForward()
                            performConfirmHaptic()
                        }
                        true
                    } else false
                },
                onLeft = { dpadKey ->
                    if (!showControls) {
                        if (dpadKey.isKeyDown) {
                            seekState.seekBackward(dpadKey.repeatCount)
                        } else if (dpadKey.isKeyUp) {
                            seekState.commitBackward()
                            performConfirmHaptic()
                        }
                        true
                    } else false
                },
                onSelect = {
                    if (!showControls) {
                        onShowControlsChange(true)
                        true
                    } else false
                },
                onUp = {
                    if (!showControls) {
                        onShowControlsChange(true)
                        true
                    } else false
                },
                onDown = {
                    if (!showControls) {
                        onShowControlsChange(true)
                        true
                    } else false
                },
                onBack = {
                    if (showControls) {
                        onShowControlsChange(false)
                        true
                    } else false
                },
                onPlayPause = {
                    doTogglePlayPause()
                    performConfirmHaptic()
                    true
                },
                onFastForward = {
                    doSeekForward()
                    onShowControlsChange(true)
                    performConfirmHaptic()
                    true
                },
                onRewind = {
                    doSeekBack()
                    onShowControlsChange(true)
                    performConfirmHaptic()
                    true
                },
            )
    } else if (!isTv && hasHardwareKeyboard && !isSheetOpen) {
        // Hardware-keyboard shortcuts for phones/tablets with a
        // keyboard (Chromebook, Bluetooth, Samsung DeX). TV keeps
        // the D-pad scheme above; this branch is non-TV only so the
        // two never interfere. Keys match common media conventions:
        // space=play/pause, arrows=seek/volume, F=fullscreen, M=mute,
        // Esc=back, J/L=seek like YouTube.
        Modifier
            .focusRequester(keyboardFocusRequester)
            //  focus diagnostics — desktop-only (the
            // grab seam is the same gate), so the Android
            // modifier chain is byte-identical: `.then(Modifier)`
            // short-circuits to `this`. onFocusChanged observes
            // the focus target below; the preview logger fires
            // only when a key dispatch actually DESCENDS into
            // this Box (a focused target inside it, or itself) —
            // under the null-focus fallback the chain stops at
            // the shell's scaffold Row above the screen, so
            // silence here means the key never had a Compose
            // focus target under this Box. Harness-gated no-op
            // output otherwise.
            .then(
                if (grabsKeyboardFocusWithControlsVisible()) {
                    Modifier
                        .onFocusChanged { state ->
                            onKeyboardLayerFocusChange(state.hasFocus)
                            harnessFocusDiag(
                                "player-keyboard-box focus: isFocused=" +
                                    "${state.isFocused} hasFocus=${state.hasFocus}",
                            )
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            harnessFocusDiag(
                                "player-keyboard-box PREVIEW: type=" +
                                    "${keyEvent.type} key=${keyEvent.key}",
                            )
                            false
                        }
                } else {
                    Modifier
                },
            )
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                //  diagnostic (desktop-only, harness-gated
                // no-op): proves the key HANDLER ran, separating
                // "no Compose focus target" failures from
                // "handler ran but the play state flipped back".
                if (grabsKeyboardFocusWithControlsVisible()) {
                    harnessFocusDiag(
                        "player-keyboard-box onKeyEvent: key=${keyEvent.key}",
                    )
                }
                // The interpretation moved into
                // [handleMediaKeyDown] so the shell's
                // deterministic forward (the bridge sink) runs
                // the exact same when-block.
                handleMediaKeyDown(keyEvent)
            }
    } else Modifier

/** The surface Box's tap / double-tap / pinch-zoom gesture tier (the two pointerInput modifiers verbatim). */
internal fun Modifier.playerTapAndZoomGestures(
    gesturesEnabled: Boolean,
    isScreenLocked: Boolean,
    onUserInteraction: () -> Unit,
    isHoldSpeedActive: () -> Boolean,
    holdSpeedEnabled: () -> Boolean,
    stopHoldSpeed: () -> Unit,
    startHoldSpeed: () -> Unit,
    toggleControls: () -> Unit,
    onDoubleTapSeekBack: () -> Unit,
    onDoubleTapSeekForward: () -> Unit,
    onDoubleTapCenter: () -> Unit,
    applyZoomDelta: (Float) -> Unit,
): Modifier =
    // pointerInput only re-launches its block when a KEY changes, so every
    // callback reading live screen state (hold-speed flags, the zoom value,
    // the rememberUpdatedState seek delegates) must arrive as a
    // reader/setter LAMBDA invoked at event time — a captured VALUE would
    // freeze until the next key change (the staleness the inline lambdas
    // avoided by reading through the parent's state delegates).
    pointerInput(gesturesEnabled, isScreenLocked) {
        if (isScreenLocked) return@pointerInput
        if (!gesturesEnabled) return@pointerInput
        detectTapGestures(
            onTap = {
                onUserInteraction()
                if (isHoldSpeedActive()) {
                    stopHoldSpeed()
                } else {
                    toggleControls()
                }
            },
            onLongPress = {
                onUserInteraction()
                if (holdSpeedEnabled()) startHoldSpeed()
            },
            onDoubleTap = { offset ->
                onUserInteraction()
                val width = size.width
                when {
                    offset.x < width * 0.35 -> onDoubleTapSeekBack()
                    offset.x > width * 0.65 -> onDoubleTapSeekForward()
                    else -> onDoubleTapCenter()
                }
            },
        )
    }
        .pointerInput(gesturesEnabled, isScreenLocked) {
            if (isScreenLocked) return@pointerInput
            if (!gesturesEnabled) return@pointerInput
            awaitEachGesture {
                var prevDistance = 0f
                do {
                    val event = awaitPointerEvent()
                    val pointers = event.changes.filter { it.pressed }
                    if (pointers.size >= 2) {
                        val p0 = pointers[0].position
                        val p1 = pointers[1].position
                        val distance = kotlin.math.sqrt(
                            (p0.x - p1.x) * (p0.x - p1.x) + (p0.y - p1.y) * (p0.y - p1.y)
                        )
                        if (prevDistance > 0f && distance > 0f) {
                            val zoom = distance / prevDistance
                            if (zoom != 1f) {
                                applyZoomDelta(zoom)
                            }
                        }
                        prevDistance = distance
                        pointers.forEach { it.consume() }
                    } else {
                        prevDistance = 0f
                    }
                } while (event.changes.any { it.pressed })
            }
        }

/** Overlays tier 1: the gesture indicator layer (swipe seek/volume/brightness) + the hold-speed pill. */
@Composable
internal fun PlayerGestureOverlayTier(
    seekState: DpadSeekState,
    gestureController: GestureSeekController,
    gestureIndicatorSide: GestureIndicatorSide,
    gesturesEnabled: Boolean,
    swipeSeekMaxMs: Long,
    showControls: Boolean,
    onShowControlsChange: (Boolean) -> Unit,
    viewModel: VideoPlayerViewModel,
    windowOps: PlayerWindowOps,
    onBack: () -> Unit,
    isHoldSpeedActive: Boolean,
    playbackSpeed: Float,
) {
    GestureOverlay(
        seekState = seekState,
        brightnessFlow = gestureController.brightnessOverlay,
        volumeFlow = gestureController.volumeOverlay,
        indicatorSide = gestureIndicatorSide,
        gesturesEnabled = gesturesEnabled,
        swipeSeekMaxMs = swipeSeekMaxMs,
        onSeekGesture = remember(gestureController) { { totalDeltaMs -> gestureController.onSeekGesture(totalDeltaMs) } },
        onBrightnessGesture = remember(gestureController) { { delta -> gestureController.onBrightnessGesture(delta) } },
        onVolumeGesture = remember(gestureController) { { delta -> gestureController.onVolumeGesture(delta) } },
        onClearOverlays = remember(gestureController, seekState) {
            {
                gestureController.onClearOverlays()
                seekState.reset()
            }
        },
        showControls = showControls,
        onEdgeSwipe = remember(onBack) {
            {
                if (!showControls) {
                    onShowControlsChange(true)
                } else {
                    onBack()
                }
            }
        },
        onHapticPulse = remember(windowOps, viewModel) {
            {
                if (viewModel.hapticsEnabled) {
                    windowOps.performConfirmHaptic()
                }
            }
        },
        onStartGesture = remember(gestureController) { { gestureController.onStartGesture() } },
        onCancelOverlays = remember(gestureController, seekState) {
            {
                gestureController.onCancelOverlays()
                seekState.reset()
            }
        },
    )

    AnimatedVisibility(
        visible = isHoldSpeedActive,
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(150)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = HOLD_SPEED_PILL_BOTTOM_CLEARANCE_DP.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .clip(ShapeCache.smoothPill)
                    .background(playerScrimColor().copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${playbackSpeed}x",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}
