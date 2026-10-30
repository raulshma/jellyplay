package com.raulshma.jellyplay.feature.shell.navigation

import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction

/**
 * The shell's EXECUTION surface for the shortcut action vocabulary
 * ([ShortcutAction] — Surprise Me / Play On / offline toggle): one module per
 * shell that owns both halves of the dispatcher —
 *
 *  - **capability** ([supports] / [supportedActions]): whether this shell can
 *    actually run the action, derived from which execution arms were supplied
 *    at construction — a shell that has no Play On strategy passes `null` and
 *    the action is unsupported, never a silent no-op;
 *  - **execution** ([execute]): the ordered effects, with the
 *    navigate-home-before-arm-surprise invariant PRIVATE here (the Home hero
 *    controller must be composed to collect the surprise signal, so
 *    [ShortcutAction.SURPRISE_ME] always focuses Home first).
 *
 * ADR-0001's shape — wiring shared, policy per-shell: the ordering rule and
 * the capability verdict are the shared wiring; the arms themselves are
 * per-shell policy (Android: MainViewModel + the Play On sheet counter;
 * desktop: its own surprise channel + the offline-mode manager, no cast
 * strategy).
 *
 * Constructed once per shell (remembered beside the rest of the
 * [ShellHomeHooks] inputs) and consumed by every action surface: the home
 * dock's header pills (through [ShellHomeHooks]), the Android overflow menu
 * and the shortcuts-customize editor's picker (via [supportedActions]).
 *
 * NOT a data class deliberately — it holds lambdas; the remember-key
 * discipline treats it like a remembered lambda (identity stable per shell).
 *
 * @param focusHome switches the shell's top-level route to Home. Shared by
 *   both shells (each owns a Home tab); the surprise arm runs strictly after
 *   it.
 * @param armSurprise fires the shell's Surprise Me signal — Android:
 *   MainViewModel's buffered flow; desktop: the same-shaped channel the home
 *   hero collects. `null` = this shell has no surprise seam.
 * @param requestPlayOn opens the Play On device picker — Android: the sheet
 *   counter PhoneContent observes. `null` = this shell has no cast strategy
 *   (desktop).
 * @param toggleOffline flips the app's offline mode — both shells route to
 *   the platform [com.raulshma.jellyplay.core.data.offline.OfflineModeManager]
 *   (desktop's manual toggle is that manager's documented only path).
 *   `null` = unsupported.
 * @param onUnsupported the loud-but-non-crashing failure for an action this
 *   shell did not arm — reachable only through a stored definition that
 *   outlived a capability change (the customize editor filters unsupported
 *   actions, but resolve-side gating is per surface). Android passes
 *   `error(..)` (every action is armed there); desktop presents a snackbar.
 */
class ShortcutActionSurface(
    private val focusHome: () -> Unit,
    private val armSurprise: (() -> Unit)?,
    private val requestPlayOn: (() -> Unit)?,
    private val toggleOffline: (() -> Unit)?,
    private val onUnsupported: (ShortcutAction) -> Unit,
) {

    /**
     * The actions this shell can execute — the customize editor's picker
     * filters its action rows to this set, so an unsupported action is never
     * OFFERED; [execute] is the last line for a definition that predates a
     * capability change.
     */
    val supportedActions: Set<ShortcutAction> = buildSet {
        if (armSurprise != null) add(ShortcutAction.SURPRISE_ME)
        if (requestPlayOn != null) add(ShortcutAction.PLAY_ON)
        if (toggleOffline != null) add(ShortcutAction.TOGGLE_OFFLINE)
    }

    /** Whether this shell can execute [action] — see [supportedActions]. */
    fun supports(action: ShortcutAction): Boolean = action in supportedActions

    /**
     * Executes [action] through the shell's own arm. The ONLY routing for
     * shortcut actions on both shells (header dock, overflow menu) — the
     * per-action click nuances that remain at the call sites are
     * presentation-only (closing the menu, the going-online re-tap guard),
     * never the effects themselves. An unsupported action routes to
     * [onUnsupported]; it must never be a silent no-op.
     */
    fun execute(action: ShortcutAction) {
        when (action) {
            ShortcutAction.SURPRISE_ME -> {
                val arm = armSurprise
                if (arm == null) {
                    onUnsupported(action)
                } else {
                    // The invariant: the Home hero controller must
                    // be composed to collect the surprise signal, so focus
                    // Home BEFORE arming. No-op when Home is already current.
                    focusHome()
                    arm()
                }
            }
            ShortcutAction.PLAY_ON -> {
                val arm = requestPlayOn
                if (arm == null) onUnsupported(action) else arm()
            }
            ShortcutAction.TOGGLE_OFFLINE -> {
                val arm = toggleOffline
                if (arm == null) onUnsupported(action) else arm()
            }
        }
    }
}
