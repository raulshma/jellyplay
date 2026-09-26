package com.raulshma.jellyplay.desktop

import androidx.compose.ui.input.key.Key

/**
 * The one desktop window-accelerator table, folded out of the two handlers
 * that used to hand-copy it — Main.kt's window-level `onPreviewKeyEvent`
 * (the matching half, replacing the old AWT MenuBar's native delivery) and
 * DesktopTitleBar's dropdown items (the same three combos rendered as
 * display literals): **Ctrl+R** refresh, **Ctrl+Q** quit, **F11** toggle
 * fullscreen. One fact, one home: adding/renaming a shortcut means editing a
 * row here, never both call sites.
 *
 * Pure on purpose, mirroring the desktopBackKeyDecision precedent: no
 * composition, no event object — Main.kt keeps the KeyDown gate on its side
 * of the line and passes `event.key` / `event.isCtrlPressed`, then executes
 * the [DesktopAcceleratorAction] the matched row carries (the effects —
 * refresh emit, exitApplication, the placement toggle — stay there).
 *
 * Row semantics are exactly the old if/else-if chain: a `requiresCtrl = true`
 * row needs `isCtrlPressed` (so plain R/Q fall through), while a
 * `requiresCtrl = false` row IGNORES modifiers entirely — the F11 arm never
 * checked Ctrl, and Ctrl+F11 still toggles fullscreen today. Pinned by
 * DesktopAcceleratorTest.
 */

/** What the host (Main.kt) executes when an accelerator row fires. */
internal enum class DesktopAcceleratorAction {
    /** Emit into the menu-refresh flow (DesktopAppRoot's pull-to-refresh). */
    Refresh,

    /** Quit the application (same path as the title bar close / tray Quit). */
    Exit,

    /** Toggle window fullscreen (the placement toggle in Main.kt). */
    ToggleFullscreen,
}

/** One accelerator row: how to match it and how the menus render it. */
internal data class DesktopAccelerator(
    val action: DesktopAcceleratorAction,

    /** Display literal for DesktopTitleBar's `MenuShortcutText`. */
    val displayLabel: String,

    val key: Key,

    /**
     * When `true` the row fires only with Ctrl held; when `false` modifiers
     * are ignored (the F11 row — it never gated on Ctrl).
     */
    val requiresCtrl: Boolean,
) {
    fun matches(key: Key, isCtrlPressed: Boolean): Boolean =
        key == this.key && (!requiresCtrl || isCtrlPressed)
}

internal object DesktopAccelerators {

    val Refresh = DesktopAccelerator(
        action = DesktopAcceleratorAction.Refresh,
        displayLabel = "Ctrl+R",
        key = Key.R,
        requiresCtrl = true,
    )

    val Exit = DesktopAccelerator(
        action = DesktopAcceleratorAction.Exit,
        displayLabel = "Ctrl+Q",
        key = Key.Q,
        requiresCtrl = true,
    )

    val ToggleFullscreen = DesktopAccelerator(
        action = DesktopAcceleratorAction.ToggleFullscreen,
        displayLabel = "F11",
        key = Key.F11,
        requiresCtrl = false,
    )

    /** The whole table, in the old chain's check order. */
    val All = listOf(Refresh, Exit, ToggleFullscreen)

    /**
     * The preview handler's fold: the first row matching this key/modifier
     * state, or `null` when the caller must decline the event (fall through
     * to the Compose focus chain unconsumed).
     */
    fun match(key: Key, isCtrlPressed: Boolean): DesktopAccelerator? =
        All.firstOrNull { it.matches(key, isCtrlPressed) }
}
