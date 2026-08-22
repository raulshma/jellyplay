package com.raulshma.jellyplay.core.model

/**
 * Wall-clock milliseconds since the epoch (`System.currentTimeMillis` on
 * Android/desktop, `Date.now()` on web). For timestamps that persist in
 * models/preferences; never for measuring durations (use
 * [monotonicNowMillis]).
 */
expect fun wallNowMillis(): Long

/**
 * Monotonic milliseconds since an arbitrary fixed origin — never wall-clock.
 * Backs [TtlCache] TTL math: on Android this is `SystemClock.elapsedRealtime`
 * (survives deep sleep); on desktop JVM `System.nanoTime`; on web a monotonic
 * time source anchored at module load.
 */
expect fun monotonicNowMillis(): Long

/**
 * The device model string reported to the Jellyfin server (session names,
 * device identities). Android reads `Build.MODEL`; desktop reads `os.name`;
 * web reports a constant. Injectable-free by design: callers format, the
 * platform supplies.
 */
expect fun deviceModel(): String
