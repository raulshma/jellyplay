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
 * STA-12 (2026-09 perf audit): every field is a lazy provider, not just
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
 */
class ShellInfra(
    val userMessageBusLazy: Lazy<UserMessageBus>,
    val networkStatusLazy: Lazy<StateFlow<NetworkStatus>>,
    val audioPlaybackManagerLazy: Lazy<AudioPlaybackManager>,
    val remoteNavigationBridgeLazy: Lazy<RemoteNavigationBridge>,
    val remoteControlReceiverLazy: Lazy<RemoteControlReceiver>,
)
