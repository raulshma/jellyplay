package com.raulshma.jellyplay.feature.player.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.ui.tv.components.DpadSeekState
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.feature.player.video.chrome.shouldSummonControlsOnPause
import com.raulshma.jellyplay.feature.player.video.components.GestureOverlay
import com.raulshma.jellyplay.feature.player.video.state.DoubleTapHoldSeekPolicy
import com.raulshma.jellyplay.feature.player.video.state.GestureSeekController
import com.raulshma.jellyplay.feature.player.video.state.PlayerWheelPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

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
    isPlaying: Boolean,
    hideOsdOnPause: Boolean,
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
                    // the summon follows the toggle's DIRECTION — pausing
                    // with the hide-OSD pref on leaves the overlay hidden
                    // (playing keeps today's summon; an already-visible
                    // overlay is never force-hidden, the auto-hide owns it).
                    if (shouldSummonControlsOnPause(
                            isPause = isPlaying,
                            hideOsdOnPause = hideOsdOnPause,
                        )
                    ) {
                        onShowControlsChange(true)
                    }
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
                    // TV remotes' media play/pause lands here — the
                    // summon follows the toggle's direction exactly like the
                    // SPACE arm above (pausing with the hide-OSD pref on
                    // leaves the overlay hidden).
                    if (shouldSummonControlsOnPause(
                            isPause = isPlaying,
                            hideOsdOnPause = hideOsdOnPause,
                        )
                    ) {
                        onShowControlsChange(true)
                    }
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

/** Outcome of one watched press: a completed tap, a long-press, or a moved/consumed stream that belongs to another gesture tier. */
private enum class PressOutcome { Tapped, LongPressed, Moved }

/**
 * Watches [down] until it resolves: all pointers up within the long-press
 * window ([PressOutcome.Tapped]), the long-press window elapsing while still
 * held and within slop ([PressOutcome.LongPressed]), or the stream leaving tap
 * candidacy — movement past [touchSlopPx], a second finger (the pinch tier's
 * cue), a consumed change (the swipe overlay's cue), or the tracked pointer
 * vanishing ([PressOutcome.Moved]). Mirrors the tap candidacy
 * `detectTapGestures` enforced before this modifier took over the tier.
 *
 * The long-press arm is a coroutine timeout, not an event-uptime comparison: a
 * stationary finger produces NO pointer events, so a deadline that only fires
 * on event arrival would never resolve a still hold (foundation's own
 * long-press arm is timeout-shaped for the same reason).
 */
private suspend fun AwaitPointerEventScope.awaitPressOutcome(
    down: PointerInputChange,
    touchSlopPx: Float,
    longPressTimeoutMs: Long,
): PressOutcome =
    withTimeoutOrNull(longPressTimeoutMs) { awaitTapOrMove(down, touchSlopPx) }
        ?: PressOutcome.LongPressed

/**
 * Tap candidacy inside the long-press window: resolves
 * [PressOutcome.Tapped] on up, [PressOutcome.Moved] on slop overrun,
 * multi-touch, a consumed change, or pointer loss.
 */
private suspend fun AwaitPointerEventScope.awaitTapOrMove(
    down: PointerInputChange,
    touchSlopPx: Float,
): PressOutcome {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.count { it.pressed } > 1) return PressOutcome.Moved
        val change = event.changes.firstOrNull { it.id == down.id } ?: return PressOutcome.Moved
        if (change.isConsumed) return PressOutcome.Moved
        if (!change.pressed) return PressOutcome.Tapped
        val dx = change.position.x - down.position.x
        val dy = change.position.y - down.position.y
        if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) return PressOutcome.Moved
    }
}

/**
 * The surface Box's tap / double-tap / double-tap-hold / pinch-zoom gesture
 * tier. The tap half is a hand-written `awaitEachGesture` detector (NOT
 * `detectTapGestures`): the double-tap-and-hold continuous seek needs
 * to keep watching the second press of a double-tap after its long-press
 * window elapses and then repeat seeks until release — a shape
 * `detectTapGestures` cannot express (its double-tap arm ends at the second
 * up/long-press with no way to attach a repeat loop to one zone).
 *
 * Preserved behaviors (each previously owned by the `detectTapGestures` call):
 *  - single tap (no second down within the double-tap window) → center
 *    toggle, or hold-speed stop when hold-speed is active;
 *  - long-press on a FIRST press → hold-speed (unchanged);
 *  - double-tap → zone split at 35%/65% of the width: seek back / seek
 *    forward / center (zoom reset or play-pause), fired on the second UP;
 *  - a second press held past the long-press window → hold-speed, as
 *    `detectTapGestures` fires `onLongPress` for a double-tap's second press
 *    too — EXCEPT in a seek zone with the double-tap-hold toggle on, where the
 *    hold becomes continuous seek instead (the resolution: hold-speed stays
 *    available in the zones only when the hold is not a double-tap's
 *    follow-through, i.e. on a first press).
 *
 * The modifier does not consume press/move/up events (only the pinch block
 * below consumes, and only mid-pinch), so the swipe overlay tier above and
 * the engine surface keep seeing whatever this tier declines.
 */
internal fun Modifier.playerTapAndZoomGestures(
    tapGesturesEnabled: Boolean,
    isScreenLocked: Boolean,
    doubleTapHoldSeekEnabled: Boolean,
    holdRepeatScope: CoroutineScope,
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
    // avoided by reading through the parent's state delegates). The
    // double-tap-hold toggle is a pref like [tapGesturesEnabled]: a VALUE
    // key, so flipping it re-arms the detector instead of needing a live
    // read (no gesture can be mid-flight across a settings change that
    // matters here).
    pointerInput(tapGesturesEnabled, isScreenLocked, doubleTapHoldSeekEnabled) {
        if (isScreenLocked) return@pointerInput
        if (!tapGesturesEnabled) return@pointerInput
        awaitEachGesture {
            val touchSlopPx = viewConfiguration.touchSlop
            val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
            val doubleTapTimeoutMs = viewConfiguration.doubleTapTimeoutMillis
            // This Compose version exposes no public double-tap distance slop
            // (the retired `doubleTapMinSlopMillis`) — the touch slop is the
            // standard stand-in for "roughly the same spot" in hand-rolled
            // double-tap detectors.
            val doubleTapSlopPx = touchSlopPx
            var press = awaitFirstDown(requireUnconsumed = false)
            while (true) {
                // ── Phase A: resolve this press — tap, long-press, or moved. ──
                when (awaitPressOutcome(press, touchSlopPx, longPressTimeoutMs)) {
                    PressOutcome.Moved -> return@awaitEachGesture
                    PressOutcome.LongPressed -> {
                        onUserInteraction()
                        if (holdSpeedEnabled()) startHoldSpeed()
                        return@awaitEachGesture
                    }
                    PressOutcome.Tapped -> {}
                }
                // ── Phase B: the double-tap window. Same pointer id, within the
                // window and the double-tap slop of the first press — a second
                // down outside the slop is a NEW first press (two slow,
                // far-apart taps are two single taps), so loop around with it.
                val second = withTimeoutOrNull(doubleTapTimeoutMs) {
                    var down: PointerInputChange?
                    do {
                        down = awaitPointerEvent()
                            .changes
                            .firstOrNull { it.id == press.id && it.changedToDown() }
                    } while (down == null)
                    down
                }
                if (second == null) {
                    // Single tap confirmed: center toggle / hold-speed stop.
                    onUserInteraction()
                    if (isHoldSpeedActive()) stopHoldSpeed() else toggleControls()
                    return@awaitEachGesture
                }
                val secondIsNearFirst =
                    abs(second.position.x - press.position.x) <= doubleTapSlopPx &&
                        abs(second.position.y - press.position.y) <= doubleTapSlopPx
                if (!secondIsNearFirst) {
                    press = second
                    continue
                }
                // ── Phase C: the double tap. Zone from the second press's down
                // position (the same 35%/65% split the inline handler used).
                val zone = DoubleTapHoldSeekPolicy.seekZone(second.position.x, size.width)
                when (awaitPressOutcome(second, touchSlopPx, longPressTimeoutMs)) {
                    PressOutcome.Moved -> return@awaitEachGesture
                    PressOutcome.LongPressed -> {
                        if (doubleTapHoldSeekEnabled && zone != 0) {
                            // Double-tap-and-hold continuous seek.
                            // The hold IS the double-tap's follow-through, so the
                            // first step fires NOW (same addOffset + immediate
                            // commit the quick double-tap performs), then the
                            // policy cadence repeats it until release while the
                            // "+Ns" chip keeps accumulating. The repeat runs on a
                            // sibling coroutine; this event loop only watches for
                            // full release (the RepeatableButton hold-repeat
                            // shape).
                            onUserInteraction()
                            val stepSeek = if (zone < 0) onDoubleTapSeekBack else onDoubleTapSeekForward
                            stepSeek()
                            val repeatJob = holdRepeatScope.launch {
                                var repeats = 0
                                while (isActive) {
                                    delay(DoubleTapHoldSeekPolicy.repeatIntervalMs(repeats))
                                    repeats++
                                    stepSeek()
                                }
                            }
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.none { it.pressed }) break
                                }
                            } finally {
                                repeatJob.cancel()
                            }
                        } else {
                            // Toggle off (or the center zone): the legacy shape —
                            // a long-press on the second press starts hold-speed,
                            // exactly what `detectTapGestures` did here.
                            onUserInteraction()
                            if (holdSpeedEnabled()) startHoldSpeed()
                        }
                        return@awaitEachGesture
                    }
                    PressOutcome.Tapped -> {
                        onUserInteraction()
                        when (zone) {
                            -1 -> onDoubleTapSeekBack()
                            1 -> onDoubleTapSeekForward()
                            else -> onDoubleTapCenter()
                        }
                        return@awaitEachGesture
                    }
                }
            }
        }
    }
        .pointerInput(tapGesturesEnabled, isScreenLocked) {
            if (isScreenLocked) return@pointerInput
            if (!tapGesturesEnabled) return@pointerInput
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

/**
 * The surface Box's mouse-wheel tier: plain wheel = volume,
 * Shift+wheel or a horizontally-dominant wheel delta = seek — the
 * jellyfin-media-player pattern, wheel direction matching mpv
 * (`WHEEL_UP` = up/forward, `WHEEL_DOWN` = down/back). Scroll events are the
 * ONLY thing this handler consumes, and only after it acts on them, so the
 * tap-and-zoom modifier later in the chain and the home rows (which run
 * their own scrollable surfaces, outside this Box) are untouched. All gate
 * state arrives as reader lambdas — a pointerInput block must never read
 * captured values (they freeze until the next key change).
 */
internal fun Modifier.playerWheelGestures(
    isWheelEnabled: () -> Boolean,
    onVolumeNotch: (Int) -> Unit,
    onSeekNotch: (Int) -> Unit,
): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            var seekAccumulator = 0f
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                if (!isWheelEnabled()) continue
                // This Compose version carries the scroll delta on the CHANGE
                // (the PointerEvent-level extension is gone); on a Scroll event
                // the change's scrollDelta is the wheel movement.
                val change = event.changes.firstOrNull() ?: continue
                val scroll = change.scrollDelta
                if (PlayerWheelPolicy.isSeekScroll(
                        isShiftPressed = event.keyboardModifiers.isShiftPressed,
                        scrollX = scroll.x,
                        scrollY = scroll.y,
                    )
                ) {
                    val axisDelta = PlayerWheelPolicy.seekAxisDelta(scroll.x, scroll.y)
                    val (notches, remainder) = PlayerWheelPolicy.accumulateSeekNotches(
                        previousAccumulator = seekAccumulator,
                        rawDelta = axisDelta,
                    )
                    seekAccumulator = remainder
                    if (notches != 0) {
                        val direction = PlayerWheelPolicy.seekDirection(notches.toFloat())
                        repeat(abs(notches)) { onSeekNotch(direction) }
                    }
                } else {
                    onVolumeNotch(PlayerWheelPolicy.volumeDirection(scroll.y))
                }
                event.changes.forEach { it.consume() }
            }
        }
    }

/** Overlays tier 1: the gesture indicator layer (swipe seek/volume/brightness) + the hold-speed pill. */
@Composable
internal fun PlayerGestureOverlayTier(
    seekState: DpadSeekState,
    gestureController: GestureSeekController,
    gestureIndicatorSide: GestureIndicatorSide,
    swipeGesturesEnabled: Boolean,
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
        swipeGesturesEnabled = swipeGesturesEnabled,
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
