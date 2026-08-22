package com.raulshma.jellyplay.core.data.util

/**
 * Seam for the legacy `:core:data` `BuildConfig.DEBUG` flag
 * (docs/kmp-migration-plan.md §Phase C4 part 2): common code in
 * `:shared:core:data` cannot read an Android library's generated
 * `BuildConfig`, so moved call sites read [debugBuild] instead.
 *
 * Set from the app's `FLAG_DEBUGGABLE` by `androidDataModule` at startup
 * (and from the `jellyplay.debug` system property by `desktopDataModule`)
 * as a side effect of the module function — see those builders for why the
 * assignment must precede the module definition.
 */
object DataBuildFlags {
    var debugBuild: Boolean = false
}
