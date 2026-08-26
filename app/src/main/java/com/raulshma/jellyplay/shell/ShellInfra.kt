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
 * [audioPlaybackManagerLazy] is deliberately a lazy provider: callers resolve
 * it with `get()` only once the user is authenticated, so the playback engine
 * is never built for auth/onboarding-only sessions.
 */
class ShellInfra(
    val userMessageBus: UserMessageBus,
    val networkStatus: StateFlow<NetworkStatus>,
    val audioPlaybackManagerLazy: Lazy<AudioPlaybackManager>,
    val remoteNavigationBridge: RemoteNavigationBridge,
    val remoteControlReceiver: RemoteControlReceiver,
)
