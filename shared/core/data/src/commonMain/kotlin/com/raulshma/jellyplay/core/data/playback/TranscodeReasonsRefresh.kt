package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Transcode-reason lookups: the server registers a session's TranscodingInfo
 * a beat after playback starts, so wait before the first fetch and retry once
 * before giving up (a miss just leaves the reasons list empty).
 */
private const val TRANSCODE_REASONS_FIRST_DELAY_MS = 2_000L
private const val TRANSCODE_REASONS_RETRY_DELAY_MS = 2_500L

/**
 * Shared wait-then-fetch-then-retry-once refresh of a live session's
 * transcode reasons, used by both the VOD player (PlayerSessionManager) and
 * the Live TV player. Callers cancel/replace the returned [Job] per
 * resolution or tune.
 *
 * @param fetchReasons resolves the server's current reason tokens (usually
 *   [com.raulshma.jellyplay.core.data.repository.PlaybackRepository.fetchActiveTranscodeReasons]).
 * @param isCurrent stale-write guard: reasons are only applied while the
 *   caller still shows [itemId].
 * @param onReasons applies the fetched reasons to the caller's state.
 */
fun CoroutineScope.launchTranscodeReasonsRefresh(
    itemId: String,
    fetchReasons: suspend (String) -> List<String>,
    isCurrent: () -> Boolean,
    onReasons: (List<String>) -> Unit,
): Job = launch {
    delay(TRANSCODE_REASONS_FIRST_DELAY_MS)
    var reasons = fetchReasons(itemId)
    if (reasons.isEmpty()) {
        delay(TRANSCODE_REASONS_RETRY_DELAY_MS)
        reasons = fetchReasons(itemId)
    }
    if (isCurrent()) onReasons(reasons)
}

/**
 * Owns the per-player transcode-reason lookup slot: the cancel/replace of
 * the in-flight [launchTranscodeReasonsRefresh] job plus the shared
 * orchestration around it (drop stale reasons, skip non-transcode
 * resolutions). Used by both the VOD player (PlayerSessionManager) and the
 * Live TV player so their tune/re-resolve paths stay in lockstep.
 */
class TranscodeReasonsRefresher(
    private val scope: CoroutineScope,
    private val fetchReasons: suspend (String) -> List<String>,
) {
    private var job: Job? = null

    /** Cancels any in-flight lookup so its result can no longer land. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /**
     * Drops the previous resolution's reasons via [clear] and, when the new
     * resolution is a transcode ([isTranscode]) with a live [itemId]), re-arms
     * the shared wait/retry-once fetch whose result lands via [onReasons]
     * while [isCurrent] still holds. A transcode→transcode re-resolve
     * (quality change, engine fallback) must not keep showing the previous
     * item's reasons while the new fetch settles — hence the unconditional
     * [clear].
     */
    fun refresh(
        itemId: String?,
        isTranscode: Boolean,
        isCurrent: () -> Boolean,
        clear: () -> Unit,
        onReasons: (List<String>) -> Unit,
    ) {
        cancel()
        clear()
        if (itemId == null || !isTranscode) return
        job = scope.launchTranscodeReasonsRefresh(
            itemId,
            fetchReasons = fetchReasons,
            isCurrent = isCurrent,
            onReasons = onReasons,
        )
    }
}
