package com.raulshma.jellyplay.core.datastore.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the notification + newsletter preference store: defaults, the
 * nested [com.raulshma.jellyplay.core.model.NotificationPreferences] aggregate
 * update, JSON round-trips for the library configs / newsletter sections, and
 * the migrated newsletter keys.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: NotificationStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = NotificationStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.notification.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.notification.first()
        val np = slice.notificationPreferences
        assertFalse(np.enabled)
        assertEquals(CheckFrequency.EVERY_6_HOURS, np.checkFrequency)
        assertFalse(np.quietHoursEnabled)
        assertEquals(1380, np.quietHoursStart)
        assertEquals(420, np.quietHoursEnd)
        assertTrue(np.soundEnabled)
        assertTrue(np.vibrateEnabled)
        assertTrue(np.lightsEnabled)
        assertEquals(10, np.maxPerCheck)
        assertTrue(np.libraryConfigs.isEmpty())
        // Newsletter defaults
        assertTrue(slice.newsletterEnabled)
        assertEquals(7, slice.newsletterDayOfWeek)
        assertEquals(0L, slice.newsletterLastViewedMs)
        assertEquals(NewsletterSectionType.entries.toSet(), slice.enabledNewsletterSections)
        assertEquals(NewsletterSectionType.DEFAULT_ORDER, slice.newsletterSectionOrder)
    }

    @Test
    fun `updateNotificationPreferences round-trips the aggregate`() = runTest {
        store.updateNotificationPreferences { current ->
            current.copy(
                enabled = true,
                checkFrequency = CheckFrequency.EVERY_HOUR,
                quietHoursEnabled = true,
                quietHoursStart = 1320,
                quietHoursEnd = 480,
                soundEnabled = false,
                vibrateEnabled = false,
                lightsEnabled = false,
                maxPerCheck = 25,
                libraryConfigs = mapOf("library1" to LibraryNotificationConfig(enabled = false, mediaTypes = setOf("Movie"))),
            )
        }
        val np = store.notification.first().notificationPreferences
        assertTrue(np.enabled)
        assertEquals(CheckFrequency.EVERY_HOUR, np.checkFrequency)
        assertTrue(np.quietHoursEnabled)
        assertEquals(1320, np.quietHoursStart)
        assertEquals(480, np.quietHoursEnd)
        assertFalse(np.soundEnabled)
        assertFalse(np.vibrateEnabled)
        assertFalse(np.lightsEnabled)
        assertEquals(25, np.maxPerCheck)
        assertEquals(
            mapOf("library1" to LibraryNotificationConfig(enabled = false, mediaTypes = setOf("Movie"))),
            np.libraryConfigs,
        )
    }

    @Test
    fun `setNewsletterEnabled round-trips`() = runTest {
        store.setNewsletterEnabled(false)
        assertFalse(store.notification.first().newsletterEnabled)
    }

    @Test
    fun `setNewsletterDayOfWeek round-trips`() = runTest {
        store.setNewsletterDayOfWeek(3)
        assertEquals(3, store.notification.first().newsletterDayOfWeek)
    }

    @Test
    fun `setNewsletterLastViewed round-trips`() = runTest {
        store.setNewsletterLastViewed(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, store.notification.first().newsletterLastViewedMs)
    }

    @Test
    fun `setEnabledNewsletterSections round-trips`() = runTest {
        val sections = setOf(NewsletterSectionType.RECENTLY_ADDED, NewsletterSectionType.NEXT_UP)
        store.setEnabledNewsletterSections(sections)
        assertEquals(sections, store.notification.first().enabledNewsletterSections)
    }

    @Test
    fun `setNewsletterSectionOrder round-trips`() = runTest {
        val order = listOf(NewsletterSectionType.NEXT_UP, NewsletterSectionType.RECENTLY_ADDED)
        store.setNewsletterSectionOrder(order)
        assertEquals(order, store.notification.first().newsletterSectionOrder)
    }
}
