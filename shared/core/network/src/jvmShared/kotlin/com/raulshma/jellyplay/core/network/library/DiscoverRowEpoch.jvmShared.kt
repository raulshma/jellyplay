package com.raulshma.jellyplay.core.network.library

import java.util.concurrent.atomic.AtomicLong

/**
 * JVM/Android actual of the discover-row epoch counter (see
 * HomeSectionsFetcher.kt in commonMain) — a plain `AtomicLong`, the same
 * shape as the detail epoch in `core:data`'s MediaRepositoryInternals. The
 * expect/actual seam exists because `kotlin.concurrent.atomics` is still
 * experimental at this stdlib version and this module's commonMain has no
 * other atomics dependency; androidMain and jvmMain both see this source set
 * through jvmShared, so one actual serves both targets.
 */
internal actual class DiscoverRowEpoch {
    private val value = AtomicLong(0L)

    actual fun incrementAndGet(): Long = value.incrementAndGet()

    actual fun get(): Long = value.get()
}
