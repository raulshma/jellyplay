package com.raulshma.jellyplay.feature.player.video.chrome

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What one BACK press means for the chrome — the hide-or-exit ladder's
 * verdicts, in dispatch order. [CloseSheet] folds a modal away first,
 * [HideControls]/[SummonControls] are the chrome's own arms (which one is
 * armed is the ladder style — a ladder never arms both), and [Exit] leaves
 * the player.
 */
enum class PlayerChromeBackVerdict {
    CloseSheet,
    HideControls,
    SummonControls,
    Exit,
}

/**
 * The player chrome module (the GLOSSARY "Player chrome module"): everything
 * the controls chrome knows about itself, composed over this package's pure
 * predicates (which stay — this class is their ONE caller pair across the
 * VOD and live screens). Owns the auto-hide timer, the summon policy, the
 * focus-grab arming verdict and the BACK hide-or-exit ladder; the visibility
 * VALUE itself stays screen-bound — both hosts keep their composition state
 * (`rememberSaveable` on VOD so a config-change restore preserves it,
 * exactly as before) and hand it to this controller as a get/set pair, so
 * every DECISION about the value lives here while the saveability contract
 * stays with the screen.
 *
 * Compose-free core (the player-video GestureSeekController extraction
 * shape — plain class, injected scope, reader lambdas for every value that
 * changes per recomposition, side-effect ports for the edges); the screens
 * bind it through player-video's `rememberPlayerChromeController`. Every reader
 * lambda is invoked at DECISION time, never captured: a rebuilt map, a
 * flipped pref or an opened sheet is visible to an already-armed timer.
 *
 * The summon funnel is [summonForPlayPause] — the
 * [shouldSummonControlsOnPause] fold (jellyfin-androidtv #3924). The other
 * half of the summon guarantee,
 * [com.raulshma.jellyplay.core.model.PlayerInputMap.ensureControlsSummonable],
 * stays a read-time model repair (the datastore spec applies it when the map
 * is read) — it guarantees a summon ARM exists, this class owns what an
 * armed summon does.
 *
 * The visibility edges (a controller-issued show/hide, the auto-hide
 * expiry included) fire [onVisibilityChange] once per actual flip — the
 * VOD screen wires it to the ViewModel's poll-cadence switch
 * (`SetControlsVisible` → the engine's 250 ms-visible / 1000 ms-hidden
 * polling interval).
 *
 * JVM-testable directly with a fake time source (runTest's scheduler drives
 * [delay] inside the injected scope).
 */
class PlayerChromeController(
    private val scope: CoroutineScope,
    // ── visibility (screen-bound get/set pair) ──
    private val isVisibleNow: () -> Boolean,
    private val applyVisible: (Boolean) -> Unit,
    // ── policy inputs (reader lambdas, invoked at decision time) ──
    /** Platform fact — static for the session (same assumption the window-seam effects make). */
    private val isTv: () -> Boolean,
    /** The pref-sourced BASE auto-hide timeout (the TV doubling is the fold's). */
    private val controlsTimeoutMs: () -> Long,
    private val isSheetOpen: () -> Boolean,
    private val isSeeking: () -> Boolean = { false },
    private val isOverflowMenuOpen: () -> Boolean = { false },
    /** Non-TV focused-chrome suppression (the live chrome passes constant false). */
    private val controlsHasFocus: () -> Boolean = { false },
    private val hideOsdOnPause: () -> Boolean = { false },
    // ── edges (controller-issued visibility flips) ──
    private val onVisibilityChange: (Boolean) -> Unit = {},
) {
    /** The bound visibility value (the screen's composition state). */
    val visible: Boolean get() = isVisibleNow()

    /** The registered sheet term, re-exposed for the grab arms that read it alone. */
    val sheetOpen: Boolean get() = isSheetOpen()

    private var autoHideJob: Job? = null

    fun show() = setVisible(true)

    fun hide() = setVisible(false)

    fun toggle() = setVisible(!visible)

    /**
     * The play/pause transport's directional summon: [isPauseDirection] is
     * the toggle's DIRECTION (true = this toggle pauses), the same value the
     * arms pass as [shouldSummonControlsOnPause]'s `isPause`. An
     * already-visible overlay is never force-hidden — the auto-hide owns
     * dismissal.
     */
    fun summonForPlayPause(isPauseDirection: Boolean) {
        if (shouldSummonControlsOnPause(isPause = isPauseDirection, hideOsdOnPause = hideOsdOnPause())) {
            show()
        }
    }

    /**
     * Re-arms the auto-hide timer: cancels any pending hide and re-evaluates
     * the [shouldScheduleControlsAutoHide] gate; an armed timer hides after
     * [controlsAutoHideTimeoutMs] (the TV-doubling fold over the pref). The
     * screens call this from the effect keyed on their gate inputs (the
     * user-interaction counter included — a bump restarts the timer, exactly
     * the old per-key LaunchedEffect restart); the controller re-arms itself
     * on every visibility edge.
     */
    fun scheduleAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = null
        if (!shouldScheduleControlsAutoHide(
                showControls = visible,
                isSeeking = isSeeking(),
                isSheetOpen = isSheetOpen(),
                isOverflowMenuOpen = isOverflowMenuOpen(),
                isTv = isTv(),
                controlsHasFocus = controlsHasFocus(),
            )
        ) {
            return
        }
        val timeoutMs = controlsAutoHideTimeoutMs(controlsTimeoutMs(), isTv())
        autoHideJob = scope.launch {
            delay(timeoutMs)
            hide()
        }
    }

    /**
     * The ONE BACK decision (the ladder the five former sites carried in
     * parallel): sheet arm first, then the hide or summon chrome arm per the
     * ladder's style, then exit. Arm flags per site:
     *  - the system BACK pipelines (the screens' back handlers, the live
     *    D-pad BACK arm): defaults — sheet, hide-when-visible, exit;
     *  - the VOD system handler arms hide on TV only (a touch BACK exits
     *    with the controls up): `hideArm = isTv`;
     *  - the keyboard/shell executor's BACK_OR_HIDE: `sheetArm = false`
     *    (those paths decline while a sheet is open);
     *  - the touch edge-swipe's summon-then-exit ladder:
     *    `hideArm = false, summonArm = true, sheetArm = false`.
     */
    fun backVerdict(
        hideArm: Boolean = true,
        summonArm: Boolean = false,
        sheetArm: Boolean = true,
    ): PlayerChromeBackVerdict = when {
        sheetArm && isSheetOpen() -> PlayerChromeBackVerdict.CloseSheet
        hideArm && visible -> PlayerChromeBackVerdict.HideControls
        summonArm && !visible -> PlayerChromeBackVerdict.SummonControls
        else -> PlayerChromeBackVerdict.Exit
    }

    /**
     * Whether the chrome layer (controls hidden) should own focus: controls
     * hidden, sheet closed, and none of the screen's extra claimants up
     * (the VOD surface feeds its overlay-claims/cast/host-overlay terms; the
     * live surface its error-banner term). The screens feed the verdict to
     * `PlayerTvFocusGrabEffect`'s `layerComposed`.
     */
    fun shouldLayerOwnFocus(vararg claimants: Boolean): Boolean =
        !visible && !isSheetOpen() && claimants.none { it }

    private fun setVisible(next: Boolean) {
        val previous = visible
        applyVisible(next)
        if (next == previous) return
        onVisibilityChange(next)
        // Visibility is the timer's master switch: every flip re-arms (a
        // hide expiry re-evaluates into a no-op — the gate reads false).
        scheduleAutoHide()
    }
}
