package com.raulshma.jellyplay.feature.subtitle.tester.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.subtitle.tester.SubtitleTesterScreen

fun EntryProviderScope<NavKey>.subtitleTesterSection(
    navigator: Navigator,
) {
    entry<Route.SubtitleTester> {
        SubtitleTesterScreen(onBack = { navigator.goBack() })
    }
}
