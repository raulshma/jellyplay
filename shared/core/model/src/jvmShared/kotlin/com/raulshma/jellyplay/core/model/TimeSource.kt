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
 * split: the epoch-millis slice was promoted to the commonMain
 * [EpochMillisSource] seam (the promoted commonMain repository impls take
 * that type — java.time cannot cross into commonMain), so this interface
 * extends it. Every existing `TimeSource` fake across the repo therefore
 * satisfies [EpochMillisSource] unchanged, and the `today(zone)` java.time
 * surface stays available to the JVM-only consumers (feature/home's
 * commonMain, NewsletterTriggerManager, StatisticsMath, ...).
 *
 * D3 move note: born as `:core:data` jvmShared `util/TimeSource.kt`; moved
 * down to :shared:core:model jvmShared (same file/package root as the
 * platform clock seams it delegates to) because core:network sits BELOW
 * core:data in the module graph and could never adopt a seam living there.
 * Lives in jvmShared, not commonMain, for exactly the reason the
 * [EpochMillisSource] KDoc records: the `today(zone)` surface is java.time.
 * The old FQIN survives as a deprecated typealias in core:data until the
 * not-yet-migrated imports move per-touch.
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
