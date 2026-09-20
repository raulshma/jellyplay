package com.raulshma.jellyplay.core.data.util

/**
 * Common, epoch-millis-only slice of the clock seam — the promotion
 * counterpart to the JVM-facing [TimeSource] (jvmShared).
 *
 * Why the split: [TimeSource]'s surface includes `today(zone: ZoneId):
 * LocalDate` — a java.time signature that cannot live in commonMain while
 * feature/home's commonMain (and the jvmTest fakes across the repo) keep
 * calling it with java.time types, and this module must not break consumers
 * it does not own. The Room-backed repository impls promoted to commonMain in
 * (SearchHistoryRepositoryImpl, ItemPlaybackPreferenceRepositoryImpl,
 * PlaybackOutboxRepositoryImpl, MoodPlaylistRepository) only ever read
 * `nowEpochMillis`, so they depend on THIS interface — the widest common
 * clock slice.
 *
 * Wiring:
 *  - android/desktop: [SystemTimeSource] implements [TimeSource] which
 *    extends this interface, and dataJvmModule binds
 *    `EpochMillisSource -> get<TimeSource>()` — the same SystemTimeSource
 *    single serves both seams (one framework per clock).
 */
fun interface EpochMillisSource {

    /** Current wall-clock time in epoch milliseconds. */
    fun nowEpochMillis(): Long
}
