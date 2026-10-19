package com.raulshma.jellyplay.core.network.library

/**
 * The home pipeline's ONE cache-write generation token, branded so the raw
 * Long cannot travel the seam unnamed: the value of the data layer's single
 * monotonic home cache-write counter (`MediaRepositoryImpl.homeWriteGeneration`),
 * delivered to the two mutating [HomeSectionsCachePort] verbs so the fetcher's
 * write-guard mirror ([HomeSectionsFetcher.observedGeneration]) observes
 * exactly what its single owner produced — the roll protocol's
 * bump-at-invalidate-AND-commit rule, whose single owner is the KDoc on
 * `HomeFeed.rerollDiscoverRow`. A raw epoch of a different axis (the
 * detail-cache epoch, the feature layer's identity-scoped roll generations)
 * no longer type-checks where the token is required.
 *
 * Construction is the funnel's: the counter lives in the data layer and its
 * bump funnel is the only production writer, handing each verb its post-bump
 * value through [of] (the `CacheIdentity` precedent — private constructor,
 * named factories; tests construct through [of] the same way, any
 * strictly-advancing value being protocol-faithful). [INITIAL] is the zero
 * both guarded caches start on.
 *
 * Lives in core:network beside the port verbs it qualifies: the token's
 * whole journey runs between this module's port/fetcher and the data layer's
 * funnel, and core:network cannot see core:data (the dependency direction
 * that puts every other type these verbs carry in core:model — this one is
 * ours).
 *
 * Deliberately NOT a @JvmInline value class: an inline class's JVM slot is
 * its underlying `long` (the method name mangled, the parameter unboxed), so
 * mockk stubs of the port verbs receive a boxed Long and any `arg<T>`-style
 * capture casts — and fails. The token flows through test seams far more
 * often than through hot loops; a boxed data class costs nothing here and
 * keeps the port verbs stubbable.
 */
public data class HomeWriteGeneration private constructor(public val value: Long) {
    public companion object {
        /** The zero generation both guarded caches start on (a fresh counter, a fresh mirror). */
        public val INITIAL: HomeWriteGeneration = HomeWriteGeneration(0L)

        /**
         * Wraps a counter read into the token — the data layer's post-bump
         * funnel value in production, a strictly-advancing value in tests.
         */
        public fun of(value: Long): HomeWriteGeneration = HomeWriteGeneration(value)
    }
}
