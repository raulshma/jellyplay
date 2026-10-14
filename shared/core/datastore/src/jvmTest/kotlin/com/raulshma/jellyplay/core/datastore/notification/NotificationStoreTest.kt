package com.raulshma.jellyplay.core.datastore.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SeriesNotificationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Exercises the notification + newsletter preference store: defaults, the
 * nested [com.raulshma.jellyplay.core.model.NotificationPreferences] aggregate
 * update, JSON round-trips for the library configs / newsletter sections, and
 * the migrated newsletter keys.
 */
class NotificationStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: NotificationStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
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
        assertFalse(np.newEpisodesEnabled)
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
                newEpisodesEnabled = true,
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
        assertTrue(np.newEpisodesEnabled)
    }

    // ── D12: the per-series configs ─────────────────────────────────────

    @Test
    fun `seriesConfigs default to empty`() = runTest {
        assertTrue(store.notification.first().notificationPreferences.seriesConfigs.isEmpty())
    }

    @Test
    fun `setSeriesNotificationEnabled round-trips - disable removes the entry`() = runTest {
        store.setSeriesNotificationEnabled("series-1", enabled = true)
        val enabled = store.notification.first().notificationPreferences.seriesConfigs
        assertEquals(mapOf("series-1" to SeriesNotificationConfig(enabled = true)), enabled)

        store.setSeriesNotificationEnabled("series-1", enabled = false)
        assertTrue(store.notification.first().notificationPreferences.seriesConfigs.isEmpty())
    }

    @Test
    fun `disable removes the entry - re-enable starts a fresh baseline`() = runTest {
        store.updateSeriesNotificationConfigs {
            it + ("series-1" to SeriesNotificationConfig(lastNotifiedEpisodeId = "ep-9"))
        }
        // Disable then re-enable: the entry is removed on disable...
        store.setSeriesNotificationEnabled("series-1", enabled = false)
        assertTrue(store.notification.first().notificationPreferences.seriesConfigs.isEmpty())
        // ...so re-enable starts fresh — the assertion pins the REMOVE (not
        // disable-in-place) semantics: a re-enabled series re-baselines on
        // the worker's next check instead of resuming a stale id.
        store.setSeriesNotificationEnabled("series-1", enabled = true)
        val config = store.notification.first().notificationPreferences.seriesConfigs.getValue("series-1")
        assertEquals(SeriesNotificationConfig(enabled = true), config)
    }

    @Test
    fun `opt-in re-enables a surviving disabled husk and keeps its baseline`() = runTest {
        // An older config shape may leave an enabled=false entry behind; the
        // opt-in command must flip it to enabled while preserving the
        // lastNotifiedEpisodeId baseline (per the store's KDoc).
        store.updateSeriesNotificationConfigs {
            it + ("series-1" to SeriesNotificationConfig(enabled = false, lastNotifiedEpisodeId = "ep-9"))
        }
        store.setSeriesNotificationEnabled("series-1", enabled = true)

        val config = store.notification.first().notificationPreferences.seriesConfigs.getValue("series-1")
        assertTrue(config.enabled)
        assertEquals("ep-9", config.lastNotifiedEpisodeId)
    }

    @Test
    fun `updateSeriesNotificationConfigs preserves sibling entries`() = runTest {
        store.setSeriesNotificationEnabled("series-1", enabled = true)
        store.setSeriesNotificationEnabled("series-2", enabled = true)
        store.updateSeriesNotificationConfigs { configs ->
            configs + ("series-1" to configs.getValue("series-1").copy(lastNotifiedEpisodeId = "ep-3"))
        }

        val configs = store.notification.first().notificationPreferences.seriesConfigs
        assertEquals("ep-3", configs.getValue("series-1").lastNotifiedEpisodeId)
        assertTrue(configs.getValue("series-2").enabled)
    }

    @Test
    fun `clearSeriesNotificationConfigs removes every entry`() = runTest {
        store.setSeriesNotificationEnabled("series-1", enabled = true)
        store.setSeriesNotificationEnabled("series-2", enabled = true)
        store.clearSeriesNotificationConfigs()
        assertTrue(store.notification.first().notificationPreferences.seriesConfigs.isEmpty())
    }

    @Test
    fun `aggregate update round-trips seriesConfigs and restore writes them back`() = runTest {
        val configs = mapOf(
            "series-1" to SeriesNotificationConfig(enabled = true, lastNotifiedEpisodeId = "ep-7", lastCheckAt = 123L),
        )
        store.updateNotificationPreferences { it.copy(seriesConfigs = configs) }
        assertEquals(configs, store.notification.first().notificationPreferences.seriesConfigs)

        // The backup slice is faithful: a full restore reproduces the configs.
        val slice = store.notification.first()
        store.updateNotificationPreferences { it.copy(seriesConfigs = emptyMap()) }
        store.restore(slice)
        assertEquals(configs, store.notification.first().notificationPreferences.seriesConfigs)
    }

    @Test
    fun `seriesConfigs key participates in the NOTIFICATIONS reset category`() {
        val keys = store.resetKeysFor(PreferenceResetCategory.NOTIFICATIONS)
        val seriesKey = NotificationStore.Keys.NOTIFICATIONS_SERIES_CONFIGS
        assertTrue(keys.any { it.name == seriesKey.name })
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
