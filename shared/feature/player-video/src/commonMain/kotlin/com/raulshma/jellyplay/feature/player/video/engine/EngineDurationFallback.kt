package com.raulshma.jellyplay.feature.player.video.engine

/**
 * The shared duration fallback ladder hoisted from the twin `durationMs`
 * getters (ExoPlayerEngine / MpvPlayerEngine): prefer the engine-resolved
 * duration when it reports a positive value, else the server-reported
 * runTimeTicks — for HLS/transcoded streams the only accurate total-runtime
 * source (the engines' own duration is unset or only partially resolved
 * unless the manifest advertises a finite VOD duration, leaving the seek bar
 * and end-detection without a duration).
 *
 * Engine-specific invalid sentinels (Media3's C.TIME_UNSET) are negative, so
 * the caller can pass them through and the >0 check rejects them.
 */
internal fun resolveDurationMs(engineDurationMs: Long, serverDurationMs: Long): Long =
    if (engineDurationMs > 0L) engineDurationMs else serverDurationMs
