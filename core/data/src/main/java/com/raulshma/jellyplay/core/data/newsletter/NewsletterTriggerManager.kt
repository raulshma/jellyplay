package com.raulshma.jellyplay.core.data.newsletter

import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsletterTriggerManager @Inject constructor(
    private val preferencesStore: UserPreferencesStore,
) {
    fun shouldShowBanner(): Flow<Boolean> = preferencesStore.preferences.map { prefs ->
        if (!prefs.newsletterEnabled) return@map false

        val configuredDay = dayOfWeekFromPref(prefs.newsletterDayOfWeek)
        val today = LocalDate.now().dayOfWeek
        if (today != configuredDay) return@map false

        if (prefs.newsletterLastViewedMs <= 0L) return@map true

        val lastViewedDate = java.time.Instant
            .ofEpochMilli(prefs.newsletterLastViewedMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val startOfToday = LocalDate.now()
        lastViewedDate < startOfToday
    }

    private fun dayOfWeekFromPref(value: Int): DayOfWeek = when (value) {
        1 -> DayOfWeek.MONDAY
        2 -> DayOfWeek.TUESDAY
        3 -> DayOfWeek.WEDNESDAY
        4 -> DayOfWeek.THURSDAY
        5 -> DayOfWeek.FRIDAY
        6 -> DayOfWeek.SATURDAY
        7 -> DayOfWeek.SUNDAY
        else -> DayOfWeek.SATURDAY
    }
}
