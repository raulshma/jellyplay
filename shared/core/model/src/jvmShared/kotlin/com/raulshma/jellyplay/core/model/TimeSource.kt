package com.raulshma.jellyplay.core.model

import java.time.LocalDate
import java.time.ZoneId

/**
 * Read-only seam over the system clocks, so time-aware logic (TTL gates,
 * refresh jitter, calendar windows) can be unit-tested by injecting a fake.
 *
 * The system implementation delegates to this module's platform seams
 * ([wallNowMillis] / [monotonicNowMillis]).
 *
 * Split: this interface extends the commonMain [EpochMillisSource] seam (the
 * commonMain repository impls take that type — java.time cannot cross into
 * commonMain). Every `TimeSource` fake across the repo therefore satisfies
 * [EpochMillisSource] unchanged, and the `today(zone)` java.time surface
 * stays available to the JVM-only consumers (feature/home's commonMain,
 * NewsletterTriggerManager, StatisticsMath, ...).
 *
 * Lives in :shared:core:model jvmShared (same file/package root as the
 * platform clock seams it delegates to) because core:network sits BELOW
 * core:data in the module graph and cannot adopt a seam living there.
 * jvmShared, not commonMain, because the `today(zone)` surface is java.time.
 * A deprecated typealias in core:data keeps the legacy FQIN importable until
 * per-touch migration completes.
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
 * The system clock implementation — the two clock reads delegate to this
 * module's platform seams:
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
