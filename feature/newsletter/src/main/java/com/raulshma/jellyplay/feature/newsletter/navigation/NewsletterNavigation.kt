package com.raulshma.jellyplay.feature.newsletter.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.newsletter.NewsletterScreen
import com.raulshma.jellyplay.feature.newsletter.NewsletterSectionListScreen

fun EntryProviderScope<NavKey>.newsletterSection(navigator: Navigator) {
    entry<Route.Newsletter> {
        NewsletterScreen(
            onBack = { navigator.goBack() },
            onItemClick = { item ->
                if (item.mediaType == MediaType.COLLECTION) {
                    navigator.navigate(Route.CollectionDetail(item.id))
                } else {
                    navigator.navigate(Route.MediaDetail(item.id))
                }
            },
            onPlayClick = { itemId, mediaSourceId, startPosition ->
                navigator.navigate(Route.VideoPlayer(itemId, mediaSourceId, startPosition))
            },
            onViewAllFreshPicks = {
                navigator.navigate(Route.NewsletterSectionList("FRESH_PICKS"))
            },
        )
    }

    entry<Route.NewsletterSectionList> { key ->
        NewsletterSectionListScreen(
            sectionType = key.sectionType,
            onBack = { navigator.goBack() },
            onItemClick = { item ->
                if (item.mediaType == MediaType.COLLECTION) {
                    navigator.navigate(Route.CollectionDetail(item.id))
                } else {
                    navigator.navigate(Route.MediaDetail(item.id))
                }
            },
        )
    }
}
