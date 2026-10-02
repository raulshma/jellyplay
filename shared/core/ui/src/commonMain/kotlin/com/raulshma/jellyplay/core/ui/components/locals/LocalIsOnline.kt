package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * CompositionLocal that provides a [StateFlow] of [NetworkStatus]
 * representing the device's current connectivity state.
 *
 * Provided at the app level in [JellyPlayApp].
 */
val LocalNetworkStatus: ProvidableCompositionLocal<StateFlow<NetworkStatus>> =
    staticCompositionLocalOf { error("LocalNetworkStatus not provided") }

/**
 * CompositionLocal that provides a [StateFlow] of [ServerHealth]
 * representing the health status of the connected Jellyfin server.
 *
 * Provided at the app level in [JellyPlayApp].
 */
val LocalServerHealth: ProvidableCompositionLocal<StateFlow<ServerHealth>> =
    staticCompositionLocalOf { error("LocalServerHealth not provided") }

/**
 * Holder for the "Surprise Me" launcher-shortcut signal
 * [armed] flips to `true` when the shortcut fires; the Home hero controller
 * observes it and calls [consume] once it has acted, so re-mounting Home
 * (e.g. back-and-forth navigation) doesn't re-trigger the surprise pick.
 */
@androidx.compose.runtime.Immutable
class SurpriseLaunchController(
    val armed: StateFlow<Boolean>,
    val consume: () -> Unit,
)

/**
 * The inert default for [LocalSurpriseOnLaunch]: never armed, consuming
 * nothing. A shell that forgets to provide the local gets inert "no surprise
 * launch" behavior, not a crash — the composition-locals contract for
 * shell-supplied values whose readers (HomeHeroController) read
 * unconditionally. Desktop rides this default (its in-app "Surprise Me" path
 * is the surpriseRequests flow parameter and never touches the local);
 * Android's JellyPlayApp provides the real armed controller.
 */
private val inertSurpriseLaunchController = SurpriseLaunchController(
    armed = MutableStateFlow(false),
    consume = {},
)

val LocalSurpriseOnLaunch: ProvidableCompositionLocal<SurpriseLaunchController> =
    staticCompositionLocalOf { inertSurpriseLaunchController }
