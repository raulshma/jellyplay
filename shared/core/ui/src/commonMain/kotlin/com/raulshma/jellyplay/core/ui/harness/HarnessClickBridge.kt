package com.raulshma.jellyplay.core.ui.harness

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.concurrent.Volatile

/**
 * Harness-gated click-target bridge — the "compose-semantics-driven click
 * surface" the e2e ledgers prescribed (docs/e2e/desktop-native-dialogs.md,
 * residual) so an in-app harness can CLICK real UI rows without
 * per-machine pixel maps.
 *
 * The mechanism is deliberately tiny and strictly opt-in:
 *
 *  - A screen annotates a clickable row with `Modifier.harnessClickTarget(id)`.
 *  - When [enabled] is false (every normal boot — the desktop shell flips it
 *    from a system property only when an e2e harness run arms it), the
 *    modifier factory returns `this` untouched: no layout node, no callback,
 *    zero cost. No other platform ever sets the flag.
 *  - When armed, the modifier reports the row's live bounds — in the
 *    coordinate space of the WINDOW hosting the row (a material3
 *    ModalBottomSheet opens its own top-level window on desktop, so sheet
 *    rows report sheet-window coordinates while the player's in-window
 *    sheets report main-window ones) — plus, for gated rows, the row's own
 *    enabled state. The entry is removed when the row leaves composition.
 *  - The harness (apps/desktop) owns the window half: it tracks AWT window
 *    creation (the newest visible non-dialog window is the open sheet),
 *    converts window-space bounds to screen coordinates (÷ the AWT
 *    defaultTransform scale — Compose px vs AWT logical px on HiDPI) and
 *    drives a REAL java.awt.Robot mouse click at the row's center — the
 *    OS-level pointer event travels the production hit-testing + onClick
 *    wiring, so what is verified is the actual row wiring, not a synthetic
 *    callback.
 *
 * The registry lives in commonMain (shared by every KMP target so annotations
 * compile everywhere) and touches nothing platform-specific. Concurrency:
 * the flag is @Volatile (set once at boot, read everywhere); the target map
 * is only ever touched from the Compose UI thread on the only platform that
 * arms the bridge (desktop — the modifier callbacks fire in composition /
 * layout passes and the harness reads from the Main dispatcher, i.e. the
 * same AWT EDT), so a plain map needs no lock.
 */
object HarnessClickBridge {

    /**
     * Armed by the desktop shell at boot from `jellyplay.flowpass.enabled`
     * (before any screen composes — see Main.kt). Default false everywhere;
     * nothing else in the app reads or writes it. kotlin.concurrent.Volatile
     * — the common annotation with per-platform actuals (SeerrRepositoryImpl
     * precedent), so commonMain compiles for every KMP target.
     */
    @Volatile
    var enabled: Boolean = false

    /** id → last reported target (null bounds while the row is unplaced). */
    private val targets = LinkedHashMap<String, Target>()

    /**
     * One registered click target. [bounds] are window-space (the hosting
     * window's coordinate system — see the class KDoc for the sheet case).
     * [enabled] mirrors the row's own enabled state where the annotation
     * passes it (confirm buttons) so a harness can await a clickable state
     * instead of clicking a disabled button and timing out downstream.
     */
    data class Target(val id: String, val bounds: Rect?, val enabled: Boolean = true)

    /** Called from the modifier's position callback; keeps the newest bounds. */
    internal fun report(id: String, bounds: Rect?, enabled: Boolean) {
        targets[id] = Target(id, bounds, enabled)
    }

    /** Called when an annotated row leaves composition. */
    internal fun remove(id: String) {
        targets.remove(id)
    }

    /** Point-in-time copy for harness polling/diagnostics. */
    fun snapshot(): Map<String, Target> = targets.toMap()

    /** The target for [id], or null when absent or not yet placed. */
    fun target(id: String): Target? =
        targets[id]?.takeIf { it.bounds != null && !it.bounds!!.isEmpty }
}

/**
 * Marks this node as a named click target for the e2e harness (no-op unless
 * [HarnessClickBridge.enabled]). The id is stable harness vocabulary — see the
 * per-flow step tables in docs/e2e/desktop-native-dialogs.md. [enabled] lets
 * gating rows (confirm buttons) publish their live clickable state; it does
 * not affect the modifier chain.
 */
@Composable
fun Modifier.harnessClickTarget(id: String, enabled: Boolean = true): Modifier {
    if (!HarnessClickBridge.enabled) return this
    // KNOWN CONSTRAINT ( review): `enabled` state reaches the bridge
    // only on a LAYOUT PASS (onGloballyPositioned), so a disabled→enabled
    // flip that triggers no size/position change can leave Target.enabled
    // stale until some sheet reflow happens. All current call sites flip
    // enabled together with a visibility/size change; if a future flow needs
    // a pure state flip, re-report from a snapshotFlow side effect here.
    DisposableEffect(id) {
        onDispose { HarnessClickBridge.remove(id) }
    }
    return onGloballyPositioned { coords ->
        val position = coords.positionInWindow()
        val size = coords.size
        HarnessClickBridge.report(
            id = id,
            bounds = Rect(
                left = position.x,
                top = position.y,
                right = position.x + size.width,
                bottom = position.y + size.height,
            ),
            enabled = enabled,
        )
    }
}
