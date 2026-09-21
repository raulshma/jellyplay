package com.raulshma.jellyplay.navigation

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * Pure predicate (extracted verbatim from MainContent's full-screen-layout
 * branch decision so the composition rule is JVM-testable, same pattern as
 * VisibleTopLevelRoutes.kt): `true` while *any* route on [backStack] is a
 * full-screen [Route].
 *
 * A full-screen route may sit below the top of the back stack (e.g. the
 * video player with the subtitle tester pushed on top of it), so the shell
 * keeps the full-screen layout branch active for the whole round-trip:
 * switching the branch mid-round-trip re-registers the player's NavKey in a
 * second NavDisplay subtree against the shared SaveableStateHolder, crashing
 * with "Key VideoPlayer(...) was used multiple times" on the back-pop.
 * `null` (no stack for the current tab yet) and an empty stack both read
 * `false`; non-[Route] NavKeys never count as full-screen.
 */
internal fun isFullScreenRouteActive(backStack: List<NavKey>?): Boolean =
    backStack?.any { it is Route && it.isFullScreen } ?: false
