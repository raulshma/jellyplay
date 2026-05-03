package com.raulshma.jellyplay.feature.player.audio.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.player.audio.AmbientScreen
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerScreen

fun EntryProviderScope<NavKey>.audioPlayerSection(
    navigator: Navigator,
) {
    entry<Route.AudioPlayer> { key ->
        AudioPlayerScreen(
            itemId = key.itemId,
            onBack = { navigator.goBack() },
            onAmbientClick = { imageUrl, title, artist ->
                navigator.navigate(
                    Route.Ambient(
                        imageUrl = imageUrl,
                        title = title,
                        artist = artist,
                    )
                )
            },
        )
    }
    entry<Route.Ambient> { key ->
        AmbientScreen(
            imageUrl = key.imageUrl,
            title = key.title,
            artist = key.artist,
            onTap = { navigator.goBack() },
        )
    }
}
