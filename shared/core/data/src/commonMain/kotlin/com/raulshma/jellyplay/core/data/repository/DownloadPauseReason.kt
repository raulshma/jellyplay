package com.raulshma.jellyplay.core.data.repository

/**
 * Deep module: the vocabulary for why a download was paused, plus the
 * auto-retry budget that bounds reconnect-driven resume attempts.
 *
 * Previously these were `const val` strings living on the 1058-LOC
 * [DownloadRepositoryImpl]'s companion object, referenced across the seam by
 * [com.raulshma.jellyplay.core.data.worker.DownloadWorker] (which reached in as
 * `DownloadRepositoryImpl.PAUSE_REASON_NETWORK`). That made the worker depend on
 * the repository's companion for its own state vocabulary. Centralizing them
 * here gives the pause-reason taxonomy a single home and stops the cross-module
 * companion leak.
 *
 * Persisted as the `pausedReason` column string on `downloads`; the column
 * stays a String for Room migration safety, so [persistedValue] round-trips.
 *
 *   - [USER] — a long-press Pause. The reconnect auto-resume leaves these alone.
 *   - [NETWORK] — an in-flight transfer interrupted by a connectivity drop.
 *     The reconnect auto-resume resumes only these.
 */
enum class DownloadPauseReason(val persistedValue: String) {
    USER("USER"),
    NETWORK("NETWORK");

    companion object {
        /** Parses a stored `pausedReason` column value back to the enum. */
        fun fromPersisted(value: String?): DownloadPauseReason? =
            entries.firstOrNull { it.persistedValue == value }
    }
}

/**
 * Maximum auto-resume attempts the reconnect listener will make for a single
 * download before dead-lettering it (leaving it FAILED for a manual retry).
 * Mirrors the playback-outbox dead-letter budget.
 *
 * The listener enqueues fresh WorkManager jobs (KEEP policy) that bypass
 * WorkManager's own run-attempt cap, so without this DB-side budget a
 * persistently failing download (storage full, 404, auth) would re-attempt on
 * every reconnect indefinitely.
 */
const val DOWNLOAD_MAX_AUTO_RETRY: Int = 3
