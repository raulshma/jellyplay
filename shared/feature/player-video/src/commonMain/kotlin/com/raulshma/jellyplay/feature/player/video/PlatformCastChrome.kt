package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.feature.player.video.session.CastRouteButton
import kotlinx.coroutines.CoroutineScope

/**
 * Cast-chrome seam for the commonMain [VideoPlayerScreen]: the screen's cast
 * UI reaches the legacy Android cast stack (discovery/connect `CastManager`,
 * the Context-bound disconnect, the `CastSessionEvent` flow) through these
 * members instead of androidMain ViewModel extensions. The jvmMain actuals
 * are inert — [platformCastRouteButton] is null (the cast button hides),
 * disconnect and the session-event collector do nothing — matching the
 * desktop `NoOpCastManager`/`NoOpPlayerCastController` seams, so the
 * cast-connected branch of the screen is unreachable on desktop. The route
 * button reaches the chrome as the cast slice's typed [CastRouteButton]
 * projection (availability + click); the legacy manager never crosses.
 */

/**
 * The cast route button's projection for the controls' cast entry, built
 * over this VM's cast seam. Android: the projection over the legacy
 * discovery/connect manager. Desktop: `null` (the button hides).
 */
internal expect fun VideoPlayerViewModel.platformCastRouteButton(): CastRouteButton?

/**
 * Tear down the cast route from the companion dashboard's disconnect action.
 * Android also releases the transport (Context-bound legacy call); desktop
 * no-ops.
 */
@Composable
internal expect fun rememberCastDisconnect(viewModel: VideoPlayerViewModel): () -> Unit

/**
 * Collect the platform cast-session events for the screen's lifetime and
 * dispatch them into the cast slice (Connected → `castToDevice`,
 * Disconnected → `onCastDisconnected`). Android keeps the original collector
 * verbatim; desktop launches nothing (no cast stack).
 */
internal expect fun CoroutineScope.launchPlatformCastSessionEvents(viewModel: VideoPlayerViewModel)

/**
 * The cast button in the controls' action row. Android renders the real
 * [com.raulshma.jellyplay.feature.player.video.components.CastButton] over
 * the projection's platform implementation; desktop renders nothing (never
 * composed — [platformCastRouteButton] is null).
 */
@Composable
internal expect fun PlatformCastButton(routeButton: CastRouteButton)
