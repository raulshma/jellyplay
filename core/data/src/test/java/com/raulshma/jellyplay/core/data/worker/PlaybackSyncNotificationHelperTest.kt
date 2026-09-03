package com.raulshma.jellyplay.core.data.worker

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.R
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
 * Pins the playback-sync notification the user sees while
 * [PlaybackSyncWorker] drains the offline outbox:
 *
 *  - The foreground card uses the **stable** [PlaybackSyncNotificationHelper.NOTIFICATION_ID]
 *    (47505) and — on U+ (SDK ≥ 34) — the DATA_SYNC foreground service type.
 *  - The `playback_sync` channel is created **once** with the pinned
 *    IMPORTANCE_LOW config (dedup via `getNotificationChannel`).
 *  - The body text tracks the pending count: the plural "N items queued"
 *    while work remains, the "Finishing…" string at zero.
 *  - `dismissNotification` cancels the same stable id.
 *
 * Real framework inflation (PendingIntents, NotificationManager, module
 * plurals) runs against Robolectric shadows, mirroring
 * `DownloadNotificationHelperTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackSyncNotificationHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // updateNotification skips posting when POST_NOTIFICATIONS is denied on
        // T+; grant it so assertions are about notification shape.
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun notificationManager() =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // ── Foreground info shape ─────────────────────────────────────────

    @Test
    fun `foreground info pins the stable id and the data-sync foreground service type on U plus`() {
        val foregroundInfo = PlaybackSyncNotificationHelper.createForegroundInfo(context, pendingCount = 3)

        assertEquals(PlaybackSyncNotificationHelper.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertEquals(47505, foregroundInfo.notificationId)
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            foregroundInfo.foregroundServiceType,
        )
    }

    @Test
    @Config(sdk = [28])
    fun `foreground info omits the foreground service type below U`() {
        val foregroundInfo = PlaybackSyncNotificationHelper.createForegroundInfo(context, pendingCount = 1)

        assertEquals(PlaybackSyncNotificationHelper.NOTIFICATION_ID, foregroundInfo.notificationId)
        assertEquals(0, foregroundInfo.foregroundServiceType)
    }

    @Test
    fun `foreground notification shows the sync title with queued-count text and is ongoing`() {
        val info = PlaybackSyncNotificationHelper.createForegroundInfo(context, pendingCount = 2)
        val notification = info.notification

        assertEquals(
            context.getString(R.string.data_sync_title),
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(
            context.resources.getQuantityString(R.plurals.data_sync_items_queued, 2, 2),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        // Indeterminate progress: the drain has no byte-level progress.
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        // Taps return to MainActivity.
        val contentIntent = shadowOf(notification.contentIntent).savedIntent
        assertEquals("com.raulshma.jellyplay.MainActivity", contentIntent.component?.className)
        assertEquals(context.packageName, contentIntent.component?.packageName)
    }

    // ── Channel dedup ─────────────────────────────────────────────────

    @Test
    fun `channel is created exactly once with the pinned low-importance config`() {
        PlaybackSyncNotificationHelper.createForegroundInfo(context, pendingCount = 1)
        PlaybackSyncNotificationHelper.createForegroundInfo(context, pendingCount = 0)

        val channels = notificationManager().notificationChannels
        assertEquals(1, channels.size)
        val channel = channels.single()
        assertEquals(PlaybackSyncNotificationHelper.CHANNEL_ID, channel.id)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals(context.getString(R.string.data_sync_channel_name), channel.name.toString())
        assertEquals(context.getString(R.string.data_sync_channel_desc), channel.description)
        assertFalse(channel.canShowBadge())
    }

    // ── Count text updates ────────────────────────────────────────────

    @Test
    fun `updateNotification posts the singular and plural queued text under the stable id`() {
        PlaybackSyncNotificationHelper.updateNotification(context, pendingCount = 1)
        val singular = shadowOf(notificationManager())
            .getNotification(PlaybackSyncNotificationHelper.NOTIFICATION_ID)
        assertNotNull(singular)
        assertEquals(
            context.resources.getQuantityString(R.plurals.data_sync_items_queued, 1, 1),
            singular.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )

        PlaybackSyncNotificationHelper.updateNotification(context, pendingCount = 7)
        val plural = shadowOf(notificationManager())
            .getNotification(PlaybackSyncNotificationHelper.NOTIFICATION_ID)
        assertNotNull(plural)
        assertEquals(
            context.resources.getQuantityString(R.plurals.data_sync_items_queued, 7, 7),
            plural.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `zero pending falls back to the finishing text`() {
        PlaybackSyncNotificationHelper.updateNotification(context, pendingCount = 0)

        val notification = shadowOf(notificationManager())
            .getNotification(PlaybackSyncNotificationHelper.NOTIFICATION_ID)
        assertNotNull(notification)
        assertEquals(
            context.getString(R.string.data_sync_finishing),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `dismissNotification cancels the stable id`() {
        PlaybackSyncNotificationHelper.updateNotification(context, pendingCount = 2)
        assertNotNull(
            shadowOf(notificationManager()).getNotification(PlaybackSyncNotificationHelper.NOTIFICATION_ID),
        )

        PlaybackSyncNotificationHelper.dismissNotification(context)

        assertNull(
            shadowOf(notificationManager()).getNotification(PlaybackSyncNotificationHelper.NOTIFICATION_ID),
        )
    }
}
