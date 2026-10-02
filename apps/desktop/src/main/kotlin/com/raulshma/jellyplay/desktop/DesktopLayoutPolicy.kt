package com.raulshma.jellyplay.desktop

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * Whether the desktop shell should hide its navigation rail: `true` exactly
 * when a full-screen [Route] (the video player) sits on TOP of the current
 * back stack — the scaffold's `topRouteIsFullscreen` read, named.
 *
 * TOP-ONLY on purpose — the deliberate delta vs the Android shell's shared
 * isFullScreenRouteActive fold (navigation/FullScreenRoutePolicy.kt), which
 * scans the WHOLE current stack. The Android scan exists because a
 * full-screen route can sit below the top there (the subtitle tester pushed
 * onto the player) and switching its layout branch mid-round-trip
 * re-registers the player's NavKey in a second NavDisplay subtree against a
 * shared SaveableStateHolder — a crash. Neither hazard exists on this shell:
 * it has ONE NavDisplay that stays composed whether the rail shows or not
 * (the rail is a Row sibling, not a layout-branch swap around the display),
 * so no NavKey is ever re-registered against this read flipping, and the
 * subtitle tester is not registered on desktop (its push dead-ends in the
 * guard — see the VideoPlayer registration comment in DesktopNavScaffold),
 * so a full-screen route cannot sit below the top through real navigation.
 * Recorded at both sites; do not unify on either behavior without
 * re-deriving both halves.
 *
 * Pure: `null` (no stack for the current tab yet) and non-[Route] NavKeys
 * (nav3 keys are the base type; only our Route subclasses carry
 * isFullScreen) read `false`. The truth table is pinned by
 * DesktopLayoutPolicyTest.
 *
 * @param topRoute the current stack's last entry (already null-safe at the
 *   call sites, which read `backStack.lastOrNull()`).
 */
internal fun desktopTopRouteIsFullscreen(topRoute: NavKey?): Boolean =
    (topRoute as? Route)?.isFullScreen == true
