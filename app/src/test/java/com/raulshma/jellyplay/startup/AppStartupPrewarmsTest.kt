package com.raulshma.jellyplay.startup

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DownloadReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentity
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.notification.scheduler.NotificationReconnectListener
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import com.raulshma.jellyplay.feature.player.video.engine.VideoStreamCache
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.widget.NowPlayingWidgetUpdater
import com.raulshma.jellyplay.widget.WidgetWorkScheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the cold-start GROUP ORDER of [AppStartupPrewarms] — the ordering the
 * former inline `JellyPlayApplication.onCreate` launch blocks carried:
 *
 *  - the DataStore prewarms run first, strictly in order (offline slice →
 *    device-id await → persisted-security slice): the Coil DiskCache sizing
 *    race depends on the offline read winning its head start;
 *  - download recovery and the APK sweep begin at t=0, after group 1's head;
 *  - the audio/widget group and the scheduler group are gated behind the
 *    2 s deferral (virtual time), audio → widget first, then the scheduler
 *    group in its enqueue order.
 *
 * Every collaborator is a recording mock. All five launches land on one
 * single-threaded virtual-time dispatcher (the test-only constructor seam —
 * production uses Dispatchers.IO), so each block's actions execute in the
 * scheduler FIFO order the launches establish.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStartupPrewarmsTest {

    private data class Event(val atMs: Long, val name: String)

    private val events = mutableListOf<Event>()
    private var nowMs: () -> Long = { 0L }

    private fun tag(name: String) {
        events += Event(nowMs(), name)
    }

    private val networkOfflineStore: NetworkOfflineStore = mockk(relaxed = true) {
        every { networkOffline } answers {
            tag("networkOffline.first")
            MutableStateFlow(NetworkOfflineSlice())
        }
    }
    private val serverIdentityStore: ServerIdentityStore = mockk(relaxed = true) {
        coEvery { ensureDeviceId() } coAnswers {
            tag("identity.ensureDeviceId")
            "device-1"
        }
        every { identity } answers {
            tag("identity.await")
            MutableStateFlow(ServerIdentity(deviceId = "device-1"))
        }
    }
    private val securityStore: SecurityStore = mockk(relaxed = true) {
        coEvery { firstPersistedSecurity() } coAnswers {
            tag("security.firstPersistedSecurity")
            mockk<SecuritySlice>()
        }
    }
    private val fontProvider: FontProvider = mockk(relaxed = true) {
        coEvery { prewarm() } coAnswers { tag("font.prewarm") }
    }
    private val videoStreamCache: VideoStreamCache = mockk(relaxed = true) {
        every { prewarm() } answers { tag("stream.prewarm") }
    }
    private val audioPlaybackManager: AudioPlaybackManager = mockk(relaxed = true) {
        every { start() } answers { tag("audio.start") }
    }
    private val nowPlayingWidgetUpdater: NowPlayingWidgetUpdater = mockk(relaxed = true) {
        every { start() } answers { tag("widget.start") }
    }
    private val widgetWorkScheduler: WidgetWorkScheduler = mockk(relaxed = true) {
        every { enqueuePeriodic() } answers { tag("widgetWork.enqueuePeriodic") }
    }
    private val userDataSyncScheduler: UserDataSyncScheduler = mockk(relaxed = true) {
        every { enqueuePeriodic() } answers { tag("userDataSync.enqueuePeriodic") }
    }
    private val playbackSyncScheduler: PlaybackSyncScheduler = mockk(relaxed = true) {
        every { enqueuePeriodic() } answers { tag("playbackSync.enqueuePeriodic") }
    }
    private val playbackSyncReconnectListener: PlaybackSyncReconnectListener = mockk(relaxed = true) {
        every { start() } answers { tag("playbackSyncReconnect.start") }
    }
    private val downloadReconnectListener: DownloadReconnectListener = mockk(relaxed = true) {
        every { start() } answers { tag("downloadReconnect.start") }
    }
    private val notificationReconnectListener: NotificationReconnectListener = mockk(relaxed = true) {
        every { start() } answers { tag("notificationReconnect.start") }
    }
    private val autoDownloadScheduler: AutoDownloadScheduler = mockk(relaxed = true) {
        every { sync() } answers { tag("autoDownload.sync") }
    }
    private val notificationScheduler: NotificationScheduler = mockk(relaxed = true) {
        coEvery { scheduleOrUpdate() } coAnswers { tag("notificationScheduler.scheduleOrUpdate") }
    }
    private val downloadRecoveryInitializer: DownloadRecoveryInitializer = mockk(relaxed = true) {
        coEvery { recover() } coAnswers { tag("recovery.recover") }
    }
    private val appUpdateRepository: AppUpdateRepository = mockk(relaxed = true) {
        every { cleanupDownloadedUpdate() } answers { tag("update.sweep") }
    }

    private fun createPrewarms(
        scope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher,
    ) = AppStartupPrewarms(
        applicationScope = scope,
        ioDispatcher = ioDispatcher,
        networkOfflineStore = lazy { networkOfflineStore },
        serverIdentityStore = lazy { serverIdentityStore },
        securityStore = lazy { securityStore },
        fontProvider = lazy { fontProvider },
        videoStreamCache = lazy { videoStreamCache },
        audioPlaybackManager = lazy { audioPlaybackManager },
        nowPlayingWidgetUpdater = lazy { nowPlayingWidgetUpdater },
        widgetWorkScheduler = lazy { widgetWorkScheduler },
        userDataSyncScheduler = lazy { userDataSyncScheduler },
        playbackSyncScheduler = lazy { playbackSyncScheduler },
        playbackSyncReconnectListener = lazy { playbackSyncReconnectListener },
        downloadReconnectListener = lazy { downloadReconnectListener },
        notificationReconnectListener = lazy { notificationReconnectListener },
        autoDownloadScheduler = lazy { autoDownloadScheduler },
        notificationScheduler = lazy { notificationScheduler },
        downloadRecoveryInitializer = lazy { downloadRecoveryInitializer },
        appUpdateRepository = lazy { appUpdateRepository },
    )

    private fun indexOf(name: String) = events.indexOfFirst { it.name == name }
    private fun timeOf(name: String) = events.first { it.name == name }.atMs

    @Test
    fun `start fires the four groups in the pinned order`() = runTest {
        nowMs = { testScheduler.currentTime }
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

        createPrewarms(scope, StandardTestDispatcher(testScheduler)).start()
        advanceUntilIdle()
        scope.cancel()

        // Every collaborator action ran exactly once.
        assertEquals(events.size, events.map { it.name }.distinct().size)

        // ── group 1: the DataStore prewarms run FIRST, strictly in order ──
        assertEquals(
            listOf(
                "networkOffline.first",
                "identity.ensureDeviceId",
                "identity.await",
                "security.firstPersistedSecurity",
            ),
            events.take(4).map { it.name },
        )
        assertTrue(
            "DataStore prewarms must run at t=0, before the deferral window",
            events.take(4).all { it.atMs == 0L },
        )

        // ── group order: recovery + sweep begin after group 1's head ──
        assertTrue(indexOf("networkOffline.first") < indexOf("recovery.recover"))
        assertTrue(indexOf("recovery.recover") < indexOf("update.sweep"))

        // ── the 2 s deferral: audio/widget + schedulers all land at 2_000 ──
        assertEquals(0L, timeOf("recovery.recover"))
        assertEquals(0L, timeOf("update.sweep"))

        val postDeferralOrder = listOf(
            "audio.start",
            "widget.start",
            "widgetWork.enqueuePeriodic",
            "userDataSync.enqueuePeriodic",
            "playbackSync.enqueuePeriodic",
            "playbackSyncReconnect.start",
            "downloadReconnect.start",
            "notificationReconnect.start",
            "autoDownload.sync",
            "notificationScheduler.scheduleOrUpdate",
        )
        assertEquals(postDeferralOrder, events.takeLast(postDeferralOrder.size).map { it.name })
        assertTrue(
            "audio/widget + scheduler groups must run only after the 2 s deferral",
            events.takeLast(postDeferralOrder.size).all { it.atMs == 2_000L },
        )

        // The prewarm/recovery/sweep work itself stays at t=0 — the deferral
        // gates only the two deferred groups.
        assertTrue(
            "Everything before the deferral must run at t=0",
            events.dropLast(postDeferralOrder.size).all { it.atMs == 0L },
        )
        assertTrue(
            "Audio must start before the scheduler group runs",
            indexOf("audio.start") < indexOf("widgetWork.enqueuePeriodic"),
        )
    }
}
