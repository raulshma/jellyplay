package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-cutting shell services the shell host (MainActivity) injects and
 * hands to [com.raulshma.jellyplay.navigation.JellyPlayApp] as one bundle,
 * instead of five parameters that always travel together down to
 * MainContent. Pure parameter aggregation — each service stays owned by its
 * provider; the ViewModel-owned signals stay on [com.raulshma.jellyplay.MainViewModel].
 *
 * Every field is a lazy provider, not just
 * [audioPlaybackManagerLazy]. The former eager fields forced MainActivity's
 * onCreate to construct UserMessageBus, NetworkMonitor (whose constructor
 * registers a connectivity callback) and the remote-control objects on the
 * critical path just to bundle them here; the consumers in JellyPlayApp
 * resolve each provider at its first real use — the bus and the network
 * status flow inside their composition branches, the remote-control pair
 * inside their post-frame collection effects — so none of that construction
 * gates onCreate (and the auth/onboarding branches skip NetworkMonitor and
 * the remote-control objects entirely, the same deferral
 * [audioPlaybackManagerLazy] always had for the playback engine).
 *
 * @param keyDispatcher the activity's key-event synthesis seam:
 *   feeds one Android keycode through the activity's key dispatch (down +
 *   up) and reports whether anything consumed it. The remote navigation
 *   ladder (MoveFocus/InvokeSelect/OpenContextMenu) drives Compose's
 *   existing D-pad handling through it — no focus plumbing of our own.
 *   Eager (not lazy): it closes over the activity's window, which exists
 *   for the whole of onCreate.
 */
class ShellInfra(
    val userMessageBusLazy: Lazy<UserMessageBus>,
    val networkStatusLazy: Lazy<StateFlow<NetworkStatus>>,
    val audioPlaybackManagerLazy: Lazy<AudioPlaybackManager>,
    val remoteNavigationBridgeLazy: Lazy<RemoteNavigationBridge>,
    val remoteControlReceiverLazy: Lazy<RemoteControlReceiver>,
    val keyDispatcher: (Int) -> Boolean,
)
