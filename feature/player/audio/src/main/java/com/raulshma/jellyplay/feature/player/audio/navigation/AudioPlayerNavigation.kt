package com.raulshma.jellyplay.feature.player.audio.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.player.audio.AmbientScreen
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerScreen

fun EntryProviderScope<NavKey>.audioPlayerSection(
    navigator: Navigator,
) {
    entry<Route.AudioPlayer> { key ->
        val animatedVisibilityScope = LocalNavAnimatedContentScope.current
        CompositionLocalProvider(
            LocalAnimatedVisibilityScope provides animatedVisibilityScope
        ) {
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
                onArtistClick = { artistId ->
                    navigator.navigate(Route.ArtistDetail(artistId = artistId))
                },
            )
        }
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
