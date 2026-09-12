package com.raulshma.jellyplay.feature.book.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.book.BookReaderScreen

fun EntryProviderScope<NavKey>.bookReaderSection(
    navigator: Navigator,
) {
    entry<Route.BookReader> { key ->
        val animatedVisibilityScope = LocalNavAnimatedContentScope.current
        CompositionLocalProvider(
            LocalAnimatedVisibilityScope provides animatedVisibilityScope
        ) {
            BookReaderScreen(
                itemId = key.itemId,
                onBack = { navigator.goBack() },
            )
        }
    }
}
