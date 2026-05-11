package com.raulshma.jellyplay.feature.player.video.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.player.video.VideoPlayerScreen

fun EntryProviderScope<NavKey>.videoPlayerSection(
    navigator: Navigator,
    onEnterPip: () -> Unit = {},
    onEnterMiniMode: () -> Unit = {},
) {
    entry<Route.VideoPlayer> { key ->
        VideoPlayerScreen(
            itemId = key.itemId,
            mediaSourceId = key.mediaSourceId,
            startPositionTicks = key.startPositionTicks,
            subtitleStreamIndex = key.subtitleStreamIndex,
            audioStreamIndex = key.audioStreamIndex,
            onBack = { navigator.goBack() },
            onEnterPip = onEnterPip,
            onEnterMiniMode = onEnterMiniMode,
        )
    }
    entry<Route.LiveTvChannelPlayer> { key ->
        VideoPlayerScreen(
            itemId = key.channelId,
            mediaSourceId = null,
            startPositionTicks = 0L,
            onBack = { navigator.goBack() },
            onEnterPip = onEnterPip,
            onEnterMiniMode = onEnterMiniMode,
        )
    }
}
