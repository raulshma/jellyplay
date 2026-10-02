package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.arr.ArrRedownloadResult
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepResult
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind

/**
 * The one delete & re-download step-ladder, shared by the Radarr (movie) and
 * Sonarr (episode) flows: lookup → hard DELETE_FILE gate → re-query VERIFY →
 * MONITOR if unmonitored → SEARCH. Everything service-specific — lookup abort
 * reasons, the verify re-query semantics (including Sonarr's
 * inconclusive-re-query WARNING branches) — lives in the [ArrServiceClient]
 * adapters; the engine owns only the step order and the generic step
 * messages, plus the two flow rules that genuinely differ between the
 * historical twins: the service display name interpolated into those
 * messages, and Sonarr's hard gate on a FAILED verify (Radarr's verify
 * failure is best-effort and the flow continues to completion).
 *
 * Extracted verbatim from [ArrRepositoryImpl] (which keeps the server
 * resolution, the fan-out first-success-past-DELETE_FILE arbitration in
 * `redownloadMedia`, and the `clientFor` dispatch) so the ladder is
 * drivable against a bare [ArrServiceClient] fake — no repository graph,
 * no Seerr stores.
 */
internal class ArrRedownloadEngine(
    private val client: ArrServiceClient,
    private val kind: ArrServiceKind,
    private val ref: ArrRedownloadRef,
) {
    suspend fun run(): ArrRedownloadResult {
        val service = client.serviceName
        val steps = mutableListOf<ArrRedownloadStepResult>()

        // Lookup: resolve the tracked item, or abort at the DELETE_FILE gate
        // with the service-specific reason (unresolvable ids, lookup error,
        // not tracked, episode not found).
        val item = when (val lookup = client.lookup(ref)) {
            is ArrRedownloadLookup.Found -> lookup.item
            is ArrRedownloadLookup.Aborted -> {
                steps += ArrRedownloadStepResult(
                    ArrRedownloadStep.DELETE_FILE,
                    ArrRedownloadStepStatus.FAILED,
                    lookup.message,
                )
                return ArrRedownloadResult(steps, isComplete = false)
            }
        }

        // Step 1: delete the file. No file → skip (already gone, not an error).
        if (item.fileId == 0) {
            steps += ArrRedownloadStepResult(
                ArrRedownloadStep.DELETE_FILE,
                ArrRedownloadStepStatus.SKIPPED,
                "No file to delete.",
            )
        } else {
            val deleteOk = client.deleteFile(item.fileId)
            steps += ArrRedownloadStepResult(
                ArrRedownloadStep.DELETE_FILE,
                if (deleteOk) ArrRedownloadStepStatus.SUCCESS else ArrRedownloadStepStatus.FAILED,
                if (deleteOk) null else "$service rejected the file delete.",
            )
            if (!deleteOk) return ArrRedownloadResult(steps, isComplete = false)
        }

        // Step 2: verify deleted via the service's own re-query (Sonarr
        // answers WARNING when the re-query is inconclusive).
        val verify = client.verifyDeleted(item)
        steps += verify
        // A FAILED verify is a hard gate on Sonarr only (file still present →
        // search would no-op); Radarr continues best-effort.
        if (kind == ArrServiceKind.SONARR && verify.status == ArrRedownloadStepStatus.FAILED) {
            return ArrRedownloadResult(steps, isComplete = false)
        }

        // Step 3: monitor only if not already monitored (idempotent otherwise).
        if (item.monitored) {
            steps += ArrRedownloadStepResult(
                ArrRedownloadStep.MONITOR,
                ArrRedownloadStepStatus.SKIPPED,
                "Already monitored.",
            )
        } else {
            val monOk = client.monitor(item.id)
            steps += ArrRedownloadStepResult(
                ArrRedownloadStep.MONITOR,
                if (monOk) ArrRedownloadStepStatus.SUCCESS else ArrRedownloadStepStatus.FAILED,
                if (monOk) null else "Failed to re-monitor.",
            )
        }

        // Step 4: search.
        val search = client.search(item.id)
        steps += ArrRedownloadStepResult(
            ArrRedownloadStep.SEARCH,
            if (search) ArrRedownloadStepStatus.SUCCESS else ArrRedownloadStepStatus.FAILED,
            if (search) "$service is searching for a new download." else "Search command failed.",
        )
        return ArrRedownloadResult(steps, isComplete = true)
    }
}
