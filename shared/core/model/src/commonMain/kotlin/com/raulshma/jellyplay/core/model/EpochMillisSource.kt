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
 * D3 move note: born in `:core:data` commonMain `util` next to its JVM
 * supertype; promoted down to :shared:core:model when core:network
 * (strictly BELOW core:data in the module graph) needed the clock seam for
 * its daysSincePlayed filtering. The old FQIN survives as a deprecated
 * typealias there until the not-yet-migrated imports move per-touch.
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
