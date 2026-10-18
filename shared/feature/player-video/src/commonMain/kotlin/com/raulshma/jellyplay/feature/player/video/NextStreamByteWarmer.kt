package com.raulshma.jellyplay.feature.player.video

/**
 * androidMain-owned seam for the next-video-item byte pre-warm (the video
 * sibling of the audio side's AudioPrefetchEngine warming): streams at most
 * [NextStreamByteWarmer.warmStreamHead] `maxBytes` of a direct-play/direct-
 * stream URL into the process-wide [com.raulshma.jellyplay.feature.player.video.engine.VideoStreamCache]
 * [androidx.media3.datasource.cache.SimpleCache], so the next item's real
 * load finds its first segments on disk instead of re-fetching them over the
 * network (the inter-item rebuffer the episode/queue advance otherwise pays).
 *
 * Kept as a one-method interface because the warmer is an androidMain fact
 * (media3 CacheDataSource + the shared OkHttp client) while its trigger lives
 * in commonMain ([VideoPlayerViewModel]); the nullable-defaulted constructor
 * slot follows the `videoQueue`/`cinemaModeOverride` precedent so test graphs
 * that don't wire it keep compiling (a null seam simply disables warming).
 *
 * Contract: never throws (failures are logged inside the androidMain impl and
 * swallowed — warming is a pure optimization); cancellation IS honored and
 * leaves whatever bytes already landed committed in the cache.
 */
fun interface NextStreamByteWarmer {

    /**
     * Warms at most [maxBytes] of [url]'s head into the video stream cache.
     * Returns without throwing regardless of outcome (cache unavailable,
     * network error, session-keyed URL — the last is refused at the cache
     * boundary and again by the caller's eligibility gate).
     */
    suspend fun warmStreamHead(url: String, maxBytes: Long)
}
