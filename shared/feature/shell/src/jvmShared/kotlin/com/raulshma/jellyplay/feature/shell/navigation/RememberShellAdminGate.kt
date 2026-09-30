package com.raulshma.jellyplay.feature.shell.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember

/**
 * The admin hooks' lazy reads ([ShellHostHooks.isAdmin] /
 * [ShellHostHooks.isRefreshingAdmin]) as one value — ONE construction site
 * for the `remember(state) { { state.value } }` pair both shells used to
 * hand-copy beside the [rememberShellHost] factory. The refresh arm stays
 * per shell (each wraps its own session owner); only the reads are shared.
 *
 * Lazy `.value` reads on purpose — "Lazy reads — admin refreshes don't
 * rebuild the graph": admin refreshes re-compose entries through these reads
 * WITHOUT rebuilding the hooks or either section graph.
 *
 * REMEMBER-KEY DISCIPLINE (owned here, the same contract the rememberShellHost
 * factory's KDoc states): every parameter is a remember key; pass the
 * collected States as-is (`collectAsState` / `collectAsStateWithLifecycle`
 * return an identity-stable State per slot), so the gate rebuilds only when a
 * slot's State instance is swapped — never on a mere value change.
 */
class ShellAdminGate(
    val isAdmin: () -> Boolean,
    val isRefreshingAdmin: () -> Boolean,
)

/**
 * Builds the admin gate's lazy reads over the shell's collected admin states.
 *
 * @param isAdminState the collected admin flag (Android: MainViewModel state;
 *   desktop: the shared ShellSessionController's).
 * @param isRefreshingAdminState the collected refresh-in-flight flag — the
 *   state the admin route's guard renders so it never flashes access-denied.
 */
@Composable
fun rememberShellAdminGate(
    isAdminState: State<Boolean>,
    isRefreshingAdminState: State<Boolean>,
): ShellAdminGate = remember(isAdminState, isRefreshingAdminState) {
    ShellAdminGate(
        isAdmin = { isAdminState.value },
        isRefreshingAdmin = { isRefreshingAdminState.value },
    )
}
