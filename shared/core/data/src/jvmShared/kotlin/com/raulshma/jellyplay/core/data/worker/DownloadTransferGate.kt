package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.data.repository.DownloadFailurePolicy
import com.raulshma.jellyplay.core.data.repository.DownloadStates
import com.raulshma.jellyplay.core.data.repository.applyTo
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

/**
 * The per-row transfer choreography shared by the two download orchestrators —
 * Android's [DownloadWorker] and desktop's [DesktopDownloadManager] — extracted
 * after their bodies drifted into pasted twins (same statements, same
 * comments). BOTH halves live here; the orchestrators are thin callers that
 * keep only their platform surfaces:
 *
 *  1. [runTransfer] — the preamble: everything between "row is QUEUED" and
 *     "transfer body may start".
 *  2. [execute] — the post-preamble tail: the HEAD probe, the resume-vs-fresh
 *     dispatch (single- vs multi-connection), the cancellation re-throw, and
 *     the outer failure classification through [DownloadFailurePolicy].
 *
 * The preamble, step by step:
 *
 *  1. **Credential resolution** — read `activeUserId`, load the stored
 *     (encrypted) access token, and decrypt it for use as the
 *     `Authorization: MediaBrowser` header value (tokens are stored encrypted
 *     in Room; decrypt before use).
 *  2. **Connection coerce** — `downloadConnections.coerceIn(1, 8)`; the
 *     per-file parallel-stream count the strategy consumes.
 *  3. **Permit gate** — the whole choreography plus the caller's [transfer]
 *     body hold one shared [DownloadConcurrencyLimiter] slot, so at most
 *     `maxConcurrentDownloads` transfers run at once; the rest block here.
 *     Releasing after the preamble alone would uncap the actual transfers —
 *     the pre-extraction code kept the entire transfer inside `withPermit`.
 *  4. **Post-QUEUED re-check** — with a slot in hand, re-read the row's
 *     status: the user may have paused or cancelled while the row was
 *     QUEUED. An inactive row expresses through the caller's [onInactive]
 *     body so each orchestrator keeps its own outcome surface
 *     (Android: `TransferOutcome.Success`; desktop: plain return).
 *  5. **The DOWNLOADING write** — only after the re-check passes, and with
 *     the start bytes preserved for the resume path.
 *  6. **Handover** — the [DownloadTransferRunner] built with the
 *     platform-supplied lambdas is handed to the caller's [transfer] body
 *     together with the resolved token and connection count; that body
 *     (normally just [execute]) runs INSIDE the same permit (see 3).
 *
 * **What deliberately stays at the call sites (per-platform surfaces):**
 *
 *  - The `QUEUED` write and the limiter's `configure(maxConcurrentDownloads)`
 *    re-size both happen BEFORE this gate — Android wraps its QUEUED write in
 *    a foreground promotion with a `Result.retry()` recovery path; desktop
 *    writes the row bare (no notification surface).
 *  - The runner-path foreground/stop plumbing handed to [runTransfer]:
 *    Android's `updateForeground` re-promotes + refreshes the summary per
 *    progress tick and `dismissForeground` cancels the shade item; desktop
 *    passes no-op lambdas (no foreground surface) and its cooperative
 *    `TransferHandle` stop flag for `isStopped`.
 *  - The multi-path [DownloadTransferNotifications] adapter + notification id
 *    handed to [execute]: Android derives a real notification id from the row
 *    and maps the port onto `DownloadNotificationHelper` + `setForeground`;
 *    desktop threads a stable dummy id of 0 and a no-op port.
 *  - Outcome mapping only: Android maps [TransferOutcome] onto WorkManager
 *    results (`Result.success/retry/failure`); desktop additionally re-kicks
 *    `Retry` outcomes through its backoff scheduler and clears the retry
 *    budget on `Success`.
 *
 * The PREAMBLE ([runTransfer]) performs no network I/O of its own — the HEAD
 * probe belongs to [execute] (pinned by DownloadTransferGateTest).
 *
 * Construction is inline from deps the orchestrators already hold — no Koin
 * involvement (the gate is stateless per orchestrator).
 */
class DownloadTransferGate(
    private val dao: DownloadDao,
    private val userDao: UserDao,
    private val serverIdentityStore: ServerIdentityStore,
    private val downloadsStore: DownloadsStore,
    private val tokenCipher: TokenCipher,
    private val concurrencyLimiter: DownloadConcurrencyLimiter,
    private val transferClient: DownloadTransferClient,
) {

    /** What the [transfer][runTransfer] body runs with: runner, token, connection count. */
    data class Preparation(
        val runner: DownloadTransferRunner,
        /** Decrypted access token (null when no active user / no stored token). */
        val accessToken: String?,
        /** `downloadConnections` coerced to the strategy's 1..8 domain. */
        val numConnections: Int,
    )

    /**
     * Runs the preamble choreography and the caller's transfer body inside
     * ONE concurrency permit (held until [transfer] returns, so
     * `maxConcurrentDownloads` caps actual transfers). [existingBytes] is the
     * row's start offset, re-written verbatim into the DOWNLOADING progress
     * write. The notification lambdas and [isStopped] are platform-supplied
     * (see the class KDoc's divergence list). A row paused/cancelled while
     * QUEUED never reaches [transfer]; it resolves through [onInactive]
     * instead, letting each orchestrator keep its own early-out surface.
     */
    suspend fun <T> runTransfer(
        downloadId: String,
        existingBytes: Long,
        isStopped: () -> Boolean,
        updateForeground: suspend (name: String, progress: Int, downloaded: Long, total: Long, speed: Long, notificationId: Int) -> Unit,
        dismissForeground: suspend (notificationId: Int) -> Unit,
        onInactive: suspend () -> T,
        transfer: suspend (Preparation) -> T,
    ): T {
        val activeUserId = serverIdentityStore.activeUserId.firstOrNull()
        val accessToken = activeUserId?.let { uid ->
            // Tokens are stored encrypted in Room. Decrypt before use as the
            // `Authorization: MediaBrowser` header value.
            tokenCipher.decrypt(userDao.getUserById(uid)?.accessToken)
        }

        val numConnections = downloadsStore.downloads.value.downloadConnections.coerceIn(1, 8)

        // Hold the permit for the WHOLE transfer — re-check, DOWNLOADING
        // write, and the caller's transfer body — so at most
        // `maxConcurrentDownloads` run at once; the rest block here.
        return concurrencyLimiter.withPermit {
            // Re-check status now that a slot is ours: the user may have paused
            // or cancelled while the row was QUEUED.
            val statusAfterQueue = dao.getStatus(downloadId)
            if (DownloadStates.isInactive(statusAfterQueue)) {
                onInactive()
            } else {
                dao.updateProgress(downloadId, existingBytes, DownloadStatus.DOWNLOADING.name)
                transfer(
                    Preparation(
                        runner = DownloadTransferRunner(
                            dao = dao,
                            client = transferClient,
                            isStopped = isStopped,
                            updateForeground = updateForeground,
                            dismissForeground = dismissForeground,
                        ),
                        accessToken = accessToken,
                        numConnections = numConnections,
                    ),
                )
            }
        }
    }

    /**
     * Runs the shared post-preamble tail (normally as the [runTransfer]
     * `transfer` body, i.e. still inside the caller's permit): the HEAD probe,
     * the resume-vs-fresh dispatch, the cancellation re-throw, and the outer
     * failure classification. Both orchestrators previously hand-copied this
     * ladder byte-for-byte; it now has one home.
     *
     *  - **Resume** ([existingBytes] > 0): re-probe the authoritative size so
     *    the runner's integrity check can catch a truncated transcoded stream
     *    (the resume path once skipped the probe and shipped short files as
     *    COMPLETED), then transfer from the persisted offset.
     *  - **Fresh**: probe once, then dispatch — multi-connection
     *    ([MultiConnectionDownloadStrategy]) when the probed size exceeds
     *    [DownloadTransferRunner.MIN_MULTI_SIZE] AND
     *    [Preparation.numConnections] > 1; the single-connection runner
     *    otherwise (both thresholds are strict).
     *  - **Cancellation** ([CancellationException]) propagates untouched — a
     *    structured-concurrency control signal, never a download failure.
     *  - Any other [Throwable] routes through [DownloadFailurePolicy.decide]
     *    with the documented outer-path flags — `madeProgress = false`
     *    (pre-body failures: HEAD probe, request build — wrote nothing this
     *    run) and `isResumablePartial = true` (the outer catch only guards the
     *    single-connection strategy; the multi strategy classifies its own
     *    throwables with `false`) — then applies the outcome to the row with
     *    [existingBytes] preserved and maps it onto [TransferOutcome].
     *
     * **Declared fix of a former divergence:** the runner path refreshed the
     * notification group summary alongside its foreground promotions
     * (Android's `updateForeground`) while the multi-connection path's start
     * promotion did not refresh anything. [execute] now pushes ONE
     * start-of-transfer summary refresh through [notifications] on BOTH
     * paths (Android: the helper's summary refresh; desktop: the no-op
     * port). The strategy keeps its own lifecycle refreshes and the runner
     * path keeps its per-tick refresh in the platform lambda.
     */
    suspend fun execute(
        preparation: Preparation,
        entity: DownloadEntity,
        existingBytes: Long,
        notificationId: Int,
        notifications: DownloadTransferNotifications,
    ): TransferOutcome {
        val runner = preparation.runner
        val accessToken = preparation.accessToken
        return try {
            // The start-of-transfer summary refresh rides the notifications
            // port on BOTH paths — the healed divergence (see KDoc). Best-effort:
            // a notify failure must never be classified as a transfer failure.
            runCatchingRethrowingCancellation {
                notifications.refreshSummary(dao.getInFlightDownloadCount())
            }
            if (existingBytes > 0L) {
                // Resume: re-probe the authoritative size so the integrity
                // check in the runner can catch a truncated stream — a
                // transcoded resume could otherwise complete short of the
                // true size and ship a truncated file as COMPLETED.
                val probedSize = runner.probeContentSize(entity.downloadUrl, accessToken)
                runner.transfer(
                    entity = entity,
                    existingBytes = existingBytes,
                    notificationId = notificationId,
                    accessToken = accessToken,
                    probedTotalSize = probedSize,
                )
            } else {
                val totalSize = runner.probeContentSize(entity.downloadUrl, accessToken)
                if (totalSize > DownloadTransferRunner.MIN_MULTI_SIZE && preparation.numConnections > 1) {
                    MultiConnectionDownloadStrategy.execute(
                        downloadClient = transferClient, // same seam as the single-connection path
                        dao = dao,
                        downloadId = entity.id,
                        entity = entity,
                        totalSize = totalSize,
                        numConnections = preparation.numConnections,
                        notificationId = notificationId,
                        accessToken = accessToken,
                        notifications = notifications,
                    )
                } else {
                    runner.transfer(
                        entity = entity,
                        existingBytes = 0L,
                        notificationId = notificationId,
                        accessToken = accessToken,
                        probedTotalSize = totalSize,
                    )
                }
            }
        } catch (e: CancellationException) {
            // Structured-concurrency control signal, not a download failure —
            // the parent scope (user navigated away, app dying) was cancelled.
            // Must propagate; classifying it would break cancellation.
            throw e
        } catch (e: Throwable) {
            // Single home for the failure-classification rule: DownloadFailurePolicy.
            // Pre-body failures (HEAD probe, request build) wrote nothing this run,
            // so madeProgress = false; existingBytes is what the row held at start.
            val row = dao.getDownloadById(entity.id)
            val status = row?.status ?: DownloadStatus.PENDING.name
            val policyOutcome = DownloadFailurePolicy.decide(
                error = e,
                madeProgress = false,
                currentStatus = status,
                isResumablePartial = true, // single-connection strategy for the outer path
            )
            policyOutcome.applyTo(dao, entity.id, File(entity.downloadPath), existingBytes)
            policyOutcome.toTransferOutcome()
        }
    }
}
