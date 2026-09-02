package com.raulshma.jellyplay.core.notification.channel

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.model.NotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Pins [NotificationChannelManager]'s channel-bookkeeping invariants against the
 * real (shadowed) [NotificationManager]:
 *
 * 1. `channelIdFor` always namespaces with [NotificationChannelManager.CHANNEL_PREFIX]
 *    and truncates the library id to 20 chars so oversized server ids cannot
 *    blow past the channel-id length budget.
 * 2. `ensureChannel` / `ensureSummaryChannel` are idempotent — calling them
 *    repeatedly for the same library must never create a duplicate channel
 *    (Android caps total channels per app).
 * 3. `deleteStaleChannels` deletes *only* channels carrying the
 *    `new_media_` prefix whose library is no longer in the valid set;
 *    unrelated channels (no prefix) and channels for valid libraries survive.
 *
 * Note: `new_media_summary` also carries the `new_media_` prefix, so the
 * current `deleteStaleChannels` filter removes it too when it is not in the
 * valid set — the test pins that actual behaviour (see the last test) rather
 * than an idealised one, since the summary channel is re-created by
 * `ensureSummaryChannel` on the next dispatch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = NotificationChannelManagerTest.StubStringsApplication::class)
class NotificationChannelManagerTest {

    /**
     * This module's unit tests do not bundle its merged android resources, so
     * `context.getString(R.string.…)` inside `ensureChannel` would throw
     * `Resources$NotFoundException`. The channel *names* are irrelevant to the
     * invariants pinned here (ids, importance, idempotence, deletion), so the
     * application hands out a stub [Resources] that answers every string
     * lookup with a canned value.
     */
    class StubStringsApplication : android.app.Application() {
        private val stubResources = object : android.content.res.Resources(
            android.content.res.Resources.getSystem().assets,
            android.util.DisplayMetrics(),
            android.content.res.Configuration(),
        ) {
            override fun getString(resId: Int): String = "res:$resId"
            override fun getString(resId: Int, vararg formatArgs: Any?): String = "res:$resId"
        }

        override fun getResources(): android.content.res.Resources = stubResources
    }

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var manager: NotificationChannelManager

    private val prefs = NotificationPreferences()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager = NotificationChannelManager(context)
    }

    private fun channelIds(): Set<String> =
        notificationManager.notificationChannels.map { it.id }.toSet()

    @Test
    fun `channelIdFor prefixes the library id`() {
        assertEquals(
            "new_media_abc123",
            NotificationChannelManager.channelIdFor("abc123"),
        )
    }

    @Test
    fun `channelIdFor truncates library ids longer than 20 characters`() {
        val longId = "0123456789abcdefghijEXTRA_SUFFIX"

        val channelId = NotificationChannelManager.channelIdFor(longId)

        // Exactly the first 20 chars survive; the prefix is intact.
        assertEquals("new_media_${longId.take(20)}", channelId)
        assertTrue(channelId.length <= NotificationChannelManager.CHANNEL_PREFIX.length + 20)
    }

    @Test
    fun `ensureChannel creates the channel once`() {
        manager.ensureChannel("lib1", "Movies", prefs)

        val channel = notificationManager.getNotificationChannel(
            NotificationChannelManager.channelIdFor("lib1"),
        )
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun `ensureChannel is idempotent - no duplicate channels`() {
        manager.ensureChannel("lib1", "Movies", prefs)
        // Different display name on the second call — the existing channel must
        // still not be replaced or duplicated.
        manager.ensureChannel("lib1", "Movies (renamed)", prefs)

        val matches = notificationManager.notificationChannels.filter {
            it.id == NotificationChannelManager.channelIdFor("lib1")
        }
        assertEquals(1, matches.size)
        assertEquals(1, notificationManager.notificationChannels.size)
    }

    @Test
    fun `ensureSummaryChannel creates and stays idempotent`() {
        manager.ensureSummaryChannel()
        manager.ensureSummaryChannel()

        val matches = notificationManager.notificationChannels.filter {
            it.id == NotificationChannelManager.CHANNEL_SUMMARY
        }
        assertEquals(1, matches.size)
    }

    @Test
    fun `ensureChannel creates distinct channels per library`() {
        manager.ensureChannel("lib1", "Movies", prefs)
        manager.ensureChannel("lib2", "Shows", prefs)

        val ids = channelIds()
        assertEquals(
            setOf(NotificationChannelManager.channelIdFor("lib1"), NotificationChannelManager.channelIdFor("lib2")),
            ids,
        )
    }

    @Test
    fun `deleteStaleChannels keeps channels for valid libraries and unprefixed channels`() {
        manager.ensureChannel("lib1", "Movies", prefs)
        manager.ensureChannel("lib2", "Shows", prefs)
        manager.ensureSummaryChannel()
        // An unrelated channel from another feature — never ours to delete.
        val foreign = android.app.NotificationChannel("other_feature", "Other", NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(foreign)

        // lib2 was removed server-side; only its channel may go.
        manager.deleteStaleChannels(validLibraryIds = setOf("lib1"))

        val ids = channelIds()
        assertTrue("valid library channel kept", ids.contains(NotificationChannelManager.channelIdFor("lib1")))
        assertTrue("unprefixed foreign channel kept", ids.contains("other_feature"))
        assertFalse("stale library channel removed", ids.contains(NotificationChannelManager.channelIdFor("lib2")))
    }

    @Test
    fun `deleteStaleChannels removes a prefixed channel whose id matches no valid library`() {
        // A leftover channel from a previous run with a now-unknown library id.
        val stale = android.app.NotificationChannel(
            NotificationChannelManager.CHANNEL_PREFIX + "deletedLibrary",
            "Old library",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        notificationManager.createNotificationChannel(stale)

        manager.deleteStaleChannels(validLibraryIds = emptySet())

        assertNull(notificationManager.getNotificationChannel(stale.id))
    }

    @Test
    fun `deleteStaleChannels with a valid library id longer than 20 chars matches the truncated channel id`() {
        val longId = "0123456789abcdefghijEXTRA_SUFFIX"
        manager.ensureChannel(longId, "Long", prefs)

        // The valid set carries the full library id; the deletion logic must
        // truncate it the same way `channelIdFor` does or the channel would be
        // wrongly treated as stale.
        manager.deleteStaleChannels(validLibraryIds = setOf(longId))

        assertNotNull(notificationManager.getNotificationChannel(NotificationChannelManager.channelIdFor(longId)))
    }

    /**
     * Current-behaviour pin: `new_media_summary` starts with `new_media_` and is
     * not a library channel id, so `deleteStaleChannels` deletes it whenever no
     * library is valid. The dispatcher re-creates the summary channel via
     * `ensureSummaryChannel()` on its next dispatch, which is why the app keeps
     * working — if the deletion order ever flips, this test fails loudly.
     */
    @Test
    fun `deleteStaleChannels currently also removes the prefixed summary channel`() {
        manager.ensureSummaryChannel()

        manager.deleteStaleChannels(validLibraryIds = emptySet())

        assertNull(notificationManager.getNotificationChannel(NotificationChannelManager.CHANNEL_SUMMARY))
    }

    @Test
    fun `deleteStaleChannels on an empty channel list is a no-op`() {
        // Must not throw (Robolectric backs this with the shadowed manager).
        manager.deleteStaleChannels(validLibraryIds = setOf("lib1"))
        assertEquals(0, notificationManager.notificationChannels.size)
    }
}
