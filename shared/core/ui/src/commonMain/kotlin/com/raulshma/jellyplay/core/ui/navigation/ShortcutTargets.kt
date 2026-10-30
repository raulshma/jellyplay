package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import com.raulshma.jellyplay.core.model.shortcutTargetKey
import org.jetbrains.compose.resources.StringResource

/**
 * The shortcut TARGET vocabulary — what a user shortcut can point at — and
 * the catalog entry metadata every shortcut surface renders from.
 *
 * Split from the resolution policy ([resolveShortcuts] in
 * ShortcutsCustomization.kt) so the policy stays a pure function over these
 * plain-data types: the catalog IMPLEMENTATION (which entries exist, their
 * labels) lives in the shortcuts feature, which owns the string resources;
 * core:ui here only fixes the shapes and the stable id vocabulary.
 */

/**
 * The shell-level actions a shortcut may invoke — the non-navigation entries
 * the overflow menu has always carried. The [id] is the persisted vocabulary
 * (never the enum name — see the `Route.navKey` rationale).
 */
enum class ShortcutAction(val id: String) {
    /** Play a random recommendation (the "Surprise Me" pill). */
    SURPRISE_ME("surprise_me"),

    /** Open the Play On device picker. */
    PLAY_ON("play_on"),

    /** Toggle offline mode (dynamic icon/label — resolved at render). */
    TOGGLE_OFFLINE("toggle_offline"),
    ;

    companion object {
        private val byId = entries.associateBy { it.id }

        /** Lenient parse of a persisted action id — null when unknown. */
        fun fromId(id: String): ShortcutAction? = byId[id]
    }
}

/**
 * What a catalog entry (and therefore a resolved shortcut) launches. `Screen`
 * carries the [Route] to navigate to plus its stable `Route.navKey` key (the
 * persisted id); `Action` carries the shell action.
 */
@Immutable
sealed interface ShortcutCatalogTarget {
    /** Navigate to the app screen registered under [route]. */
    @Immutable
    data class Screen(val route: Route, val key: String) : ShortcutCatalogTarget

    /** Invoke the shell action [action]. */
    @Immutable
    data class Action(val action: ShortcutAction) : ShortcutCatalogTarget
}

/**
 * One pickable entry in the shortcut catalog: a target plus the render
 * defaults every surface falls back to. Implemented by the shortcuts feature
 * (labels are that module's string resources); consumed by the editor, the
 * Shortcuts screen, and both shortcut surfaces.
 */
@Immutable
data class ShortcutCatalogEntry(
    val target: ShortcutCatalogTarget,
    /** The localized display label (the default; a definition's override wins). */
    val labelRes: StringResource,
    /** The default icon (a definition's iconId wins when it resolves). */
    val defaultIcon: ImageVector,
    /** Optional longer description (the Shortcuts catalog screen's subtitle; the editor ignores it). */
    val descriptionRes: StringResource? = null,
    /** Hidden for non-admin users (admin screens), matching the Shortcuts screen's filter. */
    val requiresAdmin: Boolean = false,
    /** Hidden while offline (e.g. Live TV), matching the nav-bar offline policy. */
    val hiddenOffline: Boolean = false,
) {
    /** Whether this entry launches a shell action (vs navigating to a screen). */
    val isAction: Boolean get() = target is ShortcutCatalogTarget.Action

    /** The stable persisted target id: the screen's `navKey` or the action's
     * id. Together with [isAction] this is the lookup key into a
     * catalog — screens and actions live in disjoint id spaces by convention,
     * and the kind bit keeps a hypothetical collision from aliasing.
     */
    val targetId: String = when (val t = target) {
        is ShortcutCatalogTarget.Screen -> t.key
        is ShortcutCatalogTarget.Action -> t.action.id
    }

    /**
     * The kind-discriminated key this entry is looked up (and deduped) by —
     * the shared [shortcutTargetKey] encoding, so the entry side and the
     * definition side of a catalog match can never drift apart.
     */
    val storageKey: String get() =
        shortcutTargetKey(if (isAction) ShortcutTargetKind.ACTION else ShortcutTargetKind.SCREEN, targetId)
}

/**
 * The read-side catalog of pickable shortcut targets — one registration site
 * (implemented in the shortcuts feature, which owns the string resources)
 * consumed by the editor's target picker, the Shortcuts screen, and the
 * resolution policy's lookups. The entries list is fixed per process; the
 * admin/offline filtering happens downstream ([resolveShortcuts]) so the
 * catalog itself never needs live state.
 */
interface ShortcutCatalog {
    val entries: List<ShortcutCatalogEntry>

    /** The entry a stored `ShortcutDefinition`-shaped (kind, targetId) pair resolves to, or null. */
    fun entryFor(kind: ShortcutTargetKind, targetId: String): ShortcutCatalogEntry? = entries.firstOrNull { entry ->
        when (kind) {
            ShortcutTargetKind.ACTION -> entry.target is ShortcutCatalogTarget.Action && entry.targetId == targetId
            ShortcutTargetKind.SCREEN -> entry.target is ShortcutCatalogTarget.Screen && entry.targetId == targetId
        }
    }
}
