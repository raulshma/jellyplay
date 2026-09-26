package com.raulshma.jellyplay.core.data.newsletter

import com.raulshma.jellyplay.core.data.testutil.FakeTimeSource
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [NewsletterTriggerManager.shouldShowBanner]'s weekday-window
 * logic. D3 routed the manager's calendar read through the injected
 * TimeSource, so the suite pins a FIXED today (Wednesday 2026-01-07) via
 * FakeTimeSource and derives every expectation from it — the *relative*
 * contract, now deterministic rather than machine-clock dependent:
 *
 *  - the newsletter toggle is the master switch;
 *  - a never-viewed state (lastViewedMs <= 0) is always due;
 *  - the issue stays "due" from one configured weekday up to (but not
 *    including) the next, i.e. until the user has viewed an issue dated on or
 *    after the most recent configured weekday (issueDate).
 */
class NewsletterTriggerManagerTest {

    private val notificationStore: NotificationStore = mockk()
    private val prefs = MutableStateFlow(NotificationSlice())

    /** Pinned wall-clock today for every test (a Wednesday). */
    private val today: LocalDate = LocalDate.of(2026, 1, 7)
    private val timeSource = FakeTimeSource(todayDate = today)

    private lateinit var manager: NewsletterTriggerManager

    @BeforeTest
    fun setup() {
        every { notificationStore.notification } returns prefs
        manager = NewsletterTriggerManager(notificationStore, timeSource)
    }

    private fun viewedAt(localDate: LocalDate): Long =
        localDate.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private suspend fun bannerShown(): Boolean = manager.shouldShowBanner().first()

    private fun withPrefs(enabled: Boolean, dayOfWeek: Int, lastViewedMs: Long) {
        prefs.value = NotificationSlice(
            newsletterEnabled = enabled,
            newsletterDayOfWeek = dayOfWeek,
            newsletterLastViewedMs = lastViewedMs,
        )
    }

    @Test
    fun `disabled newsletter never shows the banner`() = runTest {
        withPrefs(enabled = false, dayOfWeek = today.dayOfWeek.value, lastViewedMs = 0L)

        assertFalse(bannerShown())
    }

    @Test
    fun `never viewed - banner is due`() = runTest {
        withPrefs(enabled = true, dayOfWeek = today.dayOfWeek.value, lastViewedMs = 0L)

        assertTrue(bannerShown())
    }

    @Test
    fun `viewed on the issue day - banner not due`() = runTest {
        // Configured day == today → this week's issue is today; a same-day
        // view already consumed it (lastViewedDate == issueDate is not "before").
        withPrefs(enabled = true, dayOfWeek = today.dayOfWeek.value, lastViewedMs = viewedAt(today))

        assertFalse(bannerShown())
    }

    @Test
    fun `viewed the day before the issue - banner still due`() = runTest {
        // The user opened the app before this week's digest was ready.
        withPrefs(enabled = true, dayOfWeek = today.dayOfWeek.value, lastViewedMs = viewedAt(today.minusDays(1)))

        assertTrue(bannerShown())
    }

    @Test
    fun `missed a full week - banner due again on launch`() = runTest {
        withPrefs(enabled = true, dayOfWeek = today.dayOfWeek.value, lastViewedMs = viewedAt(today.minusDays(8)))

        assertTrue(bannerShown())
    }

    @Test
    fun `viewed this week after last week's issue - banner not due`() = runTest {
        // Configured day == today, viewed 3 days ago: the current issue's
        // window started today, so a pre-issue view of *this cycle* is older
        // than the issue only if it precedes it — 3 days ago is before today,
        // so it IS due…
        withPrefs(enabled = true, dayOfWeek = today.dayOfWeek.value, lastViewedMs = viewedAt(today.minusDays(3)))

        assertTrue(bannerShown())
    }

    @Test
    fun `configured day tomorrow - issue is six days back and consumed views silence it`() = runTest {
        // todayDow < configuredDow ⇒ the issue date is today - 6 (the wrap
        // branch). A view on/before the issue day is fresh; an older one is not.
        val configuredDay = today.plusDays(1).dayOfWeek.value

        withPrefs(enabled = true, dayOfWeek = configuredDay, lastViewedMs = viewedAt(today.minusDays(7)))
        assertTrue(bannerShown(), "a view older than the issue date must stay due")

        withPrefs(enabled = true, dayOfWeek = configuredDay, lastViewedMs = viewedAt(today.minusDays(6)))
        assertFalse(bannerShown(), "a view on the issue day consumed it")

        withPrefs(enabled = true, dayOfWeek = configuredDay, lastViewedMs = viewedAt(today))
        assertFalse(bannerShown(), "a view today is after the issue date")
    }

    @Test
    fun `an out-of-range day-of-week pref falls back to Saturday`() = runTest {
        // dayOfWeekFromPref maps any unknown int to SATURDAY. Derive the
        // expectation from the actual calendar rather than assuming today is
        // not Saturday: with configured = Saturday the issue date is the most
        // recent Saturday, and a view strictly before it keeps the banner due.
        val lastSaturday = today.minusDays(((today.dayOfWeek.value + 1) % 7).toLong())

        withPrefs(enabled = true, dayOfWeek = 0, lastViewedMs = viewedAt(lastSaturday.minusDays(1)))
        assertTrue(bannerShown())

        withPrefs(enabled = true, dayOfWeek = 99, lastViewedMs = viewedAt(today))
        assertFalse(bannerShown())
    }
}
