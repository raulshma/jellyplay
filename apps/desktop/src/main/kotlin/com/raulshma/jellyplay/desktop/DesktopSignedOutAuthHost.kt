package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import com.raulshma.jellyplay.feature.auth.navigation.authSection
import org.koin.compose.koinInject

/**
 * The signed-out half of DesktopAppRoot's session gate (wave 19A): a compact
 * NavDisplay over the SHARED auth section, replacing the retired
 * DesktopSignInPane (which offered only server-URL + username + password and
 * cut everything else). One top-level route, [Route.ServerList] — the exact
 * seed of the Android shell's signed-out AuthContent (JellyPlayApp) — with
 * authSection's five entries (ServerList/AddServer/Login/QuickConnect/
 * UserSelection) stacked on it. This closes three of the v1 cut-list items:
 *  - QuickConnect — Route.Login's Quick Connect button → Route.QuickConnect,
 *    real polling over AuthRepository (the flow is jvmTest-covered in
 *    shared/feature/auth; nothing is duplicated here);
 *  - remembered-user picker — ServerList → a server → UserSelection, whose
 *    user cards run the passwordless AuthRepository.switchUser;
 *  - server-address alternates — Route.AddServer (SSDP discovery — plain UDP
 *    multicast on the desktop JVM behind the NoopDiscoveryMulticastGuard, so
 *    an empty scan degrades to the screen's retry row, never a crash — plus
 *    manual entry), and per-address add/remove/switch for a remembered server
 *    lives one sign-in away in the settings ServerManagement drill-in this
 *    app already registers.
 *
 * The seed is deliberately ServerList, NOT a Login prefilled with the last
 * server address (the old pane's prefill): that prefill read the API engine's
 * in-memory currentServer, which is null in every state this host composes in
 * — boot after a signed-out shutdown (restoreSession published nothing) and
 * after a mid-session logout (disconnect publishes the null pair) — so the
 * field it "preserved" was empty anyway. Landing on the picker is strictly
 * stronger for the common home-server case: server card → user card → in,
 * no password, and the health dot says up front whether the server answers.
 *
 * Success needs no callback wiring of its own: authSection's onAuthenticated
 * is a no-op here exactly as on Android — [AuthRepository.isAuthenticated]
 * flips, DesktopAppRoot's observer swaps this host out for DesktopNavScaffold,
 * and the flip disposes the stack. (The signed-in scaffold's own authSection
 * registration passes goBack instead, because there the section only manages
 * servers while the shell stays composed.) Sign-out is the mirror image: the
 * observer swaps this host back in, freshly seeded.
 *
 * Esc / Alt+Left pop the stack, refusing to pop below the ServerList root
 * (same rule as DesktopNavScaffold's tab roots): at the root the key event
 * falls through unconsumed — it deliberately neither quits the app nor
 * navigates; the window closes via the titlebar / tray Quit like everywhere
 * else in the shell.
 *
 * Still cut (the one remaining v1 item): self-signed-cert trust — needs
 * OkHttpConfig trust plumbing plus a client-rebuild semantics design, so a
 * private-server user still needs a CA-signed (or system-trusted) endpoint.
 *
 * The wave-13B session harness is unaffected: it performs the login through
 * AuthRepository itself and waits for the signed-in scaffold to appear.
 */
@Composable
internal fun DesktopSignedOutAuthHost(authRepository: AuthRepository = koinInject()) {
    val navigation = rememberNavigationState(
        startRoute = Route.ServerList,
        topLevelRoutes = setOf(Route.ServerList),
        savedStateConfiguration = desktopNavSavedStateConfiguration(),
    )
    val navigator = Navigator(navigation)
    val backStack = checkNotNull(navigation.backStacks[Route.ServerList]) {
        "no signed-out back stack for ${Route.ServerList}"
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isBack = event.key == Key.Escape ||
                    (event.key == Key.DirectionLeft && event.isAltPressed)
                if (isBack && backStack.size > 1) {
                    navigator.goBack()
                    true
                } else {
                    // Root-refusing pop: at the ServerList seed the event is
                    // not consumed (no quit-on-Esc convention in this shell).
                    false
                }
            },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.goBack() },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider {
                // Empty onAuthenticated: the isAuthenticated observer in
                // DesktopAppRoot owns the signed-out → scaffold swap (see
                // class KDoc).
                authSection(navigator) {}
            },
        )
    }
}
