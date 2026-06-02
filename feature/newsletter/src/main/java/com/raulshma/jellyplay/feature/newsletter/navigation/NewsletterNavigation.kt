package com.raulshma.jellyplay.feature.newsletter.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.newsletter.NewsletterScreen

fun EntryProviderScope<NavKey>.newsletterSection(navigator: Navigator) {
    entry<Route.Newsletter> {
        NewsletterScreen(
            onBack = { navigator.goBack() },
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
            onPlayClick = { itemId, mediaSourceId, startPosition ->
                navigator.navigate(Route.VideoPlayer(itemId, mediaSourceId, startPosition))
            },
        )
    }
}
