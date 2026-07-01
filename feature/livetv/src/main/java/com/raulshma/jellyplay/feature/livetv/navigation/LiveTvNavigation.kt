package com.raulshma.jellyplay.feature.livetv.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.livetv.channels.ChannelsScreen
import com.raulshma.jellyplay.feature.livetv.dvr.DvrScreen
import com.raulshma.jellyplay.feature.livetv.epg.EpgScreen

fun EntryProviderScope<NavKey>.liveTvSection(navigator: Navigator) {
    entry<Route.LiveTv> {
        ChannelsScreen(
            onChannelClick = { channelId, channelName ->
                navigator.navigate(Route.LiveTvChannelPlayer(channelId, channelName))
            },
            onGuideClick = {
                navigator.navigate(Route.LiveTvGuide)
            },
            onDvrClick = {
                navigator.navigate(Route.Dvr)
            },
        )
    }
    entry<Route.LiveTvGuide> {
        EpgScreen(
            onProgramClick = { program ->
                navigator.navigate(
                    Route.LiveTvChannelPlayer(program.channelId, program.name)
                )
            },
            onBack = { navigator.goBack() },
            // onRecordClick defaults to the EPG's local record-confirmation dialog
            // (EpgViewModel.requestRecord), so recording is handled in-place instead
            // of navigating away to the DVR list.
        )
    }
    entry<Route.Dvr> {
        DvrScreen(
            onBack = { navigator.goBack() },
        )
    }
}
