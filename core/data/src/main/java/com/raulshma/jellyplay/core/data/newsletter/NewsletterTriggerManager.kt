package com.raulshma.jellyplay.core.data.newsletter

import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsletterTriggerManager @Inject constructor(
    private val notificationStore: NotificationStore,
) {
    fun shouldShowBanner(): Flow<Boolean> = notificationStore.notification.map { prefs ->
        if (!prefs.newsletterEnabled) return@map false

        val configuredDay = dayOfWeekFromPref(prefs.newsletterDayOfWeek)
        val today = LocalDate.now()
        val todayDow = today.dayOfWeek

        // The configured weekday is the *primary* trigger, but if the user never
        // opened the app that day (or opened it before the digest was ready and
        // hasn't viewed this week's issue), surface it on the next launch instead
        // of silently dropping it. The issue stays "current" for a full week —
        // from one configured weekday up to (but not including) the next — so it
        // must remain due on any day in that window, including the Mon–Thu after
        // a Friday digest the user missed. The lastViewedDate < issueDate check
        // below is the sole gate for whether this cycle's issue has been seen.
        if (prefs.newsletterLastViewedMs <= 0L) return@map true

        val lastViewedDate = java.time.Instant
            .ofEpochMilli(prefs.newsletterLastViewedMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        // Show again only if the user hasn't viewed an issue since the most recent
        // configured weekday. Compute this week's issue date: if today is on/after
        // the configured day, the issue date is this week's configured day; for a
        // Sunday wrap-around the issue is the prior week's day.
        val issueDate = if (todayDow.value >= configuredDay.value) {
            today.minusDays((todayDow.value - configuredDay.value).toLong())
        } else {
            // todayDow < configuredDay.value only happens at the Sunday wrap branch
            today.minusDays((todayDow.value + 7 - configuredDay.value).toLong())
        }

        lastViewedDate < issueDate
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
