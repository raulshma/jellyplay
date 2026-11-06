package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.model.ExoBufferPosture

/**
 * The Media3 `DefaultLoadControl.setTargetBufferBytes` sentinel: "derive the
 * target from the selected tracks". The same `-1` (`C.LENGTH_UNSET`) value
 * [LoadControlNumbers.targetBufferBytes] carries verbatim — defined here so
 * the conversion below and the plan's sentinel prose share one symbol.
 */
internal const val EXO_TARGET_BUFFER_AUTO_BYTES = -1

/** Bytes per MiB, spelled out so the multiplication below reads at a glance. */
private const val BYTES_PER_MIB = 1024L * 1024L

/**
 * The persisted playback-slice MB pref → the Media3 target-buffer-bytes
 * domain. ONE conversion for the whole feed (`EngineConfigBuilder`'s Exo
 * fold is its only caller) so the sentinel and the overflow posture cannot
 * drift between build paths.
 *
 * Semantics:
 *  - `mb <= 0` → [EXO_TARGET_BUFFER_AUTO_BYTES] — the store's `0` is the
 *    AUTO sentinel ([com.raulshma.jellyplay.core.datastore.playback] spec
 *    prose); it MUST NOT reach Media3 as a raw `0`, which is an absolute
 *    zero-byte target (playback would starve). Negatives never persist (the
 *    store setter clamps to `0` or 5–512), but they fold to AUTO too — a
 *    hand-edited/stale blob degrades to the engine default, never to a
 *    broken target.
 *  - Otherwise `mb * 1 MiB`, computed in Long and saturated at
 *    [Int.MAX_VALUE]: the declared pref range (5–512) is far inside Int
 *    (2047 MiB is the largest exact value), so the guard only fires on
 *    out-of-range data — where a silent Int overflow (wrapping negative)
 *    would hand Media3 a garbage negative target, and saturation is the
 *    least-surprising repair. The plan fold deliberately does NOT clamp the
 *    result further (see [LoadControlNumbers]' sentinel prose: any
 *    non-negative value is an absolute byte target).
 *
 * Pure and jvmTest-pinnable ([ExoBufferBytesTest]).
 */
internal fun exoTargetBufferBytesFromMb(mb: Int): Int {
    if (mb <= 0) return EXO_TARGET_BUFFER_AUTO_BYTES
    val bytes = mb.toLong() * BYTES_PER_MIB
    return if (bytes > Int.MAX_VALUE) Int.MAX_VALUE else bytes.toInt()
}

/**
 * The playback slice's four Exo buffer rows → the engine-facing
 * [ExoBufferPosture] — the ONE extraction, so the engine-config fold
 * (`EngineConfigBuilder`'s Exo arm) and the diagnostics surfaces (the stats
 * overlay's Buffer rows, via the ViewModel) cannot drift. The MB row goes
 * through the shared [exoTargetBufferBytesFromMb] conversion (`0` → the
 * derive-from-tracks sentinel, never a raw `0`).
 */
internal fun PlaybackSlice.exoBufferPosture(): ExoBufferPosture =
    ExoBufferPosture(
        initialBufferMs = exoInitialBufferMs,
        rebufferBufferMs = exoRebufferMs,
        targetBufferBytes = exoTargetBufferBytesFromMb(exoTargetBufferMb),
        retainBackBuffer = exoRetainBackBuffer,
    )
