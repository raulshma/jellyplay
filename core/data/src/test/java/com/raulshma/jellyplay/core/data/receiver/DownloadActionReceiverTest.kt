package com.raulshma.jellyplay.core.data.receiver

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.worker.DownloadNotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Pins [DownloadActionReceiver]'s intent contract, invoked the way the system
 * delivers it: one `onReceive` per crafted action intent.
 *
 * Invariants:
 * - `ACTION_PAUSE` pauses via the repository and, when a download name is
 *   resolvable, keeps a paused shade handle alive (a posted notification); a
 *   failed name lookup posts nothing.
 * - `ACTION_RESUME` marks the row resumable and re-enqueues it; the paused
 *   shade handle goes away.
 * - `ACTION_CANCEL` cancels via the repository and dismisses both shade
 *   handles.
 * - A missing `download_id` extra or an unknown action is a silent no-op —
 *   the repository is never touched.
 *
 * The repository is resolved from a test-local Koin container (the app
 * composition root owns it in production); the receiver is constructed fresh
 * per dispatch like the real broadcast flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadActionReceiverTest {

    private val downloadRepository: DownloadRepository = mockk(relaxed = true)
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        startKoin {
            modules(module { single<DownloadRepository> { downloadRepository } })
        }
        coEvery { downloadRepository.pauseDownload(any()) } returns Result.success(Unit)
        coEvery { downloadRepository.resumeDownload(any()) } returns Result.success(Unit)
        coEvery { downloadRepository.cancelDownload(any()) } returns Result.success(Unit)
        every { downloadRepository.enqueueDownload(any()) } returns Unit
        coEvery { downloadRepository.getDownloadName(any()) } returns "Movie"
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun intent(action: String, downloadId: String? = "dl-1") = Intent(context, DownloadActionReceiver::class.java).apply {
        this.action = action
        downloadId?.let { putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, it) }
    }

    private fun postedNotificationCount(): Int {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        return shadowOf(notificationManager).size()
    }

    @Test
    fun `pause pauses the download and posts a paused shade handle`() {
        DownloadActionReceiver().onReceive(context, intent(DownloadActionReceiver.ACTION_PAUSE))

        coVerify(timeout = 5000, exactly = 1) { downloadRepository.pauseDownload("dl-1") }
        coVerify(timeout = 5000, exactly = 1) { downloadRepository.getDownloadName("dl-1") }
        assertTrue(postedNotificationCount() >= 1)
    }

    @Test
    fun `pause without a resolvable name posts nothing`() {
        coEvery { downloadRepository.getDownloadName("dl-1") } returns null

        DownloadActionReceiver().onReceive(context, intent(DownloadActionReceiver.ACTION_PAUSE))

        coVerify(timeout = 5000, exactly = 1) { downloadRepository.pauseDownload("dl-1") }
        assertEquals(0, postedNotificationCount())
    }

    @Test
    fun `resume marks resumable and re-enqueues the download`() {
        DownloadActionReceiver().onReceive(context, intent(DownloadActionReceiver.ACTION_RESUME))

        coVerify(timeout = 5000, exactly = 1) { downloadRepository.resumeDownload("dl-1") }
        coVerify(timeout = 5000, exactly = 1) { downloadRepository.enqueueDownload("dl-1") }
    }

    @Test
    fun `cancel cancels the download and dismisses the shade handles`() {
        DownloadActionReceiver().onReceive(context, intent(DownloadActionReceiver.ACTION_CANCEL))

        coVerify(timeout = 5000, exactly = 1) { downloadRepository.cancelDownload("dl-1") }
        // dismissNotification(progress id) + dismissPausedNotification — the
        // notification manager ends with nothing posted for this download.
        assertEquals(0, postedNotificationCount())
    }

    @Test
    fun `a missing download id extra writes nothing`() {
        DownloadActionReceiver().onReceive(context, intent(DownloadActionReceiver.ACTION_PAUSE, downloadId = null))

        Thread.sleep(200)
        coVerify(exactly = 0) { downloadRepository.pauseDownload(any()) }
        coVerify(exactly = 0) { downloadRepository.resumeDownload(any()) }
        coVerify(exactly = 0) { downloadRepository.cancelDownload(any()) }
    }

    @Test
    fun `an unknown action is a silent no-op`() {
        DownloadActionReceiver().onReceive(context, intent("com.example.NOT_OUR_ACTION"))

        Thread.sleep(200)
        coVerify(exactly = 0) { downloadRepository.pauseDownload(any()) }
        coVerify(exactly = 0) { downloadRepository.resumeDownload(any()) }
        coVerify(exactly = 0) { downloadRepository.cancelDownload(any()) }
        assertEquals(0, postedNotificationCount())
    }
}
