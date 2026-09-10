package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Resilience + edge companions to [PlayedStateSyncImplTest] — the seams the
 * main suite leaves open, each one a way the sync has actually broken:
 *
 *  1. a CONFIRMED online flip must survive a failing local mirror (the
 *     server mutation already landed — a Room failure must not surface or
 *     stage a redundant outbox row);
 *  2. auto-delete-after-watch is fire-and-forget: a failing prefs read or
 *     download lookup must never bubble into the flip's result;
 *  3. the #157 heal's delivery probe reporting undelivered defers the local
 *     reset (no partial application: the row resets only once the flip is
 *     confirmed server-side);
 *  4. a THROWING delivery probe reads as undelivered — the drain retries,
 *     the intent is never assumed delivered on the strength of an exception;
 *  5. favorite-only drift (played state agrees) adopts the server favorite
 *     and still reports NoChange — the worker must not refresh caches over a
 *     favorite mirror it cannot distinguish from no-op;
 *  6. [PlayedStateSync.computePlayedPercentage] matrix — the pure helper the
 *     reconcile ladder's percentage write goes through.
 */
class PlayedStateSyncImplResilienceTest {

    private lateinit var apiClient: JellyfinApiClient
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var outboxRepository: PlaybackOutboxRepository
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var mediaRepository: MediaRepository
    private lateinit var downloadsStore: DownloadsStore
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var sync: PlayedStateSyncImpl

    @BeforeTest
    fun setup() {
        apiClient = mockk()
        offlineRepository = mockk(relaxUnitFun = true)
        outboxRepository = mockk(relaxed = true)
        offlineModeManager = mockk()
        mediaRepository = mockk()
        downloadsStore = mockk()
        downloadRepository = mockk()
        sync = PlayedStateSyncImpl(
            apiClient = apiClient,
            offlineRepository = offlineRepository,
            playbackOutboxRepository = outboxRepository,
            offlineModeManager = offlineModeManager,
            mediaRepository = lazy { mediaRepository },
            downloadsStore = lazy { downloadsStore },
            downloadRepository = lazy { downloadRepository },
            timeSource = object : TimeSource {
                override fun nowEpochMillis(): Long = epochMillis("2024-06-15T10:31:00Z")
                override fun nowElapsedRealtimeMillis(): Long = epochMillis("2024-06-15T10:31:00Z")
                override fun today(zone: ZoneId): LocalDate = LocalDate.of(2024, 6, 15)
            },
        )
        every { offlineModeManager.isOffline } returns false
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice())
        // The confirmed-write announcement (strict mock otherwise).
        every { mediaRepository.notifyUserDataChanged(any()) } returns Unit
    }

    // ── 1. A confirmed flip survives a failing local mirror ─────────────

    @Test
    fun `a confirmed online played flip survives a failing offline mirror`() = runTest {
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)
        coEvery { offlineRepository.applyPlayedState(ITEM_ID, true) } throws
            RuntimeException("disk I/O error")

        val result = sync.flip(ITEM_ID, played = true)

        assertTrue(result.isSuccess)
        // The server mutation landed — no outbox row (replaying the flip is
        // harmless but redundant) and no surfaced failure.
        coVerify(exactly = 0) { outboxRepository.enqueuePlayedState(any(), any()) }
    }

    // ── 2. Auto-delete-after-watch is fire-and-forget ───────────────────

    @Test
    fun `auto-delete survives a failing downloads prefs read`() = runTest {
        // The store exposes a StateFlow, so the read failure is modeled as a
        // collect that throws — `first()` funnels through collect.
        every { downloadsStore.downloads } returns mockk {
            coEvery { collect(any()) } answers { throw RuntimeException("datastore read failed") }
        }
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)

        val result = sync.flip(ITEM_ID, played = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { downloadRepository.getDownloadByMediaItemId(any()) }
        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
    }

    @Test
    fun `auto-delete survives a failing download lookup`() = runTest {
        every { downloadsStore.downloads } returns
            MutableStateFlow(DownloadsSlice(autoDeleteAfterWatch = true))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM_ID) } throws
            RuntimeException("room read failed")

        val result = sync.flip(ITEM_ID, played = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
    }

    // ── 3. The #157 heal defers the local reset when undelivered ────────

    @Test
    fun `a heal whose delivery probe reports undelivered defers the local reset`() = runTest {
        // Server row is the poisoned #157 state: Played=true with a stale
        // resume position. The markPlayed push lands, but the surviving
        // outbox probe says the intent row is still there (it was re-staged
        // by a racing enqueue) → treat as undelivered, reset nothing.
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            offlineItem(isPlayed = false, playbackPositionTicks = 30_000_000L)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = true, playbackPositionTicks = 5_000_000L))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)
        coEvery { outboxRepository.isPlayedStateIntentDelivered(ITEM_ID, played = true) } returns false

        val outcome = sync.reconcileOfflineRow(ITEM_ID)

        assertEquals(PlayedStateSync.ReconcileOutcome.UndeliveredIntent, outcome)
        // No partial application: the local row must not read "played, clean"
        // while the server-side zeroing is in doubt.
        coVerify(exactly = 0) { offlineRepository.updatePlaybackProgress(any(), any(), any(), any()) }
    }

    // ── 4. A throwing delivery probe reads as undelivered ───────────────

    @Test
    fun `an intent push survives a throwing delivery probe and reports undelivered`() = runTest {
        // #153 push path: local played, server unplayed, undelivered PLAYED
        // intent. The push fails AND the probe throws — an exception must
        // never be read as "delivered".
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(isPlayed = true)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = false))
        coEvery { outboxRepository.hasUnsyncedPlayedIntent(ITEM_ID) } returns true
        coEvery { apiClient.markPlayed(ITEM_ID) } returns
            Result.failure(java.io.IOException("HTTP 503"))
        coEvery { outboxRepository.isPlayedStateIntentDelivered(ITEM_ID, played = true) } throws
            RuntimeException("room read failed")

        val outcome = sync.reconcileOfflineRow(ITEM_ID)

        assertEquals(PlayedStateSync.ReconcileOutcome.UndeliveredIntent, outcome)
        // The failed push re-staged the intent for the next drain.
        coVerify(exactly = 1) { outboxRepository.enqueuePlayedState(ITEM_ID, true) }
    }

    // ── 5. Favorite-only drift reports NoChange ─────────────────────────

    @Test
    fun `favorite-only drift adopts the server favorite and reports NoChange`() = runTest {
        // Played state agrees (both unplayed) and the server's activity stamp
        // is not newer: only the favorite column drifts. The favorite is
        // adopted (server-authoritative), but the outcome must stay NoChange
        // so the worker does not refresh caches over a favorite mirror.
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            offlineItem(isFavorite = false, lastPlayedDate = "2024-06-15T10:31:00Z")
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isFavorite = true, lastPlayedDate = "2024-06-15T10:30:00Z"))

        val outcome = sync.reconcileOfflineRow(ITEM_ID)

        assertEquals(PlayedStateSync.ReconcileOutcome.NoChange, outcome)
        coVerify(exactly = 1) { offlineRepository.applyFavoriteState(ITEM_ID, true) }
        coVerify(exactly = 0) { offlineRepository.updatePlaybackProgress(any(), any(), any(), any()) }
    }

    // ── 6. computePlayedPercentage matrix ───────────────────────────────

    @Test
    fun `computePlayedPercentage short-circuits a played item to 100`() {
        // Server UserData semantics: a finished item reports full progress
        // regardless of the last tick position (even 0).
        assertEquals(
            100.0,
            PlayedStateSync.computePlayedPercentage(positionTicks = 0L, runTimeTicks = 100L, isPlayed = true),
        )
    }

    @Test
    fun `computePlayedPercentage maps a missing or zero position to 0`() {
        assertEquals(0.0, PlayedStateSync.computePlayedPercentage(null, 100L, isPlayed = false))
        assertEquals(0.0, PlayedStateSync.computePlayedPercentage(0L, 100L, isPlayed = false))
        assertEquals(0.0, PlayedStateSync.computePlayedPercentage(-5L, 100L, isPlayed = false))
    }

    @Test
    fun `computePlayedPercentage maps a missing or zero runtime to 0`() {
        // Divide-by-zero guard: an item with no runtime must not produce
        // Infinity/NaN into the row's threshold math.
        assertEquals(0.0, PlayedStateSync.computePlayedPercentage(50L, null, isPlayed = false))
        assertEquals(0.0, PlayedStateSync.computePlayedPercentage(50L, 0L, isPlayed = false))
    }

    @Test
    fun `computePlayedPercentage derives the ratio for a normal position`() {
        assertEquals(
            42.0,
            PlayedStateSync.computePlayedPercentage(positionTicks = 42L, runTimeTicks = 100L, isPlayed = false),
        )
    }

    @Test
    fun `computePlayedPercentage clamps a beyond-runtime position to 100`() {
        assertEquals(
            100.0,
            PlayedStateSync.computePlayedPercentage(positionTicks = 150L, runTimeTicks = 100L, isPlayed = false),
        )
    }

    // ── fixtures ────────────────────────────────────────────────────────

    private fun offlineItem(
        isPlayed: Boolean = false,
        isFavorite: Boolean = false,
        lastPlayedDate: String? = null,
        playbackPositionTicks: Long? = null,
    ) = OfflineMediaItem(
        id = ITEM_ID,
        name = "Movie",
        mediaType = MediaType.MOVIE,
        isPlayed = isPlayed,
        isFavorite = isFavorite,
        lastPlayedDate = lastPlayedDate,
        runTimeTicks = 100_000_000L,
        playbackPositionTicks = playbackPositionTicks,
    )

    private fun detail(
        isPlayed: Boolean = false,
        isFavorite: Boolean = false,
        lastPlayedDate: String? = null,
        playbackPositionTicks: Long? = null,
    ) = MediaDetail(
        item = MediaItem(
            id = ITEM_ID,
            name = "Movie",
            mediaType = MediaType.MOVIE,
            isPlayed = isPlayed,
            isFavorite = isFavorite,
            lastPlayedDate = lastPlayedDate,
            playbackPositionTicks = playbackPositionTicks,
            runTimeTicks = 100_000_000L,
        ),
    )

    private companion object {
        const val ITEM_ID = "item-1"

        fun epochMillis(iso: String): Long =
            java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    }
}
