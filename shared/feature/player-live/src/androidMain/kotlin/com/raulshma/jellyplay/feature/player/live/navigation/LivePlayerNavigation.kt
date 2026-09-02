package com.raulshma.jellyplay.feature.player.live.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.player.live.LivePlayerScreen

/**
 * Navigation entry for [Route.LiveTvChannelPlayer]. Renders
 * [LivePlayerScreen] — the dedicated Live TV player built around the
 * HLS via Media3 ExoPlayer. The previous routing
 * through `VideoPlayerScreen` (and its `isLive` plumbing) has been retired.
 *
 * The route data class [Route.LiveTvChannelPlayer] lives in `:core:ui`
 * because both `:feature:livetv` (which navigates to it from a channel tap)
 * and `:feature:player:live` (which renders it) depend on it.
 */
fun EntryProviderScope<NavKey>.livePlayerSection(
    navigator: Navigator,
) {
    entry<Route.LiveTvChannelPlayer> { key ->
        LivePlayerScreen(
            channelId = key.channelId,
            channelName = key.channelName,
            audioStreamIndex = key.audioStreamIndex,
            subtitleStreamIndex = key.subtitleStreamIndex,
            onBack = { navigator.goBack() },
        )
    }
}
