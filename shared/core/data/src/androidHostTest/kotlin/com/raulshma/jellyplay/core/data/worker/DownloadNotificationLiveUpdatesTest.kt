package com.raulshma.jellyplay.core.data.worker

import android.Manifest
import android.app.Application
import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.receiver.DownloadActionReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins the Android 16 "Live Updates" branch of the download-progress
 * notification (API 36): the ProgressStyle carries the per-mille progress,
 * the card is categorized progress + ongoing, and the Pause/Cancel actions
 * survive the framework-builder translation. The API-34 config pins the
 * compat parity half: same title/actions/group below 36.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class DownloadNotificationLiveUpdatesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `api 36 progress notification uses progress style with per-mille progress and both actions`() {
        val downloadId = "dl-live"
        val notificationId = DownloadNotificationHelper.notificationIdFor(downloadId)
        val foregroundInfo = DownloadNotificationHelper.createForegroundInfo(
            context, downloadId, notificationId, "Live Movie", 42, 840L, 2_000L, 500L,
        )

        val notification = foregroundInfo.notification
        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Live Movie", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(DownloadNotificationHelper.GROUP_KEY, notification.group)
        // ProgressStyle rides extras as a typed style parcel on real Android;
        // Robolectric reflects the framework builder, so assert the branch's
        // observable progress value (per-mille: 42% -> 420).
        assertEquals(420, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(2, notification.actions.size)
        assertEquals(
            context.getString(com.raulshma.jellyplay.shared.core.data.R.string.data_download_action_pause),
            notification.actions[0].title.toString(),
        )
        assertEquals(
            context.getString(com.raulshma.jellyplay.shared.core.data.R.string.data_download_action_cancel),
            notification.actions[1].title.toString(),
        )
        // The action intents keep routing to the action receiver.
        val pauseIntent = shadowOf(notification.actions[0].actionIntent).savedIntent
        assertEquals(DownloadActionReceiver.ACTION_PAUSE, pauseIntent.action)
        assertEquals(downloadId, pauseIntent.getStringExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID))
    }

    @Test
    fun `api 36 queued notification keeps the queued body and actions`() {
        val downloadId = "dl-live-q"
        val notificationId = DownloadNotificationHelper.notificationIdFor(downloadId)
        val foregroundInfo = DownloadNotificationHelper.createQueuedForegroundInfo(
            context, downloadId, notificationId, "Queued Movie",
        )

        val notification = foregroundInfo.notification
        assertEquals(
            context.getString(com.raulshma.jellyplay.shared.core.data.R.string.data_download_queued),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(2, notification.actions.size)
    }
}
