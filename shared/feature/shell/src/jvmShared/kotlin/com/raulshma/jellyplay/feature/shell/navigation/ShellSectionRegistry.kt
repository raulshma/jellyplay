package com.raulshma.jellyplay.feature.shell.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey

/**
 * The contentKey stamped on the fallback [NavEntry] [shellEntryProvider]
 * installs: private by construction (the shells' sections register their
 * entries with the default derived contentKey), so reference identity against
 * it is an exact registered/unregistered test.
 *
 * A String constant (not a bare `Any()`): nav3's saveable-state entry
 * decorator feeds `entry.contentKey` into
 * `SaveableStateHolder.SaveableStateProvider`, which only accepts
 * Bundle-saveable types — if a fallback entry ever rendered (a stray
 * unregistered key slipping past the dead-end guard), an `Any()` contentKey
 * would crash the composition with
 * `IllegalArgumentException: Type of the key … is not supported`. `const` keeps
 * the single-instance identity the `!==` test relies on, and the NUL prefix
 * can never collide with a real [NavKey]'s derived contentKey.
 */
internal const val UnregisteredEntryContentKey = "\u0000unregistered-entry"

/**
 * Registration ledger for a [shellEntryProvider] graph — the one place a
 * dead-end guard can ask "is this route registered?" instead of hand-listing
 * the routes the shared sections push. Derivation, not enumeration: the
 * registry resolves the key through the very entry provider the shell renders
 * with, so a section that gains or drops a route updates the ledger with no
 * second list to keep in sync (the former desktop three-route mirror is gone
 * because of this).
 *
 * A shell owns one instance across recompositions while [shellEntryProvider]
 * rebuilds — and re-attaches — the graph behind it. The one-composition
 * window before the first attach answers `false` for every route; every
 * navigate call is user/harness-event driven (post-composition), so the
 * window is unreachable and, on a hit, degrades to the same snackbar an
 * unregistered route gets. attach/isRegistered run on the main thread only
 * (composition / navigate), so a plain field needs no synchronization.
 */
class ShellSectionRegistry() {
    private var resolve: ((NavKey) -> NavEntry<NavKey>)? = null

    /**
     * `true` when [key] resolves to a real entry in the attached graph —
     * registered by [appSections] or the shell's extra sections; `false`
     * before the first attach or on a fallback hit (unregistered key).
     */
    fun isRegistered(key: NavKey): Boolean {
        val resolve = resolve ?: return false
        return resolve(key).contentKey !== UnregisteredEntryContentKey
    }

    internal fun attach(resolve: (NavKey) -> NavEntry<NavKey>) {
        this.resolve = resolve
    }
}
