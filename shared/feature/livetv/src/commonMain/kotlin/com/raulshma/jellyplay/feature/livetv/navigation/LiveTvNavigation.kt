package com.raulshma.jellyplay.feature.livetv.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.livetv.LiveTvScreen
import com.raulshma.jellyplay.feature.livetv.channeldetail.ChannelDetailScreen

/**
 * Registers the Live TV feature's navigation entries.
 *
 * Live TV is a single top-level destination ([Route.LiveTv]) rendered as a
 * 6-tab screen (Programs, Guide, Channels, Recordings, Schedule, Series) —
 * matching jellyfin-web's Live TV collection. The previous separate
 * [Route.LiveTvGuide] / [Route.Dvr] push destinations are subsumed by the
 * tabs and have been removed.
 */
fun EntryProviderScope<NavKey>.liveTvSection(navigator: Navigator) {
    entry<Route.LiveTv> {
        LiveTvScreen(
            onChannelClick = { channelId, channelName ->
                navigator.navigate(Route.LiveTvChannelPlayer(channelId, channelName))
            },
            onOpenChannelDetail = { channelId, channelName ->
                navigator.navigate(Route.ChannelDetail(channelId, channelName))
            },
            onRecordingClick = { recordingId ->
                navigator.navigate(Route.VideoPlayer(itemId = recordingId))
            },
        )
    }

    entry<Route.ChannelDetail> { key ->
        ChannelDetailScreen(
            channelId = key.channelId,
            channelName = key.channelName,
            onPlayChannel = {
                navigator.navigate(Route.LiveTvChannelPlayer(key.channelId, key.channelName))
            },
            onBack = { navigator.goBack() },
        )
    }
}
