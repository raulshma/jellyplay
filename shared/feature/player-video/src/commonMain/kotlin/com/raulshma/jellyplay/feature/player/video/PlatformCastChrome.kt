package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope

/**
 * Cast-chrome seam for the commonMain [VideoPlayerScreen] (wave 9A): the
 * screen's cast UI reaches the legacy Android cast stack (discovery/connect
 * `CastManager`, the Context-bound disconnect, the `CastSessionEvent` flow)
 * through these members instead of androidMain ViewModel extensions. The
 * jvmMain actuals are inert — [platformCastManager] is null (the cast button
 * hides), disconnect and the session-event collector do nothing — matching
 * the desktop `NoOpCastManager`/`NoOpPlayerCastController` seams, so the
 * cast-connected branch of the screen is unreachable on desktop.
 */

/**
 * The cast discovery/connect surface for the controls' cast button, opaque so
 * the legacy Android type does not leak into common code. Android: the
 * Hilt-owned legacy `core:data` CastManager. Desktop: `null`.
 */
internal expect val VideoPlayerViewModel.platformCastManager: Any?

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
 * [com.raulshma.jellyplay.feature.player.video.components.CastButton];
 * desktop renders nothing (never called — [platformCastManager] is null).
 */
@Composable
internal expect fun PlatformCastButton(castManager: Any?)
