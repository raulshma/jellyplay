package com.raulshma.jellyplay.core.notification.dispatcher

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.notification.channel.NotificationChannelManager
import com.raulshma.jellyplay.core.notification.receiver.NotificationActionReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins [NotificationDispatcher]'s dispatch behaviour against the real (shadowed)
 * [NotificationManager]:
 *
 * - An empty new-items map and disabled notifications are complete no-ops.
 * - A single library posts one notification per item plus a per-library group
 *   summary; item notifications carry the item name as their title, a
 *   mark-seen action wired to [NotificationActionReceiver] with the item's
 *   identity triple, and the summary's mark-all-seen action cancels every
 *   child id + the summary id.
 * - With two or more libraries an additional global summary (id 5000) is
 *   posted whose mark-all-seen covers both libraries.
 * - An active system DND (respectSystemDnd on, policy granted, filter ≠ ALL)
 *   suppresses everything.
 * - Stale per-library channels are deleted after a dispatch that no longer
 *   covers their library.
 * - With the opt-in new-episodes preference on, EPISODE items reroute to the
 *   fixed "New episodes" channel with "Series S2 · E4" framing; movies and
 *   the per-library summary never ride that channel, and pref-off behaviour
 *   is byte-identical to the legacy path.
 *
 * This module's unit tests do not bundle its merged android resources, so the
 * test application hands out a stub [Resources] answering every string lookup
 * with a canned value (same pattern as NotificationChannelManagerTest) —
 * resource *content* is irrelevant to these invariants.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = NotificationDispatcherDispatchTest.StubStringsApplication::class)
class NotificationDispatcherDispatchTest {

    class StubStringsApplication : Application() {
        private val stubResources = object : Resources(
            Resources.getSystem().assets,
            android.util.DisplayMetrics(),
            Configuration(),
        ) {
            override fun getString(resId: Int): String = "res:$resId"
            override fun getString(resId: Int, vararg formatArgs: Any?): String = "res:$resId"
        }

        override fun getResources(): Resources = stubResources
    }

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var dispatcher: NotificationDispatcher

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        dispatcher = NotificationDispatcher(
            context = context,
            channelManager = NotificationChannelManager(context),
        )
    }

    private fun item(id: String, name: String) = MediaItem(id = id, name = name, mediaType = MediaType.MOVIE)

    private fun episode(
        id: String,
        name: String,
        seriesName: String? = "Series",
        seasonNumber: Int? = 2,
        episodeNumber: Int? = 4,
    ) = MediaItem(
        id = id,
        name = name,
        mediaType = MediaType.EPISODE,
        seriesName = seriesName,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
    )

    private fun libraryFolder(id: String) = LibraryFolder(id = id, name = "Library $id")

    private fun postedIds(): Set<Int> =
        shadowOf(notificationManager).activeNotifications.map { it.id }.toSet()

    /**
     * The routed channel of a posted notification — the accessor form of
     * `Notification.getChannelId()` (the legacy public `channel` field is
     * absent from this module's androidHostTest classpath).
     */
    private fun postedChannelId(notification: Notification): String? = notification.channelId

    // ── no-op gates ──────────────────────────────────────────────────────

    @Test
    fun `an empty map posts nothing`() {
        dispatcher.dispatch(emptyMap(), NotificationPreferences())

        assertEquals(0, shadowOf(notificationManager).size())
    }

    @Test
    fun `disabled notifications post nothing`() {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        dispatcher.dispatch(
            mapOf(libraryFolder("lib-1") to listOf(item("i1", "Movie One"))),
            NotificationPreferences(),
        )

        assertEquals(0, shadowOf(notificationManager).size())
    }

    @Test
    fun `an active system DND with respectSystemDnd suppresses the dispatch`() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

        dispatcher.dispatch(
            mapOf(libraryFolder("lib-1") to listOf(item("i1", "Movie One"))),
            NotificationPreferences(respectSystemDnd = true),
        )

        assertEquals(0, shadowOf(notificationManager).size())
    }

    // ── single library ───────────────────────────────────────────────────

    @Test
    fun `a single library posts one notification per item plus a group summary`() {
        dispatcher.dispatch(
            mapOf(libraryFolder("lib-1") to listOf(item("i1", "Movie One"), item("i2", "Movie Two"))),
            NotificationPreferences(),
        )

        val item0 = NotificationDispatcher.notificationIdFor("lib-1", 0)
        val item1 = NotificationDispatcher.notificationIdFor("lib-1", 1)
        val summary = NotificationDispatcher.notificationIdFor("lib-1", -1)
        assertEquals(setOf(item0, item1, summary), postedIds())

        val first = shadowOf(notificationManager).getNotification(item0)
        assertEquals("Movie One", first.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("new_media_lib-1", first.group)

        val groupSummary = shadowOf(notificationManager).getNotification(summary)
        assertEquals("new_media_lib-1", groupSummary.group)
        assertTrue(groupSummary.flags and Notification.FLAG_GROUP_SUMMARY != 0)
    }

    @Test
    fun `the mark-seen action carries the item identity triple to the receiver`() {
        dispatcher.dispatch(
            mapOf(libraryFolder("lib-1") to listOf(item("i1", "Movie One"))),
            NotificationPreferences(),
        )

        val notification = shadowOf(notificationManager)
            .getNotification(NotificationDispatcher.notificationIdFor("lib-1", 0))
        val markSeen = notification.actions.first { action ->
            shadowOf(action.actionIntent).savedIntent.action == NotificationActionReceiver.ACTION_MARK_SEEN
        }
        val intent = shadowOf(markSeen.actionIntent).savedIntent

        assertEquals("i1", intent.getStringExtra(NotificationActionReceiver.EXTRA_ITEM_ID))
        assertEquals("lib-1", intent.getStringExtra(NotificationActionReceiver.EXTRA_LIBRARY_ID))
        assertEquals("MOVIE", intent.getStringExtra(NotificationActionReceiver.EXTRA_MEDIA_TYPE))
    }

    @Test
    fun `the library summary's mark-all-seen cancels every child and the summary`() {
        dispatcher.dispatch(
            mapOf(libraryFolder("lib-1") to listOf(item("i1", "Movie One"), item("i2", "Movie Two"))),
            NotificationPreferences(),
        )

        val summaryId = NotificationDispatcher.notificationIdFor("lib-1", -1)
        val notification = shadowOf(notificationManager).getNotification(summaryId)
        val markAll = notification.actions.first { action ->
            shadowOf(action.actionIntent).savedIntent.action == NotificationActionReceiver.ACTION_MARK_ALL_SEEN
        }
        val intent = shadowOf(markAll.actionIntent).savedIntent

        assertEquals(
            listOf("i1", "i2"),
            intent.getStringArrayExtra(NotificationActionReceiver.EXTRA_ITEM_IDS)?.toList(),
        )
        assertEquals(
            listOf("lib-1", "lib-1"),
            intent.getStringArrayExtra(NotificationActionReceiver.EXTRA_LIBRARY_IDS)?.toList(),
        )
        val cancelIds = intent.getIntArrayExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_IDS)?.toList()
        assertEquals(
            listOf(
                NotificationDispatcher.notificationIdFor("lib-1", 0),
                NotificationDispatcher.notificationIdFor("lib-1", 1),
                summaryId,
            ),
            cancelIds,
        )
    }

    // ── multiple libraries ───────────────────────────────────────────────

    @Test
    fun `two libraries add a global summary whose mark-all covers everything`() {
        dispatcher.dispatch(
            mapOf(
                libraryFolder("lib-1") to listOf(item("i1", "Movie One")),
                libraryFolder("lib-2") to listOf(item("i2", "Movie Two")),
            ),
            NotificationPreferences(),
        )

        // 1 item + 1 summary per library, plus the global summary.
        assertEquals(5, shadowOf(notificationManager).size())
        assertNotNull(shadowOf(notificationManager).getNotification(GLOBAL_SUMMARY_ID))

        val notification = shadowOf(notificationManager).getNotification(GLOBAL_SUMMARY_ID)
        val markAll = notification.actions.first { action ->
            shadowOf(action.actionIntent).savedIntent.action == NotificationActionReceiver.ACTION_MARK_ALL_SEEN
        }
        val intent = shadowOf(markAll.actionIntent).savedIntent

        assertEquals(
            listOf("i1", "i2"),
            intent.getStringArrayExtra(NotificationActionReceiver.EXTRA_ITEM_IDS)?.toList(),
        )
        assertEquals(
            listOf("lib-1", "lib-2"),
            intent.getStringArrayExtra(NotificationActionReceiver.EXTRA_LIBRARY_IDS)?.toList(),
        )
        val cancelIds = intent.getIntArrayExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_IDS)?.toList()
        assertTrue(cancelIds!!.contains(GLOBAL_SUMMARY_ID))
        assertEquals(5, cancelIds.size)
    }

    // ── channel bookkeeping ──────────────────────────────────────────────

    @Test
    fun `stale library channels are deleted by a dispatch that no longer covers them`() {
        val channelManager = NotificationChannelManager(context)
        val prefs = NotificationPreferences()
        channelManager.ensureChannel("lib-old", "Old Library", prefs)

        dispatcher.dispatch(
            mapOf(libraryFolder("lib-1") to listOf(item("i1", "Movie One"))),
            prefs,
        )

        assertNull(notificationManager.getNotificationChannel(NotificationChannelManager.channelIdFor("lib-old")))
        assertNotNull(notificationManager.getNotificationChannel(NotificationChannelManager.channelIdFor("lib-1")))
    }

    // ── new-episodes routing (opt-in) ────────────────────────────────────

    @Test
    fun `episodes route to the new-episodes channel with series framing when the pref is on`() {
        dispatcher.dispatch(
            mapOf(libraryFolder("lib-1") to listOf(episode("e1", "Pilot"))),
            NotificationPreferences(newEpisodesEnabled = true),
        )

        val notification = shadowOf(notificationManager)
            .getNotification(NotificationDispatcher.notificationIdFor("lib-1", 0))
        assertEquals(NotificationChannelManager.CHANNEL_NEW_EPISODES, postedChannelId(notification))
        // "Series S2 · E4" framing title, episode name as text.
        assertEquals("Series S2 \u00B7 E4", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Pilot", notification.extras.getString(Notification.EXTRA_TEXT))
        // The dedicated channel itself is created by the dispatch…
        assertNotNull(notificationManager.getNotificationChannel(NotificationChannelManager.CHANNEL_NEW_EPISODES))
        // …and the mark-seen action keeps the item's identity triple.
        val markSeen = notification.actions.first { action ->
            shadowOf(action.actionIntent).savedIntent.action == NotificationActionReceiver.ACTION_MARK_SEEN
        }
        assertEquals(
            "EPISODE",
            shadowOf(markSeen.actionIntent).savedIntent.getStringExtra(NotificationActionReceiver.EXTRA_MEDIA_TYPE),
        )
    }

    @Test
    fun `episodes keep the per-library channel and framing when the pref is off`() {
        dispatcher.dispatch(
            mapOf(libraryFolder("lib-1") to listOf(episode("e1", "Pilot"))),
            NotificationPreferences(),
        )

        val notification = shadowOf(notificationManager)
            .getNotification(NotificationDispatcher.notificationIdFor("lib-1", 0))
        assertEquals("new_media_lib-1", postedChannelId(notification))
        assertEquals("Pilot", notification.extras.getString(Notification.EXTRA_TITLE))
        // Today's generic framing: media-type label subtext.
        assertEquals("Episode", notification.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `non-episode items and the summary never ride the new-episodes channel even when the pref is on`() {
        dispatcher.dispatch(
            mapOf(libraryFolder("lib-1") to listOf(item("i1", "Movie One"), episode("e1", "Pilot"))),
            NotificationPreferences(newEpisodesEnabled = true),
        )

        val movie = shadowOf(notificationManager)
            .getNotification(NotificationDispatcher.notificationIdFor("lib-1", 0))
        assertEquals("new_media_lib-1", postedChannelId(movie))
        assertEquals("Movie One", movie.extras.getString(Notification.EXTRA_TITLE))

        val summary = shadowOf(notificationManager)
            .getNotification(NotificationDispatcher.notificationIdFor("lib-1", -1))
        assertEquals("new_media_lib-1", postedChannelId(summary))
        assertTrue(summary.flags and Notification.FLAG_GROUP_SUMMARY != 0)

        // Both children stay grouped under the library group and covered by
        // the summary regardless of which channel they posted to.
        assertEquals("new_media_lib-1", movie.group)
        val episodeNotification = shadowOf(notificationManager)
            .getNotification(NotificationDispatcher.notificationIdFor("lib-1", 1))
        assertEquals("new_media_lib-1", episodeNotification.group)
    }

    private companion object {
        const val GLOBAL_SUMMARY_ID = 5000
    }
}
