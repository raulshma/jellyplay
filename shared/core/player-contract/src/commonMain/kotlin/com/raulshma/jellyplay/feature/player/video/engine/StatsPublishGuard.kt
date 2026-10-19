package com.raulshma.jellyplay.feature.player.video.engine

/**
 * The ONE stats change-guard behind the engines' per-tick `updateVideoStats`
 * funnels — the hand-copied "cheap scalars unchanged → skip the full read"
 * ladders of `ExoPlayerEngine` (AnalyticsListener counters + bandwidth
 * estimate) and `MpvPlayerEngine` (JNI/JNA property reads), extracted before
 * a third host copied them. Only the READS differ per engine; the guard owns
 * the shared decision: sample-to-sample equality of the cheap scalars, plus
 * an optional forced full re-read cadence for hosts whose un-sampled fields
 * can move while the scalars freeze.
 *
 * The load-bearing contract both call sites preserve ([admit]'s values are
 * the caller's decision, the guard never reads a source itself):
 * - **Values are passed in their PUBLISHED shape.** Exo computes its HDR fold
 *   (the observed-SDR downgrade of `hdrOutputExpected`, the notice, the
 *   display-capability summary) BEFORE the guard and compares the folded
 *   values — a guard keyed on the raw decision would swallow an
 *   `hdrOutputActive` flip that happens while every counter stays frozen.
 *   mpv's fold is NOT pre-computed (it costs JNI reads), so its sample is
 *   scalar-only and its cadence arm (below) is what eventually admits a
 *   frozen-scalar tick for the fold to catch up. Same guard, declared
 *   per-host sample.
 * - **Sampled values are published verbatim by both adapters** (the scalars
 *   flow into `EngineVideoStats` untouched), so sample-to-sample equality is
 *   exactly the former comparison against the last published stats' fields —
 *   and admit ⇒ the final [publishStatsIfChanged] equality funnel emits (a
 *   differing sampled value is published, so the full stats cannot equal the
 *   last ones).
 * - **The cadence clock ticks on ADMIT, not on publish** (mpv's former
 *   `lastFullStatsReadMs = nowMs` placement): the funnel may still suppress
 *   the EMIT after an admitted full read, but the read itself was the cost
 *   being bounded — the next full read is due a full cadence later.
 * - **[reset] on item switch** (Exo's `resetStatsGuard` site): a new item's
 *   first tick must never be suppressed by the previous item's sample.
 *
 * Pinned by `StatsPublishGuardTest` (player-contract commonTest).
 */
class StatsPublishGuard(
    /**
     * Upper bound on how long a frozen sample may suppress the full read,
     * in milliseconds; `null` disables the cadence (Exo: nothing behind its
     * guard is expensive enough to gate, so a changed sample is the only
     * admit path).
     */
    private val fullRereadCadenceMs: Long?,
    /** Monotonic clock for the cadence arm (Android: `SystemClock.elapsedRealtime`). */
    private val nowMs: () -> Long,
) {
    /** The previous tick's published-shape sample; `null` until the first admit. */
    private var lastSample: List<Any?>? = null

    private var lastAdmitMs: Long = Long.MIN_VALUE

    /**
     * Whether this tick must proceed to the full read + publish funnel.
     * Admits when never admitted, when any sampled value moved (equality
     * with `null` compared — an unset capability flipping to a value is a
     * move), or when [fullRereadCadenceMs] has elapsed since the last admit;
     * otherwise records nothing and returns false (the cheap path).
     */
    fun admit(vararg scalars: Any?): Boolean {
        val now = nowMs()
        val last = lastSample
        val changed = last == null || last.size != scalars.size ||
            last.indices.any { last[it] != scalars[it] }
        val cadenceDue = fullRereadCadenceMs?.let { now - lastAdmitMs >= it } ?: false
        if (!changed && !cadenceDue) return false
        lastSample = scalars.toList()
        lastAdmitMs = now
        return true
    }

    /** Clears the sample and the cadence clock — the per-item reset. */
    fun reset() {
        lastSample = null
        lastAdmitMs = Long.MIN_VALUE
    }
}
