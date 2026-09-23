package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.model.monotonicNowMillis
import com.raulshma.jellyplay.core.model.wallNowMillis
import java.time.LocalDate
import java.time.ZoneId

/**
 * Read-only seam over the system clocks, so time-aware logic (TTL gates,
 * refresh jitter, calendar windows) can be unit-tested by injecting a fake.
 *
 * The system implementation delegates to the platform seams in
 * `:shared:core:model` ([wallNowMillis] / [monotonicNowMillis]). The shared
 * test fake lives in core:data's jvmTest `testutil` package; other modules
 * keep their own next to their tests.
 *
 * split: the epoch-millis slice was promoted to the commonMain
 * [EpochMillisSource] seam (the promoted commonMain repository impls take
 * that type — java.time cannot cross into commonMain), so this interface
 * extends it. Every existing [TimeSource] fake across the repo therefore
 * satisfies [EpochMillisSource] unchanged, and the `today(zone)` java.time
 * surface stays available to the JVM-only consumers (feature/home's
 * commonMain, NewsletterTriggerManager, StatisticsMath, ...).
 */
interface TimeSource : EpochMillisSource {
    /** Current wall-clock time in epoch milliseconds. */
    override fun nowEpochMillis(): Long

    /** Today's date in the given [zone]. */
    fun today(zone: ZoneId): LocalDate

    /**
     * Monotonic elapsed time in milliseconds since boot. For in-memory TTL
     * clocks only ([TtlCache]'s contract): unlike wall time it never jumps
     * backwards or forwards (NTP correction, manual clock set), but it resets
     * on reboot — which is fine because the in-memory caches it drives never
     * outlive a process.
     */
    fun nowElapsedRealtimeMillis(): Long
}

/**
 * Moved from the legacy `:core:data` `util/TimeSource.kt` (C4 part 2,
 * seam 4). The two clock reads now delegate to the model platform seams:
 *  - `nowEpochMillis` → [wallNowMillis] (`System.currentTimeMillis` on
 *    Android and desktop — identical to the legacy read).
 *  - `nowElapsedRealtimeMillis` → [monotonicNowMillis]. On Android that is
 *    `SystemClock.elapsedRealtime`, i.e. the exact legacy source (monotonic
 *    across deep sleep). On desktop JVM it is a `System.nanoTime`-based
 *    monotonic counter — a different origin, but the [TimeSource] contract
 *    only requires within-process monotonicity (deltas), never a comparable
 *    absolute value, so TTL math is unaffected.
 */
class SystemTimeSource : TimeSource {
    override fun nowEpochMillis(): Long = wallNowMillis()
    override fun today(zone: ZoneId): LocalDate = LocalDate.now(zone)
    override fun nowElapsedRealtimeMillis(): Long = monotonicNowMillis()
}
