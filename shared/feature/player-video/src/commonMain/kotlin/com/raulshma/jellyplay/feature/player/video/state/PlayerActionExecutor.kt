package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerInputKey
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.core.ui.tv.components.DpadSeekState
import com.raulshma.jellyplay.core.ui.tv.input.DpadSeekAcceleration
import com.raulshma.jellyplay.feature.player.video.KEY_AUDIO_DELAY_STEP_MS
import com.raulshma.jellyplay.feature.player.video.KEY_SUBTITLE_DELAY_STEP_MS
import com.raulshma.jellyplay.feature.player.video.VideoPlayerUiEvent
import com.raulshma.jellyplay.feature.player.video.chrome.keyboardSeekStepMs
import com.raulshma.jellyplay.feature.player.video.components.A11Y_STEP_DELTA
import com.raulshma.jellyplay.feature.player.video.components.SPEED_OPTIONS
import com.raulshma.jellyplay.feature.player.video.delayAdjustMs
import com.raulshma.jellyplay.feature.player.video.mediaKeySeek
import com.raulshma.jellyplay.feature.player.video.playerKeyCode
import com.raulshma.jellyplay.feature.player.video.playerKeyCodeOf
import com.raulshma.jellyplay.feature.player.video.togglePlayPauseWithSummon

/**
 * Owns the player's discrete-input EFFECT layer — the shared action executor
 * every detector tier's rebound arms route to (keyboard keys, TV D-pad arms,
 * tap zones, edge swipes) plus the hardware-key interpreter — extracted out of
 * `VideoPlayerScreen`'s inline `executePlayerAction` / `handleMediaKeyDown`
 * lambdas.
 *
 * Like [GestureSeekController] (and unlike [TrackSelectionHelper],
 * which reads the VM's `_uiState`), this is screen-scoped composable state
 * constructed in the screen via `remember`. It has ZERO `VideoPlayerUiState`
 * in its interface (pinned by `ControllerOwnershipTest`): everything that
 * changes per recomposition arrives as a READER LAMBDA invoked at execution
 * time, every effect is an injected port (the VM's `onEvent` funnel, the
 * play/pause delegates, the window seams), and the screen keeps the lambdas
 * that touch screen-local state (`playbackIntended`, snackbar capture)
 * outside — the executor must not import composition-local state.
 *
 * The screen's `rememberDpadSeekState` moved into the construction with the
 * keyboard seek path that shares its chip: [seekState] is the ONE D-pad seek
 * accumulator the D-pad arms, wheel notches, double-tap arms and the keyboard
 * fold all feed, and the screen's overlay UI + debounce collectors read it
 * (and [keyboardSeekStamp] / [lastKeyboardSeekChipTimestamp], now owned here
 * as Compose state) exactly as before.
 *
 * Resolution: a raw key-down maps to the catalog key, candidates go through
 * [PlayerInputPolicy.keyCandidates] (exact modifiers first, then the
 * canonical plain row — seek-family keys skip the exact arm because their
 * modifier steps are event-time semantics), and the bound action executes
 * through [executeAction]. A disabled/absent binding returns false (unmapped
 * key); an explicit NONE row consumes as a no-op. Seek-family bindings fold
 * the per-key STEP out of [mediaKeySeek] (falling back to the shared
 * [keyboardSeekStepMs] ladder) with the DIRECTION from the bound action, so
 * rebinding L to seek-back works with the same step ladder. The continuous
 * class (SWIPE_*, ZOOM, HOLD_SPEED) has no discrete arm — [executeAction]
 * returns false, exactly as the former inline `else -> false` did.
 */
internal class PlayerActionExecutor(
    // ── Live reads (invoked at execution time — the reader-lambda rule) ──
    private val getInputMap: () -> PlayerInputMap,
    private val getShowControls: () -> Boolean,
    private val getIsPlaying: () -> Boolean,
    private val getHideOsdOnPause: () -> Boolean,
    private val getPlaybackSpeed: () -> Float,
    private val getSubtitleOffsetMs: () -> Long,
    private val getSupportsAudioDelay: () -> Boolean,
    private val getAudioDelayMs: () -> Long,
    private val getSeekStepMs: () -> Long,
    // ── The D-pad seek chip's engine reads + commit funnel (pass-through) ──
    getCurrentPositionMs: () -> Long,
    getDurationMs: () -> Long,
    onSeekCommit: (Long) -> Unit,
    // ── I/O seams + side-effect ports (real in prod, recording fakes in test) ──
    private val doTogglePlayPause: () -> Unit,
    private val doPlay: () -> Unit,
    private val doPause: () -> Unit,
    private val toggleOrientation: () -> Unit,
    private val streamVolumeAdjuster: (up: Boolean) -> Unit,
    private val setAudioDelay: (Long) -> Unit,
    private val onUiEvent: (VideoPlayerUiEvent) -> Unit,
    private val doSeekForward: () -> Unit,
    private val doSeekBack: () -> Unit,
    private val setShowControls: (Boolean) -> Unit,
    private val setShowDelayOverlay: (Boolean) -> Unit,
    private val onBack: () -> Unit,
    private val onScreenshotClick: () -> Unit,
    private val performConfirmHaptic: () -> Unit,
    private val onUserInteraction: () -> Unit,
    private val gestureController: GestureSeekController,
) {
    /**
     * The shared D-pad seek chip. The screen's `rememberDpadSeekState` moved
     * here; the base step is the same live reader the keyboard fold consults.
     */
    val seekState: DpadSeekState = DpadSeekState(
        acceleration = DpadSeekAcceleration.Default,
        getBaseStepMs = getSeekStepMs,
        getCurrentPositionMs = getCurrentPositionMs,
        getDurationMs = getDurationMs,
        onCommit = onSeekCommit,
    )

    // Keyboard seek chip stamp: bumped on EVERY seek key-down, repeats
    // included. The screen's debounced commit collector observes it — the
    // keyboard analogue of the D-pad's commit-on-key-up, which the desktop
    // sink bridge cannot use (it delivers KeyDown only).
    var keyboardSeekStamp: Int by mutableIntStateOf(0)
        private set

    // The chip's seekState.timestamp as of the LAST keyboard contribution: if
    // the chip moved on since (a drag/swipe/double-tap seek inside the commit
    // window), the debounced keyboard commit stands down instead of firing a
    // stale base position over the newer, user-driven seek.
    var lastKeyboardSeekChipTimestamp: Long by mutableLongStateOf(0L)
        private set

    // Raw platform code → catalog key. Built once; the mapping persists
    // NAMES, this is the only place codes meet them. (Mirrors the capture
    // dialog's `playerInputKeyOf` inverse table rather than reusing it —
    // both fold over `playerKeyCodeOf`, so a new catalog key compiles the
    // pair in lockstep.)
    private val keyCodeToInputKey: Map<Int, PlayerInputKey> =
        PlayerInputKey.entries.associate { key -> playerKeyCodeOf(key) to key }

    /**
     * The ONE discrete-action executor every rebound arm routes to. The arms
     * are the screen's former inline `when` verbatim — only the reads go
     * through the constructor's reader lambdas and the effects through the
     * injected ports.
     */
    fun executeAction(action: PlayerAction): Boolean =
        when (action) {
            PlayerAction.TOGGLE_PLAY_PAUSE -> {
                togglePlayPauseWithSummon(
                    isPlaying = getIsPlaying(),
                    hideOsdOnPause = getHideOsdOnPause(),
                    doTogglePlayPause = doTogglePlayPause,
                    performConfirmHaptic = performConfirmHaptic,
                    summonControls = { setShowControls(true) },
                )
                true
            }
            PlayerAction.PLAY -> {
                doPlay()
                true
            }
            PlayerAction.PAUSE -> {
                doPause()
                true
            }
            PlayerAction.VOLUME_UP -> {
                streamVolumeAdjuster(true)
                setShowControls(true)
                true
            }
            PlayerAction.VOLUME_DOWN -> {
                streamVolumeAdjuster(false)
                setShowControls(true)
                true
            }
            PlayerAction.TOGGLE_MUTE -> {
                onUiEvent(VideoPlayerUiEvent.ToggleMute)
                setShowControls(true)
                true
            }
            PlayerAction.TOGGLE_ORIENTATION -> {
                toggleOrientation()
                setShowControls(true)
                true
            }
            PlayerAction.TOGGLE_SUBTITLES -> {
                // mpv's subtitle-visibility key: off remembers the
                // last track, on silently restores it — same funnel the
                // hub's Off/track rows and the CC button's long-press
                // drive. No controls change, mirroring the delay arms:
                // the effect is on the video, not the chrome.
                onUiEvent(VideoPlayerUiEvent.ToggleSubtitles)
                true
            }
            PlayerAction.SUBTITLE_DELAY_DECREASE -> {
                // Feedback rides the existing subtitle-delay overlay (the
                // screen's mount reads the live value, so each press
                // steps the shown number). Same event the overlay's own
                // steppers emit.
                setShowDelayOverlay(true)
                onUiEvent(
                    VideoPlayerUiEvent.SetSubtitleDelay(
                        delayAdjustMs(
                            currentMs = getSubtitleOffsetMs(),
                            sign = -1,
                            stepMs = KEY_SUBTITLE_DELAY_STEP_MS,
                        ),
                    ),
                )
                true
            }
            PlayerAction.SUBTITLE_DELAY_INCREASE -> {
                setShowDelayOverlay(true)
                onUiEvent(
                    VideoPlayerUiEvent.SetSubtitleDelay(
                        delayAdjustMs(
                            currentMs = getSubtitleOffsetMs(),
                            sign = 1,
                            stepMs = KEY_SUBTITLE_DELAY_STEP_MS,
                        ),
                    ),
                )
                true
            }
            PlayerAction.AUDIO_DELAY_DECREASE -> {
                // Same funnel the AVSync sheet uses
                // (setAudioDelay), same 100ms step and clamp; gated on
                // the sheet's capability row. No audio overlay exists
                // and the sheet is a modal, so there is no OSD
                // feedback — the change applies directly.
                if (getSupportsAudioDelay()) {
                    setAudioDelay(
                        delayAdjustMs(
                            currentMs = getAudioDelayMs(),
                            sign = -1,
                            stepMs = KEY_AUDIO_DELAY_STEP_MS,
                        ),
                    )
                }
                true
            }
            PlayerAction.AUDIO_DELAY_INCREASE -> {
                if (getSupportsAudioDelay()) {
                    setAudioDelay(
                        delayAdjustMs(
                            currentMs = getAudioDelayMs(),
                            sign = 1,
                            stepMs = KEY_AUDIO_DELAY_STEP_MS,
                        ),
                    )
                }
                true
            }
            PlayerAction.SEEK_FORWARD,
            PlayerAction.SEEK_BACK,
            -> {
                // One configured jump per invocation (the keyboard pipeline's
                // modifier-ladder seek keeps its own arm in [handleMediaKeyDown];
                // this is the D-pad/touch rebound site — the FF/RW legacy
                // arm's shape).
                if (action == PlayerAction.SEEK_FORWARD) {
                    doSeekForward()
                } else {
                    doSeekBack()
                }
                setShowControls(true)
                performConfirmHaptic()
                true
            }
            PlayerAction.TOGGLE_CONTROLS -> {
                setShowControls(!getShowControls())
                true
            }
            PlayerAction.BACK_OR_HIDE -> {
                // The ESC/BACK split the retired PlayerKeyAction carried,
                // resolved at execution time by the controls visibility.
                if (getShowControls()) setShowControls(false) else onBack()
                true
            }
            PlayerAction.EXIT_PLAYER -> {
                onBack()
                true
            }
            PlayerAction.BRIGHTNESS_UP,
            PlayerAction.BRIGHTNESS_DOWN,
            -> {
                // One discrete step through the swipe controller (the sole
                // brightness writer): the edge-bar overlay flashes feedback
                // and onClearOverlays commits the level + schedules the bar's
                // dismiss, exactly like a short drag.
                val sign = if (action == PlayerAction.BRIGHTNESS_UP) 1 else -1
                gestureController.onStartGesture()
                gestureController.onBrightnessGesture(sign * A11Y_STEP_DELTA)
                gestureController.onClearOverlays()
                true
            }
            PlayerAction.CYCLE_SPEED -> {
                // The SpeedPickerSheet ladder: next rung above the current
                // speed, wrapping to 1x past the top (an off-ladder speed
                // snaps to the first rung).
                val current = getPlaybackSpeed()
                val next = SPEED_OPTIONS.firstOrNull { it > current + 0.01f } ?: 1.0f
                onUiEvent(VideoPlayerUiEvent.SetPlaybackSpeed(next))
                setShowControls(true)
                true
            }
            PlayerAction.LOCK_CONTROLS -> {
                // Same funnel the controls' lock button drives; the input
                // surface dies with the lock, so this is lock-only.
                onUiEvent(VideoPlayerUiEvent.SetScreenLocked(true))
                setShowControls(false)
                true
            }
            PlayerAction.SCREENSHOT -> {
                onScreenshotClick()
                true
            }
            PlayerAction.NEXT_EPISODE -> {
                onUiEvent(VideoPlayerUiEvent.PlayNextEpisode)
                true
            }
            PlayerAction.PREVIOUS_EPISODE -> {
                onUiEvent(VideoPlayerUiEvent.PlayPreviousEpisode)
                true
            }
            // The continuous-class behaviors keep their own sites: the
            // swipe-class drags (SWIPE_*) belong to the drag detector, ZOOM
            // to the pinch tier, HOLD_SPEED to the long-press press/release
            // pair — none of them can run from a discrete executor, so
            // binding them to a key is a no-op the editor's picker allows
            // but nothing executes.
            else -> false
        }

    /**
     * The hardware-keyboard layer's whole key-down path — both delivery
     * routes run this ONE method: (a) the normal focused dispatch chain (the
     * surface Box's `onKeyEvent`, when the layer or a descendant holds
     * Compose focus) and (b) the desktop shell's deterministic forward (the
     * bridge sink, called from DesktopNavScaffold.onPreviewKeyEvent when
     * Route.VideoPlayer is current and the layer holds no focus). The screen
     * stays the single interpreter of media-key semantics; the shell
     * forwards raw events.
     */
    fun handleMediaKeyDown(keyEvent: KeyEvent): Boolean {
        onUserInteraction()
        val inputKey = keyCodeToInputKey[keyEvent.playerKeyCode]
        if (inputKey == null) {
            return false
        }
        val binding = PlayerInputPolicy.resolve(
            getInputMap(),
            PlayerInputPolicy.keyCandidates(
                key = inputKey,
                isCtrlPressed = keyEvent.isCtrlPressed,
                isShiftPressed = keyEvent.isShiftPressed,
                isAltPressed = keyEvent.isAltPressed,
            ),
        )
        return when {
            binding == null -> false
            binding.action == PlayerAction.NONE -> true
            // Seek actions: the step comes from the retired mediaKeySeek
            // table's per-key rows (modifier ladder over the configured
            // jump for arrows/J/L/FF/RW, the fixed page/home magnitudes
            // for PgUp/PgDn/Home/End), the DIRECTION from the bound
            // action — so rebinding L to seek-back works with the same
            // step ladder. Each key-down — repeats included —
            // accumulates into the shared D-pad seek chip and re-arms
            // the screen's debounced commit (unchanged).
            binding.action == PlayerAction.SEEK_FORWARD || binding.action == PlayerAction.SEEK_BACK -> {
                val legacyStep = mediaKeySeek(
                    keyCode = keyEvent.playerKeyCode,
                    configuredStepMs = getSeekStepMs(),
                    isShiftPressed = keyEvent.isShiftPressed,
                    isCtrlPressed = keyEvent.isCtrlPressed,
                    isAltPressed = keyEvent.isAltPressed,
                )?.stepMs
                    ?: keyboardSeekStepMs(
                        isShiftPressed = keyEvent.isShiftPressed,
                        isCtrlPressed = keyEvent.isCtrlPressed,
                        isAltPressed = keyEvent.isAltPressed,
                        configuredStepMs = getSeekStepMs(),
                    )
                seekState.addOffset(
                    targetDirection = if (binding.action == PlayerAction.SEEK_FORWARD) 1 else -1,
                    amountMs = legacyStep,
                )
                lastKeyboardSeekChipTimestamp = seekState.timestamp
                keyboardSeekStamp++
                setShowControls(true)
                true
            }
            else -> executeAction(binding.action)
        }
    }
}
