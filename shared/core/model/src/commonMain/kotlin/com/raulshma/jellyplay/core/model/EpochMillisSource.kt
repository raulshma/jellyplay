package com.raulshma.jellyplay.core.model

/**
 * Common, epoch-millis-only slice of the clock seam — the promotion
 * counterpart to the JVM-facing `TimeSource` (jvmShared).
 *
 * Why the split: `TimeSource`'s surface includes `today(zone: ZoneId):
 * LocalDate` — a java.time signature that cannot live in commonMain while
 * feature/home's commonMain (and the jvmTest fakes across the repo) keep
 * calling it with java.time types, and consumers must not break. The
 * Room-backed repository impls promoted to commonMain
 * (SearchHistoryRepositoryImpl, ItemPlaybackPreferenceRepositoryImpl,
 * PlaybackOutboxRepositoryImpl, MoodPlaylistRepository) only ever read
 * `nowEpochMillis`, so they depend on THIS interface — the widest common
 * clock slice.
 *
 * Lives in :shared:core:model because core:network (strictly BELOW core:data
 * in the module graph) needs the clock seam for its daysSincePlayed
 * filtering. A deprecated typealias in core:data keeps the legacy FQIN
 * importable until per-touch migration completes.
 *
 * Wiring:
 *  - android/desktop: `SystemTimeSource` implements `TimeSource` which
 *    extends this interface, and dataJvmModule (still the seam's Koin
 *    owner — core:model has no Koin module by design) binds
 *    `EpochMillisSource -> get<TimeSource>()` — the same SystemTimeSource
 *    single serves both seams (one framework per clock).
 */
fun interface EpochMillisSource {

    /** Current wall-clock time in epoch milliseconds. */
    fun nowEpochMillis(): Long
}
