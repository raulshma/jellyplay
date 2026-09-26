package com.raulshma.jellyplay.core.data.util

/**
 * D3: the JVM clock seam moved down to :shared:core:model
 * (`com.raulshma.jellyplay.core.model.TimeSource` / `SystemTimeSource`) —
 * core:network sits BELOW core:data in the module graph and could never
 * adopt a seam living here, and the seam belongs beside the platform clock
 * functions (wallNowMillis / monotonicNowMillis) it already delegated to.
 * These aliases keep the ~30 not-yet-migrated imports across the repo
 * compiling unchanged (every existing FakeTimeSource satisfies the new type
 * identically — the seam's surface did not change); migrate per-touch.
 */
@Deprecated(
    message = "Moved to :shared:core:model — use the core.model TimeSource",
    replaceWith = ReplaceWith("TimeSource", "com.raulshma.jellyplay.core.model.TimeSource"),
)
typealias TimeSource = com.raulshma.jellyplay.core.model.TimeSource

@Deprecated(
    message = "Moved to :shared:core:model — use the core.model SystemTimeSource",
    replaceWith = ReplaceWith("SystemTimeSource", "com.raulshma.jellyplay.core.model.SystemTimeSource"),
)
typealias SystemTimeSource = com.raulshma.jellyplay.core.model.SystemTimeSource
