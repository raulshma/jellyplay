package com.raulshma.jellyplay.core.data.worker

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.R
import com.raulshma.jellyplay.core.data.receiver.DownloadActionReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins the shape of the download notifications the user sees in the shade:
 * the per-transfer progress notification grouped + Pause/Cancel actions routed
 * to [DownloadActionReceiver], the paused-state notification carrying
 * Resume/Cancel, and the collapsible group summary that self-dismisses when the
 * last in-flight transfer ends.
 *
 * Real framework inflation (PendingIntents, the system NotificationManager,
 * module resources for the action labels) runs against Robolectric's shadows so
 * the assertion covers the notification shape and intent routing end to end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DownloadNotificationHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // postPausedNotification / refreshSummary skip posting when notification
        // permission is denied; Robolectric grants it by default, but pin it so
        // the assertion is about the notification shape, not permission state.
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `progress notification groups under downloads and exposes pause + cancel actions`() {
        val downloadId = "dl-1"
        val notificationId = DownloadNotificationHelper.notificationIdFor(downloadId)
        val foregroundInfo = DownloadNotificationHelper.createForegroundInfo(
            context, downloadId, notificationId, "Test Movie", 42, 1_000L, 2_000L, 500L,
        )

        assertEquals(notificationId, foregroundInfo.notificationId)
        val notification = foregroundInfo.notification
        assertEquals("Test Movie", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(DownloadNotificationHelper.GROUP_KEY, notification.group)
        assertEquals(2, notification.actions.size)

        val pauseIntent = shadowOf(notification.actions[0].actionIntent).savedIntent
        assertEquals(DownloadActionReceiver.ACTION_PAUSE, pauseIntent.action)
        assertEquals(downloadId, pauseIntent.getStringExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID))

        val cancelIntent = shadowOf(notification.actions[1].actionIntent).savedIntent
        assertEquals(DownloadActionReceiver.ACTION_CANCEL, cancelIntent.action)
        assertEquals(downloadId, cancelIntent.getStringExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID))
    }

    @Test
    fun `paused notification keeps the row resumable from the shade`() {
        val downloadId = "dl-2"
        DownloadNotificationHelper.postPausedNotification(context, downloadId, "Movie")

        val notification = shadowOf(notificationManager())
            .getNotification(DownloadNotificationHelper.pausedNotificationIdFor(downloadId))
        assertNotNull(notification)
        assertEquals("Movie", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(DownloadNotificationHelper.GROUP_KEY, notification.group)
        assertEquals(2, notification.actions.size)
        assertEquals(
            DownloadActionReceiver.ACTION_RESUME,
            shadowOf(notification.actions[0].actionIntent).savedIntent.action,
        )
        assertEquals(
            DownloadActionReceiver.ACTION_CANCEL,
            shadowOf(notification.actions[1].actionIntent).savedIntent.action,
        )
    }

    @Test
    fun `summary collapses active downloads and dismisses when the last one finishes`() {
        DownloadNotificationHelper.refreshSummary(context, 2)

        val posted = shadowOf(notificationManager())
            .getNotification(DownloadNotificationHelper.SUMMARY_NOTIFICATION_ID)
        assertNotNull(posted)
        assertEquals(DownloadNotificationHelper.GROUP_KEY, posted.group)
        assertEquals(
            context.resources.getQuantityString(R.plurals.data_download_summary_active, 2, 2),
            posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )

        DownloadNotificationHelper.refreshSummary(context, 0)

        assertNull(
            shadowOf(notificationManager())
                .getNotification(DownloadNotificationHelper.SUMMARY_NOTIFICATION_ID)
        )
    }

    private fun notificationManager() =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
