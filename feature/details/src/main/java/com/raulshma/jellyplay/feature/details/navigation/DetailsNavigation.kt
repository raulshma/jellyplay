package com.raulshma.jellyplay.feature.details.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.details.CollectionDetailScreen
import com.raulshma.jellyplay.feature.details.MediaDetailScreen
import com.raulshma.jellyplay.feature.details.PersonDetailScreen
import com.raulshma.jellyplay.feature.details.SeerrDetailScreen

fun EntryProviderScope<NavKey>.detailsSection(
    navigator: Navigator,
) {
    entry<Route.SeerrDetail> { key ->
        SeerrDetailScreen(
            tmdbId = key.tmdbId,
            mediaType = key.mediaType,
            onNavigate = { route -> navigator.navigate(route) },
            onBack = { navigator.goBack() }
        )
    }

    entry<Route.MediaDetail> { key ->
        MediaDetailScreen(
            itemId = key.itemId,
            onPlayClick = { itemId, mediaSourceId, startPosition, subtitleStreamIndex, audioStreamIndex ->
                navigator.navigate(
                    Route.VideoPlayer(
                        itemId = itemId,
                        mediaSourceId = mediaSourceId,
                        startPositionTicks = startPosition,
                        subtitleStreamIndex = subtitleStreamIndex,
                        audioStreamIndex = audioStreamIndex,
                    )
                )
            },
            onAudioClick = { itemId ->
                navigator.navigate(Route.AudioPlayer(itemId))
            },
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
            onPersonClick = { personId -> navigator.navigate(Route.PersonDetail(personId)) },
            onNavigateToSeries = { seriesId -> navigator.navigate(Route.MediaDetail(seriesId)) },
            onNavigate = { route -> navigator.navigate(route) },
            onEditClick = { itemId -> navigator.navigate(Route.MetadataEditor(itemId)) },
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.PersonDetail> { key ->
        PersonDetailScreen(
            personId = key.personId,
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.CollectionDetail> { key ->
        CollectionDetailScreen(
            collectionId = key.collectionId,
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
            onBack = { navigator.goBack() },
        )
    }
}
