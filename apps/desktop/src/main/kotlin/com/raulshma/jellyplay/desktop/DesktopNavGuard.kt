package com.raulshma.jellyplay.desktop

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.NavigationState
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The dead-end guard's decision + message, extracted from
 * DesktopNavScaffold's inline `Navigator(navigation) { … }` adapter (runtime
 * safety, not polish): NavDisplay with an unregistered top-of-stack entry is
 * a crash hazard, and the shared screens freely push routes that have no
 * desktop section. The decision is DERIVED, not enumerated —
 * [isRegistered] is the shell-owned [ShellSectionRegistry][com.raulshma.jellyplay.feature.shell.navigation.ShellSectionRegistry]
 * ledger the entry provider re-attaches on every rebuild, so a route is a
 * dead end exactly when no shellEntryProvider section registered it
 * (LiveTvChannelPlayer/SubtitleTester because their builders are
 * Android-only, VideoPlayer wherever the surface probe fails). The former
 * hand-kept three-route mirror is gone. Unregistered routes surface as a
 * snackbar — the desktop twin of the Android shell's PlaybackHostRouter
 * navigateFilter.
 *
 * Pure wiring over constructor-style lambdas (the DesktopUpdateCheckController
 * idiom): no Compose imports, JVM-pinnable by DesktopNavGuardTest.
 *
 * @param navigation the shell's [NavigationState] (nav3 back stacks).
 * @param isRegistered the section registry ledger read.
 * @param scope where the snackbar is shown from — the navigate filter must
 *   answer synchronously, so the presentation launches on this scope
 *   (the composition's scope at the call site, dying with the scaffold).
 * @param showMessage the snackbar sink.
 */
internal fun desktopGuardedNavigator(
    navigation: NavigationState,
    isRegistered: (NavKey) -> Boolean,
    scope: CoroutineScope,
    showMessage: suspend (String) -> Unit,
): Navigator = Navigator(navigation) { route ->
    if (isRegistered(route)) {
        true
    } else {
        scope.launch { showMessage(desktopDeadEndMessage(route)) }
        false
    }
}

/**
 * The dead-end snackbar wording: the route's simple class name (falling back
 * to its toString) plus the fixed suffix — top-level internal so the test
 * pins the exact user-facing string.
 */
internal fun desktopDeadEndMessage(route: NavKey): String =
    "${route::class.simpleName ?: route.toString()} is not available on desktop yet."
