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
import androidx.compose.runtime.getValue
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
import com.raulshma.jellyplay.core.model.DpadControl
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerInputKey
import com.raulshma.jellyplay.core.model.SwipeSide
import com.raulshma.jellyplay.core.model.TouchZone
import com.raulshma.jellyplay.core.model.WheelAxis
import com.raulshma.jellyplay.core.ui.tv.components.DpadSeekState
import com.raulshma.jellyplay.core.ui.tv.input.DpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.feature.player.video.chrome.shouldSummonControlsOnPause
import com.raulshma.jellyplay.feature.player.video.components.GestureOverlay
import com.raulshma.jellyplay.feature.player.video.components.GestureSwipeGates
import com.raulshma.jellyplay.feature.player.video.state.DoubleTapHoldSeekPolicy
import com.raulshma.jellyplay.feature.player.video.state.GestureSeekController
import com.raulshma.jellyplay.feature.player.video.state.PlayerInputGates
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

/**
 * TOGGLE_PLAY_PAUSE's shared effect — toggle, confirm haptic, then the
 * directional summon: pausing with the hide-OSD pref on leaves the overlay
 * hidden (playing keeps today's summon; an already-visible overlay is never
 * force-hidden, the auto-hide owns it). Shared by the keyboard executor's
 * TOGGLE_PLAY_PAUSE arm and the TV handler's SPACE and media play/pause arms.
 */
internal fun togglePlayPauseWithSummon(
    isPlaying: Boolean,
    hideOsdOnPause: Boolean,
    doTogglePlayPause: () -> Unit,
    performConfirmHaptic: () -> Unit,
    summonControls: () -> Unit,
) {
    doTogglePlayPause()
    performConfirmHaptic()
    if (shouldSummonControlsOnPause(
            isPause = isPlaying,
            hideOsdOnPause = hideOsdOnPause,
        )
    ) {
        summonControls()
    }
}

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
    /** Input-mapping resolution: pattern → bound action, null = disabled/unbound/NONE. */
    resolveAction: (InputPattern) -> PlayerAction?,
    /** Shared discrete-action executor (the keyboard shell's when-block). */
    executePlayerAction: (PlayerAction) -> Boolean,
): Modifier =
    if (isTv && !isSheetOpen) {
        // Every D-pad arm resolves its control's binding first (hoisted once
        // per arm): the default actions run the legacy arms verbatim; a
        // rebound action routes to the shared executor; a disabled/unbound
        // control returns false (the key falls through, exactly like an
        // unmapped key).
        fun actionOf(control: DpadControl): PlayerAction? =
            resolveAction(InputPattern.DPad(control))

        // One discrete D-pad arm — the shape every non-seek arm shares:
        // KeyUp is always false (arms act on KeyDown only; the seek arms
        // below own the accumulate-on-repeat / commit-on-KeyUp shape), the
        // DEFAULT action runs the legacy arm verbatim, a rebound routes to
        // the shared executor, unbound/disabled falls through.
        fun discreteArm(
            dpadKey: DpadKeyEvent,
            control: DpadControl,
            defaultAction: PlayerAction,
            legacy: () -> Boolean,
        ): Boolean {
            if (!dpadKey.isKeyDown) return false
            return when (val action = actionOf(control)) {
                defaultAction -> legacy()
                null -> false
                else -> executePlayerAction(action)
            }
        }

        Modifier
            .focusRequester(tvPlayerFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                onUserInteraction()
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.playerKeyCode == PlayerKeyCodes.KEYCODE_SPACE
                ) {
                    // SPACE resolves through its own key row (the default map
                    // binds TOGGLE_PLAY_PAUSE there; the D-pad SELECT row owns
                    // the select key, not this one). Unbound SPACE falls
                    // through like any unmapped key.
                    when (val space = resolveAction(InputPattern.Key(PlayerInputKey.SPACE))) {
                        PlayerAction.TOGGLE_PLAY_PAUSE -> {
                            togglePlayPauseWithSummon(
                                isPlaying = isPlaying,
                                hideOsdOnPause = hideOsdOnPause,
                                doTogglePlayPause = doTogglePlayPause,
                                performConfirmHaptic = performConfirmHaptic,
                                summonControls = { onShowControlsChange(true) },
                            )
                            true
                        }
                        null -> false
                        else -> executePlayerAction(space)
                    }
                } else {
                    false
                }
            }
            .onDpadKeyEvent(
                // Every arm executes on KeyDown only (repeats included) — the
                // handler is invoked for KeyUp too. The retired arms ran on
                // BOTH edges (detailed handlers with no type guard): FF/RW
                // seeked twice per press and play/pause toggled twice (a net
                // no-op). Acting once per press is the deliberate divergence
                // from that retired behavior — DECLARED, not verbatim parity;
                // the mapping rows themselves stay verbatim. The seek arms
                // are the exception shape: they accumulate on KeyDown repeats
                // and commit on KeyUp.
                onRight = { dpadKey ->
                    when (val action = actionOf(DpadControl.RIGHT)) {
                        PlayerAction.SEEK_FORWARD -> {
                            if (!showControls) {
                                if (dpadKey.isKeyDown) {
                                    seekState.seekForward(dpadKey.repeatCount)
                                } else if (dpadKey.isKeyUp) {
                                    seekState.commitForward()
                                    performConfirmHaptic()
                                }
                                true
                            } else false
                        }
                        null -> false
                        else -> if (dpadKey.isKeyDown) executePlayerAction(action) else false
                    }
                },
                onLeft = { dpadKey ->
                    when (val action = actionOf(DpadControl.LEFT)) {
                        PlayerAction.SEEK_BACK -> {
                            if (!showControls) {
                                if (dpadKey.isKeyDown) {
                                    seekState.seekBackward(dpadKey.repeatCount)
                                } else if (dpadKey.isKeyUp) {
                                    seekState.commitBackward()
                                    performConfirmHaptic()
                                }
                                true
                            } else false
                        }
                        null -> false
                        else -> if (dpadKey.isKeyDown) executePlayerAction(action) else false
                    }
                },
                onSelect = { dpadKey ->
                    discreteArm(dpadKey, DpadControl.SELECT, PlayerAction.TOGGLE_CONTROLS) {
                        if (!showControls) {
                            onShowControlsChange(true)
                            true
                        } else false
                    }
                },
                onUp = { dpadKey ->
                    discreteArm(dpadKey, DpadControl.UP, PlayerAction.TOGGLE_CONTROLS) {
                        if (!showControls) {
                            onShowControlsChange(true)
                            true
                        } else false
                    }
                },
                onDown = { dpadKey ->
                    discreteArm(dpadKey, DpadControl.DOWN, PlayerAction.TOGGLE_CONTROLS) {
                        if (!showControls) {
                            onShowControlsChange(true)
                            true
                        } else false
                    }
                },
                onBack = { dpadKey ->
                    discreteArm(dpadKey, DpadControl.BACK, PlayerAction.BACK_OR_HIDE) {
                        if (showControls) {
                            onShowControlsChange(false)
                            true
                        } else false
                    }
                },
                onPlayPause = { dpadKey ->
                    discreteArm(dpadKey, DpadControl.PLAY_PAUSE, PlayerAction.TOGGLE_PLAY_PAUSE) {
                        // TV remotes' media play/pause lands here — the
                        // summon follows the toggle's direction exactly like
                        // the SPACE arm above.
                        togglePlayPauseWithSummon(
                            isPlaying = isPlaying,
                            hideOsdOnPause = hideOsdOnPause,
                            doTogglePlayPause = doTogglePlayPause,
                            performConfirmHaptic = performConfirmHaptic,
                            summonControls = { onShowControlsChange(true) },
                        )
                        true
                    }
                },
                onFastForward = { dpadKey ->
                    discreteArm(dpadKey, DpadControl.FAST_FORWARD, PlayerAction.SEEK_FORWARD) {
                        doSeekForward()
                        onShowControlsChange(true)
                        performConfirmHaptic()
                        true
                    }
                },
                onRewind = { dpadKey ->
                    discreteArm(dpadKey, DpadControl.REWIND, PlayerAction.SEEK_BACK) {
                        doSeekBack()
                        onShowControlsChange(true)
                        performConfirmHaptic()
                        true
                    }
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
 * Each tap-class arm resolves its row's binding at fire time: the DEFAULT
 * actions run the legacy arms above verbatim; a REBOUND action routes to the
 * shared [executePlayerAction]; an unbound/disabled row (null) does nothing.
 *
 * The modifier does not consume press/move/up events (only the pinch block
 * below consumes, and only mid-pinch), so the swipe overlay tier above and
 * the engine surface keep seeing whatever this tier declines.
 */
internal fun Modifier.playerTapAndZoomGestures(
    gates: PlayerInputGates,
    isScreenLocked: Boolean,
    doubleTapHoldSeekEnabled: Boolean,
    /** Input-mapping resolution: pattern → bound action, null = disabled/unbound/NONE. */
    resolveAction: (InputPattern) -> PlayerAction?,
    /** Shared discrete-action executor (the keyboard shell's when-block). */
    executePlayerAction: (PlayerAction) -> Boolean,
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
): Modifier {
    // One tap-class arm — the D-pad helper's discreteArm shape with
    // "unbound/disabled does nothing" instead of fall-through: the DEFAULT
    // action runs the legacy arm, a rebound routes to the shared executor,
    // null (unbound/disabled) and explicit NONE both no-op.
    fun touchArm(pattern: InputPattern, defaultAction: PlayerAction, legacy: () -> Unit) {
        when (val action = resolveAction(pattern)) {
            defaultAction -> legacy()
            null -> {}
            else -> executePlayerAction(action)
        }
    }
    // pointerInput only re-launches its block when a KEY changes, so every
    // callback reading live screen state (hold-speed flags, the zoom value,
    // the rememberUpdatedState seek delegates) must arrive as a
    // reader/setter LAMBDA invoked at event time — a captured VALUE would
    // freeze until the next key change (the staleness the inline lambdas
    // avoided by reading through the parent's state delegates). The gates
    // bundle is a pref-like VALUE key ([PlayerInputGates] is an immutable
    // data class rebuilt only when the mapping actually changes), so a
    // binding flip re-arms the detector instead of needing a live read (no
    // gesture can be mid-flight across a mapping change that matters here).
    return pointerInput(gates, isScreenLocked, doubleTapHoldSeekEnabled) {
        if (isScreenLocked) return@pointerInput
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
                        // A rebound long-press runs its discrete action at
                        // press time; the default HOLD_SPEED keeps its
                        // press-and-release semantics.
                        onUserInteraction()
                        touchArm(InputPattern.LongPress, PlayerAction.HOLD_SPEED) {
                            if (holdSpeedEnabled()) startHoldSpeed()
                        }
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
                    // Single tap confirmed: the hold-speed stop ALWAYS stays
                    // available (it is the emergency exit from an active
                    // hold); the row's resolved action runs otherwise — the
                    // default TOGGLE_CONTROLS toggles, a rebound action hits
                    // the shared executor, an unbound row does nothing.
                    onUserInteraction()
                    if (isHoldSpeedActive()) {
                        stopHoldSpeed()
                    } else {
                        touchArm(InputPattern.Tap, PlayerAction.TOGGLE_CONTROLS) { toggleControls() }
                    }
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
                        // The zone's DoubleTapHold row resolves first: the
                        // default seek actions run the continuous hold-seek
                        // (direction from the ACTION, so a rebound hold-left
                        // to forward seeks forward); any other rebound fires
                        // once at hold start; an unbound zone falls back to
                        // the LongPress row exactly like a center hold. The
                        // whole row sits behind the doubleTapHoldSeekEnabled
                        // AND-gate (the map's enabled flag AND the pref): a
                        // preset flip can't wake the zone while the switch
                        // is off — pref-off falls back to hold-speed.
                        onUserInteraction()
                        val zoneAction = when {
                            doubleTapHoldSeekEnabled && zone < 0 ->
                                resolveAction(InputPattern.DoubleTapHold(TouchZone.LEFT))
                            doubleTapHoldSeekEnabled && zone > 0 ->
                                resolveAction(InputPattern.DoubleTapHold(TouchZone.RIGHT))
                            else -> null
                        }
                        when {
                            zoneAction == PlayerAction.SEEK_BACK || zoneAction == PlayerAction.SEEK_FORWARD -> {
                                // Double-tap-and-hold continuous seek.
                                // The hold IS the double-tap's follow-through, so the
                                // first step fires NOW (same addOffset + immediate
                                // commit the quick double-tap performs), then the
                                // policy cadence repeats it until release while the
                                // "+Ns" chip keeps accumulating. The repeat runs on a
                                // sibling coroutine; this event loop only watches for
                                // full release (the RepeatableButton hold-repeat
                                // shape).
                                val stepSeek = if (zoneAction == PlayerAction.SEEK_FORWARD) onDoubleTapSeekForward else onDoubleTapSeekBack
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
                            }
                            zoneAction != null -> executePlayerAction(zoneAction)
                            else -> touchArm(InputPattern.LongPress, PlayerAction.HOLD_SPEED) {
                                if (holdSpeedEnabled()) startHoldSpeed()
                            }
                        }
                        return@awaitEachGesture
                    }
                    PressOutcome.Tapped -> {
                        onUserInteraction()
                        // The zone rows resolve their OWN bindings: the
                        // default seek actions run the legacy chip arms, a
                        // rebound action hits the shared executor, an unbound
                        // zone does nothing.
                        when (zone) {
                            -1 -> touchArm(InputPattern.DoubleTap(TouchZone.LEFT), PlayerAction.SEEK_BACK) {
                                onDoubleTapSeekBack()
                            }
                            1 -> touchArm(InputPattern.DoubleTap(TouchZone.RIGHT), PlayerAction.SEEK_FORWARD) {
                                onDoubleTapSeekForward()
                            }
                            else -> touchArm(InputPattern.DoubleTap(TouchZone.CENTER), PlayerAction.TOGGLE_PLAY_PAUSE) {
                                onDoubleTapCenter()
                            }
                        }
                        return@awaitEachGesture
                    }
                }
            }
        }
    }
        .pointerInput(gates, isScreenLocked) {
            if (isScreenLocked) return@pointerInput
            if (!gates.pinch) return@pointerInput
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
    }

/**
 * The surface Box's mouse-wheel tier: the vertical-wheel row = volume,
 * the Shift+wheel / horizontal row = seek (the jellyfin-media-player pattern,
 * wheel direction matching mpv — `WHEEL_UP` = up/forward, `WHEEL_DOWN` =
 * down/back). Each Scroll event resolves through the input mapping
 * ([resolveWheelAction], most-specific candidate first): a row bound to a
 * volume-family action notches volume, a seek-family action notches seek,
 * anything else (disabled / NONE / rebound to a non-directional action) is
 * left unconsumed. Scroll events are the ONLY thing this handler consumes,
 * and only after it acts on them, so the tap-and-zoom modifier later in the
 * chain and the home rows (which run their own scrollable surfaces, outside
 * this Box) are untouched. All gate state arrives as reader lambdas — a
 * pointerInput block must never read captured values (they freeze until the
 * next key change).
 */
internal fun Modifier.playerWheelGestures(
    isWheelEnabled: () -> Boolean,
    resolveWheelAction: (axis: WheelAxis, isShiftPressed: Boolean) -> PlayerAction?,
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
                val isSeekScroll = PlayerWheelPolicy.isSeekScroll(
                    isShiftPressed = event.keyboardModifiers.isShiftPressed,
                    scrollX = scroll.x,
                    scrollY = scroll.y,
                )
                val axis = if (isSeekScroll) {
                    if (abs(scroll.x) > abs(scroll.y)) {
                        WheelAxis.HORIZONTAL
                    } else {
                        WheelAxis.VERTICAL
                    }
                } else {
                    WheelAxis.VERTICAL
                }
                when (resolveWheelAction(axis, event.keyboardModifiers.isShiftPressed)) {
                    PlayerAction.SWIPE_VOLUME,
                    PlayerAction.VOLUME_UP,
                    PlayerAction.VOLUME_DOWN,
                    -> {
                        onVolumeNotch(PlayerWheelPolicy.volumeDirection(scroll.y))
                        event.changes.forEach { it.consume() }
                    }
                    PlayerAction.SWIPE_SEEK,
                    PlayerAction.SEEK_FORWARD,
                    PlayerAction.SEEK_BACK,
                    -> {
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
                        event.changes.forEach { it.consume() }
                    }
                    else -> {
                        // Unbound / disabled / rebound elsewhere: leave the
                        // scroll unconsumed (it does nothing on the video
                        // surface, but the mapping must not swallow it).
                        seekAccumulator = 0f
                    }
                }
            }
        }
    }

/** Overlays tier 1: the gesture indicator layer (swipe seek/volume/brightness) + the hold-speed pill. */
@Composable
internal fun PlayerGestureOverlayTier(
    seekState: DpadSeekState,
    gestureController: GestureSeekController,
    gestureIndicatorSide: GestureIndicatorSide,
    gates: PlayerInputGates,
    isScreenLocked: Boolean,
    swipeSeekMaxMs: Long,
    showControls: Boolean,
    onShowControlsChange: (Boolean) -> Unit,
    viewModel: VideoPlayerViewModel,
    windowOps: PlayerWindowOps,
    onBack: () -> Unit,
    isHoldSpeedActive: Boolean,
    playbackSpeed: Float,
    /** Input-mapping resolution: pattern → bound action, null = disabled/unbound/NONE. */
    resolveAction: (InputPattern) -> PlayerAction?,
    /** Shared discrete-action executor (the keyboard shell's when-block). */
    executePlayerAction: (PlayerAction) -> Boolean,
) {
    // Live reads for the event-time edge-swipe callback — a captured value
    // would freeze until this composable recomposes with a new lambda.
    val currentShowControls by androidx.compose.runtime.rememberUpdatedState(showControls)
    val currentOnBack by androidx.compose.runtime.rememberUpdatedState(onBack)
    GestureOverlay(
        seekState = seekState,
        brightnessFlow = gestureController.brightnessOverlay,
        volumeFlow = gestureController.volumeOverlay,
        indicatorSide = gestureIndicatorSide,
        gates = GestureSwipeGates(
            brightnessSwipe = gates.swipeBrightness && !isScreenLocked,
            volumeSwipe = gates.swipeVolume && !isScreenLocked,
            seekSwipe = gates.swipeSeek && !isScreenLocked,
            edgeSwipeLeft = gates.edgeLeft && !isScreenLocked,
            edgeSwipeRight = gates.edgeRight && !isScreenLocked,
        ),
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
        onEdgeSwipe = remember(resolveAction, executePlayerAction) {
            { edge ->
                // The edge row resolves like every other arm: the default
                // BACK_OR_HIDE keeps the summon-then-exit ladder; a rebound
                // action hits the shared executor.
                when (val action = resolveAction(InputPattern.EdgeSwipe(edge))) {
                    PlayerAction.BACK_OR_HIDE -> {
                        if (!currentShowControls) {
                            onShowControlsChange(true)
                        } else {
                            currentOnBack()
                        }
                    }
                    null -> {}
                    else -> executePlayerAction(action)
                }
            }
        },
        resolveVerticalAction = remember(resolveAction) {
            { side ->
                // The half resolves like every other arm: only the continuous
                // drag actions own a swipe arm here (a discrete rebind falls
                // to the executor's unmatched arm — the picker blocks it);
                // null = unbound/disabled/NONE, the half stays inert.
                when (val action = resolveAction(InputPattern.VerticalSwipe(side))) {
                    PlayerAction.SWIPE_BRIGHTNESS, PlayerAction.SWIPE_VOLUME -> action
                    else -> null
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
