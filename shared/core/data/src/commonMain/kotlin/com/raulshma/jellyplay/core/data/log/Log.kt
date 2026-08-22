package com.raulshma.jellyplay.core.data.log

/**
 * Logging facade for data-layer code migrating off `android.util.Log`
 * (docs/kmp-migration-plan.md §Phase C4 part 2 — the "21 Log-only" portable
 * files). Signature-for-signature mirror of the `android.util.Log` overloads
 * the legacy `:core:data` actually calls (d/i/w/e; no `v`/`wtf` call sites
 * exist), so migrated files keep their log statements verbatim and only the
 * import flips.
 *
 * Android actual: delegates to `android.util.Log` unchanged — same tag
 * semantics, same returned log-line count.
 *
 * Desktop actual: `[LEVEL][tag] message` lines on stdout (stderr for `e`),
 * throwable stack traces included — a debugging aid, not logcat.
 */
expect object Log {
    fun d(tag: String, message: String): Int

    fun d(tag: String, message: String, throwable: Throwable?): Int

    fun i(tag: String, message: String): Int

    fun w(tag: String, message: String): Int

    fun w(tag: String, throwable: Throwable): Int

    fun w(tag: String, message: String, throwable: Throwable?): Int

    fun e(tag: String, message: String): Int

    fun e(tag: String, message: String, throwable: Throwable?): Int
}
