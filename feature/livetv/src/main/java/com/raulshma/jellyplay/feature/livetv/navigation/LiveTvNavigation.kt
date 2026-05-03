package com.raulshma.jellyplay.feature.livetv.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.livetv.channels.ChannelsScreen
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
        )
    }
}
