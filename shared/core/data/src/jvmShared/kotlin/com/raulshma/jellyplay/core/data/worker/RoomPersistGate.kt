package com.raulshma.jellyplay.core.data.worker

/**
 * The Room persist gate shared by the two live-progress loops —
 * [DownloadTransferRunner] (single-connection) and
 * [MultiConnectionDownloadStrategy] — extracted after the two hand-copied
 * twins drifted on nothing but variable names. Each `updateProgressWithSpeed`
 * write invalidates every observing downloads-table Flow, so a transfer may
 * persist at most every [DownloadTransferRunner.DB_PERSIST_INTERVAL_MS] AND
 * only on a >= [DownloadTransferRunner.PERSIST_MIN_DELTA_PERCENT]% move of a
 * known total since the last persist; an unknown total ([totalSize] <= 0)
 * falls back to the time gate alone. The ~2 s notification cadence is
 * unaffected — this gate throttles only the row writes.
 *
 * Both loops construct one gate at transfer start ([initialBytes] seeds the
 * byte baseline — the resume offset on the single-connection path) and call
 * [shouldPersist] per tick; a `true` return has already advanced both gates,
 * so the caller writes the row immediately after. The cancel/failure/
 * completion paths persist immediately, outside this gate (multi-connection
 * partials are never resumed, so the gate only throttles live progress there;
 * the single-connection stop checkpoint flushes and writes PAUSED directly).
 */
internal class RoomPersistGate(
    private val totalSize: Long,
    initialBytes: Long = 0L,
) {
    private var lastPersistAt = System.currentTimeMillis()
    private var lastPersistedBytes = initialBytes

    /**
     * Whether a row write is due at [now] for [downloadedBytes]; advances the
     * time and byte baselines when it is, so the caller persists right after.
     */
    fun shouldPersist(now: Long, downloadedBytes: Long): Boolean {
        val percentDelta = if (totalSize > 0L) {
            (downloadedBytes - lastPersistedBytes) * 100L / totalSize
        } else Long.MAX_VALUE
        val due = now - lastPersistAt >= DownloadTransferRunner.DB_PERSIST_INTERVAL_MS &&
            percentDelta >= DownloadTransferRunner.PERSIST_MIN_DELTA_PERCENT
        if (due) {
            lastPersistAt = now
            lastPersistedBytes = downloadedBytes
        }
        return due
    }
}
