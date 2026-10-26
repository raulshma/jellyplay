package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The user-shortcuts preference vocabulary: one shortcut definition and the
 * per-surface pin order lists read by the shortcuts editor, the home header
 * dock, and the shell overflow menu.
 *
 * The persisted vocabulary is plain strings end to end — [ShortcutDefinition.targetId]
 * is a stable nav-key/action id (never a class name, the `Route.navKey`
 * principle) and [ShortcutDefinition.iconId] is a stable icon-registry key —
 * so a renamed class or icon can never silently break stored configuration.
 */

/** What kind of thing a shortcut launches. Flat discrimination — no polymorphic serialization. */
@Serializable
enum class ShortcutTargetKind {
    /** Navigate to the app screen registered under [ShortcutDefinition.targetId]. */
    SCREEN,

    /** Invoke the shell action registered under [ShortcutDefinition.targetId]. */
    ACTION,
}

/**
 * One user-defined shortcut: what it launches ([targetId], discriminated by
 * [kind]), the icon shown for it, and an optional display-name override.
 * Identified by a stable random [id] that the per-surface order lists
 * reference, so editing a shortcut's icon never disturbs its placement.
 */
@Serializable
data class ShortcutDefinition(
    val id: String,
    val kind: ShortcutTargetKind,
    /** Stable screen key (`Route.navKey` vocabulary) or [com.raulshma.jellyplay.core.model.ShortcutTargetKind] action id. */
    val targetId: String,
    /** Stable icon-registry key; blank = the target's catalog default icon. */
    val iconId: String = "",
    /** Optional user-typed display name; null = the target's localized catalog label. */
    val labelOverride: String? = null,
) {
    /**
     * The kind-discriminated lookup key this definition matches a catalog
     * entry (and dedupes against its siblings) by — the one encoding of the
     * `(kind, targetId)` pair, shared by the store's normalize, the editor's
     * target picker, and the resolve policy.
     */
    val targetKey: String get() = shortcutTargetKey(kind, targetId)
}

/**
 * The kind-discriminated key for a `(kind, targetId)` pair — the single
 * encoding every catalog lookup and dedupe uses. The kind prefix keeps a
 * hypothetical screen-id/action-id collision from aliasing.
 */
fun shortcutTargetKey(kind: ShortcutTargetKind, targetId: String): String = "${kind.name}:$targetId"

/** Which surface a shortcut is pinned to. */
enum class ShortcutSurface { HEADER, OVERFLOW }

/**
 * Pin capacities per surface — the single source both the store's write clamp
 * and the editor UI enforce (the store stays the clamp owner; these constants
 * keep the two enforcement sites from drifting).
 */
object ShortcutLimits {
    /** Home header dock capacity — the dock capsule fits a handful of 38dp icon buttons. */
    const val HEADER_MAX = 4

    /** Navigation overflow menu capacity. */
    const val OVERFLOW_MAX = 12
}
