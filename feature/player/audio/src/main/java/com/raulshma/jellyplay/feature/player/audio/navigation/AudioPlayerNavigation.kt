package com.raulshma.jellyplay.feature.player.audio.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerScreen

fun EntryProviderScope<NavKey>.audioPlayerSection(
    navigator: Navigator,
) {
    entry<Route.AudioPlayer> { key ->
        AudioPlayerScreen(
            itemId = key.itemId,
            onBack = { navigator.goBack() },
        )
    }
}
