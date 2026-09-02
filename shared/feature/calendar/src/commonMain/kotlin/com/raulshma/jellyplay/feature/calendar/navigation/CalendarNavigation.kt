package com.raulshma.jellyplay.feature.calendar.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.calendar.UpcomingCalendarScreen

fun EntryProviderScope<NavKey>.calendarSection(
    navigator: Navigator,
) {
    entry<Route.UpcomingCalendar> {
        UpcomingCalendarScreen(
            onBack = { navigator.goBack() },
            onOpenArrSettings = { navigator.navigate(Route.ArrSettings()) },
            onItemClick = { tmdbId, mediaType ->
                navigator.navigate(Route.SeerrDetail(tmdbId, mediaType))
            },
        )
    }
}
