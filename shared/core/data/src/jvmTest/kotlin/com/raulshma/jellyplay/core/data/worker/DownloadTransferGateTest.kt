package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.UserEntity
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.DownloadStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins [DownloadTransferGate] — both halves of the transfer choreography the
 * two orchestrators (DownloadWorker on Android, DesktopDownloadManager on
 * desktop) previously hand-copied. The preamble ([runTransfer]) tests pin the
 * extracted contract order-inclusively:
 *
 *  - decrypt receives EXACTLY the stored (encrypted) token, after the
 *    user-row read, and the transfer body carries the plaintext;
 *  - the coerce bounds on `downloadConnections` (0 → 1, 20 → 8, in-range
 *    values pass through);
 *  - an inactive-after-queue row resolves through the caller's `onInactive`
 *    body BEFORE any DOWNLOADING write (the orchestrators keep their own
 *    early-outs — Success on Android, plain return on desktop);
 *  - the write ordering: stored-token read → decrypt → post-queue re-check
 *    (getStatus) → DOWNLOADING progress write, all inside a concurrency
 *    permit that also spans the caller's transfer body (a saturated limiter
 *    blocks the whole choreography, and the permit is NOT released when the
 *    preamble ends — `maxConcurrentDownloads` caps actual transfers).
 *
 * The tail ([execute]) tests pin the shared post-preamble choreography:
 *
 *  - resume (existingBytes > 0) re-probes the authoritative size and hands
 *    the probe result to the runner (the integrity check's fallback);
 *  - the fresh multi-vs-single threshold: strictly above
 *    [DownloadTransferRunner.MIN_MULTI_SIZE] AND numConnections > 1 dispatches
 *    the multi-connection strategy; at the threshold, or with one connection,
 *    the single-connection runner runs;
 *  - a [CancellationException] propagates untouched — never classified,
 *    never applied to the row;
 *  - a thrown non-IO failure routes through DownloadFailurePolicy with the
 *    documented outer-path flags (madeProgress = false,
 *    isResumablePartial = true → bytes preserved, partial kept, no retry);
 *  - the notifications port receives the start-of-transfer summary refresh on
 *    BOTH paths (the healed runner-vs-multi divergence).
 *
 * The PREAMBLE performs no network I/O — the HEAD probe belongs to [execute]
 * (declared in the gate's KDoc).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadTransferGateTest {

    private val dao: DownloadDao = mockk(relaxed = true)
    private val userDao: UserDao = mockk()
    private val tokenCipher: TokenCipher = mockk()
    private val serverIdentityStore: ServerIdentityStore = mockk()
    private val downloadsStore: DownloadsStore = mockk()
    private val limiter = DownloadConcurrencyLimiter()
    private val client = FakeDownloadTransferClient()

    private val gate = DownloadTransferGate(
        dao = dao,
        userDao = userDao,
        serverIdentityStore = serverIdentityStore,
        downloadsStore = downloadsStore,
        tokenCipher = tokenCipher,
        concurrencyLimiter = limiter,
        transferClient = client,
    )

    private val storedTokenCipherText = "stored-cipher-text"

    @BeforeTest
    fun setUp() {
        every { serverIdentityStore.activeUserId } returns flowOf("user-1")
        coEvery { userDao.getUserById("user-1") } returns userEntity(accessToken = storedTokenCipherText)
        every { tokenCipher.decrypt(storedTokenCipherText) } returns "plain-token"
        every { downloadsStore.downloads } returns MutableStateFlow(slice(downloadConnections = 4))
        coEvery { dao.getStatus("dl-1") } returns DownloadStatus.QUEUED.name
        coEvery { dao.getInFlightDownloadCount() } returns 1
    }

    private fun userEntity(accessToken: String) = UserEntity(
        userId = "user-1",
        serverId = "server-1",
        name = "User",
        accessToken = accessToken,
    )

    private fun slice(downloadConnections: Int) = DownloadsSlice(downloadConnections = downloadConnections)

    /** What [prepare] returns when the row went inactive while QUEUED. */
    private object InactiveSentinel

    private suspend fun prepare(existingBytes: Long = 0L, isStopped: () -> Boolean = { false }): Any =
        gate.runTransfer(
            downloadId = "dl-1",
            existingBytes = existingBytes,
            isStopped = isStopped,
            updateForeground = { _, _, _, _, _, _ -> },
            dismissForeground = { },
            onInactive = { InactiveSentinel },
            transfer = { it },
        )

    // ── credential resolution ────────────────────────────────────────────

    @Test
    fun `decrypt receives the stored token and the transfer body carries the plaintext`() = runTest {
        val preparation = prepare()

        val proceed = assertIs<DownloadTransferGate.Preparation>(preparation)
        assertEquals("plain-token", proceed.accessToken)
        coVerify { userDao.getUserById("user-1") }
        // The cipher saw the stored ciphertext, never the plaintext or null.
        verify(exactly = 1) { tokenCipher.decrypt(storedTokenCipherText) }
        verify(exactly = 0) { tokenCipher.decrypt(neq(storedTokenCipherText)) }
    }

    @Test
    fun `no active user skips the token lookup entirely`() = runTest {
        every { serverIdentityStore.activeUserId } returns flowOf(null)

        val preparation = prepare()

        val proceed = assertIs<DownloadTransferGate.Preparation>(preparation)
        assertEquals(null, proceed.accessToken)
        coVerify(exactly = 0) { userDao.getUserById(any()) }
        verify(exactly = 0) { tokenCipher.decrypt(any()) }
    }

    // ── connection coerce ────────────────────────────────────────────────

    @Test
    fun `downloadConnections coerce bounds - zero clamps to 1`() = runTest {
        every { downloadsStore.downloads } returns MutableStateFlow(slice(downloadConnections = 0))
        val proceed = assertIs<DownloadTransferGate.Preparation>(prepare())
        assertEquals(1, proceed.numConnections)
    }

    @Test
    fun `downloadConnections coerce bounds - twenty clamps to 8`() = runTest {
        every { downloadsStore.downloads } returns MutableStateFlow(slice(downloadConnections = 20))
        val proceed = assertIs<DownloadTransferGate.Preparation>(prepare())
        assertEquals(8, proceed.numConnections)
    }

    @Test
    fun `downloadConnections in range passes through unchanged`() = runTest {
        every { downloadsStore.downloads } returns MutableStateFlow(slice(downloadConnections = 4))
        val proceed = assertIs<DownloadTransferGate.Preparation>(prepare())
        assertEquals(4, proceed.numConnections)
    }

    // ── post-QUEUED re-check + DOWNLOADING write ─────────────────────────

    @Test
    fun `inactive-after-queue aborts before the DOWNLOADING write`() = runTest {
        coEvery { dao.getStatus("dl-1") } returns DownloadStatus.PAUSED.name

        val preparation = prepare()

        assertEquals(InactiveSentinel, preparation)
        // No status write at all — the row keeps its QUEUED status and bytes;
        // the orchestrators' early-outs (Success / plain return) express
        // through the onInactive body, not through extra writes.
        coVerify(exactly = 0) { dao.updateProgress(any(), any(), any()) }
    }

    @Test
    fun `progress-write ordering - token resolution, re-check, then DOWNLOADING write`() = runTest {
        val calls = mutableListOf<String>()
        coEvery { userDao.getUserById("user-1") } coAnswers {
            calls += "getUserById"; userEntity(storedTokenCipherText)
        }
        every { tokenCipher.decrypt(any()) } answers { calls += "decrypt"; "plain-token" }
        coEvery { dao.getStatus("dl-1") } coAnswers { calls += "getStatus"; DownloadStatus.QUEUED.name }
        coEvery { dao.updateProgress(any(), any(), any()) } coAnswers {
            calls += "updateProgress:${args[2]}"
        }

        val preparation = prepare(existingBytes = 1234L)

        val proceed = assertIs<DownloadTransferGate.Preparation>(preparation)
        assertEquals("plain-token", proceed.accessToken)
        assertEquals(
            listOf("getUserById", "decrypt", "getStatus", "updateProgress:DOWNLOADING"),
            calls,
        )
        // The DOWNLOADING write preserves the row's start bytes (resume offset).
        coVerify { dao.updateProgress("dl-1", 1234L, DownloadStatus.DOWNLOADING.name) }
    }

    @Test
    fun `choreography waits for a concurrency slot before touching the row`() = runTest {
        limiter.configure(1)
        val calls = mutableListOf<String>()
        coEvery { dao.getStatus("dl-1") } coAnswers { calls += "getStatus"; DownloadStatus.QUEUED.name }
        coEvery { dao.updateProgress(any(), any(), any()) } coAnswers { calls += "updateProgress" }

        // Hold the only permit; the gate must block before its re-check.
        val holder = launch { limiter.withPermit { awaitCancellation() } }
        runCurrent()

        var preparation: Any? = null
        val gated = launch { preparation = prepare() }
        runCurrent()
        assertTrue("getStatus" !in calls, "re-check ran before a slot was available")

        holder.cancel()
        advanceUntilIdle()
        gated.join()

        assertIs<DownloadTransferGate.Preparation>(preparation)
        assertEquals(listOf("getStatus", "updateProgress"), calls)
    }

    @Test
    fun `permit spans the transfer body - a second transfer blocks until the first returns`() = runTest {
        limiter.configure(1)
        coEvery { dao.getStatus("dl-1") } returns DownloadStatus.QUEUED.name

        // The first transfer parks INSIDE its body; the permit must stay held.
        val enteredFirstBody = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val first = launch {
            gate.runTransfer(
                downloadId = "dl-1",
                existingBytes = 0L,
                isStopped = { false },
                updateForeground = { _, _, _, _, _, _ -> },
                dismissForeground = { },
                onInactive = { "inactive-1" },
            ) {
                enteredFirstBody.complete(Unit)
                releaseFirst.await()
                "first-done"
            }
        }
        runCurrent()
        assertTrue(enteredFirstBody.isCompleted, "first transfer never entered its body")

        var secondRan = false
        val second = launch {
            gate.runTransfer(
                downloadId = "dl-1",
                existingBytes = 0L,
                isStopped = { false },
                updateForeground = { _, _, _, _, _, _ -> },
                dismissForeground = { },
                onInactive = { "inactive-2" },
            ) {
                secondRan = true
                "second-done"
            }
        }
        runCurrent()
        assertTrue(!secondRan, "second transfer entered its body while the first still held the permit")

        releaseFirst.complete(Unit)
        advanceUntilIdle()
        first.join()
        second.join()
        assertTrue(secondRan, "second transfer never ran after the permit was released")
    }

    // ── runner construction / no hidden I/O ──────────────────────────────

    @Test
    fun `preamble performs no network IO - the HEAD probe belongs to execute`() = runTest {
        val proceed = assertIs<DownloadTransferGate.Preparation>(prepare())

        assertTrue(client.requests.isEmpty(), "the preamble must not probe; the HEAD probe lives in execute")
        // The handed-over runner is a real transfer runner wired to the shared
        // client, ready for execute's probe + dispatch.
        assertIs<DownloadTransferRunner>(proceed.runner)
    }

    // ── execute: the shared post-preamble tail ─────────────────────────────

    // A real temp file the runner path writes into (the single-connection
    // transfer opens a FileOutputStream at entity.downloadPath).
    private val tempFile = Files.createTempFile("download-gate-test", ".bin").toFile()

    private val notifications = RecordingNotifications()

    private fun entity(
        status: String = DownloadStatus.DOWNLOADING.name,
        downloadedBytes: Long = 0L,
    ) = DownloadEntity(
        id = "dl-1",
        mediaItemId = "media-1",
        name = "Test Movie",
        mediaType = "Movie",
        downloadPath = tempFile.absolutePath,
        downloadUrl = "https://example/item/stream",
        totalSizeBytes = 0L,
        downloadedBytes = downloadedBytes,
        status = status,
    )

    /** A [Preparation] as [gate.runTransfer] would hand it over. */
    private fun preparation(numConnections: Int = 4) = DownloadTransferGate.Preparation(
        runner = DownloadTransferRunner(
            dao = dao,
            client = client,
            isStopped = { false },
            updateForeground = { _, _, _, _, _, _ -> },
            dismissForeground = { },
        ),
        accessToken = "plain-token",
        numConnections = numConnections,
    )

    /** Records every port call in order so tests can pin call order/counts. */
    private class RecordingNotifications : DownloadTransferNotifications {
        val calls = mutableListOf<String>()

        override suspend fun showForeground(
            downloadId: String,
            notificationId: Int,
            name: String,
            progress: Int,
            downloadedBytes: Long,
            totalBytes: Long,
            speedBytesPerSec: Long,
        ) {
            calls += "showForeground"
        }

        override fun updateNotification(
            downloadId: String,
            notificationId: Int,
            name: String,
            progress: Int,
            downloadedBytes: Long,
            totalBytes: Long,
            speedBytesPerSec: Long,
        ) {
            calls += "updateNotification"
        }

        override fun dismissNotification(notificationId: Int) {
            calls += "dismissNotification"
        }

        override fun refreshSummary(inFlightCount: Int) {
            calls += "refreshSummary:$inFlightCount"
        }
    }

    /** Parses `bytes=start-end` (one multi-connection chunk split). */
    private fun rangeBounds(range: String): Pair<Long, Long> {
        val (start, end) = range.removePrefix("bytes=").split("-")
        return start.toLong() to end.toLong()
    }

    @Test
    fun `execute re-probes on resume and hands the probe result to the runner`() = runTest {
        // HEAD: authoritative 1024. GET: a 206 resume whose body carries NO
        // total (chunked) — completion only lands if the probe result flowed
        // through as probedTotalSize (the integrity check's size fallback).
        client.enqueue(
            FakeDownloadTransferClient.Reply.Status(code = 200, totalSize = 1024L),
            FakeDownloadTransferClient.Reply.Status(code = 206, body = ByteArray(924), totalSize = null),
        )

        val outcome = gate.execute(
            preparation = preparation(),
            entity = entity(downloadedBytes = 100L),
            existingBytes = 100L,
            notificationId = 7,
            notifications = notifications,
        )

        assertEquals(TransferOutcome.Success, outcome)
        // Exactly one HEAD re-probe, then the Range resume GET.
        assertEquals(2, client.requests.size)
        assertTrue(client.requests[0].head, "the first request must be the HEAD re-probe")
        assertEquals("bytes=100-", client.requests[1].range)
        coVerify { dao.updateProgressWithSpeed("dl-1", 1024L, DownloadStatus.COMPLETED.name, 0L) }
        // Resume never re-writes the total (updateTotalSize is fresh-start-only).
        coVerify(exactly = 0) { dao.updateTotalSize(any(), any()) }
        // The healed summary refresh reached the port on the resume path too.
        assertEquals(listOf("refreshSummary:1"), notifications.calls)
    }

    @Test
    fun `execute dispatches multi-connection strictly above the threshold with multiple connections`() = runTest {
        val total = DownloadTransferRunner.MIN_MULTI_SIZE + 1
        client.replyByRequest { request ->
            if (request.head) {
                FakeDownloadTransferClient.Reply.Status(code = 200, totalSize = total)
            } else {
                val (start, end) = rangeBounds(request.range!!)
                FakeDownloadTransferClient.Reply.Status(
                    code = 206,
                    body = ByteArray((end - start + 1).toInt()),
                    totalSize = total,
                )
            }
        }

        val outcome = gate.execute(
            preparation = preparation(numConnections = 4),
            entity = entity(),
            existingBytes = 0L,
            notificationId = 7,
            notifications = notifications,
        )

        assertEquals(TransferOutcome.Success, outcome)
        assertEquals(total, tempFile.length())
        coVerify { dao.updateProgressWithSpeed("dl-1", total, DownloadStatus.COMPLETED.name, 0L) }
        // The multi path is identifiable through the port (the strategy's
        // start promotion), and the healed summary refresh PRECEDED it —
        // execute pushes it through the port before the dispatch on BOTH
        // paths (the former divergence: the runner path refreshed alongside
        // its promotions, the multi path's start promotion did not).
        assertTrue("showForeground" in notifications.calls)
        assertEquals("refreshSummary:1", notifications.calls.first())
    }

    @Test
    fun `execute stays single-connection when the probe hits the threshold exactly`() = runTest {
        val total = DownloadTransferRunner.MIN_MULTI_SIZE // strict > — at the bound it's the runner
        client.enqueue(
            FakeDownloadTransferClient.Reply.Status(code = 200, totalSize = total),
            FakeDownloadTransferClient.Reply.Status(code = 200, body = ByteArray(total.toInt()), totalSize = total),
        )

        val outcome = gate.execute(
            preparation = preparation(numConnections = 4),
            entity = entity(),
            existingBytes = 0L,
            notificationId = 7,
            notifications = notifications,
        )

        assertEquals(TransferOutcome.Success, outcome)
        // One probe + one plain GET — the strategy never ran.
        assertEquals(2, client.requests.size)
        assertEquals(null, client.requests[1].range)
        assertTrue("showForeground" !in notifications.calls, "the multi-connection strategy must not run at the threshold")
        // ...and the fresh single path still received the entry summary refresh.
        assertEquals(listOf("refreshSummary:1"), notifications.calls)
    }

    @Test
    fun `execute stays single-connection above the threshold when only one connection is allowed`() = runTest {
        val total = 4096L // small body; the dispatch hinges on numConnections = 1
        client.enqueue(
            FakeDownloadTransferClient.Reply.Status(code = 200, totalSize = total),
            FakeDownloadTransferClient.Reply.Status(code = 200, body = ByteArray(total.toInt()), totalSize = total),
        )

        val outcome = gate.execute(
            preparation = preparation(numConnections = 1),
            entity = entity(),
            existingBytes = 0L,
            notificationId = 7,
            notifications = notifications,
        )

        assertEquals(TransferOutcome.Success, outcome)
        assertEquals(2, client.requests.size)
        assertTrue("showForeground" !in notifications.calls, "one connection must never split the file")
    }

    @Test
    fun `execute propagates cancellation without classifying it`() = runTest {
        client.replyByRequest { _ ->
            FakeDownloadTransferClient.Reply.ThrowAny(CancellationException("scope cancelled"))
        }

        assertFailsWith<CancellationException> {
            gate.execute(
                preparation = preparation(),
                entity = entity(),
                existingBytes = 0L,
                notificationId = 7,
                notifications = notifications,
            )
        }
        // A control signal is not a download failure: no policy application
        // whatsoever — no status write, no error message, no retry increment.
        coVerify(exactly = 0) { dao.updateProgressWithSpeed(any(), any(), any(), any()) }
        coVerify(exactly = 0) { dao.updateErrorMessage(any(), any()) }
        coVerify(exactly = 0) { dao.updatePausedReason(any(), any()) }
        coVerify(exactly = 0) { dao.incrementRetryCount(any()) }
    }

    @Test
    fun `execute classifies a thrown failure through DownloadFailurePolicy with the outer flags`() = runTest {
        // Resume path so the preserved-bytes flag is observable: the HEAD probe
        // succeeds, then the GET throws a non-IO RuntimeException — the runner's
        // internal router catches IOException only, so the throwable reaches
        // execute's outer classification.
        client.enqueue(
            FakeDownloadTransferClient.Reply.Status(code = 200, totalSize = 1024L),
            FakeDownloadTransferClient.Reply.ThrowAny(RuntimeException("boom")),
        )
        coEvery { dao.getDownloadById("dl-1") } returns entity(status = DownloadStatus.DOWNLOADING.name)

        val outcome = gate.execute(
            preparation = preparation(),
            entity = entity(downloadedBytes = 50L),
            existingBytes = 50L,
            notificationId = 7,
            notifications = notifications,
        )

        // decide(RuntimeException, madeProgress = false, isResumablePartial = true)
        // → MarkFailed("boom", deletePartial = false, shouldRetry = false).
        assertEquals(TransferOutcome.Fail, outcome)
        // madeProgress = false: the row keeps the bytes it held at start.
        coVerify { dao.updateProgressWithSpeed("dl-1", 50L, DownloadStatus.FAILED.name, 0L) }
        coVerify { dao.updateErrorMessage("dl-1", "boom") }
        // shouldRetry = false → no retry-budget increment.
        coVerify(exactly = 0) { dao.incrementRetryCount("dl-1") }
        // isResumablePartial = true: the single-connection partial is kept.
        assertTrue(tempFile.exists(), "the partial file must survive the outer classification")
    }
}
