package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogTarget

/**
 * The overflow menu's per-entry decoration fold — the dynamic half of an
 * action pill (offline icon/label/progress trio, downloads badge, Surprise
 * highlight, menu-dismiss policy), kept Compose-free in one pure fold so the
 * action×offline-state matrix is JVM-testable (the ShellChromePlacement
 * precedent). The click wiring stays compose-side (it needs the navigator,
 * the menu state and the shell's action surface); the two halves meet in
 * PhoneContent, which passes this fold's output to
 * [com.raulshma.jellyplay.navigation.components.OverflowMenuItem] unchanged.
 *
 * Pure on purpose: no composition, no state — icons travel as the
 * [OverflowOfflineIcon] slot (resolved to Tabler vectors at the call site)
 * and labels as app string resource ids (the PipActionSet idiom), so no
 * Compose type crosses this file.
 */

/** The offline toggle's state-dependent icon slot, resolved to a Tabler vector by the call site. */
internal enum class OverflowOfflineIcon {
    /** The device is offline: the pill shows Wifi (tapping goes online). */
    Wifi,

    /** The device is online: the pill shows WifiOff (tapping goes offline). */
    WifiOff,
}

/**
 * One overflow entry's dynamic decoration (the static face — resolved icon
 * and label — lives on the [com.raulshma.jellyplay.core.ui.navigation.ResolvedShortcut]
 * the menu already carries).
 *
 * @property action the decoded shell action, `null` for a screen target —
 *   the decode-once output the click side consumes instead of re-testing the
 *   target type.
 * @property iconSlot the offline toggle's icon swap, `null` = keep the
 *   resolved shortcut's icon.
 * @property labelOverrideRes the offline toggle's state label
 *   (go online / going online / go offline), `null` = keep the resolved
 *   label.
 * @property badgeCount the downloads row's in-flight badge.
 * @property isHighlighted the primary-tinted pill face (Surprise Me; the
 *   offline-active toggle).
 * @property isProgressing the offline→online transition spinner replaces the
 *   icon.
 * @property closesMenu whether tapping dismisses the overflow menu: screen
 *   rows navigate away and Surprise Me switches tab — both close; Play On
 *   opens its sheet and the offline toggle flips in place, so both keep the
 *   menu up (the result stays visible).
 */
internal data class OverflowShortcutDecoration(
    val action: ShortcutAction?,
    val iconSlot: OverflowOfflineIcon?,
    val labelOverrideRes: Int?,
    val badgeCount: Int,
    val isHighlighted: Boolean,
    val isProgressing: Boolean,
    val closesMenu: Boolean,
)

/**
 * Folds one resolved shortcut's target plus the shell's live offline state
 * into its [OverflowShortcutDecoration]. Label precedence: `going_online`
 * outranks the online/offline pair (the transition spinner's wording wins
 * while in flight); the ICON keys on the mode alone.
 */
internal fun overflowShortcutDecoration(
    target: ShortcutCatalogTarget,
    isOfflineActive: Boolean,
    isGoingOnline: Boolean,
    downloadCount: Int,
): OverflowShortcutDecoration {
    // The two targets whose decoration is dynamic — decode once per row
    // instead of re-testing the target type in each decoration slot.
    val action = (target as? ShortcutCatalogTarget.Action)?.action
    val isToggleOffline = action == ShortcutAction.TOGGLE_OFFLINE
    val isDownloads = (target as? ShortcutCatalogTarget.Screen)?.route == Route.Downloads
    return OverflowShortcutDecoration(
        action = action,
        iconSlot = when {
            isToggleOffline && isOfflineActive -> OverflowOfflineIcon.Wifi
            isToggleOffline -> OverflowOfflineIcon.WifiOff
            else -> null
        },
        labelOverrideRes = when {
            isToggleOffline && isGoingOnline -> R.string.menu_going_online
            isToggleOffline && isOfflineActive -> R.string.menu_go_online
            isToggleOffline -> R.string.menu_go_offline
            else -> null
        },
        badgeCount = if (isDownloads) downloadCount else 0,
        isHighlighted = action == ShortcutAction.SURPRISE_ME || (isToggleOffline && isOfflineActive),
        isProgressing = isToggleOffline && isGoingOnline,
        closesMenu = when (target) {
            is ShortcutCatalogTarget.Screen -> true
            is ShortcutCatalogTarget.Action -> action == ShortcutAction.SURPRISE_ME
        },
    )
}
