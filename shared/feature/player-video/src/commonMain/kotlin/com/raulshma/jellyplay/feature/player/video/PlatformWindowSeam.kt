package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.DpadKeyHandlerScope
import com.raulshma.jellyplay.core.ui.tv.input.dpadKeyHandlerHandle
import com.raulshma.jellyplay.core.ui.tv.input.toDpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus

/**
 * Host-window seam for the commonMain [VideoPlayerScreen]: every
 * system-surface operation the screen performs on its host window — system-bar
 * visibility, keep-screen-on, window brightness, orientation lock, display
 * frame-rate matching — plus the device input facts the keyboard handling
 * needs. The androidMain actual wraps the host [android.app.Activity] exactly
 * as the screen did before the split (same guards, same constants); the
 * jvmMain actual is a stateless no-op singleton because a desktop window has
 * no system bars, no Activity orientation lock, and no OS brightness override
 * (the window manager owns those).
 *
 * Public because the LIVE player's screen cites the same seam: its
 * androidMain window effects moved off their byte-identical
 * `WindowCompat`/`FLAG_` inline code onto these ops (the shared-policy
 * precedent — [com.raulshma.jellyplay.feature.player.video.chrome.PlayerChromePolicies]
 * — both player screens must cite ONE host-window vocabulary instead of
 * carrying parallel copies). Only the type surface is public; the actuals stay
 * module-private implementation details behind [rememberPlayerWindowOps].
 */
interface PlayerWindowOps {

    /**
     * Synchronously-current PiP flag of the host (the collected
     * `pipController.isInPipMode` state lags a frame). Always false on desktop.
     */
    val isInPipMode: Boolean get() = false

    /** Immersive mode: hide the system bars (Android); no-op on desktop. */
    fun hideSystemBars() {}

    /** Restore the system bars on player exit (Android); no-op on desktop. */
    fun showSystemBars() {}

    /** FLAG_KEEP_SCREEN_ON while video plays (Android); no-op on desktop. */
    fun setKeepScreenOn(enabled: Boolean) {}

    /**
     * Read the host window's brightness override, or `-1f` when none is set
     * (the BRIGHTNESS_OVERRIDE_NONE sentinel). Always `-1f` on desktop.
     */
    fun readWindowBrightness(): Float = -1f

    /** Apply a raw brightness override to the host window (gesture path). */
    fun writeWindowBrightness(level: Float) {}

    /**
     * Restore the gesture brightness when a swipe ends: `restored >= 0f`
     * re-applies that level, anything else restores the OS default. Host-alive
     * guarded on Android; no-op on desktop.
     */
    fun restoreWindowBrightness(restored: Float) {}

    /**
     * Apply the persisted brightness level on player entry/resume
     * (host-alive guarded on Android); no-op on desktop.
     */
    fun applyWindowBrightness(level: Float) {}

    /**
     * Current/max music-stream volume pair for the volume gesture.
     * `0 to 0` on desktop — the gesture reads the engine volume instead.
     */
    fun readMusicStreamVolume(): Pair<Int, Int> = 0 to 0

    /** Set the music-stream volume (gesture path); no-op on desktop. */
    fun setMusicStreamVolume(volume: Int) {}

    /** Confirmation haptic for discrete player actions; no-op on desktop. */
    fun performConfirmHaptic() {}

    /** Lock the host to [mode] (Activity orientation lock); no-op on desktop. */
    fun lockOrientation(mode: PlayerOrientationLock) {}

    /**
     * Portrait ↔ landscape toggle. [preferLockedLandscape] resolves the
     * user's configured default landscape mode so the toggle is symmetric.
     * No-op on desktop.
     */
    fun toggleOrientation(preferLockedLandscape: Boolean) {}

    /** Display frame-rate matching (Android FrameRateMatcher); no-op on desktop. */
    fun matchFrameRate(frameRate: Float?, targetWidth: Int?, targetHeight: Int?, mode: RefreshRateMode) {}

    /** Restore the original display mode on player exit; no-op on desktop. */
    fun restoreFrameRateMode() {}

    /**
     * Host-window teardown when the player screen leaves composition (both the
     * background-cast and the full-release paths): unlock orientation to
     * [orientation], clear keep-screen-on, restore OS-default brightness,
     * re-show the system bars and hand the display mode back. Host-alive
     * guarded on Android; no-op on desktop.
     */
    fun restoreOnPlayerExit(orientation: PlayerOrientationLock) {}
}

/**
 * Platform-neutral orientation requests. The androidMain actual maps each
 * value to the `ActivityInfo` screen-orientation constant the screen used
 * before the split. Public alongside [PlayerWindowOps] — the live player's
 * screen passes its entry locks through the same vocabulary.
 */
enum class PlayerOrientationLock {
    SENSOR_LANDSCAPE,
    SENSOR_PORTRAIT,
    SENSOR,
    LOCKED_LANDSCAPE,
    LOCKED_PORTRAIT,
    /** TV players lock to sensor-landscape. */
    TV_LANDSCAPE,
    /** Follow the user (cast-connected phones). */
    USER,
    /**
     * Landscape following the user's rotation lock — the live player's
     * non-TV entry lock (its pre-seam inline code requested
     * `SCREEN_ORIENTATION_USER_LANDSCAPE`; distinct from [USER], which also
     * allows portrait).
     */
    USER_LANDSCAPE,
    /** Restore the OS default on player exit. */
    UNSPECIFIED,
}

/** The host-window operations for the current composition (see [PlayerWindowOps]). */
@Composable
expect fun rememberPlayerWindowOps(): PlayerWindowOps

/**
 * STREAM_MUSIC volume nudge for the hardware-keyboard volume shortcuts.
 * Android keeps the system-volume UI/ringer behaviour of the original
 * `adjustStreamVolume` path; desktop is a no-op (hardware volume keys are
 * handled by the OS).
 */
@Composable
internal expect fun rememberStreamVolumeAdjuster(): (up: Boolean) -> Unit

/**
 * Whether the device has a hardware keyboard (Chromebooks, BT keyboards,
 * DeX) — gates the non-TV keyboard-shortcut layer. Always true on desktop.
 */
@Composable
internal expect fun rememberHasHardwareKeyboard(): Boolean

/**
 * Whether the current configuration is portrait (drives the controls'
 * portrait/landscape layout switch and the companion dashboard's). Desktop
 * reports false — the desktop player is a resizable landscape-style window.
 */
@Composable
internal expect fun rememberIsPortraitOrientation(): Boolean

/**
 * Locale 24-hour clock preference for the controls' clock. Desktop derives it
 * from the default JDK time format pattern.
 */
@Composable
internal expect fun rememberIs24HourFormat(): Boolean

/**
 * Whether the non-TV hardware-keyboard layer grabs focus onto its player Box
 * whenever it composes, not only on the controls-hidden edge (the screen's
 * at-HEAD focus-request condition, [VideoPlayerScreen]'s focus-request
 * effect). Static platform fact — no remember needed.
 *
 * Desktop is TRUE. The desktop shell takes no Compose focus of its
 * own while the player is up: Route.VideoPlayer is isFullScreen so the nav
 * rail is removed from composition, the sign-in pane was replaced when the
 * session started, and PlayerControls' own focus grab is TV-only — but the
 * controls START visible (`showControls = true`), so the at-HEAD "request when
 * `!showControls`" condition left the player Box unfocused until the first
 * controls auto-hide (default `controlsTimeoutMs` is 5 s), or indefinitely
 * while a sheet/seek/overflow-menu suppressed the auto-hide. With no focused
 * node, Compose dispatches key events through the chain from the root down to
 * the TOPMOST key-input node only ([androidx.compose.ui.focus.FocusOwnerImpl]
 * `dispatchKeyEvent` null-focus fallback) — on desktop that topmost node is
 * DesktopNavScaffold's `onPreviewKeyEvent` Row, so ESC still popped the route
 * while SPACE/arrows never reached the player's `onKeyEvent` (the
 * session-harness focus finding this seam's desktop grab fixes).
 *
 * Android is FALSE: the phone keyboard layer keeps the exact at-HEAD timing
 * (focus requested on the `showControls → false` edge only), so Android
 * behavior is byte-identical.
 */
internal expect fun grabsKeyboardFocusWithControlsVisible(): Boolean

/**
 * The desktop keyboard-focus grab: whenever [layerComposed] flips true (the
 * hardware-keyboard layer entered composition — screen entry, or a sheet
 * closing and the layer re-composing), grabs focus onto [focusRequester] with
 * the codebase's retry idiom ([RequestOrRestoreFocus]: the requester's node
 * may not be laid out on the first frame, so retry across a frame, up to 3
 * attempts). Additionally: whenever [targetHoldsFocus] flips false
 * while [layerComposed] is true — focus was taken AWAY from the grab target
 * after a successful grab — the grab is re-asserted with the same retry
 * idiom. The real desktop window needs this: the live session pass proved the
 * initial grab succeeds (attempt=1 ok=true, +68 ms) yet Compose focus is
 * dropped again ~1–2 s later when the mpv SwingPanel/HWND surface
 * mounts (the AWT focus owner shuffles off the SkiaLayer and the player Box
 * reports hasFocus=false), and nothing ever re-grabbed — the auto-hide edge
 * re-request is a single un-retried attempt and the controls auto-hide had
 * already suppressed itself, so SPACE at the ~11 s injection found no Compose
 * focused node and died at the scaffold's null-focus fallback (the
 * live-pass OVERLAY_SPACE failure; its desktop UI test passed because the
 * miniature topology has no surface mount to steal focus).
 *
 * The re-assert watches [targetHoldsFocus] = the target's **hasFocus** (not
 * isFocused), so a controls button INSIDE the player Box that took focus via
 * a pointer click keeps it — the re-assert only fires when nothing in the
 * player subtree holds focus at all. It is edge-triggered and gives up after
 * 3 failed frames, so a detached target cannot loop it.
 *
 * Composes nothing on platforms whose
 * [grabsKeyboardFocusWithControlsVisible] is false (Android: the at-HEAD
 * showControls→false-edge request in the screen is the whole story there).
 */
@Composable
internal fun PlayerKeyboardFocusGrabEffect(
    focusRequester: FocusRequester,
    layerComposed: Boolean,
    targetHoldsFocus: Boolean,
) {
    if (!grabsKeyboardFocusWithControlsVisible()) return
    PlayerFocusGrabEffect(focusRequester, layerComposed, targetHoldsFocus, tag = "keyboard_player")
}

/**
 * The TV twin of [PlayerKeyboardFocusGrabEffect]: the surface Box's D-pad
 * layer (`playerBoxKeyInputModifier`'s TV branch — `.focusable()` +
 * `onDpadKeyEvent`) only receives keys while the Box subtree holds Compose
 * focus, and the historical single fire-and-forget re-grab on the
 * controls-hidden edge loses races (the controls' `AnimatedVisibility` exits
 * dispose the previously focused play button; an overlay unmount later
 * orphans focus again). When the Box ends up with no focus target at all,
 * Compose's null-focus fallback dispatch stops at the topmost key-input node —
 * on Android there is none above the player, so EVERY D-pad key died (issue
 * #174: controls auto-hide, then no remote key brings them back; Back survives
 * only because `OnBackPressedDispatcher` needs no focus).
 *
 * Same mechanics as the keyboard grab (see its KDoc for the live-pass
 * rationale): grabs on every [layerComposed] true edge and re-asserts whenever
 * [targetHoldsFocus] flips false while composed, both with the 3-frame retry.
 * The screen computes [layerComposed] so the grab runs ONLY when the layer
 * should own focus — controls hidden, sheet closed, no overlay claiming focus
 * (the cinema-intro / next-episode / skip-segment overlays own focus when
 * visible; when one unmounts, the layerComposed edge re-arms here). While the
 * controls are visible the TV layer composes nothing: the play button's own
 * grab ([PlayerControls]' TV focus grab) owns focus, exactly as before.
 *
 * Public because the LIVE player's screen composes it too (same D-pad shape,
 * worse gap — it had no hide-edge re-grab at all); mirrors the
 * [PlayerWindowOps] public-for-live precedent.
 */
@Composable
public fun PlayerTvFocusGrabEffect(
    focusRequester: FocusRequester,
    layerComposed: Boolean,
    targetHoldsFocus: Boolean,
) {
    PlayerFocusGrabEffect(focusRequester, layerComposed, targetHoldsFocus, tag = "tv_player")
}

/**
 * The TV key-sink seam: while a player screen's TV D-pad layer
 * is up, publishes that screen's EXACT arm set ([config]) to the platform
 * key bridge ([installPlayerKeySink] → AndroidPlayerKeyBridge), so keys that
 * reach nothing — no Compose focus target left, PlayerActivity's
 * dispatchKeyEvent finding nothing consumed — are still interpreted by the
 * screen's own arms. PlayerActivity forwards post-dispatch-unconsumed keys
 * into the bridge, so a key the focused chain consumed never reaches the
 * sink (no double handling); the declines keep the desktop sink's
 * single-interpretation guard for the fall-through cases:
 *
 *  - [isSheetOpen] — the sheet owns its own key handling; the arms disarm
 *    together with the focused-dispatch modifier branch.
 *  - [isOverlayScreenActive] — a host-composed overlay screen ABOVE this
 *    screen (PlayerActivity's subtitle tester) owns the keys: the sink must
 *    not act on keys the overlay declined, and must not act on the player
 *    hiding underneath it.
 *  - [layerHoldsFocus] — the focused dispatch chain owns the key (an arm
 *    declined it there); re-running it here would double-handle.
 *
 * The [config] block is compiled into a [dpadKeyHandlerHandle] ONCE per
 * config instance ([remember] keyed on it — the screens hand in the same
 * remembered instance the focused-dispatch modifier runs, so a root
 * recomposition no longer pays the scope construction), and delivered
 * through a live-read delegate. Composes nothing off-TV ([LocalTvMode]).
 *
 * Public because the LIVE player's screen composes it too (same D-pad shape;
 * it previously installed its arm set on the bridge directly, which is why
 * [installPlayerKeySink]/[uninstallPlayerKeySink] stayed module-private);
 * mirrors the [PlayerTvFocusGrabEffect] public-for-live precedent.
 */
@Composable
public fun PlayerTvKeySinkEffect(
    isSheetOpen: Boolean,
    isOverlayScreenActive: Boolean,
    layerHoldsFocus: Boolean,
    onResetLayerFocus: () -> Unit,
    config: DpadKeyHandlerScope.() -> Unit,
) {
    val isTv = LocalTvMode.current
    if (!isTv) return
    val handle = remember(config) { dpadKeyHandlerHandle(config) }
    val latestHandle = rememberUpdatedState(handle)
    val latestSheetOpen = rememberUpdatedState(isSheetOpen)
    val latestOverlayActive = rememberUpdatedState(isOverlayScreenActive)
    val latestLayerHoldsFocus = rememberUpdatedState(layerHoldsFocus)
    val latestResetLayerFocus = rememberUpdatedState(onResetLayerFocus)
    DisposableEffect(isTv) {
        val sink: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { keyEvent ->
            when {
                latestSheetOpen.value -> false
                latestOverlayActive.value -> false
                latestLayerHoldsFocus.value -> false
                else -> keyEvent.toDpadKeyEvent()?.let(latestHandle.value) ?: false
            }
        }
        installPlayerKeySink(sink)
        onDispose {
            uninstallPlayerKeySink(sink)
            // onFocusChanged never fires for a composition that simply left;
            // the screen resets its flag so a stale true can't decline a
            // future sink.
            latestResetLayerFocus.value()
        }
    }
}

/**
 * The shared grab/re-assert body behind [PlayerKeyboardFocusGrabEffect] and
 * [PlayerTvFocusGrabEffect] ([tag] only disambiguates the diagnostics lines).
 */
@Composable
private fun PlayerFocusGrabEffect(
    focusRequester: FocusRequester,
    layerComposed: Boolean,
    targetHoldsFocus: Boolean,
    tag: String,
) {
    LaunchedEffect(layerComposed) {
        if (layerComposed) {
            val startMs = System.currentTimeMillis()
            for (attempt in 1..3) {
                androidx.compose.runtime.withFrameNanos { }
                val ok = focusRequester.tryRequestFocus(tag)
                harnessFocusDiag(
                    "grab effect: attempt=$attempt ok=$ok t=+" +
                        (System.currentTimeMillis() - startMs) + "ms layerComposed=true",
                )
                if (ok) break
            }
        } else {
            harnessFocusDiag("grab effect: disarmed (layerComposed=false)")
        }
    }
    if (layerComposed) {
        LaunchedEffect(targetHoldsFocus) {
            if (targetHoldsFocus) return@LaunchedEffect
            val startMs = System.currentTimeMillis()
            for (attempt in 1..3) {
                androidx.compose.runtime.withFrameNanos { }
                val ok = focusRequester.tryRequestFocus("${tag}_regrab")
                harnessFocusDiag(
                    "re-assert: attempt=$attempt ok=$ok t=+" +
                        (System.currentTimeMillis() - startMs) + "ms targetHoldsFocus=false",
                )
                if (ok) break
            }
        }
    }
}

/**
 * Harness-gated focus diagnostic: emits one stdout line when the
 * desktop session harness is armed (`jellyplay.harness.enabled=true`, the same
 * zero-cost gate [DesktopSessionHarness] uses), so a live
 * `tools/e2e/desktop-session-pass.sh` run can correlate the Compose-side focus
 * story (grab attempts, player-Box focus transitions, keys entering the
 * screen's dispatch subtree) with the harness's AWT-side focus-owner
 * observations. Every other platform is a silent no-op, and no call site runs
 * outside the jvm-gated desktop branches, so normal boots (and all Android
 * behavior) are byte-identical.
 */
internal expect fun harnessFocusDiag(message: String)

/**
 *  deterministic desktop key delivery: publish the composing player
 * screen's media-key handler to the desktop shell ([DesktopPlayerKeyBridge],
 * jvmMain) so `DesktopNavScaffold.onPreviewKeyEvent` can forward raw key
 * events into [VideoPlayerScreen]'s own handler even while the player Box
 * holds no Compose focus (the AWT/Compose focus flap leaves continuous
 * focus-less gaps in which Compose's null-focus fallback dispatch stops at
 * the shell's Row and the key dies). Passing `null` uninstalls (screen
 * disposal).
 *
 * The screen is the ONLY interpreter of media-key semantics: the sink lambda
 * it installs re-runs the screen's own key `when`-block, and declines when
 * the keyboard Box subtree holds focus (the normal focused dispatch chain
 * owns the key) or a sheet is open — so the shell's forward can never double
 * or misinterpret. The Android actual is a no-op and the install call site
 * sits behind [grabsKeyboardFocusWithControlsVisible] (false on Android), so
 * Android behavior is byte-identical.
 */
internal expect fun installPlayerKeySink(sink: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)?)

/**
 * [installPlayerKeySink]'s dispose twin, identity-guarded: the sink is
 * cleared only when it is still the [expected] instance — a stacked player
 * installed a newer sink that the popped screen must not disarm (latent; no
 * current route stacks players). Android actual is a no-op like install.
 */
internal expect fun uninstallPlayerKeySink(expected: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)?)
