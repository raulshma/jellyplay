package com.raulshma.jellyplay.feature.shell.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import com.raulshma.jellyplay.feature.auth.navigation.authSection

/**
 * The signed-out half of the shell session gate, SHARED by both shells —
 * the former hand-copies (Android's inline AuthContent in JellyPlayApp and
 * apps/desktop's DesktopSignedOutAuthHost) collapsed into one composable
 * beside the rest of the shared shell wiring ([appSections],
 * [ShellHostHooks]). One top-level route, [Route.ServerList] — the seed both
 * shells ran identically — with authSection's five entries
 * (ServerList/AddServer/Login/QuickConnect/UserSelection) stacked on it.
 *
 * The seed is deliberately ServerList, NOT a Login prefilled with the last
 * server address: landing on the picker is strictly stronger for the common
 * home-server case (server card → user card → in, no password), and the
 * prefill it replaced read an in-memory currentServer that is null in every
 * state this host composes in — boot after a signed-out shutdown
 * (restoreSession published nothing) and after a mid-session logout
 * (disconnect publishes the null pair).
 *
 * Success needs no callback wiring of its own: [onAuthenticated] defaults to
 * a no-op exactly as both shells passed — the caller's isAuthenticated
 * observer flips, its session gate swaps this host out for the signed-in
 * shell, and the flip disposes the stack. (A signed-in shell's own
 * authSection registration passes goBack instead, because there the section
 * only manages servers while the shell stays composed.) Sign-out is the
 * mirror image: the observer swaps this host back in, freshly seeded.
 *
 * [content] is the per-shell frame around the display: the default renders
 * the [NavDisplay] bare (Android), while desktop passes its Esc / Alt+Left
 * back-key [androidx.compose.ui.input.key.onPreviewKeyEvent] chrome, which
 * needs the [Navigator] and the live stack depth that live inside the host.
 * [savedStateConfiguration] stays a parameter because the saved-state
 * serializer seam is genuinely platform-specific (see
 * rememberNavigationState): Android must pass null (the reflection overload
 * is the device-pass fix), desktop passes its Route-serializer
 * configuration.
 *
 * Session policy is NOT shared here — ADR 0001: each shell's gate decides
 * when this host composes, and the host is UI seeding only.
 *
 * @param savedStateConfiguration `null` on Android; desktop passes its
 *   NavKey-polymorphic serializer configuration.
 * @param onAuthenticated empty in both shells today (see above).
 * @param content `(navigator, backStackDepth, display) -> Unit` — compose
 *   [NavDisplay]-provided `display` inside any per-shell chrome.
 */
@Composable
fun SignedOutAuthHost(
    modifier: Modifier = Modifier,
    savedStateConfiguration: SavedStateConfiguration? = null,
    onAuthenticated: () -> Unit = {},
    content: @Composable (navigator: Navigator, backStackDepth: () -> Int, display: @Composable () -> Unit) -> Unit =
        { _, _, display -> display() },
) {
    val navigationState = rememberNavigationState(
        startRoute = Route.ServerList,
        topLevelRoutes = setOf(Route.ServerList),
        savedStateConfiguration = savedStateConfiguration,
    )
    val navigator = Navigator(navigationState)
    val backStack = checkNotNull(navigationState.backStacks[Route.ServerList]) {
        "no signed-out back stack for ${Route.ServerList}"
    }
    // Explicit holder + decorator (the Android original's form): the no-arg
    // overload the desktop hand-copy used only resolves through the desktop
    // app's fork substitution, which this module's own compilations cannot
    // depend on.
    val saveableStateHolder = rememberSaveableStateHolder()

    content(navigator, { backStack.size }) {
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.goBack() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(saveableStateHolder),
            ),
            entryProvider = entryProvider {
                authSection(navigator, onAuthenticated)
            },
        )
    }
}
