package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.MediaCacheInvalidator
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drain-loop resilience companions to [PlaybackOutboxDrainerTest]: the seams
 * the happy-path suite leaves open — every one of them a way a real device
 * has of breaking an offline→online sync (ported from the legacy
 * `PlaybackSyncWorkerResilienceTest` minus the WorkManager notification
 * cases, which stayed Android-side):
 *
 *  1. suppression is ORDER-INDEPENDENT — a STOP captured before the watched
 *     flip must still be dropped when a PLAYED intent is staged for the item
 *     (a trailing near-end STOP replayed after markPlayedItem is the #153
 *     "watched online again" poison producer);
 *  2. a Room read failure during derived-flip derivation (getOfflineItem
 *     throws) must degrade to "no derivation", never crash the drain — the
 *     telemetry itself is still replayed;
 *  3. a failing played-intent probe must fall back to REPLAYING telemetry
 *     (getOrDefault(false) — suppression requires a confirmed intent, not an
 *     unknown), so a transient DB error cannot silently delete position
 *     reports.
 *
 * The second half pins the [PlaybackOutboxDrainer.Notifier] protocol — the
 * seam the Android worker's foreground-notification plumbing hangs on
 * (promote only on a non-empty outbox, tick down only on multi-entry drains
 * with entries remaining, dismiss exactly once, nothing on early-exit runs).
 */
class PlaybackOutboxDrainerResilienceTest {

    private val outbox: PlaybackOutboxRepository = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk()
    private val playedStateSync: PlayedStateSync = mockk(relaxed = true)
    private val offlineRepository: OfflineRepository = mockk(relaxed = true)
    private val userDataSyncTrigger: PlaybackOutboxDrainer.UserDataSyncTrigger = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val cacheInvalidator: MediaCacheInvalidator = mockk(relaxed = true)

    @BeforeTest
    fun setup() {
        every { offlineModeManager.isOffline } returns false
        coEvery { playbackRepository.replayOutboxEntry(any()) } returns true
        coEvery { outbox.drain() } returns emptyList()
        coEvery { offlineRepository.getDownloadedItemIds() } returns emptyList()
        coEvery { outbox.hasUnsyncedPlayedIntent(any()) } returns false
        coEvery { outbox.hasUnsyncedUnplayedIntent(any()) } returns false
        coEvery { offlineRepository.getOfflineItem(any()) } returns null
        coEvery { outbox.isPlayedStateIntentDelivered(any(), any()) } returns true
        coEvery { mediaRepository.markPlayed(any()) } returns Result.success(Unit)
        coEvery { playedStateSync.reconcileOfflineRow(any()) } returns
            PlayedStateSync.ReconcileOutcome.NoChange
    }

    private fun drainer(notifier: PlaybackOutboxDrainer.Notifier = PlaybackOutboxDrainer.Notifier.NONE): PlaybackOutboxDrainer =
        PlaybackOutboxDrainerImpl(
            outbox = outbox,
            playbackRepository = playbackRepository,
            offlineModeManager = offlineModeManager,
            playedStateSync = playedStateSync,
            offlineRepository = offlineRepository,
            mediaRepository = mediaRepository,
            cacheInvalidator = cacheInvalidator,
            userDataSyncTrigger = userDataSyncTrigger,
            notifier = notifier,
        )

    // ── 1. Suppression is order-independent (#153) ──────────────────────

    @Test
    fun `telemetry captured before the watched flip is still suppressed`() = runTest {
        // Drain order is oldest-first by createdAt; a STOP recorded at t=5
        // with the PLAYED intent staged at t=6 still precedes it in the list.
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.STOP, positionTicks = 58_000_000L),
            entry("e2", ITEM_ID, PlaybackOutboxEventType.PROGRESS, positionTicks = 30_000_000L),
            entry("e3", ITEM_ID, PlaybackOutboxEventType.PLAYED),
        )
        coEvery { outbox.hasUnsyncedPlayedIntent(ITEM_ID) } returns true

        val result = drainer().drainOnce(attempt = 0)

        assertFalse(result.retriesPending)
        // Only the PLAYED intent is replayed; both telemetry rows are dropped
        // in place — replaying either would re-poison the server row.
        coVerify(exactly = 1) {
            playbackRepository.replayOutboxEntry(match { it.eventType == PlaybackOutboxEventType.PLAYED })
        }
        coVerify(exactly = 0) {
            playbackRepository.replayOutboxEntry(match { it.eventType == PlaybackOutboxEventType.STOP })
            playbackRepository.replayOutboxEntry(match { it.eventType == PlaybackOutboxEventType.PROGRESS })
        }
        coVerify(exactly = 3) { outbox.delete(any()) }
    }

    // ── 2. Mirror-row read failure degrades, never crashes ──────────────

    @Test
    fun `a failing mirror-row read replays telemetry and skips the derived flip`() = runTest {
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.STOP, positionTicks = 58_000_000L),
        )
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } throws RuntimeException("room read failed")

        val result = drainer().drainOnce(attempt = 0)

        assertFalse(result.retriesPending)
        coVerify(exactly = 1) { playbackRepository.replayOutboxEntry(any()) }
        coVerify(exactly = 1) { outbox.delete("e1") }
        // No mirror row → no derivation; markPlayed must not fire blind.
        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
    }

    // ── 3. A failing intent probe replays instead of suppressing ────────

    @Test
    fun `a failing played-intent probe replays telemetry instead of deleting it`() = runTest {
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.STOP, positionTicks = 58_000_000L),
        )
        // Unknown ≠ staged: suppression needs a confirmed intent, so a DB
        // failure must fall back to replaying the position report.
        coEvery { outbox.hasUnsyncedPlayedIntent(ITEM_ID) } throws RuntimeException("room read failed")
        // Mirror row mid-watch: nothing to derive even if it were readable.
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns mirrorRow(playedPercentage = 40.0)

        val result = drainer().drainOnce(attempt = 0)

        assertFalse(result.retriesPending)
        coVerify(exactly = 1) { playbackRepository.replayOutboxEntry(any()) }
        coVerify(exactly = 1) { outbox.delete("e1") }
        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
    }

    // ── 4. Notifier protocol (the Android notification adapter's seam) ──

    @Test
    fun `a non-empty outbox promotes and dismisses exactly once`() = runTest {
        coEvery { outbox.drain() } returns listOf(entry("e1", ITEM_ID, PlaybackOutboxEventType.PLAYED))
        val notifier = RecordingNotifier()

        drainer(notifier).drainOnce(attempt = 0)

        assertEquals(listOf(1), notifier.started)
        assertEquals(1, notifier.finished)
    }

    @Test
    fun `a multi-entry drain ticks the remaining count down between entries`() = runTest {
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.PLAYED),
            entry("e2", OTHER_ITEM_ID, PlaybackOutboxEventType.UNPLAYED),
        )
        val notifier = RecordingNotifier()

        drainer(notifier).drainOnce(attempt = 0)

        // remaining counts 1 after the first of two entries; the loop ends
        // before reporting remaining == 0 (the dismissal carries that).
        assertEquals(listOf(1), notifier.progress)
    }

    @Test
    fun `a reconcile-only run stays silent`() = runTest {
        // Empty outbox but a downloaded row to reconcile: a background
        // freshness check, no notification choreography.
        coEvery { offlineRepository.getDownloadedItemIds() } returns listOf(ITEM_ID)
        val notifier = RecordingNotifier()

        drainer(notifier).drainOnce(attempt = 0)

        assertEquals(emptyList(), notifier.started)
        assertEquals(emptyList(), notifier.progress)
        assertEquals(0, notifier.finished)
    }

    @Test
    fun `an empty outbox never notifies`() = runTest {
        val notifier = RecordingNotifier()

        drainer(notifier).drainOnce(attempt = 0)

        assertEquals(emptyList(), notifier.started)
        assertEquals(emptyList(), notifier.progress)
        assertEquals(0, notifier.finished)
    }

    /** Records the drain-progress callbacks for protocol assertions. */
    private class RecordingNotifier : PlaybackOutboxDrainer.Notifier {
        val started = mutableListOf<Int>()
        val progress = mutableListOf<Int>()
        var finished = 0

        override suspend fun onDrainStarted(pendingCount: Int) {
            started += pendingCount
        }

        override suspend fun onDrainProgress(remaining: Int) {
            progress += remaining
        }

        override suspend fun onDrainFinished() {
            finished++
        }
    }

    private fun mirrorRow(
        isPlayed: Boolean = false,
        playedPercentage: Double = 50.0,
    ) = OfflineMediaItem(
        id = ITEM_ID,
        name = "Episode",
        mediaType = MediaType.EPISODE,
        isPlayed = isPlayed,
        playedPercentage = playedPercentage,
        runTimeTicks = 60_000_000L,
        playbackPositionTicks = ((playedPercentage / 100.0) * 60_000_000L).toLong(),
    )

    private companion object {
        const val ITEM_ID = "item-1"
        const val OTHER_ITEM_ID = "item-2"
    }
}
